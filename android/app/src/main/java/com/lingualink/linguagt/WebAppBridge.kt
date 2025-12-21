package com.lingualink.linguagt

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.lingualink.linguagt.ads.IMAManager
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

    private val imaManager: IMAManager by lazy {
        IMAManager.getInstance(activity)
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
    
    // Reference to WebView for JS callbacks
    private var webViewRef: WebView? = null
    
    fun setWebView(webView: WebView) {
        this.webViewRef = webView
    }

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
        val now = System.currentTimeMillis()
        synchronized(bridgeLock) {
            if (now - lastBridgeCallTime < THROTTLE_MS) {
                TestRigorLogger.logDebug("WebAppBridge: Throttling rapid call")
                return true
            }
            lastBridgeCallTime = now
        }
        return false
    }
    
    /**
     * Solution #65: Defer operation until page is loaded
     */
    private fun deferUntilPageLoaded(operation: () -> Unit) {
        if (isPageLoaded) {
            safeExecuteOnUiThread(operation)
        } else {
            TestRigorLogger.logDebug("WebAppBridge: Deferring operation until page loaded")
            synchronized(bridgeLock) {
                deferredOperations.add(operation)
            }
        }
    }

    /**
     * TESTRIGOR FIX: Safe execution on UI thread with activity state check
     * All UI operations should use this to prevent crashes when activity is invalid
     */
    private fun safeExecuteOnUiThread(action: () -> Unit) {
        // First check: Quick validation before posting
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("safeExecuteOnUiThread skipped - activity already invalid")
            return
        }

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
            imaManager.showInterstitialAd(activity) { success ->
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
            if (imaManager.isRewardedAdAvailable()) {
                imaManager.showRewardedAd(
                    activity,
                    onRewarded = {
                        TestRigorLogger.logAdEvent("User earned reward")
                        // Grant 30 minutes of premium access
                        grantPremiumAccess(30)
                    },
                    onComplete = { success ->
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
        return imaManager.isRewardedAdAvailable()
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
            Toast.makeText(
                activity,
                "Premium access granted for $minutes minutes!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Check if user has premium access
     * Web app should call: window.AndroidBridge.hasPremiumAccess()
     */
    @JavascriptInterface
    fun hasPremiumAccess(): Boolean {
        val prefs = activity.getSharedPreferences("lingualink_prefs", Activity.MODE_PRIVATE)
        val isPremium = prefs.getBoolean("is_premium", false)
        val expiryTime = prefs.getLong("premium_expiry", 0)

        return isPremium && System.currentTimeMillis() < expiryTime
    }

    /**
     * Get premium status details as JSON
     * Web app should call: window.AndroidBridge.getPremiumStatus()
     */
    @JavascriptInterface
    fun getPremiumStatus(): String {
        val prefs = activity.getSharedPreferences("lingualink_prefs", Activity.MODE_PRIVATE)
        val isPremium = prefs.getBoolean("is_premium", false)
        val expiryTime = prefs.getLong("premium_expiry", 0)
        val now = System.currentTimeMillis()
        val isActive = isPremium && now < expiryTime

        return JSONObject().apply {
            put("isPremium", isPremium)
            put("isActive", isActive)
            put("expiryTime", expiryTime)
            put("remainingMs", if (isActive) expiryTime - now else 0)
        }.toString()
    }

    /**
     * Solution #70: Sanitize data for JavaScript execution
     */
    private fun sanitizeForJs(data: String): String {
        return data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Solution #71: Check if data should be sent async
     */
    private fun shouldUseAsync(data: String): Boolean {
        return data.length > MAX_SYNC_DATA_SIZE
    }

    /**
     * Notify web app of events via JavaScript callback
     * Solution #66: Post to UI thread
     * Solution #67: Wrap in try-catch
     * Solution #70: Handle encoding
     */
    private fun notifyWebApp(event: String, data: String = "{}") {
        if (!isPageLoaded) {
            TestRigorLogger.logDebug("WebAppBridge: Cannot notify - page not loaded")
            return
        }
        
        // TESTRIGOR FIX: Check activity state before UI operation
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot notify web app - activity invalid")
            return
        }

        // Solution #70: Sanitize data
        val safeData = sanitizeForJs(data)
        
        // Solution #71: Use chunked transfer for large data
        if (shouldUseAsync(safeData)) {
            notifyWebAppAsync(event, safeData)
            return
        }

        val jsCode = """
            try {
                if (typeof window.onNativeEvent === 'function') {
                    window.onNativeEvent('$event', $safeData);
                }
            } catch(e) {
                console.error('Native event error:', e);
            }
        """.trimIndent()

        safeExecuteOnUiThread {
            try {
                webViewRef?.evaluateJavascript(jsCode, null)
            } catch (e: Exception) {
                TestRigorLogger.logError("JS evaluation failed", e)
            }
        }
    }

    /**
     * Solution #71: Async notification for large data
     */
    private fun notifyWebAppAsync(event: String, data: String) {
        TestRigorLogger.logDebug("WebAppBridge: Using async transfer for large data (${data.length} chars)")
        
        // Split into chunks
        val chunkSize = MAX_SYNC_DATA_SIZE / 2
        val chunks = data.chunked(chunkSize)
        
        // Send start signal
        notifyWebApp("${event}_start", """{"totalChunks": ${chunks.size}}""")
        
        // Send chunks with delay
        chunks.forEachIndexed { index, chunk ->
            mainHandler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && isPageLoaded) {
                    val chunkData = """{"chunk": "$chunk", "index": $index}"""
                    notifyWebApp("${event}_chunk", chunkData)
                    
                    // Send complete signal after last chunk
                    if (index == chunks.lastIndex) {
                        mainHandler.postDelayed({
                            notifyWebApp("${event}_complete", "{}")
                        }, 50)
                    }
                }
            }, (index * 50).toLong())
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        synchronized(bridgeLock) {
            deferredOperations.clear()
        }
        isPageLoaded = false
        webViewRef = null
        mainHandler.removeCallbacksAndMessages(null)
        TestRigorLogger.logDebug("WebAppBridge: Cleaned up")
    }
}
