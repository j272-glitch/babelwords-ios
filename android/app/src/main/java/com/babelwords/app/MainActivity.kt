package com.babelwords.com

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.babelwords.com.analytics.AnalyticsManager
import com.babelwords.com.ads.AdMobManager
import com.babelwords.com.ads.AppOpenAdManager
import com.babelwords.com.ads.ConsentManager
import com.babelwords.com.bridge.AdBridge
import com.babelwords.com.bridge.SubscriptionBridge
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var adMobManager: AdMobManager? = null
    private lateinit var adBridge: AdBridge
    private lateinit var consentManager: ConsentManager
    private var appOpenAdManager: AppOpenAdManager? = null
    private lateinit var subscriptionBridge: SubscriptionBridge

    private val WEB_APP_URL = "https://linguagt.com"

    // ==================== Mic Safety ====================
    @Volatile
    var isMicActive: Boolean = false
        private set

    private val micWatchdogHandler = Handler(Looper.getMainLooper())
    private var micWatchdogRunnable: Runnable? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val MIC_WATCHDOG_MS = 45_000L
    }

    fun setMicState(active: Boolean) {
        isMicActive = active
        if (active) {
            micWatchdogRunnable?.let { micWatchdogHandler.removeCallbacks(it) }
            val watchdog = Runnable {
                if (isMicActive) {
                    isMicActive = false
                    Log.w(TAG, "isMicActive reset after ${MIC_WATCHDOG_MS / 1000}s (stale lock cleared)")
                }
            }
            micWatchdogRunnable = watchdog
            micWatchdogHandler.postDelayed(watchdog, MIC_WATCHDOG_MS)
        } else {
            micWatchdogRunnable?.let {
                micWatchdogHandler.removeCallbacks(it)
                micWatchdogRunnable = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        val loadingContainer = findViewById<View>(R.id.loading_container)
        val errorContainer = findViewById<View>(R.id.error_container)
        val retryButton = findViewById<Button>(R.id.retry_button)
        val offlineButton = findViewById<Button>(R.id.offline_mode_button)

        consentManager = ConsentManager(this)

        // Retry handler: reload the web app when the user taps Retry
        val onRetry = {
            errorContainer.visibility = View.GONE
            loadingContainer.visibility = View.VISIBLE
            webView.loadUrl(WEB_APP_URL)
        }
        retryButton.setOnClickListener { onRetry() }
        offlineButton.setOnClickListener {
            // Offline mode: load a local fallback or show a simplified UI
            errorContainer.visibility = View.GONE
            webView.loadUrl("file:///android_asset/offline.html")
        }

        lifecycleScope.launch {
            MobileAds.initialize(this@MainActivity)

            adMobManager = AdMobManager(
                this@MainActivity,
                eventCallback = { event, data ->
                    runOnUiThread {
                        val escaped = (data ?: "")
                            .replace("\\", "\\\\")
                            .replace("'", "\\'")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                        webView.evaluateJavascript(
                            "window.onAdBridgeEvent && window.onAdBridgeEvent('$event', '$escaped');",
                            null
                        )
                    }
                },
                getConsentManager = { consentManager }
            )

            appOpenAdManager = AppOpenAdManager(this@MainActivity) { consentManager }
            appOpenAdManager?.let { ProcessLifecycleOwner.get().lifecycle.addObserver(it) }

            // Wire network callback for auto-reload on connectivity return
            adMobManager?.registerNetworkCallback()

            // Show consent form if needed, then load ads
            consentManager.requestConsent(this@MainActivity) { canRequestAds ->
                adMobManager?.preloadInterstitial()
                appOpenAdManager?.loadAd()
            }
        }

        adBridge = AdBridge(
            this,
            adMobManagerProvider = { adMobManager },
            consentManagerProvider = { consentManager }
        )
        subscriptionBridge = SubscriptionBridge(this, webView)

        WebViewConfig.configure(
            webView, this, adBridge, subscriptionBridge,
            loadingView = loadingContainer,
            errorView = errorContainer,
            onRetry = onRetry
        )

        // Keep cookies so the production access gate only needs the code once
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        val accessToken = BuildConfig.ACCESS_TOKEN
        if (accessToken.isNotEmpty()) {
            webView.loadUrl("$WEB_APP_URL/?access=${Uri.encode(accessToken)}")
        } else {
            webView.loadUrl(WEB_APP_URL)
        }
    }

    fun evalJs(script: String) {
        runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        adMobManager?.onActivityResumed(this)
        AnalyticsManager.logScreenView("main", "MainActivity")
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        adMobManager?.onActivityPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        adMobManager?.unregisterNetworkCallback()
        adMobManager?.destroy()
        appOpenAdManager?.let {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(it)
            it.cleanup()
        }
        // Cancel mic watchdog
        micWatchdogRunnable?.let { micWatchdogHandler.removeCallbacks(it) }
        micWatchdogRunnable = null
        webView.destroy()
        subscriptionBridge.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
