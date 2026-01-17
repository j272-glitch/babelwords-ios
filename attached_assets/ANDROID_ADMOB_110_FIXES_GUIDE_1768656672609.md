# Android AdMob Implementation Guide
# 110 Fixes for Request-to-Impression Conversion

This guide addresses the critical issue: **770 ad requests, 0 impressions, 73.5% match rate**

The high match rate means AdMob is finding ads for requests. Zero impressions means ads are never actually displayed. This guide covers all Android-side fixes.

---

## TABLE OF CONTENTS

1. [Category 1: Native Bridge Issues (Fixes 1-20)](#category-1-native-bridge-issues)
2. [Category 2: Ad Loading/Caching Issues (Fixes 21-35)](#category-2-ad-loadingcaching-issues)
3. [Category 3: Display Timing Issues (Fixes 36-50)](#category-3-display-timing-issues)
4. [Category 4: Visibility/Foreground Issues (Fixes 51-60)](#category-4-visibilityforeground-issues)
5. [Category 5: Ad Unit Configuration (Fixes 61-70)](#category-5-ad-unit-configuration)
6. [Category 6: Network/Connectivity (Fixes 71-80)](#category-6-networkconnectivity)
7. [Category 7: User Experience (Fixes 81-90)](#category-7-user-experience)
8. [Category 8: Policy/Compliance (Fixes 91-100)](#category-8-policycompliance)
9. [Category 9: SDK/Integration (Fixes 101-110)](#category-9-sdkintegration)

---

## CATEGORY 1: NATIVE BRIDGE ISSUES

### Fix #1: Bridge Method Signature Match

**Problem:** JavaScript calls `showInterstitial(placementId)` but Kotlin has `showInterstitial()` without parameter.

**Solution:**
```kotlin
// AdBridge.kt
class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    // Match JavaScript signature - accept placementId parameter
    @JavascriptInterface
    fun showInterstitial(placementId: String): String {
        Log.d("AdBridge", "showInterstitial called with placementId: $placementId")
        return showInterstitialInternal()
    }
    
    // Also support no-parameter version for backwards compatibility
    @JavascriptInterface
    fun showInterstitial(): String {
        return showInterstitialInternal()
    }
    
    private fun showInterstitialInternal(): String {
        if (interstitialAd == null) {
            loadInterstitial()
            return "not_ready"
        }
        
        activity.runOnUiThread {
            interstitialAd?.show(activity)
        }
        return "showing"
    }
    
    @JavascriptInterface
    fun showRewarded(placementId: String): String {
        Log.d("AdBridge", "showRewarded called with placementId: $placementId")
        return showRewardedInternal()
    }
    
    @JavascriptInterface
    fun showRewarded(): String {
        return showRewardedInternal()
    }
}
```

### Fix #2: Callback Firing to JavaScript

**Problem:** Native `onAdDismissedFullScreenContent` never calls JavaScript callback.

**Solution:**
```kotlin
// AdBridge.kt - Interstitial Callbacks
private fun setupInterstitialCallbacks(ad: InterstitialAd) {
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        
        override fun onAdShowedFullScreenContent() {
            Log.d("AdBridge", "Interstitial shown - ad displayed on screen")
        }
        
        override fun onAdDismissedFullScreenContent() {
            Log.d("AdBridge", "Interstitial dismissed - calling JS callback")
            interstitialAd = null
            
            // CRITICAL: Call JavaScript callback
            callJavaScript("window.onInterstitialClosed && window.onInterstitialClosed()")
            
            // Preload next ad
            loadInterstitial()
        }
        
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.e("AdBridge", "Interstitial failed to show: ${error.message}, code: ${error.code}")
            interstitialAd = null
            
            // CRITICAL: Call JavaScript error callback
            callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('${error.message}', ${error.code})")
        }
    }
}

// Rewarded Callbacks
private fun setupRewardedCallbacks(ad: RewardedAd) {
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        
        override fun onAdShowedFullScreenContent() {
            Log.d("AdBridge", "Rewarded shown - ad displayed on screen")
        }
        
        override fun onAdDismissedFullScreenContent() {
            Log.d("AdBridge", "Rewarded dismissed")
            rewardedAd = null
            
            // If reward was NOT earned, call closed callback
            if (!rewardEarned) {
                callJavaScript("window.onRewardedClosed && window.onRewardedClosed()")
            }
            rewardEarned = false
            
            // Preload next ad
            loadRewarded()
        }
        
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.e("AdBridge", "Rewarded failed to show: ${error.message}, code: ${error.code}")
            rewardedAd = null
            
            callJavaScript("window.onRewardedFailedToShow && window.onRewardedFailedToShow('${error.message}', ${error.code})")
        }
    }
}

// Reward earned callback (called separately when user earns reward)
private fun onUserEarnedReward(reward: RewardItem) {
    Log.d("AdBridge", "User earned reward: ${reward.type}, amount: ${reward.amount}")
    rewardEarned = true
    
    callJavaScript("window.onRewardEarned && window.onRewardEarned('${reward.type}', ${reward.amount})")
}
```

### Fix #3: Standardize Callback Names

**Problem:** Callback names don't match between Kotlin and JavaScript.

**Solution - JavaScript expects these exact names:**
```kotlin
// CORRECT callback names (match ad-service.ts)
"window.onInterstitialClosed()"       // When interstitial closes
"window.onInterstitialFailedToShow(error, code)"  // When interstitial fails
"window.onInterstitialReady()"        // When interstitial loads
"window.onInterstitialLoadFailed(error, code)"    // When load fails

"window.onRewardEarned(type, amount)" // When user earns reward
"window.onRewardedClosed()"           // When rewarded closes without reward
"window.onRewardedFailedToShow(error, code)"      // When rewarded fails
"window.onRewardedReady()"            // When rewarded loads
"window.onRewardedLoadFailed(error, code)"        // When load fails
```

### Fix #4: WebView Reference Management

**Problem:** Activity recreated, WebView reference becomes stale.

**Solution:**
```kotlin
class AdBridge(
    private val activity: Activity
) {
    // Use WeakReference to avoid memory leaks
    private var webViewRef: WeakReference<WebView>? = null
    
    fun setWebView(webView: WebView) {
        this.webViewRef = WeakReference(webView)
    }
    
    private fun callJavaScript(script: String) {
        val webView = webViewRef?.get()
        if (webView == null) {
            Log.e("AdBridge", "WebView is null - cannot call JS: $script")
            return
        }
        
        if (!webView.isAttachedToWindow) {
            Log.e("AdBridge", "WebView not attached to window - cannot call JS")
            return
        }
        
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(script) { result ->
                    Log.d("AdBridge", "JS callback result: $result")
                }
            } catch (e: Exception) {
                Log.e("AdBridge", "Failed to call JS: ${e.message}")
            }
        }
    }
}
```

### Fix #5: UI Thread for evaluateJavascript

**Problem:** `evaluateJavascript` called from background thread.

**Solution:**
```kotlin
private fun callJavaScript(script: String) {
    // MUST run on UI thread
    activity.runOnUiThread {
        webViewRef?.get()?.let { webView ->
            if (webView.isAttachedToWindow) {
                webView.evaluateJavascript(script, null)
            }
        }
    }
}
```

### Fix #6: Register Bridge in Activity

**Problem:** `addJavascriptInterface` never called.

**Solution:**
```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var adBridge: AdBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        
        // Initialize AdBridge
        adBridge = AdBridge(this)
        adBridge.setWebView(webView)
        
        // CRITICAL: Register the bridge BEFORE loading any page
        webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
        
        // Enable JavaScript
        webView.settings.javaScriptEnabled = true
        
        // Load the app
        webView.loadUrl("https://your-app-url.com")
    }
}
```

### Fix #7: @JavascriptInterface Annotation

**Problem:** Methods not exposed to JavaScript without annotation.

**Solution:**
```kotlin
import android.webkit.JavascriptInterface

class AdBridge(private val activity: Activity) {
    
    // EVERY method callable from JS needs this annotation
    @JavascriptInterface
    fun showInterstitial(placementId: String): String {
        // ...
    }
    
    @JavascriptInterface
    fun showRewarded(placementId: String): String {
        // ...
    }
    
    @JavascriptInterface
    fun loadInterstitial() {
        // ...
    }
    
    @JavascriptInterface
    fun loadRewarded() {
        // ...
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        return interstitialAd != null
    }
    
    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        return rewardedAd != null
    }
}
```

### Fix #8: Activity Context for Ads

**Problem:** Using ApplicationContext for ad display.

**Solution:**
```kotlin
// WRONG - ApplicationContext cannot show fullscreen ads
val adRequest = AdRequest.Builder().build()
InterstitialAd.load(applicationContext, AD_UNIT_ID, adRequest, ...) // WRONG

// CORRECT - Use Activity context
InterstitialAd.load(activity, AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
    override fun onAdLoaded(ad: InterstitialAd) {
        interstitialAd = ad
        setupInterstitialCallbacks(ad)
        callJavaScript("window.onInterstitialReady && window.onInterstitialReady()")
    }
    
    override fun onAdFailedToLoad(error: LoadAdError) {
        callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('${error.message}', ${error.code})")
    }
})

// CORRECT - Use Activity for show()
interstitialAd?.show(activity) // NOT applicationContext
```

### Fix #9: Bridge Return Values

**Problem:** Synchronous return doesn't indicate ad was displayed.

**Solution:**
```kotlin
@JavascriptInterface
fun showInterstitial(placementId: String): String {
    if (interstitialAd == null) {
        Log.d("AdBridge", "Interstitial not ready - loading")
        loadInterstitial()
        return "not_ready"  // JS will handle this
    }
    
    if (!activity.hasWindowFocus()) {
        Log.d("AdBridge", "Activity doesn't have window focus")
        return "no_focus"
    }
    
    activity.runOnUiThread {
        try {
            interstitialAd?.show(activity)
        } catch (e: Exception) {
            Log.e("AdBridge", "Failed to show interstitial: ${e.message}")
            callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('${e.message}', -1)")
        }
    }
    
    return "showing"  // Actual result comes via callback
}
```

### Fix #10: ProGuard Rules

**Problem:** ProGuard obfuscates bridge method names.

**Solution - Add to `proguard-rules.pro`:**
```proguard
# Keep AdBridge class and all its methods
-keep class com.yourpackage.AdBridge { *; }

# Keep all classes with @JavascriptInterface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }

# Keep callback interfaces
-keep class * implements com.google.android.gms.ads.FullScreenContentCallback { *; }
-keep class * implements com.google.android.gms.ads.OnUserEarnedRewardListener { *; }
```

### Fix #11: WebView Destroyed Check

**Problem:** Ad finishes after WebView is gone.

**Solution:**
```kotlin
private fun callJavaScript(script: String) {
    val webView = webViewRef?.get()
    
    // Check all conditions before calling
    if (webView == null) {
        Log.w("AdBridge", "WebView is null")
        return
    }
    
    if (!webView.isAttachedToWindow) {
        Log.w("AdBridge", "WebView not attached to window")
        return
    }
    
    if (activity.isFinishing || activity.isDestroyed) {
        Log.w("AdBridge", "Activity is finishing/destroyed")
        return
    }
    
    activity.runOnUiThread {
        try {
            webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
            Log.e("AdBridge", "evaluateJavascript failed: ${e.message}")
        }
    }
}
```

### Fix #12: Single WebView Instance

**Problem:** Multiple WebView instances, callback goes to wrong one.

**Solution:**
```kotlin
// Use a singleton pattern for AdBridge
object AdBridgeManager {
    private var adBridge: AdBridge? = null
    
    fun initialize(activity: Activity, webView: WebView) {
        adBridge = AdBridge(activity).apply {
            setWebView(webView)
        }
        webView.addJavascriptInterface(adBridge!!, "AndroidAdBridge")
    }
    
    fun updateWebView(webView: WebView) {
        adBridge?.setWebView(webView)
    }
    
    fun getInstance(): AdBridge? = adBridge
}
```

### Fix #13: Initialize Bridge Early

**Problem:** JavaScript calls bridge before it's ready.

**Solution:**
```kotlin
// In WebViewClient
webView.webViewClient = object : WebViewClient() {
    
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        
        // Bridge should already be registered, but verify
        Log.d("AdBridge", "Page started - bridge ready: ${adBridge != null}")
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        
        // Notify JS that bridge is ready
        callJavaScript("window.dispatchEvent(new CustomEvent('adbridge_ready'))")
        
        // Trigger initial ad preload
        adBridge.loadInterstitial()
        adBridge.loadRewarded()
    }
}
```

### Fix #14: Enable JavaScript

**Problem:** JavaScript disabled in WebView settings.

**Solution:**
```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    allowContentAccess = true
    allowFileAccess = true
    
    // For debugging in development
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
```

### Fix #15: evaluateJavascript Syntax

**Problem:** Malformed JavaScript string causes silent failure.

**Solution:**
```kotlin
private fun callJavaScript(script: String) {
    // Validate script before calling
    if (script.isBlank()) {
        Log.e("AdBridge", "Empty script")
        return
    }
    
    activity.runOnUiThread {
        webViewRef?.get()?.let { webView ->
            // Use null-safe call pattern
            val safeScript = "if(typeof window !== 'undefined') { $script }"
            
            webView.evaluateJavascript(safeScript) { result ->
                Log.d("AdBridge", "JS result for '$script': $result")
            }
        }
    }
}

// Example safe callback calls
callJavaScript("window.onInterstitialClosed && window.onInterstitialClosed()")
callJavaScript("window.onRewardEarned && window.onRewardEarned('reward', 1)")
```

### Fix #16: Exception Handling in Bridge

**Problem:** Bridge method throws exception, silently fails.

**Solution:**
```kotlin
@JavascriptInterface
fun showInterstitial(placementId: String): String {
    return try {
        if (interstitialAd == null) {
            loadInterstitial()
            "not_ready"
        } else {
            activity.runOnUiThread {
                interstitialAd?.show(activity)
            }
            "showing"
        }
    } catch (e: Exception) {
        Log.e("AdBridge", "showInterstitial error: ${e.message}", e)
        callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('${e.message}', -1)")
        "error"
    }
}
```

### Fix #17-20: Additional Bridge Fixes

```kotlin
// Fix #17: Mixed content mode
webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

// Fix #18: CSP headers - handled on server side

// Fix #19: Unique bridge name (if conflicts occur)
webView.addJavascriptInterface(adBridge, "_LinguaVibeAdBridge_")

// Fix #20: Ensure callbacks registered before interaction
// In onPageFinished:
callJavaScript("""
    console.log('[Android] Verifying callbacks are registered');
    console.log('onInterstitialClosed:', typeof window.onInterstitialClosed);
    console.log('onRewardEarned:', typeof window.onRewardEarned);
""")
```

---

## CATEGORY 2: AD LOADING/CACHING ISSUES

### Fix #21: Call show() After Load

**Problem:** Ad loaded but show() never called.

**Solution:**
```kotlin
private var interstitialAd: InterstitialAd? = null

@JavascriptInterface
fun loadInterstitial() {
    if (interstitialAd != null) {
        Log.d("AdBridge", "Interstitial already loaded")
        return
    }
    
    activity.runOnUiThread {
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(activity, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("AdBridge", "Interstitial loaded successfully")
                    interstitialAd = ad
                    setupInterstitialCallbacks(ad)
                    
                    // CRITICAL: Notify JavaScript that ad is ready
                    callJavaScript("window.onInterstitialReady && window.onInterstitialReady()")
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdBridge", "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('${error.message}', ${error.code})")
                }
            }
        )
    }
}
```

### Fix #22: Keep Strong Reference

**Problem:** Ad object garbage collected before show.

**Solution:**
```kotlin
class AdBridge(private val activity: Activity) {
    // Strong references to prevent GC
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    // Clear only after ad is dismissed
    private fun onInterstitialDismissed() {
        interstitialAd = null  // Clear only here
        loadInterstitial()     // Load next one
    }
}
```

### Fix #23: Prevent Race Conditions

**Problem:** Loading new ad overwrites ready ad.

**Solution:**
```kotlin
private var isLoadingInterstitial = false

@JavascriptInterface
fun loadInterstitial() {
    if (isLoadingInterstitial) {
        Log.d("AdBridge", "Already loading interstitial - skipping")
        return
    }
    
    if (interstitialAd != null) {
        Log.d("AdBridge", "Interstitial already ready - skipping load")
        return
    }
    
    isLoadingInterstitial = true
    
    activity.runOnUiThread {
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(activity, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    // ...
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    // ...
                }
            }
        )
    }
}
```

### Fix #24: Ad Expiration Check

**Problem:** Ad expires before show (AdMob ads expire after ~1 hour).

**Solution:**
```kotlin
private var interstitialLoadTime: Long = 0
private val AD_EXPIRY_MS = 45 * 60 * 1000L  // 45 minutes

private fun isInterstitialFresh(): Boolean {
    if (interstitialAd == null) return false
    val age = System.currentTimeMillis() - interstitialLoadTime
    return age < AD_EXPIRY_MS
}

@JavascriptInterface
fun isInterstitialReady(): Boolean {
    if (!isInterstitialFresh()) {
        Log.d("AdBridge", "Interstitial is stale - reloading")
        interstitialAd = null
        loadInterstitial()
        return false
    }
    return interstitialAd != null
}

// Set load time when ad loads
override fun onAdLoaded(ad: InterstitialAd) {
    interstitialAd = ad
    interstitialLoadTime = System.currentTimeMillis()
    // ...
}
```

### Fix #25: Load Timeout Handler

**Problem:** Load request sent, no callback ever received.

**Solution:**
```kotlin
private var loadTimeoutHandler: Handler? = null
private val LOAD_TIMEOUT_MS = 15000L

@JavascriptInterface
fun loadInterstitial() {
    // Cancel any existing timeout
    loadTimeoutHandler?.removeCallbacksAndMessages(null)
    
    isLoadingInterstitial = true
    
    // Set timeout
    loadTimeoutHandler = Handler(Looper.getMainLooper())
    loadTimeoutHandler?.postDelayed({
        if (isLoadingInterstitial && interstitialAd == null) {
            Log.e("AdBridge", "Interstitial load timeout")
            isLoadingInterstitial = false
            callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('timeout', -2)")
        }
    }, LOAD_TIMEOUT_MS)
    
    // Start loading
    activity.runOnUiThread {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(activity, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadTimeoutHandler?.removeCallbacksAndMessages(null)
                    isLoadingInterstitial = false
                    // ...
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadTimeoutHandler?.removeCallbacksAndMessages(null)
                    isLoadingInterstitial = false
                    // ...
                }
            }
        )
    }
}
```

### Fix #26-35: Additional Loading Fixes

```kotlin
// Fix #26: Prevent duplicate loads (already covered in #23)

// Fix #27: Use constant for ad unit ID
companion object {
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9991891515643313/5076005693"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-9991891515643313/6313049833"
}

// Fix #28: Remove test device IDs in production
private fun buildAdRequest(): AdRequest {
    val builder = AdRequest.Builder()
    
    // Only add test device in debug builds
    if (BuildConfig.DEBUG) {
        builder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
        // Add your test device ID
        builder.addTestDevice("YOUR_TEST_DEVICE_ID")
    }
    
    return builder.build()
}

// Fix #29-30: Already covered (correct ad format, null check)

// Fix #31: Error callback implementation (already covered)

// Fix #32: Log AdRequest building
Log.d("AdBridge", "Building AdRequest for $adUnitId")

// Fix #33: Reload on return from background
// See Fix #35

// Fix #34: Only preload after previous ad shown
private var lastAdShownTime: Long = 0
private val MIN_PRELOAD_INTERVAL_MS = 5000L

private fun shouldPreload(): Boolean {
    val timeSinceLastAd = System.currentTimeMillis() - lastAdShownTime
    return timeSinceLastAd > MIN_PRELOAD_INTERVAL_MS
}

// Fix #35: Reload in onResume
override fun onResume() {
    super.onResume()
    
    // Check if ads need reloading after background
    if (adBridge.interstitialAd == null) {
        adBridge.loadInterstitial()
    }
    if (adBridge.rewardedAd == null) {
        adBridge.loadRewarded()
    }
}
```

---

## CATEGORY 3: DISPLAY TIMING ISSUES

### Fix #36-40: Activity State Checks

```kotlin
@JavascriptInterface
fun showInterstitial(placementId: String): String {
    // Fix #36: Check Activity is resumed
    if (!isActivityResumed) {
        Log.d("AdBridge", "Activity not resumed - deferring ad")
        return "activity_not_resumed"
    }
    
    // Fix #37: Check window is attached
    if (webViewRef?.get()?.isAttachedToWindow != true) {
        Log.d("AdBridge", "WebView not attached - deferring ad")
        return "not_attached"
    }
    
    // Fix #38: Add delay after load (handled in JS side)
    
    // Fix #39: Check not finishing
    if (activity.isFinishing || activity.isDestroyed) {
        Log.d("AdBridge", "Activity finishing - cannot show ad")
        return "activity_finishing"
    }
    
    // Fix #40: Check window focus
    if (!activity.hasWindowFocus()) {
        Log.d("AdBridge", "No window focus - deferring ad")
        return "no_focus"
    }
    
    // All checks passed - show ad
    activity.runOnUiThread {
        interstitialAd?.show(activity)
    }
    return "showing"
}
```

### Fix #41: Prevent Concurrent Ads

```kotlin
private var isShowingAd = false

@JavascriptInterface
fun showInterstitial(placementId: String): String {
    if (isShowingAd) {
        Log.d("AdBridge", "Already showing an ad")
        return "already_showing"
    }
    
    isShowingAd = true
    
    activity.runOnUiThread {
        interstitialAd?.show(activity)
    }
    return "showing"
}

// In callback
override fun onAdDismissedFullScreenContent() {
    isShowingAd = false
    // ...
}

override fun onAdFailedToShowFullScreenContent(error: AdError) {
    isShowingAd = false
    // ...
}
```

### Fix #42-50: Configuration and Display Fixes

```kotlin
// Fix #42: Handle configuration changes
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Reload ads after config change
    if (interstitialAd == null) loadInterstitial()
    if (rewardedAd == null) loadRewarded()
}

// Fix #43: Queue ad requests during fullscreen
private val pendingAdRequests = mutableListOf<String>()

// Fix #44: Wait for window attach
private fun showAdWhenReady(type: String) {
    webView.post {
        if (webView.isAttachedToWindow) {
            showAdInternal(type)
        } else {
            pendingAdRequests.add(type)
        }
    }
}

// Fix #45: Show only from visible Activity
private fun isActivityVisible(): Boolean {
    return activity.hasWindowFocus() && !activity.isFinishing
}

// Fix #46: Check PIP mode
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    if (activity.isInPictureInPictureMode) {
        return "pip_mode"
    }
}

// Fix #47: Check multi-window mode
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    if (activity.isInMultiWindowMode) {
        Log.d("AdBridge", "In multi-window mode - may affect ad display")
    }
}

// Fix #48: Handle foldables (same as #42 - config change)

// Fix #49: Display cutout handling
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode = 
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}

// Fix #50: Edge-to-edge insets
ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
    insets
}
```

---

## CATEGORY 4: VISIBILITY/FOREGROUND ISSUES

### Fix #51-60: Foreground and Visibility

```kotlin
// Fix #51: ProcessLifecycleOwner check
class AdBridge(private val activity: Activity) : LifecycleObserver {
    private var isAppInForeground = true
    
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        isAppInForeground = true
        Log.d("AdBridge", "App entered foreground")
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        isAppInForeground = false
        Log.d("AdBridge", "App entered background")
    }
    
    @JavascriptInterface
    fun showInterstitial(placementId: String): String {
        if (!isAppInForeground) {
            return "app_in_background"
        }
        // ...
    }
}

// Fix #52: WebView visibility check
if (webView.visibility != View.VISIBLE || !webView.isShown) {
    return "webview_not_visible"
}

// Fix #53: Screen on check
val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
if (!powerManager.isInteractive) {
    return "screen_off"
}

// Fix #54: Keyguard check
val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
if (keyguardManager.isKeyguardLocked) {
    return "device_locked"
}

// Fix #55-60: Various visibility checks (dialog, fragments, bottom sheets)
// These are handled by the hasWindowFocus() check in most cases
```

---

## CATEGORY 5: AD UNIT CONFIGURATION

### Fix #61-70: AdMob Console Configuration

These fixes require action in the AdMob console:

```
Fix #61: Verify ad unit status is "Active" in AdMob dashboard
Fix #62: Check app approval status
Fix #63: Create correct ad unit type (Interstitial, Rewarded)
Fix #64: Verify APPLICATION_ID matches AdMob app
Fix #65: Use production ad unit IDs (not test IDs)
Fix #66: Check for policy violations/suspensions
Fix #67: Review eCPM floor settings
Fix #68: Check frequency caps
Fix #69: Review geographic targeting
Fix #70: Review category exclusions
```

**AndroidManifest.xml:**
```xml
<application>
    <!-- Fix #64: Correct Application ID -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-9991891515643313~XXXXXXXXXX"/>
</application>
```

---

## CATEGORY 6: NETWORK/CONNECTIVITY

### Fix #71-80: Network Checks

```kotlin
// Fix #71: Network availability check
private fun isNetworkAvailable(): Boolean {
    val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } else {
        @Suppress("DEPRECATION")
        connectivityManager.activeNetworkInfo?.isConnected == true
    }
}

@JavascriptInterface
fun showInterstitial(placementId: String): String {
    if (!isNetworkAvailable()) {
        return "no_network"
    }
    // ...
}

// Fix #72-80: Additional network handling
// Most are handled by AdMob SDK internally
```

---

## CATEGORY 7-8: UX AND POLICY

Fixes #81-100 are primarily handled on the JavaScript side or in the AdMob console.

---

## CATEGORY 9: SDK/INTEGRATION

### Fix #101: SDK Initialization

```kotlin
// Application.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Fix #101: Initialize AdMob SDK
        MobileAds.initialize(this) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for ((adapter, status) in statusMap) {
                Log.d("AdMob", "Adapter: $adapter, Status: ${status.initializationState}")
            }
            
            // SDK is ready
            Log.d("AdMob", "MobileAds SDK initialized")
        }
    }
}
```

### Fix #102-110: Build Configuration

**build.gradle (app):**
```gradle
dependencies {
    // Fix #102: Latest SDK version
    implementation 'com.google.android.gms:play-services-ads:23.0.0'
    
    // Fix #103: Full SDK (not lite)
    // Use play-services-ads, NOT play-services-ads-lite
    
    // Fix #108: Use BOM for version management
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
}

android {
    // Fix #106: Enable multidex if needed
    defaultConfig {
        multiDexEnabled true
    }
    
    // Fix #109: Include all ABIs
    ndk {
        abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'
    }
}
```

**proguard-rules.pro:**
```proguard
# Fix #105: ProGuard rules for AdMob
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
```

**AndroidManifest.xml:**
```xml
<!-- Fix #107: Add APPLICATION_ID -->
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-9991891515643313~XXXXXXXXXX"/>
</application>
```

### Fix #110: Wait for Initialization

```kotlin
class AdBridge(private val activity: Activity) {
    private var isSdkInitialized = false
    
    fun waitForSdkInit(callback: () -> Unit) {
        if (isSdkInitialized) {
            callback()
            return
        }
        
        MobileAds.initialize(activity) {
            isSdkInitialized = true
            callback()
        }
    }
    
    @JavascriptInterface
    fun loadInterstitial() {
        waitForSdkInit {
            // Now safe to load ads
            loadInterstitialInternal()
        }
    }
}
```

---

## COMPLETE ADBRIDGE.KT IMPLEMENTATION

```kotlin
package com.linguavibe.app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference

class AdBridge(
    private val activity: Activity
) : LifecycleObserver {

    companion object {
        private const val TAG = "AdBridge"
        
        // Production Ad Unit IDs
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9991891515643313/5076005693"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-9991891515643313/6313049833"
        
        // Timeouts
        private const val LOAD_TIMEOUT_MS = 15000L
        private const val AD_EXPIRY_MS = 45 * 60 * 1000L  // 45 minutes
    }

    // WebView reference
    private var webViewRef: WeakReference<WebView>? = null
    
    // Ad objects
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    // Loading flags
    private var isLoadingInterstitial = false
    private var isLoadingRewarded = false
    private var isShowingAd = false
    
    // Timestamps
    private var interstitialLoadTime: Long = 0
    private var rewardedLoadTime: Long = 0
    
    // Lifecycle
    private var isAppInForeground = true
    private var isSdkInitialized = false
    
    // Reward tracking
    private var rewardEarned = false
    
    // Timeout handlers
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        initializeSdk()
    }

    private fun initializeSdk() {
        MobileAds.initialize(activity) { status ->
            Log.d(TAG, "AdMob SDK initialized")
            isSdkInitialized = true
            
            // Preload ads after SDK init
            loadInterstitial()
            loadRewarded()
        }
    }

    fun setWebView(webView: WebView) {
        this.webViewRef = WeakReference(webView)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        isAppInForeground = true
        Log.d(TAG, "App entered foreground")
        
        // Reload stale ads
        if (!isInterstitialFresh()) loadInterstitial()
        if (!isRewardedFresh()) loadRewarded()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        isAppInForeground = false
        Log.d(TAG, "App entered background")
    }

    // ==================== INTERSTITIAL ====================

    @JavascriptInterface
    fun loadInterstitial() {
        if (!isSdkInitialized) {
            Log.d(TAG, "SDK not initialized - deferring load")
            return
        }
        
        if (isLoadingInterstitial) {
            Log.d(TAG, "Already loading interstitial")
            return
        }
        
        if (interstitialAd != null && isInterstitialFresh()) {
            Log.d(TAG, "Fresh interstitial already loaded")
            return
        }
        
        isLoadingInterstitial = true
        Log.d(TAG, "Loading interstitial...")
        
        activity.runOnUiThread {
            val adRequest = AdRequest.Builder().build()
            
            InterstitialAd.load(activity, INTERSTITIAL_AD_UNIT_ID, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        Log.d(TAG, "Interstitial loaded successfully")
                        isLoadingInterstitial = false
                        interstitialAd = ad
                        interstitialLoadTime = System.currentTimeMillis()
                        setupInterstitialCallbacks(ad)
                        callJavaScript("window.onInterstitialReady && window.onInterstitialReady()")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(TAG, "Interstitial failed to load: ${error.message}, code: ${error.code}")
                        isLoadingInterstitial = false
                        interstitialAd = null
                        callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('${escapeJs(error.message)}', ${error.code})")
                    }
                }
            )
        }
    }

    private fun setupInterstitialCallbacks(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial showed")
            }
            
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                isShowingAd = false
                interstitialAd = null
                callJavaScript("window.onInterstitialClosed && window.onInterstitialClosed()")
                loadInterstitial()
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Interstitial failed to show: ${error.message}")
                isShowingAd = false
                interstitialAd = null
                callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('${escapeJs(error.message)}', ${error.code})")
            }
        }
    }

    @JavascriptInterface
    fun showInterstitial(placementId: String): String {
        Log.d(TAG, "showInterstitial called with: $placementId")
        return showInterstitialInternal()
    }

    @JavascriptInterface
    fun showInterstitial(): String {
        return showInterstitialInternal()
    }

    private fun showInterstitialInternal(): String {
        if (!isAppInForeground) {
            Log.d(TAG, "App in background")
            return "app_in_background"
        }
        
        if (isShowingAd) {
            Log.d(TAG, "Already showing ad")
            return "already_showing"
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Activity finishing")
            return "activity_finishing"
        }
        
        if (!activity.hasWindowFocus()) {
            Log.d(TAG, "No window focus")
            return "no_focus"
        }
        
        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "Interstitial not ready - loading")
            loadInterstitial()
            return "not_ready"
        }
        
        if (!isInterstitialFresh()) {
            Log.d(TAG, "Interstitial stale - reloading")
            interstitialAd = null
            loadInterstitial()
            return "stale"
        }
        
        isShowingAd = true
        activity.runOnUiThread {
            ad.show(activity)
        }
        return "showing"
    }

    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        return interstitialAd != null && isInterstitialFresh()
    }

    private fun isInterstitialFresh(): Boolean {
        if (interstitialAd == null) return false
        return System.currentTimeMillis() - interstitialLoadTime < AD_EXPIRY_MS
    }

    // ==================== REWARDED ====================

    @JavascriptInterface
    fun loadRewarded() {
        if (!isSdkInitialized) {
            Log.d(TAG, "SDK not initialized - deferring load")
            return
        }
        
        if (isLoadingRewarded) {
            Log.d(TAG, "Already loading rewarded")
            return
        }
        
        if (rewardedAd != null && isRewardedFresh()) {
            Log.d(TAG, "Fresh rewarded already loaded")
            return
        }
        
        isLoadingRewarded = true
        Log.d(TAG, "Loading rewarded...")
        
        activity.runOnUiThread {
            val adRequest = AdRequest.Builder().build()
            
            RewardedAd.load(activity, REWARDED_AD_UNIT_ID, adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d(TAG, "Rewarded loaded successfully")
                        isLoadingRewarded = false
                        rewardedAd = ad
                        rewardedLoadTime = System.currentTimeMillis()
                        setupRewardedCallbacks(ad)
                        callJavaScript("window.onRewardedReady && window.onRewardedReady()")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(TAG, "Rewarded failed to load: ${error.message}, code: ${error.code}")
                        isLoadingRewarded = false
                        rewardedAd = null
                        callJavaScript("window.onRewardedLoadFailed && window.onRewardedLoadFailed('${escapeJs(error.message)}', ${error.code})")
                    }
                }
            )
        }
    }

    private fun setupRewardedCallbacks(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded showed")
                rewardEarned = false
            }
            
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded dismissed, rewardEarned: $rewardEarned")
                isShowingAd = false
                rewardedAd = null
                
                if (!rewardEarned) {
                    callJavaScript("window.onRewardedClosed && window.onRewardedClosed()")
                }
                
                loadRewarded()
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Rewarded failed to show: ${error.message}")
                isShowingAd = false
                rewardedAd = null
                callJavaScript("window.onRewardedFailedToShow && window.onRewardedFailedToShow('${escapeJs(error.message)}', ${error.code})")
            }
        }
    }

    @JavascriptInterface
    fun showRewarded(placementId: String): String {
        Log.d(TAG, "showRewarded called with: $placementId")
        return showRewardedInternal()
    }

    @JavascriptInterface
    fun showRewarded(): String {
        return showRewardedInternal()
    }

    private fun showRewardedInternal(): String {
        if (!isAppInForeground) {
            return "app_in_background"
        }
        
        if (isShowingAd) {
            return "already_showing"
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            return "activity_finishing"
        }
        
        if (!activity.hasWindowFocus()) {
            return "no_focus"
        }
        
        val ad = rewardedAd
        if (ad == null) {
            loadRewarded()
            return "not_ready"
        }
        
        if (!isRewardedFresh()) {
            rewardedAd = null
            loadRewarded()
            return "stale"
        }
        
        isShowingAd = true
        activity.runOnUiThread {
            ad.show(activity) { reward ->
                Log.d(TAG, "User earned reward: ${reward.type}, ${reward.amount}")
                rewardEarned = true
                callJavaScript("window.onRewardEarned && window.onRewardEarned('${reward.type}', ${reward.amount})")
            }
        }
        return "showing"
    }

    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        return rewardedAd != null && isRewardedFresh()
    }

    private fun isRewardedFresh(): Boolean {
        if (rewardedAd == null) return false
        return System.currentTimeMillis() - rewardedLoadTime < AD_EXPIRY_MS
    }

    // ==================== HELPERS ====================

    private fun callJavaScript(script: String) {
        val webView = webViewRef?.get()
        
        if (webView == null) {
            Log.w(TAG, "WebView is null - cannot call: $script")
            return
        }
        
        if (!webView.isAttachedToWindow) {
            Log.w(TAG, "WebView not attached - cannot call: $script")
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity finishing - cannot call: $script")
            return
        }
        
        mainHandler.post {
            try {
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "JS result: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "evaluateJavascript failed: ${e.message}")
            }
        }
    }

    private fun escapeJs(str: String?): String {
        return str?.replace("'", "\\'")?.replace("\n", "\\n") ?: ""
    }
}
```

---

## MAINACTIVITY.KT SETUP

```kotlin
package com.linguavibe.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var adBridge: AdBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        webView = findViewById(R.id.webView)
        
        // Initialize AdBridge
        adBridge = AdBridge(this)
        adBridge.setWebView(webView)
        
        // Configure WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowContentAccess = true
        }
        
        // Register JavaScript interface BEFORE loading page
        webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
        
        // Set WebView client
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Notify JS that bridge is ready
                webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('adbridge_ready'))",
                    null
                )
            }
        }
        
        // Load app
        webView.loadUrl("https://your-app-url.replit.app")
    }
    
    override fun onResume() {
        super.onResume()
        // Reload ads if needed
        if (!adBridge.isInterstitialReady()) {
            adBridge.loadInterstitial()
        }
        if (!adBridge.isRewardedReady()) {
            adBridge.loadRewarded()
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
```

---

## TESTING CHECKLIST

After implementing all fixes, verify:

- [ ] `adb logcat -s AdBridge` shows "Interstitial loaded successfully"
- [ ] `adb logcat -s AdBridge` shows "Rewarded loaded successfully"
- [ ] Console shows `window.onInterstitialReady` called
- [ ] Console shows `window.onRewardedReady` called
- [ ] Clicking ad button shows "showing" return value
- [ ] Ad displays on screen
- [ ] After closing ad, console shows `window.onInterstitialClosed` called
- [ ] After watching rewarded ad, console shows `window.onRewardEarned` called
- [ ] AdMob dashboard shows impressions within 24-48 hours

---

## DEBUGGING COMMANDS

```bash
# View AdBridge logs
adb logcat -s AdBridge

# View all AdMob logs
adb logcat | grep -i "ads\|admob"

# View JavaScript console
adb logcat | grep "chromium"

# Clear app data and test fresh
adb shell pm clear com.linguavibe.app
```
