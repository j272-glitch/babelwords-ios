package com.babelwords.app

import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.babelwords.app.ads.AdMobManager
import com.babelwords.app.ads.ConsentManager
import com.babelwords.app.bridge.AdBridge
import com.babelwords.app.bridge.SubscriptionBridge
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var adMobManager: AdMobManager? = null
    private lateinit var adBridge: AdBridge
    private lateinit var consentManager: ConsentManager
    private lateinit var subscriptionBridge: SubscriptionBridge

    private val WEB_APP_URL = "https://linguagt.com"

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

            // Show consent form if needed, then load ads
            consentManager.requestConsent(this@MainActivity) { canRequestAds ->
                adMobManager?.preloadInterstitial()
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
        adMobManager?.cancelRetries()
        webView.destroy()
        subscriptionBridge.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
