# Android WebView Callbacks Integration Guide

## Overview

This guide explains how to properly connect the Android `AdPreloadManager` to the web app via JavaScript callbacks. The web app is fully configured and waiting for these callbacks.

---

## Web App Expected Callbacks

The web app registers these global JavaScript functions that Android MUST call:

| Callback | When to Call | Parameters |
|----------|--------------|------------|
| `window.onNativeAdReady(adType)` | After ad finishes loading | `'interstitial'` or `'rewarded'` |
| `window.onRewardEarned(type, amount)` | After user watches rewarded ad | `type: string`, `amount: number` |
| `window.onAdEvent(eventJson)` | For various ad lifecycle events | JSON string (see below) |

---

## AndroidAdBridge Implementation

Create or update your `AndroidAdBridge.kt` class:

```kotlin
package com.lingualink.linguagt.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.os.Handler
import android.os.Looper
import com.lingualink.linguagt.ads.AdPreloadManager

/**
 * JavaScript interface that bridges WebView to native AdMob ads.
 * All methods annotated with @JavascriptInterface are callable from JavaScript.
 */
class AndroidAdBridge(
    private val webView: WebView
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val TAG = "AndroidAdBridge"
    }
    
    // ═══════════════════════════════════════════════════════════════
    // CRITICAL: Initialize callbacks to AdPreloadManager
    // Call this in MainActivity.onCreate() AFTER WebView is ready
    // ═══════════════════════════════════════════════════════════════
    
    fun setupAdCallbacks() {
        // When interstitial is ready to show
        AdPreloadManager.onInterstitialReady = {
            notifyWebApp("window.onNativeAdReady('interstitial')")
        }
        
        // When rewarded ad is ready to show
        AdPreloadManager.onRewardedReady = {
            notifyWebApp("window.onNativeAdReady('rewarded')")
        }
        
        // When any ad is dismissed/closed
        AdPreloadManager.onAdDismissed = {
            val event = """{"type":"adClosed","adType":"interstitial"}"""
            notifyWebApp("window.onAdEvent('$event')")
        }
        
        // When user earns reward from watching ad
        AdPreloadManager.onRewardEarned = { type, amount ->
            notifyWebApp("window.onRewardEarned('$type', $amount)")
        }
        
        android.util.Log.d(TAG, "✅ Ad callbacks connected to WebView")
    }
    
    // ═══════════════════════════════════════════════════════════════
    // JavaScript Interface Methods
    // These are called FROM JavaScript via window.AndroidAdBridge.*
    // ═══════════════════════════════════════════════════════════════
    
    @JavascriptInterface
    fun loadInterstitial(placementId: String) {
        android.util.Log.d(TAG, "📥 JS called loadInterstitial: $placementId")
        mainHandler.post {
            AdPreloadManager.preloadInterstitial(webView.context)
        }
    }
    
    @JavascriptInterface
    fun showInterstitial(placementId: String): String {
        android.util.Log.d(TAG, "📺 JS called showInterstitial: $placementId")
        mainHandler.post {
            AdPreloadManager.showInterstitial { shown ->
                if (shown) {
                    // Ad was shown - impression tracked by AdMob
                    val event = """{"type":"adImpression","adType":"interstitial"}"""
                    notifyWebApp("window.onAdEvent('$event')")
                } else {
                    val event = """{"type":"adFailed","adType":"interstitial","error":"show_failed"}"""
                    notifyWebApp("window.onAdEvent('$event')")
                }
            }
        }
        return "showing"
    }
    
    @JavascriptInterface
    fun loadRewarded(placementId: String) {
        android.util.Log.d(TAG, "📥 JS called loadRewarded: $placementId")
        mainHandler.post {
            AdPreloadManager.preloadRewarded(webView.context)
        }
    }
    
    @JavascriptInterface
    fun showRewarded(placementId: String): String {
        android.util.Log.d(TAG, "🎬 JS called showRewarded: $placementId")
        mainHandler.post {
            AdPreloadManager.showRewarded { rewarded, rewardType, rewardAmount ->
                if (rewarded) {
                    // Reward will be delivered via onRewardEarned callback
                    val event = """{"type":"adImpression","adType":"rewarded"}"""
                    notifyWebApp("window.onAdEvent('$event')")
                } else {
                    val event = """{"type":"adFailed","adType":"rewarded","error":"show_failed"}"""
                    notifyWebApp("window.onAdEvent('$event')")
                }
            }
        }
        return "showing"
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): String {
        return if (AdPreloadManager.isInterstitialReady()) "true" else "false"
    }
    
    @JavascriptInterface
    fun isRewardedReady(): String {
        return if (AdPreloadManager.isRewardedReady()) "true" else "false"
    }
    
    // ═══════════════════════════════════════════════════════════════
    // CRITICAL: Notify WebApp via evaluateJavascript
    // This is the key method that bridges native events to JavaScript
    // ═══════════════════════════════════════════════════════════════
    
    private fun notifyWebApp(jsCode: String) {
        mainHandler.post {
            try {
                android.util.Log.d(TAG, "📤 Calling JS: $jsCode")
                webView.evaluateJavascript(jsCode) { result ->
                    android.util.Log.d(TAG, "   → JS result: $result")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ evaluateJavascript failed: ${e.message}")
            }
        }
    }
}
```

---

## MainActivity Integration

Update your `MainActivity.kt`:

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var adBridge: AndroidAdBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        
        // Initialize AdMob SDK
        AdPreloadManager.initialize(this)
        
        // Setup WebView
        setupWebView()
        
        // Create and register the bridge
        adBridge = AndroidAdBridge(webView)
        webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
        
        // CRITICAL: Connect ad callbacks AFTER bridge is registered
        adBridge.setupAdCallbacks()
        
        // Load the web app
        webView.loadUrl("https://your-app-url.replit.app")
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
                // WebView is ready, JavaScript can now call the bridge
                Log.d("MainActivity", "✅ WebView loaded, bridge available")
            }
        }
    }
}
```

---

## AdPreloadManager Updates Required

Your `AdPreloadManager.kt` needs these callback signatures:

```kotlin
object AdPreloadManager {
    
    // Callback properties - set these from AndroidAdBridge
    var onInterstitialReady: (() -> Unit)? = null
    var onRewardedReady: (() -> Unit)? = null
    var onAdDismissed: (() -> Unit)? = null
    var onRewardEarned: ((type: String, amount: Int) -> Unit)? = null
    
    // Check if ads are ready
    fun isInterstitialReady(): Boolean {
        return interstitialAd != null && isInterstitialFresh()
    }
    
    fun isRewardedReady(): Boolean {
        return rewardedAd != null && isRewardedFresh()
    }
    
    // Show interstitial with callback
    fun showInterstitial(callback: (shown: Boolean) -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            callback(false)
            return
        }
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Impression tracked automatically by AdMob
            }
            
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onAdDismissed?.invoke()
                preloadInterstitial(context) // Preload next
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                callback(false)
            }
        }
        
        ad.show(activity)
        callback(true)
    }
    
    // Show rewarded with callback
    fun showRewarded(callback: (rewarded: Boolean, type: String, amount: Int) -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            callback(false, "", 0)
            return
        }
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onAdDismissed?.invoke()
                preloadRewarded(context) // Preload next
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                callback(false, "", 0)
            }
        }
        
        ad.show(activity) { rewardItem ->
            // User earned reward
            val type = rewardItem.type
            val amount = rewardItem.amount
            onRewardEarned?.invoke(type, amount)
            callback(true, type, amount)
        }
    }
    
    // In your InterstitialAdLoadCallback:
    private val interstitialLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
            interstitialAd = ad
            interstitialLoadTime = System.currentTimeMillis()
            
            // CRITICAL: Notify JavaScript that ad is ready
            onInterstitialReady?.invoke()
        }
        
        override fun onAdFailedToLoad(error: LoadAdError) {
            // Handle error, retry with backoff
        }
    }
    
    // In your RewardedAdLoadCallback:
    private val rewardedLoadCallback = object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
            rewardedAd = ad
            rewardedLoadTime = System.currentTimeMillis()
            
            // CRITICAL: Notify JavaScript that ad is ready
            onRewardedReady?.invoke()
        }
        
        override fun onAdFailedToLoad(error: LoadAdError) {
            // Handle error, retry with backoff
        }
    }
}
```

---

## Event Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         USER PRESSES AD BUTTON                   │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  WEB APP: adManager.showInterstitial()                          │
│  → Calls: window.AndroidAdBridge.loadInterstitial(placementId)  │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  ANDROID: @JavascriptInterface loadInterstitial()               │
│  → Calls: AdPreloadManager.preloadInterstitial()                │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  ADMOB SDK: Loads ad from network                               │
│  → onAdLoaded() callback fires                                  │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  ANDROID: onInterstitialReady?.invoke()                         │
│  → notifyWebApp("window.onNativeAdReady('interstitial')")       │
│  → webView.evaluateJavascript(...)                              │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  WEB APP: window.onNativeAdReady('interstitial') executes       │
│  → Sets isInterstitialReady = true                              │
│  → Calls: window.AndroidAdBridge.showInterstitial()             │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  ANDROID: @JavascriptInterface showInterstitial()               │
│  → Calls: interstitialAd.show(activity)                         │
│  → AD DISPLAYS TO USER                                          │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  USER CLOSES AD                                                  │
│  → onAdDismissedFullScreenContent()                             │
│  → onAdDismissed?.invoke()                                      │
│  → notifyWebApp("window.onAdEvent('{\"type\":\"adClosed\"...}')│
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│  WEB APP: Receives adClosed event                               │
│  → Resolves Promise                                             │
│  → Grants +5 translations or premium time                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Testing Checklist

1. **Bridge Registration**
   - [ ] `webView.addJavascriptInterface(bridge, "AndroidAdBridge")` called
   - [ ] `adBridge.setupAdCallbacks()` called AFTER bridge registration

2. **Load Flow**
   - [ ] JS calls `AndroidAdBridge.loadInterstitial()` → logged in Logcat
   - [ ] AdMob SDK loads ad → `onAdLoaded` fires
   - [ ] `onInterstitialReady?.invoke()` called
   - [ ] `evaluateJavascript("window.onNativeAdReady...")` called

3. **Show Flow**
   - [ ] JS calls `AndroidAdBridge.showInterstitial()` → logged in Logcat
   - [ ] `interstitialAd.show(activity)` called → ad displays
   - [ ] User closes ad → `onAdDismissed?.invoke()` called
   - [ ] `evaluateJavascript("window.onAdEvent...")` called

4. **Console Verification**
   - Check browser console for: `[AdManager] 📦 onNativeAdReady: interstitial`
   - Check Logcat for: `📤 Calling JS: window.onNativeAdReady...`

---

## Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| JS never receives callbacks | `evaluateJavascript` not called | Add `notifyWebApp()` calls in callbacks |
| "Bridge not found" | Bridge registered too late | Register before `loadUrl()` |
| Ad loads but never shows | `showInterstitial` not called after ready | Check `onNativeAdReady` triggers show |
| Callbacks on wrong thread | Not using `mainHandler.post` | Wrap all WebView calls in Handler |
