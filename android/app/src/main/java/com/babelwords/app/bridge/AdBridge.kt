package com.babelwords.app.bridge

import android.app.Activity
import android.webkit.JavascriptInterface
import com.babelwords.app.MainActivity
import com.babelwords.app.ads.AdMobManager
import org.json.JSONObject

/**
 * Exposed to JavaScript as window.AdBridge
 *
 * Matches the NativeAdBridge TypeScript interface in android.d.ts exactly.
 * Callbacks fire via window.onAdBridgeEvent (handled in MainActivity).
 */
class AdBridge(
    private val activity: Activity,
    private val adMobManagerProvider: () -> AdMobManager?,
) {

    @JavascriptInterface
    fun initialize() {
        val mgr = adMobManagerProvider() ?: return
        activity.runOnUiThread {
            mgr.preloadInterstitial()
            mgr.preloadRewarded()
        }
        fireEvent("adMobInitialized", "true")
    }

    @JavascriptInterface
    fun loadInterstitial() {
        val mgr = adMobManagerProvider() ?: return
        activity.runOnUiThread { mgr.preloadInterstitial() }
    }

    @JavascriptInterface
    fun showInterstitial() {
        val mgr = adMobManagerProvider() ?: run {
            fireEvent("interstitialFailed", "manager_not_ready")
            return
        }
        activity.runOnUiThread { mgr.showInterstitial(activity) }
    }

    @JavascriptInterface
    fun isInterstitialReady(): Boolean =
        adMobManagerProvider()?.isInterstitialReady() ?: false

    @JavascriptInterface
    fun showRewarded() {
        val mgr = adMobManagerProvider() ?: run {
            fireEvent("rewardedFailed", "manager_not_ready")
            return
        }
        activity.runOnUiThread { mgr.showRewarded(activity) }
    }

    @JavascriptInterface
    fun isRewardedReady(): Boolean =
        adMobManagerProvider()?.isRewardedReady() ?: false

    @JavascriptInterface
    fun isInitialized(): Boolean =
        adMobManagerProvider()?.isInitialized() ?: false

    @JavascriptInterface
    fun getDiagnostics(): String {
        val mgr = adMobManagerProvider()
        return JSONObject().apply {
            put("adMobInitialized", mgr?.isInitialized() ?: false)
            put("interstitialReady", mgr?.isInterstitialReady() ?: false)
            put("rewardedReady", mgr?.isRewardedReady() ?: false)
            put("timestamp", System.currentTimeMillis())
        }.toString()
    }

    @JavascriptInterface
    fun testShowInterstitial() {
        val mgr = adMobManagerProvider() ?: return
        activity.runOnUiThread { mgr.showInterstitial(activity) }
    }

    @JavascriptInterface
    fun logEvent(eventName: String) {
        android.util.Log.d("AdBridge", "JS logEvent: $eventName")
    }

    internal fun fireEvent(eventType: String, data: String = "") {
        android.util.Log.d("AdBridge", "fireEvent: $eventType data=$data")
        val escaped = data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        activity.runOnUiThread {
            (activity as? MainActivity)?.evalJs(
                "window.onAdBridgeEvent && window.onAdBridgeEvent('$eventType', '$escaped');"
            )
        }
    }
}
