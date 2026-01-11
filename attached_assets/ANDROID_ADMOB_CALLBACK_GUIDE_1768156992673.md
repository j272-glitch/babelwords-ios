# Android AdMob Callback Implementation Guide

This guide explains how to implement the native Android AdBridge to work with LinguaVibe's Promise-based ad system.

## Overview

The web app's `ad-service.ts` now waits for native callbacks before confirming ad impressions. This ensures accurate impression tracking and fixes the issue where synchronous bridge returns were falsely counted as successful impressions.

## Required JavaScript Callbacks

Your Android AdBridge must emit these callbacks to the WebView:

| Callback | When to Call | Purpose |
|----------|-------------|---------|
| `window.onInterstitialClosed()` | After interstitial is dismissed | Confirms impression, grants bonus |
| `window.onRewardEarned(type, amount)` | After user earns reward | Confirms reward, grants 30 min access |
| `window.onInterstitialFailedToShow(error)` | When interstitial fails to display | Resets cooldown for retry |
| `window.onRewardedFailedToShow(error)` | When rewarded fails to display | Resets cooldown for retry |
| `window.onRewardedClosed()` | When rewarded dismissed without reward | User skipped, no reward granted |

## Complete AdBridge Implementation

### AdBridge.kt

```kotlin
package com.lingualink.linguagt

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "AdBridge"
        
        // Production Ad Unit IDs
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9991891515643313/5076005693"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-9991891515643313/6313049833"
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false
    private var rewardEarned = false
    
    // ========================================
    // INTERSTITIAL AD METHODS
    // ========================================
    
    fun loadInterstitial() {
        if (isLoadingInterstitial || interstitialAd != null) {
            Log.d(TAG, "Interstitial already loading or loaded")
            return
        }
        
        isLoadingInterstitial = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial loaded successfully")
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    setupInterstitialCallbacks()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }
    
    private fun setupInterstitialCallbacks() {
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed - impression counted by AdMob")
            }
            
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed by user")
                interstitialAd = null
                
                // CRITICAL: Emit callback to JavaScript
                emitCallback("if(window.onInterstitialClosed) window.onInterstitialClosed();")
                
                // Preload next ad
                loadInterstitial()
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Interstitial failed to show: ${error.message}")
                interstitialAd = null
                
                // CRITICAL: Emit failure callback to JavaScript
                val escapedError = error.message.replace("'", "\\'")
                emitCallback("if(window.onInterstitialFailedToShow) window.onInterstitialFailedToShow('$escapedError');")
                
                // Try to load another ad
                loadInterstitial()
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Interstitial clicked")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Interstitial impression recorded")
            }
        }
    }
    
    @JavascriptInterface
    fun showInterstitial(adUnitId: String): String {
        Log.d(TAG, "showInterstitial called with adUnitId: $adUnitId")
        
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "Interstitial not ready - attempting to load")
            loadInterstitial()
            return "not_ready"
        }
        
        activity.runOnUiThread {
            Log.d(TAG, "Showing interstitial on UI thread")
            ad.show(activity)
        }
        
        return "showing"
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        return interstitialAd != null
    }
    
    // ========================================
    // REWARDED AD METHODS
    // ========================================
    
    fun loadRewarded() {
        if (isLoadingRewarded || rewardedAd != null) {
            Log.d(TAG, "Rewarded already loading or loaded")
            return
        }
        
        isLoadingRewarded = true
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            activity,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded loaded successfully")
                    rewardedAd = ad
                    isLoadingRewarded = false
                    setupRewardedCallbacks()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded failed to load: ${error.message}")
                    rewardedAd = null
                    isLoadingRewarded = false
                }
            }
        )
    }
    
    private fun setupRewardedCallbacks() {
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded showed - impression counted by AdMob")
                rewardEarned = false // Reset reward flag
            }
            
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded dismissed by user, rewardEarned: $rewardEarned")
                rewardedAd = null
                
                // If reward wasn't earned, emit closed callback
                if (!rewardEarned) {
                    emitCallback("if(window.onRewardedClosed) window.onRewardedClosed();")
                }
                
                // Preload next ad
                loadRewarded()
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Rewarded failed to show: ${error.message}")
                rewardedAd = null
                
                // CRITICAL: Emit failure callback to JavaScript
                val escapedError = error.message.replace("'", "\\'")
                emitCallback("if(window.onRewardedFailedToShow) window.onRewardedFailedToShow('$escapedError');")
                
                // Try to load another ad
                loadRewarded()
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded clicked")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Rewarded impression recorded")
            }
        }
    }
    
    @JavascriptInterface
    fun showRewarded(adUnitId: String): String {
        Log.d(TAG, "showRewarded called with adUnitId: $adUnitId")
        
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded not ready - attempting to load")
            loadRewarded()
            return "not_ready"
        }
        
        activity.runOnUiThread {
            Log.d(TAG, "Showing rewarded on UI thread")
            ad.show(activity) { rewardItem ->
                // User earned the reward
                Log.d(TAG, "Reward earned: type=${rewardItem.type}, amount=${rewardItem.amount}")
                rewardEarned = true
                
                // CRITICAL: Emit reward callback to JavaScript
                emitCallback("if(window.onRewardEarned) window.onRewardEarned('${rewardItem.type}', ${rewardItem.amount});")
            }
        }
        
        return "showing"
    }
    
    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        return rewardedAd != null
    }
    
    // ========================================
    // UTILITY METHODS
    // ========================================
    
    private fun emitCallback(javascript: String) {
        activity.runOnUiThread {
            Log.d(TAG, "Emitting callback: $javascript")
            webView.evaluateJavascript(javascript, null)
        }
    }
    
    fun preloadAds() {
        Log.d(TAG, "Preloading ads...")
        loadInterstitial()
        loadRewarded()
    }
    
    fun destroy() {
        interstitialAd = null
        rewardedAd = null
    }
}
```

## MainActivity Integration

### MainActivity.kt

```kotlin
package com.lingualink.linguagt

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
        
        // Initialize Mobile Ads SDK FIRST
        MobileAds.initialize(this) { initializationStatus ->
            android.util.Log.d("MainActivity", "AdMob SDK initialized: $initializationStatus")
            
            // Preload ads after SDK is initialized
            adBridge.preloadAds()
        }
        
        // Set up WebView
        webView = findViewById(R.id.webView)
        setupWebView()
        
        // Create and register AdBridge
        adBridge = AdBridge(this, webView)
        webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
        
        // Load the web app
        webView.loadUrl("https://linguagt.com")
    }
    
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("MainActivity", "Page loaded: $url")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        adBridge.destroy()
    }
    
    // Handle back button properly - don't finish() while ads are showing
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
```

## AndroidManifest.xml Configuration

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.lingualink.linguagt">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="false">

        <!-- AdMob App ID - REQUIRED -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-9991891515643313~XXXXXXXXXX"/>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:hardwareAccelerated="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

## build.gradle Dependencies

```gradle
dependencies {
    // Google Mobile Ads SDK
    implementation 'com.google.android.gms:play-services-ads:22.6.0'
    
    // Other dependencies...
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.webkit:webkit:1.8.0'
}
```

## Checklist for Ensuring Ads Play

### AdMob Console Setup
- [ ] App is registered in AdMob console
- [ ] Ad units are created and status is "Active"
- [ ] App ID is correctly set in AndroidManifest.xml
- [ ] No policy violations on account

### Code Configuration
- [ ] Remove ALL test device IDs from production builds
- [ ] MobileAds.initialize() called before loading ads
- [ ] Ads preloaded on app start (after SDK init)
- [ ] FullScreenContentCallback implemented for all ad types
- [ ] JavaScript callbacks emitted on all events (success/failure/dismiss)

### Activity Configuration
- [ ] `android:launchMode="singleTask"` set in manifest
- [ ] `android:configChanges` includes orientation and screen size
- [ ] Never call `finish()` before/during ad display
- [ ] `ad.show(activity)` always called on UI thread

### Web App Verification
- [ ] `app-ads.txt` accessible at https://linguagt.com/app-ads.txt
- [ ] Contains: `google.com, pub-9991891515643313, DIRECT`
- [ ] `assetlinks.json` accessible at https://linguagt.com/.well-known/assetlinks.json

### Testing
- [ ] Check Logcat for "Interstitial loaded successfully"
- [ ] Check Logcat for "Rewarded loaded successfully"
- [ ] Verify "Emitting callback" logs appear when ads close
- [ ] Monitor AdMob dashboard for increasing impressions
- [ ] Test on multiple devices/network conditions

## Troubleshooting

### No Ads Loading
1. Check internet connectivity
2. Verify AdMob App ID is correct
3. Check for AdMob policy violations
4. Wait 24-48 hours for new ad units to serve

### Ads Load But Don't Show
1. Ensure `show()` is called on UI thread
2. Check Activity is not finishing
3. Verify ad is not null before showing
4. Check for FullScreenContentCallback errors

### Callbacks Not Firing
1. Ensure WebView reference is valid
2. Check `runOnUiThread` wrapper is used
3. Verify JavaScript interface name matches ("AndroidAdBridge")
4. Add logging to confirm callbacks are reached

### Low Impressions Despite High Requests
1. Ensure callbacks are actually emitting to JavaScript
2. Verify Promise-based ad-service.ts is deployed
3. Check for callback timeout (30 second limit)
4. Monitor both native and JavaScript logs

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2025-01-11 | 1.0 | Initial Promise-based callback implementation |
