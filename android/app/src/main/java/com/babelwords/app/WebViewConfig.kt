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
        // Redirect-loop detection: count repeated onPageStarted without onPageFinished
        // for the same base URL. If threshold exceeded, stop and show error UI.
        var pageStartCount = 0
        var lastBaseUrl: String? = null
        val REDIRECT_LOOP_THRESHOLD = 3

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // allowFileAccess=false is the secure default, but we need file:// access
            // for the offline fallback page (offline.html in assets). We only load our
            // own asset files, not arbitrary user files.
            allowFileAccess = true
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }

        webView.addJavascriptInterface(adBridge, "AdBridge")
        webView.addJavascriptInterface(subscriptionBridge, "AndroidSubscriptionBridge")

        webView.webViewClient = object : WebViewClient() {
            /** Extract base URL (scheme+host+path, no query) for redirect-loop detection. */
            private fun baseUrl(url: String?): String? {
                if (url == null) return null
                return try {
                    val u = java.net.URI(url)
                    "${u.scheme}://${u.host}${u.path}"
                } catch (_: Exception) { url }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                val base = baseUrl(url)
                if (base == lastBaseUrl) {
                    pageStartCount++
                } else {
                    lastBaseUrl = base
                    pageStartCount = 1
                }
                if (pageStartCount >= REDIRECT_LOOP_THRESHOLD) {
                    android.util.Log.e("WebViewError",
                        "Redirect loop detected ($pageStartCount starts for $base). Halting.")
                    view?.stopLoading()
                    loadingView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                    return  // Skip super call to avoid further processing
                }

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
                // Reset redirect counter on successful load
                pageStartCount = 0
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

            override fun onReceivedSslError(
                view: WebView, handler: SslErrorHandler, error: android.net.http.SslError
            ) {
                android.util.Log.e("WebViewError",
                    "SSL error on ${error.url}: ${error.primaryError}")
                // Cancel the load and show error UI instead of silently failing
                handler.cancel()
                loadingView.visibility = View.GONE
                errorView.visibility = View.VISIBLE
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Mic safety: reset mic on renderer crash
                (activity as? MainActivity)?.let {
                    if (it.isMicActive) {
                        it.setMicState(false)
                        android.util.Log.w("Mic", "Reset on renderer crash")
                    }
                }
                // Return true to indicate we handled it; WebView will be recreated on next loadUrl
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // NOTE: do NOT override onProgressChanged to toggle loadingView visibility.
            // Page-load progress can reset on redirects, which would re-show the overlay
            // after onPageFinished already hid it. Only main-frame lifecycle callbacks
            // (onPageStarted / onPageFinished / onReceivedError) should control the
            // overlay to prevent the spinner re-sticking after successful load.

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
