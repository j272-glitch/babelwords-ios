package com.babelwords.com

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.webkit.*
import com.babelwords.com.bridge.AdBridge
import com.babelwords.com.bridge.SubscriptionBridge

object WebViewConfig {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        activity: Activity,
        adBridge: AdBridge,
        subscriptionBridge: SubscriptionBridge,
        loadingView: View,
        errorView: View,
        onRetry: () -> Unit,
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
                // Show loading overlay at start of every navigation
                loadingView.visibility = View.VISIBLE
                errorView.visibility = View.GONE

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
                // Page loaded successfully: hide loading, show webview
                if (url != "about:blank") {
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.GONE
                }
                view.evaluateJavascript("window.__androidWebChromeClient = true;", null)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    android.util.Log.e("WebViewError",
                        "Main-frame error: ${error.description} (code=${error.errorCode}) on ${request.url}")
                    // Show error UI instead of infinite reload loop
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
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
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                // Keep loading visible until page is at least 80% loaded
                if (newProgress < 80) {
                    loadingView.visibility = View.VISIBLE
                }
            }

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
