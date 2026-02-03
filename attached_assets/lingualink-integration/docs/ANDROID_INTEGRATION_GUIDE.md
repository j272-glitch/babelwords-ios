# LinguaVibe Android Integration Guide

## Overview

This guide covers integrating Google Play Billing for premium subscriptions and configuring WebView for conversation mode in your Android app.

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
        
        // Load web app
        webView.loadUrl("https://your-app-url.replit.app")
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

## Conversation Mode WebView Configuration

### Setup WebView for Real-Time Communication

```kotlin
class ConversationActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var conversationManager: ConversationWebViewManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)
        
        webView = findViewById(R.id.webView)
        
        // Initialize conversation manager
        conversationManager = ConversationWebViewManager(this, webView)
        conversationManager.configureForConversation()
        
        // Register lifecycle observer
        lifecycle.addObserver(conversationManager)
        
        // Load conversation page
        webView.loadUrl("https://your-app-url.replit.app/conversation")
    }
    
    fun onConversationStarted() {
        conversationManager.startConversation()
    }
    
    fun onConversationEnded() {
        conversationManager.stopConversation()
    }
}
```

### Key WebView Settings for Conversation Mode

```kotlin
webView.settings.apply {
    // Required for WebSocket communication
    javaScriptEnabled = true
    domStorageEnabled = true
    
    // Required for audio recording
    mediaPlaybackRequiresUserGesture = false
    
    // Handle mixed content (HTTPS + WS)
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    
    // Improve performance
    cacheMode = WebSettings.LOAD_DEFAULT
}
```

### Handle Microphone Permissions

```kotlin
class ConversationWebChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        mainHandler.post {
            if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
        }
    }
}
```

## WebSocket Stability Features

The conversation mode includes:

1. **Keep-Alive Pings**: Sends heartbeat every 15 seconds
2. **Connection Health Checks**: Monitors WebSocket state every 5 seconds
3. **Network Change Detection**: Automatically handles network transitions
4. **Background/Foreground Handling**: Notifies web app of app state changes
5. **Auto-Reconnect**: Attempts reconnection on network restore

## ANR Prevention

To prevent Application Not Responding errors:

```kotlin
// Always use Handler for WebView operations
mainHandler.post {
    webView.evaluateJavascript(script, null)
}

// Use coroutines for billing operations
scope.launch {
    // Billing operations here
}

// Don't block main thread
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

### WebSocket Disconnects

- Check network permissions
- Verify WebView JavaScript is enabled
- Check for battery optimization killing background connections

### Subscription Not Updating

- Verify purchase acknowledgement
- Check billing client connection
- Verify product IDs match Play Console

### Audio Recording Issues

- Request RECORD_AUDIO permission at runtime
- Grant WebView audio capture permission
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
    └── ANDROID_INTEGRATION_GUIDE.md    # This guide
```
