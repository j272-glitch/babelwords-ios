# LinguaVibe Android Integration Guide

## Overview

This guide covers integrating Google Play Billing for premium subscriptions and configuring WebView for conversation mode in your Android app.

**Important:** Conversation mode uses HTTP Polling (not WebSocket or SSE) for maximum cross-platform reliability.

## Prerequisites

- Android Studio Arctic Fox or later
- Kotlin 1.6+
- Google Play Billing Library 6.0+
- Minimum SDK: 24 (Android 7.0)

## Setup

### 1. Add Dependencies

Add to your `app/build.gradle`:

```gradle
dependencies {
    // Google Play Billing
    implementation 'com.android.billingclient:billing-ktx:6.1.0'
    
    // Coroutines for async operations
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // Lifecycle for WebView management
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
}
```

### 2. Add Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="com.android.vending.BILLING" />
```

### 3. Configure Google Play Console

1. Go to Google Play Console → Your App → Monetization → Subscriptions
2. Create a subscription with ID: `premium_monthly`
3. Set base plan: $4.99/month with 3-day free trial
4. Activate the subscription

## Integration

### Initialize Subscription Manager

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var webView: WebView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        
        // Initialize subscription manager
        subscriptionManager = SubscriptionManager(this, this)
        subscriptionManager.initialize()
        
        // Add JavaScript bridge
        val subscriptionBridge = SubscriptionBridge(subscriptionManager, webView)
        webView.addJavascriptInterface(subscriptionBridge, SubscriptionBridge.BRIDGE_NAME)
        
        // Configure WebView settings
        configureWebView()
        
        // Load web app
        webView.loadUrl("https://your-app-url.replit.app")
    }
    
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        // Grant microphone permissions
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        subscriptionManager.destroy()
    }
}
```

### JavaScript Bridge Usage (Web App)

The web app can interact with the subscription system:

```javascript
// Check if running in Android app
const isAndroid = typeof window.AndroidSubscriptionBridge !== 'undefined';

// Subscribe
if (isAndroid) {
    window.AndroidSubscriptionBridge.subscribe('premium_monthly');
}

// Restore purchases
if (isAndroid) {
    window.AndroidSubscriptionBridge.restorePurchases();
}

// Check subscription status
if (isAndroid) {
    const isPremium = window.AndroidSubscriptionBridge.checkSubscription();
    const status = window.AndroidSubscriptionBridge.getSubscriptionStatus();
}

// Listen for subscription events
window.addEventListener('subscription_event', (event) => {
    const data = event.detail;
    
    switch (data.event) {
        case 'subscription_purchased':
            console.log('Subscription purchased!', data.productId);
            // Update UI, save to server
            break;
            
        case 'subscription_restored':
            console.log('Subscription restored!', data.productId);
            break;
            
        case 'subscription_error':
            console.error('Subscription error:', data.message);
            break;
            
        case 'premium_status_changed':
            console.log('Premium status:', data.isPremium);
            break;
    }
});
```

## Conversation Mode (HTTP Polling)

**The conversation mode uses HTTP Polling** - not WebSocket or SSE. This provides maximum reliability across all platforms.

### How It Works

1. **Sending messages**: Web app makes HTTP POST requests
2. **Receiving messages**: Web app polls server at regular intervals
3. **No connection state**: Each request is independent

### WebView Configuration for Conversation Mode

```kotlin
class ConversationActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var networkMonitor: ConversationNetworkMonitor
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)
        
        webView = findViewById(R.id.webView)
        
        // Configure WebView for conversation
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        // Grant microphone permission for voice recording
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    request.grant(request.resources)
                }
            }
        }
        
        // Monitor network changes
        networkMonitor = ConversationNetworkMonitor(this, webView)
        networkMonitor.start()
        
        // Load conversation page
        webView.loadUrl("https://your-app-url.replit.app/sse-conversation")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stop()
    }
}
```

### Network Monitoring

```kotlin
class ConversationNetworkMonitor(
    private val context: Context,
    private val webView: WebView
) {
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('network_restored'));", 
                        null
                    )
                }
            }
            
            override fun onLost(network: Network) {
                handler.post {
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('network_lost'));", 
                        null
                    )
                }
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback!!)
    }

    fun stop() {
        callback?.let {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(it)
        }
    }
}
```

## ANR Prevention

To prevent Application Not Responding errors:

```kotlin
// Always use Handler for WebView operations from background threads
handler.post {
    webView.evaluateJavascript(script, null)
}

// Use coroutines for billing operations
scope.launch {
    // Billing operations here
}

// Don't block main thread with network calls
GlobalScope.launch(Dispatchers.IO) {
    // Network/database operations
}
```

## Testing

### Test Subscription Flow

1. Use Google Play Console test accounts
2. Use `android.test.purchased` for testing
3. Clear test purchases in Play Console between tests

### Test Conversation Mode

1. Test with device on WiFi and mobile data
2. Test network transitions (WiFi → mobile)
3. Test app backgrounding during conversation
4. Test with poor network conditions

## Troubleshooting

### HTTP Polling Issues

- Check network permissions in manifest
- Verify WebView JavaScript is enabled
- Check for CORS issues (use same-origin or proper headers)

### Subscription Not Updating

- Verify purchase acknowledgement
- Check billing client connection
- Verify product IDs match Play Console

### Audio Recording Issues

- Request RECORD_AUDIO permission at runtime
- Grant WebView audio capture permission in onPermissionRequest
- Check `mediaPlaybackRequiresUserGesture = false`

## File Structure

```
android-integration/
├── src/main/java/com/lingualink/
│   ├── billing/
│   │   └── SubscriptionManager.kt      # Google Play Billing integration
│   └── webview/
│       ├── SubscriptionBridge.kt       # JS bridge for subscriptions
│       └── ConversationWebViewManager.kt # WebView conversation handler
└── docs/
    ├── ANDROID_INTEGRATION_GUIDE.md    # This guide
    └── CONVERSATION_MODE_WEBVIEW.md    # Detailed WebView configuration
```
