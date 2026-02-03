package com.lingualink.linguagt.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.lingualink.linguagt.TestRigorLogger

class ConversationWebViewManager(
    private val context: Context,
    private val webView: WebView
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ConversationWebView"
        private const val WEBSOCKET_KEEP_ALIVE_INTERVAL = 15000L
        private const val CONNECTION_CHECK_INTERVAL = 5000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isConversationActive = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (isConversationActive) {
                sendKeepAlive()
                mainHandler.postDelayed(this, WEBSOCKET_KEEP_ALIVE_INTERVAL)
            }
        }
    }

    private val connectionCheckRunnable = object : Runnable {
        override fun run() {
            if (isConversationActive) {
                checkConnectionHealth()
                mainHandler.postDelayed(this, CONNECTION_CHECK_INTERVAL)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configureForConversation() {
        TestRigorLogger.logDebug("ConversationWebViewManager: Configuring WebView for conversation mode")
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webChromeClient = ConversationWebChromeClient()
        webView.webViewClient = ConversationWebViewClient()

        registerNetworkCallback()
        
        TestRigorLogger.logDebug("ConversationWebViewManager: Configuration complete")
    }

    fun startConversation() {
        isConversationActive = true
        mainHandler.post(keepAliveRunnable)
        mainHandler.post(connectionCheckRunnable)
        TestRigorLogger.logAdEvent("ConversationWebViewManager: Conversation mode started")
        Log.d(TAG, "Conversation mode started")
    }

    fun stopConversation() {
        isConversationActive = false
        mainHandler.removeCallbacks(keepAliveRunnable)
        mainHandler.removeCallbacks(connectionCheckRunnable)
        TestRigorLogger.logAdEvent("ConversationWebViewManager: Conversation mode stopped")
        Log.d(TAG, "Conversation mode stopped")
    }

    private fun sendKeepAlive() {
        val script = """
            (function() {
                if (window.conversationWebSocket && window.conversationWebSocket.readyState === 1) {
                    window.conversationWebSocket.send(JSON.stringify({ type: 'ping', timestamp: Date.now() }));
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun checkConnectionHealth() {
        val script = """
            (function() {
                var status = {
                    wsConnected: window.conversationWebSocket ? window.conversationWebSocket.readyState === 1 : false,
                    isRecording: window.isRecording || false,
                    sessionId: window.currentSessionId || null
                };
                return JSON.stringify(status);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "Connection health: $result")
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                TestRigorLogger.logDebug("ConversationWebViewManager: Network available")
                if (isConversationActive) {
                    mainHandler.post { notifyNetworkChange(true) }
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                TestRigorLogger.logWarning("ConversationWebViewManager: Network lost")
                if (isConversationActive) {
                    mainHandler.post { notifyNetworkChange(false) }
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d(TAG, "Network capabilities changed, hasInternet: $hasInternet")
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to register network callback", e)
        }
    }

    private fun notifyNetworkChange(isConnected: Boolean) {
        val script = """
            (function() {
                window.dispatchEvent(new CustomEvent('network_status_change', { 
                    detail: { isConnected: $isConnected } 
                }));
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    override fun onPause(owner: LifecycleOwner) {
        if (isConversationActive) {
            TestRigorLogger.logDebug("ConversationWebViewManager: App paused during conversation")
            val script = """
                (function() {
                    window.dispatchEvent(new CustomEvent('app_background'));
                })();
            """.trimIndent()
            webView.evaluateJavascript(script, null)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (isConversationActive) {
            TestRigorLogger.logDebug("ConversationWebViewManager: App resumed during conversation")
            val script = """
                (function() {
                    window.dispatchEvent(new CustomEvent('app_foreground'));
                })();
            """.trimIndent()
            webView.evaluateJavascript(script, null)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        TestRigorLogger.logDebug("ConversationWebViewManager: Destroying")
        stopConversation()
        networkCallback?.let {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                TestRigorLogger.logError("Failed to unregister network callback", e)
            }
        }
    }

    inner class ConversationWebChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            mainHandler.post {
                val resources = request.resources
                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.grant(resources)
                    TestRigorLogger.logAdEvent("ConversationWebViewManager: Microphone permission granted")
                    Log.d(TAG, "Microphone permission granted")
                } else {
                    request.deny()
                }
            }
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(TAG, "JS: ${consoleMessage.message()}")
            return true
        }
    }

    inner class ConversationWebViewClient : WebViewClient() {
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            Log.e(TAG, "WebView error: ${error.description}")
            TestRigorLogger.logError("ConversationWebViewManager: WebView error - ${error.description}")
            if (request.isForMainFrame && isConversationActive) {
                mainHandler.postDelayed({
                    if (isConversationActive) {
                        TestRigorLogger.logDebug("ConversationWebViewManager: Auto-reloading after error")
                        view.reload()
                    }
                }, 3000)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return if (url.startsWith("http://") || url.startsWith("https://")) {
                false
            } else {
                true
            }
        }
    }
}
