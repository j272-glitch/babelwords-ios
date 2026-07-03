package com.babelwords.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.babelwords.app.ads.AdMobManager
import com.babelwords.app.ads.AppOpenAdManager
import com.babelwords.app.ads.ConsentManager
import com.babelwords.app.bridge.AdBridge
import com.babelwords.app.bridge.SubscriptionBridge
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var adMobManager: AdMobManager? = null
    private lateinit var adBridge: AdBridge
    private lateinit var consentManager: ConsentManager
    private lateinit var appOpenAdManager: AppOpenAdManager
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

        consentManager = ConsentManager(this)

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

            appOpenAdManager = AppOpenAdManager(this@MainActivity)
            ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenAdManager)

            // Show consent form if needed, then load ads
            consentManager.requestConsent(this@MainActivity) { canRequestAds ->
                adMobManager?.preloadInterstitial()
                appOpenAdManager.loadAd()
            }
        }

        adBridge = AdBridge(
            this,
            adMobManagerProvider = { adMobManager },
            consentManagerProvider = { consentManager }
        )
        subscriptionBridge = SubscriptionBridge(this, webView)

        WebViewConfig.configure(webView, this, adBridge, subscriptionBridge)

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
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        adMobManager?.onActivityPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        adMobManager?.destroy()
        appOpenAdManager.cleanup()
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
