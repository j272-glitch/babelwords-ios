# Android Foreground Recovery: 55 Causes & Solutions
# Why App Doesn't Return After AdMob Ads Close

## Executive Summary

When AdMob ads open the Play Store, Android's activity stack can leave your app backgrounded. Even with `FLAG_ACTIVITY_REORDER_TO_FRONT`, `launchMode="singleTask"`, and `taskAffinity=""`, recovery can fail due to task removal, process death, or manufacturer-specific behaviors.

---

## CATEGORY 1: Intent Flag Issues (Fixes 1-10)

### Fix #1: REORDER_TO_FRONT Only Works If Task Exists

**Problem:** `FLAG_ACTIVITY_REORDER_TO_FRONT` is a no-op if the task was killed.

**Solution:**
```kotlin
private fun bringAppToFront() {
    val activity = activityRef.get() ?: return
    val intent = Intent(activity, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                 Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                 Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    activity.startActivity(intent)
}
```

### Fix #2: Missing FLAG_ACTIVITY_NEW_TASK

**Problem:** Without `NEW_TASK`, intent may not launch from background context.

**Solution:**
```kotlin
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
```

### Fix #3: Missing FLAG_ACTIVITY_CLEAR_TOP

**Problem:** Multiple activity instances pile up, confusing the back stack.

**Solution:**
```kotlin
intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
```

### Fix #4: Missing FLAG_ACTIVITY_SINGLE_TOP

**Problem:** Activity recreates instead of resuming existing instance.

**Solution:**
```kotlin
intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
```

### Fix #5: Using REORDER_TO_FRONT Alone

**Problem:** Doesn't create task if it was destroyed.

**Solution:** Combine with NEW_TASK | CLEAR_TOP | SINGLE_TOP as fallback.

### Fix #6: Intent Fired Before Play Store Returns

**Problem:** bringToFront() called while Play Store still has focus.

**Solution:**
```kotlin
Handler(Looper.getMainLooper()).postDelayed({
    bringAppToFront()
}, 500) // Wait for Play Store to release focus
```

### Fix #7: Intent Package Not Set

**Problem:** Ambiguous intent resolution.

**Solution:**
```kotlin
intent.setPackage(activity.packageName)
```

### Fix #8: Using Implicit Intent

**Problem:** System may not resolve to your activity.

**Solution:** Always use explicit intent with `MainActivity::class.java`.

### Fix #9: FLAG_ACTIVITY_BROUGHT_TO_FRONT Misuse

**Problem:** This flag is set BY the system, not FOR the system.

**Solution:** Don't set this flag manually; use REORDER_TO_FRONT or NEW_TASK.

### Fix #10: Conflicting FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

**Problem:** Activity excluded from recents can't be reordered.

**Solution:** Remove this flag from manifest and intent.

---

## CATEGORY 2: Activity Lifecycle Issues (Fixes 11-20)

### Fix #11: Activity Destroyed During Ad

**Problem:** System kills activity for memory while ad is showing.

**Solution:**
```kotlin
override fun onResume() {
    super.onResume()
    // Check if returning from ad
    if (wasShowingAd) {
        wasShowingAd = false
        Handler(Looper.getMainLooper()).postDelayed({
            ensureInForeground()
        }, 300)
    }
}
```

### Fix #12: onAdDismissed Called After onDestroy

**Problem:** Callback fires on dead activity reference.

**Solution:**
```kotlin
override fun onAdDismissedFullScreenContent() {
    val activity = activityRef.get()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
        Log.w(TAG, "Activity dead - using application context")
        bringAppToFrontViaApplication()
        return
    }
    bringAppToFront()
}
```

### Fix #13: Weak Reference Nullified

**Problem:** WeakReference to activity cleared by GC.

**Solution:**
```kotlin
// Store application context as fallback
private val appContext = activity.applicationContext

private fun bringAppToFrontViaApplication() {
    val intent = Intent(appContext, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                 Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                 Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    appContext.startActivity(intent)
}
```

### Fix #14: onStop Called But onDestroy Not

**Problem:** Activity in stopped state can't receive UI updates.

**Solution:** Use onStart/onResume to verify foreground state, not onCreate.

### Fix #15: Configuration Change During Ad

**Problem:** Screen rotation destroys and recreates activity.

**Solution:**
```xml
<activity
    android:configChanges="orientation|screenSize|keyboardHidden"
    ...>
```

### Fix #16: Activity Not in Resumed State

**Problem:** startActivity called when activity is paused.

**Solution:**
```kotlin
if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
    startActivity(intent)
} else {
    pendingForegroundIntent = intent
}
```

### Fix #17: Missing onNewIntent Handler

**Problem:** singleTask activity receives intent in onNewIntent, not onCreate.

**Solution:**
```kotlin
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    // Handle any pending operations
    handleForegroundRecovery()
}
```

### Fix #18: isFinishing Check Missing

**Problem:** Operating on finishing activity causes crash or no-op.

**Solution:**
```kotlin
if (!activity.isFinishing && !activity.isDestroyed) {
    activity.startActivity(intent)
}
```

### Fix #19: Lifecycle Observer Not Removed

**Problem:** Memory leak prevents proper lifecycle transitions.

**Solution:**
```kotlin
override fun onDestroy() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
    super.onDestroy()
}
```

### Fix #20: Saved Instance State Not Restored

**Problem:** Activity recreated without ad state knowledge.

**Solution:**
```kotlin
override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("was_showing_ad", isShowingAd)
    super.onSaveInstanceState(outState)
}
```

---

## CATEGORY 3: Task Affinity & Launch Mode Issues (Fixes 21-25)

### Fix #21: Wrong taskAffinity Value

**Problem:** Non-empty taskAffinity groups with other apps.

**Solution:**
```xml
android:taskAffinity=""
```

### Fix #22: Wrong launchMode

**Problem:** `standard` or `singleTop` don't prevent multi-instance.

**Solution:**
```xml
android:launchMode="singleTask"
```

### Fix #23: Play Store Opens in Same Task

**Problem:** Play Store intent opens in your task, stealing focus.

**Solution:** This is normal; ensure your recovery intent has NEW_TASK.

### Fix #24: Multiple Tasks Created

**Problem:** NEW_TASK creates duplicate tasks.

**Solution:** Use CLEAR_TOP with NEW_TASK to reuse existing task.

### Fix #25: documentLaunchMode Conflict

**Problem:** `documentLaunchMode` overrides launchMode.

**Solution:**
```xml
android:documentLaunchMode="none"
```

---

## CATEGORY 4: AdMob Callback Timing Issues (Fixes 26-35)

### Fix #26: Callback Fires Too Early

**Problem:** onAdDismissed fires before Play Store animation completes.

**Solution:**
```kotlin
override fun onAdDismissedFullScreenContent() {
    Handler(Looper.getMainLooper()).postDelayed({
        bringAppToFront()
    }, 800) // Wait for Play Store to fully close
}
```

### Fix #27: Callback Never Fires

**Problem:** Ad crashes or force-closed without callback.

**Solution:**
```kotlin
// Watchdog timer
private var adWatchdog: Runnable? = null

fun startAdWatchdog() {
    adWatchdog = Runnable {
        if (isShowingAd) {
            Log.w(TAG, "Ad watchdog triggered - forcing recovery")
            isShowingAd = false
            bringAppToFront()
        }
    }
    handler.postDelayed(adWatchdog!!, 60000) // 60 second timeout
}
```

### Fix #28: Multiple Callbacks Fire

**Problem:** onAdDismissed fires multiple times.

**Solution:**
```kotlin
private var dismissHandled = AtomicBoolean(false)

override fun onAdDismissedFullScreenContent() {
    if (!dismissHandled.compareAndSet(false, true)) {
        Log.w(TAG, "Duplicate dismiss callback - ignoring")
        return
    }
    // Handle dismissal
}
```

### Fix #29: Callback On Wrong Thread

**Problem:** Callback fires on background thread.

**Solution:**
```kotlin
override fun onAdDismissedFullScreenContent() {
    mainHandler.post {
        bringAppToFront()
    }
}
```

### Fix #30: FullScreenContentCallback Not Set

**Problem:** Callback object not assigned to ad.

**Solution:**
```kotlin
ad.fullScreenContentCallback = object : FullScreenContentCallback() {
    // Always set before showing
}
ad.show(activity)
```

### Fix #31: Callback Overwritten

**Problem:** Later code overwrites callback.

**Solution:** Set callback immediately before show(), not during load.

### Fix #32: onAdFailedToShowFullScreenContent Ignored

**Problem:** Ad fails to show but no recovery triggered.

**Solution:**
```kotlin
override fun onAdFailedToShowFullScreenContent(error: AdError) {
    isShowingAd = false
    bringAppToFront() // Still recover
}
```

### Fix #33: onAdShowedFullScreenContent Not Tracking

**Problem:** Not tracking when ad actually displays.

**Solution:**
```kotlin
override fun onAdShowedFullScreenContent() {
    isShowingAd = true
    adShowTime = System.currentTimeMillis()
}
```

### Fix #34: Race Between Dismiss and Show

**Problem:** New ad tries to show while dismissing previous.

**Solution:**
```kotlin
if (isShowingAd) {
    Log.w(TAG, "Already showing ad - blocking new show")
    return
}
```

### Fix #35: Preloaded Ad Callback Stale

**Problem:** Cached ad has old callback referencing dead activity.

**Solution:**
```kotlin
// Reset callbacks before showing preloaded ad
preloadedAd.fullScreenContentCallback = createFreshCallback()
preloadedAd.show(activity)
```

---

## CATEGORY 5: Thread & Handler Issues (Fixes 36-40)

### Fix #36: Handler Leaked

**Problem:** Handler prevents activity GC.

**Solution:**
```kotlin
private val handler = Handler(Looper.getMainLooper())

override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
}
```

### Fix #37: postDelayed Never Executes

**Problem:** Activity destroyed before delayed runnable runs.

**Solution:**
```kotlin
private var recoveryRunnable: Runnable? = null

fun scheduleRecovery() {
    recoveryRunnable = Runnable { bringAppToFront() }
    handler.postDelayed(recoveryRunnable!!, 500)
}

override fun onDestroy() {
    recoveryRunnable?.let { handler.removeCallbacks(it) }
}
```

### Fix #38: Wrong Looper

**Problem:** Using background looper for UI operations.

**Solution:** Always use `Looper.getMainLooper()` for activity operations.

### Fix #39: Handler Queue Blocked

**Problem:** Long-running task blocks handler queue.

**Solution:** Use separate thread for heavy work, post results to main thread.

### Fix #40: Runnable Reference Lost

**Problem:** Anonymous runnable can't be cancelled.

**Solution:** Store runnable reference for cancellation.

---

## CATEGORY 6: WebView-Specific Issues (Fixes 41-45)

### Fix #41: WebView Consumes Back Press

**Problem:** WebView's back navigation interferes with recovery.

**Solution:**
```kotlin
override fun onBackPressed() {
    if (webView.canGoBack()) {
        webView.goBack()
    } else {
        super.onBackPressed()
    }
}
```

### Fix #42: WebView Destroyed During Ad

**Problem:** WebView state lost, can't evaluate JavaScript.

**Solution:**
```kotlin
if (::webView.isInitialized && webView.isAttachedToWindow) {
    webView.evaluateJavascript(script, null)
}
```

### Fix #43: evaluateJavascript Fails Silently

**Problem:** WebView not attached, JS never executes.

**Solution:** Buffer JS calls and execute on resume.

### Fix #44: WebView Process Killed

**Problem:** Separate WebView process terminated.

**Solution:**
```kotlin
override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
    webView.destroy()
    recreateWebView()
    return true
}
```

### Fix #45: WebView Hardware Acceleration Issue

**Problem:** WebView rendering fails after background.

**Solution:**
```kotlin
webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
```

---

## CATEGORY 7: Android Version-Specific Issues (Fixes 46-50)

### Fix #46: Android 10+ Background Restrictions

**Problem:** Can't start activities from background on Android 10+.

**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    // Use full-screen intent or notification
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

### Fix #47: Android 12+ Trampoline Restrictions

**Problem:** Can't launch activity from service/receiver on Android 12+.

**Solution:** Launch directly from activity context, not application context.

### Fix #48: Android 13+ Notification Permission

**Problem:** Can't use notification-based recovery without permission.

**Solution:** Request POST_NOTIFICATIONS permission.

### Fix #49: Android 14+ Foreground Service Changes

**Problem:** Background activity launch restrictions tightened.

**Solution:** Ensure recovery happens within activity lifecycle.

### Fix #50: Legacy API Compatibility

**Problem:** Flags behave differently on API < 21.

**Solution:**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    // Modern flags
} else {
    // Legacy approach
}
```

---

## CATEGORY 8: Manufacturer-Specific Issues (Fixes 51-55)

### Fix #51: Samsung Game Booster

**Problem:** Samsung's Game Booster kills backgrounded apps aggressively.

**Solution:** Prompt user to whitelist app in Game Booster settings.

### Fix #52: Xiaomi Battery Saver

**Problem:** MIUI kills apps not on whitelist.

**Solution:**
```kotlin
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
```

### Fix #53: Huawei Power Manager

**Problem:** Huawei's aggressive power management kills background tasks.

**Solution:**
```kotlin
fun openHuaweiProtectedApps(context: Context) {
    try {
        val intent = Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Huawei Protected Apps not available")
    }
}
```

### Fix #54: Oppo/Vivo Auto-Start Manager

**Problem:** App not allowed to auto-start after being killed.

**Solution:**
```kotlin
fun openAutoStartSettings(context: Context) {
    val manufacturers = mapOf(
        "oppo" to "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
        "vivo" to "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity"
    )
    // Try manufacturer-specific intent
}
```

### Fix #55: OnePlus Optimization

**Problem:** OnePlus OxygenOS battery optimization.

**Solution:**
```kotlin
// Add app to "Don't Optimize" list
fun promptDontOptimize(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        // Show dialog explaining why optimization should be disabled
        showBatteryOptimizationDialog()
    }
}
```

---

## RECOMMENDED IMPLEMENTATION

### Robust Foreground Recovery with Retry

```kotlin
// AdMobBridge.kt or AdPreloadManager.kt
private val mainHandler = Handler(Looper.getMainLooper())
private var recoveryAttempts = 0
private val MAX_RECOVERY_ATTEMPTS = 5
private val RECOVERY_DELAY_MS = 500L

private fun bringAppToFrontWithRetry() {
    recoveryAttempts = 0
    attemptRecovery()
}

private fun attemptRecovery() {
    if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
        Log.w(TAG, "Max recovery attempts reached")
        return
    }
    
    recoveryAttempts++
    Log.d(TAG, "Recovery attempt $recoveryAttempts/$MAX_RECOVERY_ATTEMPTS")
    
    val activity = activityRef.get()
    
    // Method 1: Try with activity context
    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
        val intent = Intent(activity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        activity.startActivity(intent)
    } else {
        // Method 2: Use application context
        val appContext = LinguaLinkApplication.instance
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        appContext.startActivity(intent)
    }
    
    // Schedule verification
    mainHandler.postDelayed({
        if (!isAppInForeground) {
            attemptRecovery()
        } else {
            Log.d(TAG, "Recovery successful on attempt $recoveryAttempts")
        }
    }, RECOVERY_DELAY_MS)
}
```

---

## VERIFICATION CHECKLIST

- [ ] FLAG_ACTIVITY_NEW_TASK is set
- [ ] FLAG_ACTIVITY_CLEAR_TOP is set  
- [ ] FLAG_ACTIVITY_SINGLE_TOP is set
- [ ] launchMode="singleTask" in manifest
- [ ] taskAffinity="" in manifest
- [ ] Recovery has retry logic with 500ms delay
- [ ] Both activity and application context fallbacks exist
- [ ] Callback fires on main thread
- [ ] Watchdog timer for stuck ads
- [ ] Battery optimization prompt for Chinese OEMs
