package com.linguawonder.app

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.*
import com.linguawonder.app.bridge.AdBridge
import com.linguawonder.app.bridge.SubscriptionBridge

object WebViewConfig {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        activity: Activity,
        adBridge: AdBridge,
        subscriptionBridge: SubscriptionBridge,
    ) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }

        webView.addJavascriptInterface(adBridge, "AdBridge")
        webView.addJavascriptInterface(subscriptionBridge, "AndroidSubscriptionBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript("window.__androidWebChromeClient = true;", null)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadUrl("about:blank")
                    view.loadUrl(view.url ?: "about:blank")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }
    }
}
