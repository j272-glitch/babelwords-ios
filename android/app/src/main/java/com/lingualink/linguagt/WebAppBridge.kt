package com.lingualink.linguagt

import android.app.Activity
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
 */
class WebAppBridge(private val activity: Activity) {

    companion object {
        private const val TAG = "WebAppBridge"
    }

    private val adMobManager: AdMobManager by lazy {
        AdMobManager.getInstance(activity)
    }

    /**
     * Show interstitial ad (called when user hits 5 translation limit)
     * Web app should call: window.AndroidBridge.showInterstitialAd()
     */
    @JavascriptInterface
    fun showInterstitialAd() {
        TestRigorLogger.logAdEvent("Web app requested interstitial ad")

        activity.runOnUiThread {
            adMobManager.showInterstitialAd(activity) {
                // After ad closes, notify web app
                notifyWebApp("interstitial_closed")
            }
        }
    }

    /**
     * Show rewarded ad for 30 minutes of premium access
     * Web app should call: window.AndroidBridge.showRewardedAd()
     */
    @JavascriptInterface
    fun showRewardedAd() {
        TestRigorLogger.logAdEvent("Web app requested rewarded ad")

        activity.runOnUiThread {
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

        Toast.makeText(activity, "Premium access granted for $minutes minutes!", Toast.LENGTH_LONG).show()
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
     */
    private fun notifyWebApp(eventType: String, data: String = "{}") {
        activity.runOnUiThread {
            val js = """
                (function() {
                    try {
                        if (window.onNativeEvent) {
                            window.onNativeEvent('$eventType', $data);
                        }

                        // Also dispatch custom event
                        const event = new CustomEvent('native_$eventType', { 
                            detail: $data 
                        });
                        window.dispatchEvent(event);

                        console.log('Native event dispatched: $eventType', $data);
                    } catch(e) {
                        console.error('Error dispatching native event:', e);
                    }
                })();
            """.trimIndent()

            try {
                (activity as? MainActivity)?.getWebView()?.evaluateJavascript(js, null)
            } catch (e: Exception) {
                TestRigorLogger.logError("Failed to notify web app: $eventType", e)
            }
        }
    }
}
