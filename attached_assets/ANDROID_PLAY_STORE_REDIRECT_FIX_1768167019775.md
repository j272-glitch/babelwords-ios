# Android Play Store Redirect Fix

## Problem
After watching a rewarded ad, users are redirected to the Play Store instead of returning to the app. This happens with "app install" ads that promote other apps.

## Solution 1: Activity Configuration (Required)

Update your `MainActivity.kt` to properly handle returning from Play Store:

```kotlin
package com.lingualink.linguagt

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var adBridge: AdBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) { initializationStatus ->
            android.util.Log.d("MainActivity", "AdMob SDK initialized")
            adBridge.preloadAds()
        }
        
        webView = findViewById(R.id.webView)
        setupWebView()
        
        adBridge = AdBridge(this, webView)
        webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
        
        webView.loadUrl("https://linguagt.com")
    }
    
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
        
        webView.webViewClient = WebViewClient()
    }
    
    // CRITICAL: Handle returning from Play Store or other apps
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        android.util.Log.d("MainActivity", "onNewIntent called - user returned to app")
        
        // Bring activity to front
        setIntent(intent)
        
        // Ensure WebView is visible and focused
        webView.requestFocus()
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume - ensuring app is in foreground")
        
        // Restore WebView state if needed
        webView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        webView.onPause()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        adBridge.destroy()
    }
}
```

## Solution 2: AndroidManifest.xml Configuration (Required)

Ensure your manifest has these settings:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:taskAffinity=""
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
    android:hardwareAccelerated="true">
    
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    
    <!-- Deep link handling to return from Play Store -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="linguagt.com" />
    </intent-filter>
</activity>
```

Key settings:
- `launchMode="singleTask"` - Ensures only one instance of the activity
- `taskAffinity=""` - Keeps Play Store in separate task
- Deep link intent-filter - Allows returning to app from web links

## Solution 3: Block App Install Ads (Recommended)

In your AdMob Console:

1. Go to **AdMob Console** → **Blocking Controls**
2. Navigate to **Ad review center** or **General categories**
3. Find and block **"App Install"** category
4. Optionally block specific problematic advertisers

This prevents the redirect issue entirely by not showing ads that open Play Store.

## Solution 4: Ad Callback with Return Intent

Update your `AdBridge.kt` to bring app to foreground after ad closes:

```kotlin
private fun bringAppToForeground() {
    val intent = Intent(activity, activity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
    }
    activity.startActivity(intent)
}

private fun setupRewardedCallbacks() {
    rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "Rewarded dismissed")
            rewardedAd = null
            
            // CRITICAL: Bring app back to foreground
            bringAppToForeground()
            
            // Emit callback
            if (!rewardEarned) {
                emitCallback("if(window.onRewardedClosed) window.onRewardedClosed();")
            }
            
            loadRewarded()
        }
        
        // ... other callbacks
    }
}
```

Also update `setupInterstitialCallbacks()` similarly.

## Testing Checklist

- [ ] `launchMode="singleTask"` set in manifest
- [ ] `taskAffinity=""` added to isolate Play Store task
- [ ] `onNewIntent()` override implemented
- [ ] `bringAppToForeground()` called in ad dismiss callbacks
- [ ] App install ads blocked in AdMob console (optional but recommended)

## Why This Happens

When AdMob shows an "app install" ad:
1. User watches the ad video
2. User clicks (or ad auto-clicks) to Play Store
3. Play Store opens as new activity
4. When user presses back, Android may not know to return to your app
5. Without proper configuration, the back stack gets confused

With `singleTask` + `taskAffinity` + `bringAppToForeground()`:
1. Your app always has a single instance
2. Play Store runs in its own task
3. After ad dismiss, your app explicitly brings itself to front
4. User sees your app, not Play Store

## AdMob Console Settings

To completely prevent this issue, consider blocking app install ads:

1. Login to [AdMob Console](https://admob.google.com)
2. Go to **Blocking controls** → **General categories**
3. Block these categories:
   - Install Apps
   - Games
   - Social
   (Any categories that typically open Play Store)

This reduces revenue slightly but eliminates the redirect issue.
