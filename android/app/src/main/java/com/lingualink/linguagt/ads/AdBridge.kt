package com.lingualink.linguagt.ads

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.lingualink.linguagt.TestRigorLogger

/**
 * JavaScript bridge for VAST ad functionality
 * Exposes ad methods to the web app via window.AdBridge
 */
class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private val vastAdManager: VASTAdManager by lazy {
        VASTAdManager.getInstance(activity)
    }

    /**
     * Initialize VAST Ad Manager - called from web app
     * Usage: window.AdBridge.initialize()
     */
    @JavascriptInterface
    fun initialize() {
        TestRigorLogger.logAdEvent("AdBridge.initialize() called from web app")

        activity.runOnUiThread {
            vastAdManager.initialize(activity) { success ->
                notifyWebApp("vastInitialized", success.toString())
            }
        }
    }

    /**
     * Show interstitial ad
     * Usage: window.AdBridge.showInterstitial()
     * Usage with custom URL: window.AdBridge.showInterstitial("https://your-vast-url")
     */
    @JavascriptInterface
    fun showInterstitial(vastUrl: String? = null) {
        TestRigorLogger.logAdEvent("AdBridge.showInterstitial() called from web app, vastUrl=${vastUrl?.take(30) ?: "default"}")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show ad - activity is invalid")
            notifyWebApp("interstitialFailed", "Activity is invalid")
            return
        }

        activity.runOnUiThread {
            vastAdManager.showInterstitialAd(activity, vastUrl) { success ->
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

    /**
     * Show rewarded ad
     * Usage: window.AdBridge.showRewarded()
     * Usage with custom URL: window.AdBridge.showRewarded("https://your-vast-url")
     */
    @JavascriptInterface
    fun showRewarded(vastUrl: String? = null) {
        TestRigorLogger.logAdEvent("AdBridge.showRewarded() CALLED from web app, vastUrl=${vastUrl?.take(30) ?: "default"}")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("Cannot show rewarded ad - activity invalid")
            notifyWebApp("rewardedFailed", "activity_invalid")
            return
        }

        if (!vastAdManager.isInitialized.get()) {
            TestRigorLogger.logWarning("Cannot show rewarded ad - VAST not initialized")
            notifyWebApp("rewardedFailed", "not_initialized")
            return
        }

        if (!vastAdManager.isRewardedAdAvailable()) {
            TestRigorLogger.logWarning("Rewarded ad not ready yet")
            notifyWebApp("rewardedFailed", "not_ready")
            return
        }

        activity.runOnUiThread {
            TestRigorLogger.logAdEvent("Attempting to show rewarded ad...")
            vastAdManager.showRewardedAd(
                activity,
                vastUrl,
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

    /**
     * Check if rewarded ad is ready
     * Usage: window.AdBridge.isRewardedReady()
     */
    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        return vastAdManager.isRewardedAdAvailable()
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
        return """
        {
            "vastInitialized": ${vastAdManager.isInitialized.get()},
            "interstitialReady": ${vastAdManager.isInterstitialAdAvailable()},
            "rewardedReady": ${vastAdManager.isRewardedAdAvailable()},
            "hasWindowFocus": ${activity.hasWindowFocus()},
            "isFinishing": ${activity.isFinishing},
            "isDestroyed": ${activity.isDestroyed},
            "timestamp": ${System.currentTimeMillis()}
        }
        """.trimIndent()
    }
    
    /**
     * Check if VAST Ad Manager is initialized
     * Usage: window.AdBridge.isInitialized()
     */
    @JavascriptInterface
    fun isInitialized(): Boolean {
        return vastAdManager.isInitialized.get()
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
            vastAdManager.forceShowInterstitial(activity) { success ->
                TestRigorLogger.logAdEvent("TEST: Interstitial closed")
                notifyWebApp("testInterstitialClosed", "true")
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
