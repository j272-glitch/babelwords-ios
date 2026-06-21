package com.babelwords.app

import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.babelwords.app.ads.AdMobManager
import com.babelwords.app.bridge.AdBridge
import com.babelwords.app.bridge.SubscriptionBridge
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var adMobManager: AdMobManager? = null
    private lateinit var adBridge: AdBridge
    private lateinit var subscriptionBridge: SubscriptionBridge

    private val WEB_APP_URL = "https://linguagt.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        lifecycleScope.launch {
            MobileAds.initialize(this@MainActivity)
            adMobManager = AdMobManager(this@MainActivity) { event, data ->
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
            }
        }

        adBridge = AdBridge(this) {
            adMobManager
        }
        subscriptionBridge = SubscriptionBridge(this, webView)

        WebViewConfig.configure(webView, this, adBridge, subscriptionBridge)

        // Keep cookies so the production access gate only needs the code once:
        // ?access=<code> sets a 30-day "site_access" cookie, then later requests are allowed.
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
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
        subscriptionBridge.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
