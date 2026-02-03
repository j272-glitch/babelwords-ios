# Conversation Mode WebView Integration

This document provides detailed instructions for handling real-time conversation mode in an Android WebView.

## Overview

Conversation mode uses WebSockets for real-time communication between devices. This requires special WebView configuration to maintain stable connections.

## Critical WebView Configuration

### 1. JavaScript & Storage

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
}
```

### 2. Audio Permissions

The WebView must grant audio capture permissions:

```kotlin
webView.webChromeClient = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        val grantedResources = mutableListOf<String>()
        
        for (resource in request.resources) {
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    grantedResources.add(resource)
                }
            }
        }
        
        if (grantedResources.isNotEmpty()) {
            request.grant(grantedResources.toTypedArray())
        } else {
            request.deny()
        }
    }
}
```

### 3. Mixed Content for WebSocket

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
}
```

## WebSocket Keep-Alive

The web app sends heartbeat pings, but you should also implement native keep-alive:

```kotlin
private val keepAliveHandler = Handler(Looper.getMainLooper())
private val keepAliveInterval = 15000L // 15 seconds

private val keepAliveRunnable = object : Runnable {
    override fun run() {
        if (isConversationActive) {
            webView.evaluateJavascript("""
                if (window.conversationWebSocket?.readyState === 1) {
                    window.conversationWebSocket.send(JSON.stringify({
                        type: 'ping',
                        timestamp: Date.now()
                    }));
                }
            """.trimIndent(), null)
            
            keepAliveHandler.postDelayed(this, keepAliveInterval)
        }
    }
}

fun startKeepAlive() {
    keepAliveHandler.post(keepAliveRunnable)
}

fun stopKeepAlive() {
    keepAliveHandler.removeCallbacks(keepAliveRunnable)
}
```

## Network State Monitoring

Monitor network changes to handle reconnection:

```kotlin
private fun registerNetworkCallback() {
    val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                // Notify web app to reconnect
                webView.evaluateJavascript("""
                    window.dispatchEvent(new CustomEvent('network_restored'));
                """.trimIndent(), null)
            }
        }
        
        override fun onLost(network: Network) {
            runOnUiThread {
                // Notify web app of disconnection
                webView.evaluateJavascript("""
                    window.dispatchEvent(new CustomEvent('network_lost'));
                """.trimIndent(), null)
            }
        }
    }
    
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    
    cm.registerNetworkCallback(request, callback)
}
```

## Handling App Lifecycle

When app goes to background, notify the web app:

```kotlin
override fun onPause() {
    super.onPause()
    
    if (isConversationActive) {
        webView.evaluateJavascript("""
            window.dispatchEvent(new CustomEvent('app_background'));
            // Pause non-essential operations
            if (window.pausePreloading) window.pausePreloading();
        """.trimIndent(), null)
    }
}

override fun onResume() {
    super.onResume()
    
    if (isConversationActive) {
        webView.evaluateJavascript("""
            window.dispatchEvent(new CustomEvent('app_foreground'));
            // Resume operations and check connection
            if (window.checkConnectionHealth) window.checkConnectionHealth();
        """.trimIndent(), null)
    }
}
```

## Recording State Protection

During recording, protect the WebSocket connection:

```kotlin
// Notify native layer when recording starts/stops
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun onRecordingStarted() {
        isRecording = true
        // Extend keep-alive interval, prevent cleanup
    }
    
    @JavascriptInterface
    fun onRecordingStopped() {
        isRecording = false
        // Resume normal operation
    }
}, "AndroidRecordingBridge")
```

## Session Management

Handle session codes and joining:

```kotlin
// JavaScript interface for session management
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun getDeviceId(): String {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }
    
    @JavascriptInterface
    fun saveSessionCode(code: String) {
        getSharedPreferences("conversation", MODE_PRIVATE)
            .edit()
            .putString("last_session_code", code)
            .apply()
    }
    
    @JavascriptInterface
    fun getLastSessionCode(): String? {
        return getSharedPreferences("conversation", MODE_PRIVATE)
            .getString("last_session_code", null)
    }
}, "AndroidSessionBridge")
```

## Error Recovery

Implement automatic recovery for common errors:

```kotlin
webView.webViewClient = object : WebViewClient() {
    private var retryCount = 0
    private val maxRetries = 3
    
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame && isConversationActive) {
            if (retryCount < maxRetries) {
                retryCount++
                Handler(Looper.getMainLooper()).postDelayed({
                    view.reload()
                }, 3000L * retryCount)
            } else {
                // Show error UI or fallback
                showConnectionError()
            }
        }
    }
    
    override fun onPageFinished(view: WebView, url: String) {
        retryCount = 0 // Reset on successful load
    }
}
```

## Wake Lock for Background Operation

Keep device awake during active conversation:

```kotlin
private var wakeLock: PowerManager.WakeLock? = null

fun acquireWakeLock() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "LinguaVibe::ConversationWakeLock"
    )
    wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes max
}

fun releaseWakeLock() {
    wakeLock?.release()
    wakeLock = null
}
```

## Audio Focus Management

Handle audio focus for recording:

```kotlin
private lateinit var audioManager: AudioManager
private var audioFocusRequest: AudioFocusRequest? = null

fun requestAudioFocus() {
    audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        
        audioManager.requestAudioFocus(audioFocusRequest!!)
    }
}

fun abandonAudioFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
```

## Best Practices

1. **Never block the main thread** - Use coroutines or handlers
2. **Always use Handler.post()** for WebView JavaScript calls
3. **Monitor WebSocket state** - Check readyState before sending
4. **Implement graceful degradation** - Show offline UI when disconnected
5. **Test on real devices** - Emulators don't simulate network issues well
6. **Handle orientation changes** - Use ViewModel or retain fragments
7. **Clear WebView cache periodically** - Prevents storage issues

## Debugging

Enable WebView debugging:

```kotlin
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

Then use Chrome DevTools at `chrome://inspect` to debug the WebView.
