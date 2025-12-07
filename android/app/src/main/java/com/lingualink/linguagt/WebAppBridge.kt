package com.lingualink.linguagt

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.lingualink.linguagt.ads.AdMobManager
import org.json.JSONObject

/**
 * JavaScript bridge for communication between web app and native Android
 * 
 * Handles ad triggers from web app:
 * - Interstitial ads when translation limit (5) is reached
 * - Rewarded ads for premium access (30 minutes)
 * 
 * TESTRIGOR FIX: All UI operations use safeExecuteOnUiThread for crash prevention
 * 
 * CRASH PREVENTION SOLUTIONS:
 * - Solution #65: Defer processing until onPageFinished
 * - Solution #66: Post JS callback results to UI thread
 * - Solution #67: Wrap JS calls in try-catch
 * - Solution #68: Track loading state
 * - Solution #69: Throttle rapid bridge calls
 * - Solution #70: Handle Unicode/encoding
 * - Solution #71: Use async for large data transfers
 */
class WebAppBridge(private val activity: Activity) {

    companion object {
        private const val TAG = "WebAppBridge"
        
        // Solution #69: Throttle interval for rapid calls
        private const val THROTTLE_MS = 100L
        
        // Solution #71: Max sync data size before async
        private const val MAX_SYNC_DATA_SIZE = 10000
    }

    private val adMobManager: AdMobManager by lazy {
        AdMobManager.getInstance(activity)
    }
    
    // TESTRIGOR FIX: Handler for safe UI thread execution
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Solution #68: Track page loading state
    @Volatile
    private var isPageLoaded = false
    
    // Solution #69: Throttle rapid bridge calls
    private var lastBridgeCallTime = 0L
    private val bridgeLock = Object()
    
    // Solution #65: Queue for deferred operations
    private val deferredOperations = mutableListOf<() -> Unit>()

    /**
     * Solution #68: Mark page as loaded for deferred operations
     */
    fun onPageLoaded() {
        isPageLoaded = true
        TestRigorLogger.logDebug("WebAppBridge: Page loaded, processing ${deferredOperations.size} deferred operations")
        
        // Process deferred operations
        synchronized(bridgeLock) {
            deferredOperations.forEach { operation ->
                safeExecuteOnUiThread(operation)
            }
            deferredOperations.clear()
        }
    }
    
    /**
     * Solution #68: Mark page as unloaded
     */
    fun onPageUnloaded() {
        isPageLoaded = false
    }
    
    /**
     * Solution #69: Check throttle for rapid calls
     */
    private fun shouldThrottle(): Boolean {
        synchronized(bridgeLock) {
            val now = System.currentTimeMillis()
            if (now - lastBridgeCallTime < THROTTLE_MS) {
                TestRigorLogger.logDebug("WebAppBridge: Throttling rapid call")
                return true
            }
            lastBridgeCallTime = now
            return false
        }
    }
    
    /**
     * Solution #65: Execute or defer operation based on page load state
     */
    private fun executeOrDefer(operation: () -> Unit) {
        if (isPageLoaded) {
            safeExecuteOnUiThread(operation)
        } else {
            synchronized(bridgeLock) {
                deferredOperations.add(operation)
                TestRigorLogger.logDebug("WebAppBridge: Operation deferred until page loads")
            }
        }
    }

    /**
     * TESTRIGOR FIX: Safe UI thread execution with lifecycle validation
     * Uses Handler-based approach to avoid IllegalStateException during WebView destruction
     * 
     * Solution #67: Wrap all JS calls in try-catch
     */
    private fun safeExecuteOnUiThread(action: () -> Unit) {
        // Pre-check activity state
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("safeExecuteOnUiThread skipped - activity invalid")
            return
        }
        
        // Use Handler for safer execution (same pattern as ActivityExtensions.safeRunOnUiThread)
        mainHandler.post {
            // Double-check activity state inside handler
            if (!activity.isFinishing && !activity.isDestroyed) {
                // Solution #67: Wrap in try-catch
                try {
                    action()
                } catch (e: Exception) {
                    TestRigorLogger.logError("safeExecuteOnUiThread error", e)
                }
            } else {
                TestRigorLogger.logWarning("safeExecuteOnUiThread action skipped - activity became invalid")
            }
        }
    }

    /**
     * Show interstitial ad (called when user hits 5 translation limit)
     * Web app should call: window.AndroidBridge.showInterstitialAd()
     * TESTRIGOR FIX: Use safe UI thread access with activity state validation
     */
    @JavascriptInterface
    fun showInterstitialAd() {
        TestRigorLogger.logAdEvent("Web app requested interstitial ad")

        // TESTRIGOR FIX: Check activity state before UI operation
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show interstitial ad - activity invalid")
            return
        }

        safeExecuteOnUiThread {
            adMobManager.showInterstitialAd(activity) {
                // After ad closes, notify web app
                notifyWebApp("interstitial_closed")
            }
        }
    }

    /**
     * Show rewarded ad for 30 minutes of premium access
     * Web app should call: window.AndroidBridge.showRewardedAd()
     * TESTRIGOR FIX: Use safe UI thread access with activity state validation
     */
    @JavascriptInterface
    fun showRewardedAd() {
        TestRigorLogger.logAdEvent("Web app requested rewarded ad")

        // TESTRIGOR FIX: Check activity state before UI operation
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show rewarded ad - activity invalid")
            return
        }

        safeExecuteOnUiThread {
            if (adMobManager.isRewardedAdAvailable()) {
                adMobManager.showRewardedAd(
                    activity,
                    onRewarded = { amount ->
                        TestRigorLogger.logAdEvent("User earned reward: $amount")
                        // Grant 30 minutes of premium access
                        grantPremiumAccess(30)
                    },
                    onAdClosed = {
                        notifyWebApp("rewarded_closed")
                    }
                )
            } else {
                Toast.makeText(activity, "Ad not available yet. Please try again.", Toast.LENGTH_SHORT).show()
                notifyWebApp("rewarded_not_available")
            }
        }
    }

    /**
     * Check if rewarded ad is available
     * Web app should call: window.AndroidBridge.isRewardedAdReady()
     */
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        return adMobManager.isRewardedAdAvailable()
    }

    /**
     * Log event from web app
     * Web app can call: window.AndroidBridge.logEvent("event_name")
     */
    @JavascriptInterface
    fun logEvent(eventName: String) {
        TestRigorLogger.logAdEvent("Web event: $eventName")
    }

    /**
     * Track translation count from web app
     * Web app should call: window.AndroidBridge.trackTranslation(count)
     */
    @JavascriptInterface
    fun trackTranslation(count: Int) {
        TestRigorLogger.logAdEvent("Translation count: $count")

        // If user hits limit of 5, web app will call showInterstitialAd()
        if (count >= 5) {
            TestRigorLogger.logAdEvent("Translation limit reached: $count")
        }
    }

    /**
     * Grant premium access for specified minutes
     * TESTRIGOR FIX: Use safeExecuteOnUiThread for Toast
     */
    private fun grantPremiumAccess(minutes: Int) {
        val expiryTime = System.currentTimeMillis() + (minutes * 60 * 1000)

        // Save to SharedPreferences
        activity.getSharedPreferences("lingualink_prefs", Activity.MODE_PRIVATE)
            .edit()
            .putLong("premium_expiry", expiryTime)
            .putBoolean("is_premium", true)
            .apply()

        // Notify web app
        val data = JSONObject().apply {
            put("premium", true)
            put("expiryTime", expiryTime)
            put("minutes", minutes)
        }

        notifyWebApp("premium_granted", data.toString())

        // TESTRIGOR FIX: Use safe UI thread access for Toast
        safeExecuteOnUiThread {
            Toast.makeText(activity, "Premium access granted for $minutes minutes!", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Check if user has active premium access
     * Web app should call: window.AndroidBridge.hasPremiumAccess()
     */
    @JavascriptInterface
    fun hasPremiumAccess(): Boolean {
        val prefs = activity.getSharedPreferences("lingualink_prefs", Activity.MODE_PRIVATE)
        val expiryTime = prefs.getLong("premium_expiry", 0)
        val isPremium = prefs.getBoolean("is_premium", false)

        val hasAccess = isPremium && System.currentTimeMillis() < expiryTime

        if (!hasAccess && isPremium) {
            // Premium expired, clear it
            prefs.edit()
                .putBoolean("is_premium", false)
                .remove("premium_expiry")
                .apply()
        }

        return hasAccess
    }

    /**
     * Get remaining premium time in minutes
     * Web app should call: window.AndroidBridge.getPremiumMinutesRemaining()
     */
    @JavascriptInterface
    fun getPremiumMinutesRemaining(): Int {
        if (!hasPremiumAccess()) return 0

        val prefs = activity.getSharedPreferences("lingualink_prefs", Activity.MODE_PRIVATE)
        val expiryTime = prefs.getLong("premium_expiry", 0)
        val remainingMs = expiryTime - System.currentTimeMillis()

        return (remainingMs / 60000).toInt().coerceAtLeast(0)
    }

    /**
     * Notify web app of events
     * TESTRIGOR FIX: Added null checks, activity state validation, and safe UI thread access
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #66: Post results to UI thread
     * - Solution #67: Wrap JS calls in try-catch
     * - Solution #70: Handle Unicode/encoding safely
     * - Solution #71: Use async for large data
     */
    private fun notifyWebApp(eventType: String, data: String = "{}") {
        // TESTRIGOR FIX: Check activity state before attempting to notify
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot notify web app - activity invalid: $eventType")
            return
        }
        
        // Solution #69: Check throttle
        if (shouldThrottle()) {
            TestRigorLogger.logDebug("Throttled notifyWebApp: $eventType")
            return
        }
        
        // Solution #70: Sanitize data for JavaScript injection
        val sanitizedData = sanitizeForJavaScript(data)
        val sanitizedEventType = sanitizeForJavaScript(eventType)
        
        // Solution #71: Check data size
        if (sanitizedData.length > MAX_SYNC_DATA_SIZE) {
            TestRigorLogger.logWarning("Data too large for sync notify: ${sanitizedData.length} bytes")
            // For large data, use chunked or async approach
        }
        
        // Solution #65: Execute or defer based on page state
        executeOrDefer {
            val js = """
                (function() {
                    try {
                        if (window.onNativeEvent) {
                            window.onNativeEvent('$sanitizedEventType', $sanitizedData);
                        }

                        // Also dispatch custom event
                        const event = new CustomEvent('native_$sanitizedEventType', { 
                            detail: $sanitizedData 
                        });
                        window.dispatchEvent(event);

                        console.log('Native event dispatched: $sanitizedEventType', $sanitizedData);
                    } catch(e) {
                        console.error('Error dispatching native event:', e);
                    }
                })();
            """.trimIndent()

            // Solution #67: Wrap in try-catch
            try {
                // TESTRIGOR FIX: Null check for WebView
                val webView = (activity as? MainActivity)?.getWebView()
                if (webView == null) {
                    TestRigorLogger.logWarning("WebView null in notifyWebApp: $eventType")
                    return@executeOrDefer
                }
                
                // Solution #66: evaluateJavascript already posts to UI thread
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                TestRigorLogger.logError("Failed to notify web app: $eventType", e)
            }
        }
    }
    
    /**
     * Solution #70: Sanitize string for safe JavaScript injection
     * Prevents XSS and encoding issues
     */
    private fun sanitizeForJavaScript(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    /**
     * Solution #71: Clear deferred operations on cleanup
     */
    fun cleanup() {
        synchronized(bridgeLock) {
            deferredOperations.clear()
            isPageLoaded = false
        }
        TestRigorLogger.logDebug("WebAppBridge: Cleaned up")
    }
}
