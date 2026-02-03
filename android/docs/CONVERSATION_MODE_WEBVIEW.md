# Conversation Mode WebView Integration

This document provides detailed instructions for handling real-time conversation mode in an Android WebView.

## Overview

**LinguaVibe conversation mode uses HTTP Polling for maximum reliability across all platforms, including iOS Safari and Android WebView.**

**Communication Architecture:**
- **Receiving messages**: HTTP Long Polling to `/api/conversation/poll`
- **Sending messages**: HTTP POST to `/api/conversation/send`
- **Session management**: HTTP endpoints for create/join

This approach was chosen over WebSocket/SSE for better compatibility with mobile browsers and WebViews.

## Benefits of HTTP Polling Architecture

1. **No connection state to manage** - Each request is independent
2. **Works through all proxies** - Standard HTTP traffic
3. **Automatic browser reconnection** - No custom retry logic needed
4. **iOS Safari compatible** - No WebSocket/SSE issues
5. **Battery efficient** - No persistent connection drain

## Critical WebView Configuration

### 1. JavaScript & Storage

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    databaseEnabled = true
    
    // Enable caching for performance
    cacheMode = WebSettings.LOAD_DEFAULT
}
```

### 2. Audio Permissions

The WebView must grant audio capture permissions for voice recording:

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

### 3. Mixed Content (for development)

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
}
```

## Network State Monitoring

Monitor network changes to notify the web app when connectivity changes:

```kotlin
class ConversationNetworkMonitor(
    private val context: Context,
    private val webView: WebView
) {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    // Web app will automatically resume polling on next interval
                    webView.evaluateJavascript("""
                        console.log('[Android] Network available');
                        window.dispatchEvent(new CustomEvent('network_restored'));
                    """.trimIndent(), null)
                }
            }
            
            override fun onLost(network: Network) {
                mainHandler.post {
                    // Web app handles offline state gracefully
                    webView.evaluateJavascript("""
                        console.log('[Android] Network lost');
                        window.dispatchEvent(new CustomEvent('network_lost'));
                    """.trimIndent(), null)
                }
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    fun stop() {
        networkCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
    }
}
```

## Handling App Lifecycle

The HTTP polling automatically stops/resumes based on page visibility. For explicit control:

```kotlin
override fun onPause() {
    super.onPause()
    
    // Notify web app of background state
    webView.evaluateJavascript("""
        window.dispatchEvent(new CustomEvent('app_background'));
        // Polling automatically pauses on visibility change
    """.trimIndent(), null)
}

override fun onResume() {
    super.onResume()
    
    // Notify web app of foreground state
    webView.evaluateJavascript("""
        window.dispatchEvent(new CustomEvent('app_foreground'));
        // Polling automatically resumes on visibility change
    """.trimIndent(), null)
}
```

## HTTP Polling Behavior

The web app's `TranslationStreamService` handles all polling automatically:

| Event | Behavior |
|-------|----------|
| Tab visible | Polling starts/resumes |
| Tab hidden | Polling continues (for background operation) |
| Network offline | Polling pauses, auto-resumes when online |
| Network restored | Polling immediately resumes |
| Server error | Exponential backoff retry |

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

## Recording State Protection

During voice recording, the web app extends timeouts automatically. You can also add native-side awareness:

```kotlin
// Notify native layer when recording starts/stops
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun onRecordingStarted() {
        isRecording = true
        // Optionally acquire wake lock
    }
    
    @JavascriptInterface
    fun onRecordingStopped() {
        isRecording = false
        // Release wake lock if acquired
    }
}, "AndroidRecordingBridge")
```

## Error Recovery

The HTTP polling has built-in retry logic. For WebView-level errors:

```kotlin
webView.webViewClient = object : WebViewClient() {
    private var retryCount = 0
    private val maxRetries = 3
    
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            if (retryCount < maxRetries) {
                retryCount++
                Handler(Looper.getMainLooper()).postDelayed({
                    view.reload()
                }, 3000L * retryCount)
            } else {
                showConnectionError()
            }
        }
    }
    
    override fun onPageFinished(view: WebView, url: String) {
        retryCount = 0 // Reset on successful load
    }
}
```

## Wake Lock for Background Recording

Keep device awake during active recording:

```kotlin
private var wakeLock: PowerManager.WakeLock? = null

fun acquireWakeLock() {
    val pm = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "LinguaVibe::RecordingWakeLock"
    )
    wakeLock?.acquire(5 * 60 * 1000L) // 5 minutes max for recording
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
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
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

1. **Never block the main thread** - Use coroutines or handlers for WebView operations
2. **Always use Handler.post()** for WebView JavaScript calls from background threads
3. **Trust the web app's polling** - Don't add duplicate native-side polling
4. **Monitor network state** - Notify web app of connectivity changes
5. **Test on real devices** - Emulators don't simulate network issues well
6. **Handle orientation changes** - Use ViewModel or retain fragments
7. **Grant audio permissions** - Required for voice recording

## Debugging

Enable WebView debugging:

```kotlin
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

Then use Chrome DevTools at `chrome://inspect` to debug the WebView.

## API Endpoints Used

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/conversation/create` | POST | Create new session |
| `/api/conversation/join` | POST | Join existing session |
| `/api/conversation/poll` | GET | Long poll for messages |
| `/api/conversation/send` | POST | Send translation/audio |
| `/api/conversation/leave` | POST | Leave session |

All endpoints use standard HTTP with JSON bodies. No special headers or protocols required.
