package com.babelwords.app

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.*
import com.babelwords.app.bridge.AdBridge
import com.babelwords.app.bridge.SubscriptionBridge

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
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // Mic safety: reset mic on navigation (stale lock from crashed renderer)
                (activity as? MainActivity)?.let {
                    if (it.isMicActive) {
                        it.setMicState(false)
                        android.util.Log.d("Mic", "Reset on navigation")
                    }
                }
                super.onPageStarted(view, url, favicon)
            }

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

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Mic safety: reset mic on renderer crash
                (activity as? MainActivity)?.let {
                    if (it.isMicActive) {
                        it.setMicState(false)
                        android.util.Log.w("Mic", "Reset on renderer crash")
                    }
                }
                return super.onRenderProcessGone(view, detail)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val allowed = request.resources.filter { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> true
                        else -> false
                    }
                }
                if (allowed.isNotEmpty()) {
                    request.grant(allowed.toTypedArray())
                } else {
                    request.deny()
                }
            }
        }
    }
}
