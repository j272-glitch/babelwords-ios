package com.lingualink.linguagt.ads

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.lingualink.linguagt.TestRigorLogger

/**
 * JavaScript bridge for AdMob functionality
 * Exposes ad methods to the web app via window.AdBridge
 */
class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private val adMobManager: AdMobManager by lazy {
        AdMobManager.getInstance(activity)
    }

    /**
     * Initialize AdMob - called from web app
     * Usage: window.AdBridge.initialize()
     */
    @JavascriptInterface
    fun initialize() {
        TestRigorLogger.logAdEvent("AdBridge.initialize() called from web app")

        activity.runOnUiThread {
            adMobManager.initialize(activity) { success ->
                notifyWebApp("adMobInitialized", success.toString())
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
            adMobManager.showInterstitialAd(
                activity,
                onAdShown = {
                    TestRigorLogger.logAdEvent("🎉 Interstitial ad SHOWN - notifying web app")
                    notifyWebApp("interstitialShown", "true")
                },
                onAdClosed = {
                    TestRigorLogger.logAdEvent("Interstitial ad closed from AdBridge")
                    notifyWebApp("interstitialClosed", "true")
                }
            )
        }
    }

    /**
     * Show rewarded ad
     * Usage: window.AdBridge.showRewarded()
     */
    @JavascriptInterface
    fun showRewarded() {
        TestRigorLogger.logAdEvent("🎯 AdBridge.showRewarded() CALLED from web app")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("❌ Cannot show rewarded ad - activity invalid")
            notifyWebApp("rewardedFailed", "activity_invalid")
            return
        }

        if (!adMobManager.isInitialized.get()) {
            TestRigorLogger.logWarning("❌ Cannot show rewarded ad - AdMob not initialized")
            notifyWebApp("rewardedFailed", "not_initialized")
            return
        }

        if (!adMobManager.isRewardedAdAvailable()) {
            TestRigorLogger.logWarning("❌ Rewarded ad not ready yet")
            notifyWebApp("rewardedFailed", "not_ready")
            return
        }

        activity.runOnUiThread {
            TestRigorLogger.logAdEvent("🚀 Attempting to show rewarded ad...")
            adMobManager.showRewardedAd(
                activity,
                onRewarded = { amount ->
                    TestRigorLogger.logAdEvent("💰 Reward earned: $amount")
                    notifyWebApp("rewardEarned", amount.toString())
                },
                onAdClosed = {
                    TestRigorLogger.logAdEvent("✅ Rewarded ad closed callback")
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
        return adMobManager.isRewardedAdAvailable()
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
     * Check if AdMob is initialized
     * Usage: window.AdBridge.isInitialized()
     */
    @JavascriptInterface
    fun isInitialized(): Boolean {
        return adMobManager.isInitialized.get()
    }

    /**
     * Get diagnostic information
     * Usage: window.AdBridge.getDiagnostics()
     */
    @JavascriptInterface
    fun getDiagnostics(): String {
        return """
            {
                "adBridgeAvailable": true,
                "nativeAdBridge": true,
                "adMobInitialized": ${adMobManager.isInitialized.get()},
                "rewardedReady": ${adMobManager.isRewardedAdAvailable()},
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()
    }

    /**
     * Force show interstitial ad (bypass frequency cap for testing)
     * Usage: window.AdBridge.testShowInterstitial()
     */
    @JavascriptInterface
    fun testShowInterstitial() {
        TestRigorLogger.logAdEvent("🧪 TEST: Force showing interstitial ad")

        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("❌ Cannot test - activity invalid")
            return
        }

        activity.runOnUiThread {
            adMobManager.forceShowInterstitial(activity) {
                TestRigorLogger.logAdEvent("✅ TEST: Interstitial closed")
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