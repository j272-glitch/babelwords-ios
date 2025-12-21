package com.lingualink.linguagt.ads

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.lingualink.linguagt.TestRigorLogger

/**
 * JavaScript bridge for IMA SDK functionality
 * Exposes ad methods to the web app via window.AdBridge
 */
class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private val imaManager: IMAManager by lazy {
        IMAManager.getInstance(activity)
    }

    /**
     * Initialize IMA SDK - called from web app
     * Usage: window.AdBridge.initialize()
     */
    @JavascriptInterface
    fun initialize() {
        TestRigorLogger.logAdEvent("AdBridge.initialize() called from web app")

        activity.runOnUiThread {
            if (activity is AppCompatActivity) {
                imaManager.initialize(activity) { success ->
                    notifyWebApp("imaInitialized", success.toString())
                }
            } else {
                TestRigorLogger.logWarning("Activity is not AppCompatActivity")
                notifyWebApp("imaInitialized", "false")
            }
        }
    }

    /**
     * Show interstitial ad
     * Usage: window.AdBridge.showInterstitial()
     */
    @JavascriptInterface
    fun showInterstitial() {
        TestRigorLogger.logAdEvent("AdBridge.showInterstitial() called from web app")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show ad - activity is invalid")
            notifyWebApp("interstitialFailed", "Activity is invalid")
            return
        }

        activity.runOnUiThread {
            if (activity is AppCompatActivity) {
                imaManager.showInterstitialAd(activity) { success ->
                    if (success) {
                        TestRigorLogger.logAdEvent("Interstitial ad shown successfully")
                        notifyWebApp("interstitialShown", "true")
                    } else {
                        notifyWebApp("interstitialFailed", "Ad not available")
                    }
                    notifyWebApp("interstitialClosed", "true")
                }
            }
        }
    }

    /**
     * Show rewarded ad
     * Usage: window.AdBridge.showRewarded()
     */
    @JavascriptInterface
    fun showRewarded() {
        TestRigorLogger.logAdEvent("AdBridge.showRewarded() CALLED from web app")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show rewarded ad - activity invalid")
            notifyWebApp("rewardedFailed", "activity_invalid")
            return
        }

        if (!imaManager.isInitialized.get()) {
            TestRigorLogger.logWarning("Cannot show rewarded ad - IMA SDK not initialized")
            notifyWebApp("rewardedFailed", "not_initialized")
            return
        }

        if (!imaManager.isRewardedAdAvailable()) {
            TestRigorLogger.logWarning("Rewarded ad not ready yet")
            notifyWebApp("rewardedFailed", "not_ready")
            return
        }

        activity.runOnUiThread {
            if (activity is AppCompatActivity) {
                TestRigorLogger.logAdEvent("Attempting to show rewarded ad...")
                imaManager.showRewardedAd(
                    activity,
                    onRewarded = {
                        TestRigorLogger.logAdEvent("Reward earned")
                        notifyWebApp("rewardEarned", "1")
                    },
                    onComplete = { success ->
                        TestRigorLogger.logAdEvent("Rewarded ad closed callback")
                        notifyWebApp("rewardedClosed", "true")
                    }
                )
            }
        }
    }

    /**
     * Check if rewarded ad is ready
     * Usage: window.AdBridge.isRewardedReady()
     */
    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        return imaManager.isRewardedAdAvailable()
    }

    /**
     * Log event from web app
     * Usage: window.AdBridge.logEvent("event_name")
     */
    @JavascriptInterface
    fun logEvent(eventName: String) {
        TestRigorLogger.logAdEvent("Web app event: $eventName")
    }

    /**
     * Get diagnostic information
     * Usage: window.AdBridge.getDiagnostics()
     */
    @JavascriptInterface
    fun getDiagnostics(): String {
        val lifecycle = if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).lifecycle.currentState.name
        } else {
            "UNKNOWN"
        }
        
        return """
        {
            "imaInitialized": ${imaManager.isInitialized.get()},
            "interstitialReady": ${imaManager.isInterstitialAdAvailable()},
            "rewardedReady": ${imaManager.isRewardedAdAvailable()},
            "activityState": "$lifecycle",
            "hasWindowFocus": ${activity.hasWindowFocus()},
            "isFinishing": ${activity.isFinishing},
            "isDestroyed": ${activity.isDestroyed},
            "timestamp": ${System.currentTimeMillis()}
        }
        """.trimIndent()
    }
    
    /**
     * Check if IMA SDK is initialized
     * Usage: window.AdBridge.isInitialized()
     */
    @JavascriptInterface
    fun isInitialized(): Boolean {
        return imaManager.isInitialized.get()
    }

    /**
     * Force show interstitial ad (for testing)
     * Usage: window.AdBridge.testShowInterstitial()
     */
    @JavascriptInterface
    fun testShowInterstitial() {
        TestRigorLogger.logAdEvent("TEST: Force showing interstitial ad")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot test - activity invalid")
            return
        }

        activity.runOnUiThread {
            if (activity is AppCompatActivity) {
                imaManager.forceShowInterstitial(activity) { success ->
                    TestRigorLogger.logAdEvent("TEST: Interstitial closed")
                    notifyWebApp("testInterstitialClosed", "true")
                }
            }
        }
    }

    /**
     * Notify web app of events via JavaScript callback
     */
    private fun notifyWebApp(eventType: String, data: String) {
        activity.runOnUiThread {
            val js = "if (window.onAdBridgeEvent) { window.onAdBridgeEvent('$eventType', '$data'); }"
            webView.evaluateJavascript(js, null)
        }
    }
}
