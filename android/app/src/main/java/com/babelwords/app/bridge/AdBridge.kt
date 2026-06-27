package com.babelwords.app.bridge

import android.app.Activity
import android.webkit.JavascriptInterface
import android.util.Log
import com.babelwords.app.ads.AdMobManager
import com.babelwords.app.ads.ConsentManager
import org.json.JSONObject

/**
 * Exposed to JavaScript as window.AdBridge
 *
 * Matches the NativeAdBridge TypeScript interface. All callbacks fire via
 * window.onAdBridgeEvent (handled in MainActivity).
 *
 * Upgraded with features from the LinguaVibe v46 guide:
 *   - loadInterstitialAndShow() / loadRewardedAndShow() — 1-step load-and-show
 *   - isInterstitialReady() / isRewardedReady() — checks ad + show-state
 *   - setActivityResumed() — lifecycle tracking
 *   - getDiagnostics() — returns JSON with ad state
 *   - requestConsent() — triggers UMP consent flow
 *   - Backward-compatible aliases for existing calls
 */
class AdBridge(
    private val activity: Activity,
    private val adMobManagerProvider: () -> AdMobManager?,
    private val consentManagerProvider: () -> ConsentManager? = { null },
) {
    private val TAG = "AdBridge"

    // ==================== Consent ====================
    @JavascriptInterface
    fun requestConsent() {
        val consentManager = consentManagerProvider() ?: return
        activity.runOnUiThread {
            consentManager.requestConsent(activity) { canRequestAds ->
                Log.d(TAG, "Consent resolved: canRequestAds=$canRequestAds")
                fireEvent("consentResolved", if (canRequestAds) "true" else "false")
            }
        }
    }

    // ==================== Legacy / Backward-compatible ====================
    @JavascriptInterface
    fun initialize() {
        val mgr = adMobManagerProvider() ?: return
        activity.runOnUiThread { mgr.preloadInterstitial() }
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
    fun isInterstitialReady(): Boolean {
        val mgr = adMobManagerProvider() ?: return false
        return mgr.isInterstitialReady()
    }

    @JavascriptInterface
    fun loadRewarded() {
        // Rewarded removed — alias to interstitial preload for backward compat
        val mgr = adMobManagerProvider() ?: return
        activity.runOnUiThread { mgr.preloadInterstitial() }
    }

    @JavascriptInterface
    fun showRewarded() {
        val mgr = adMobManagerProvider() ?: run {
            fireEvent("rewardedFailed", "manager_not_ready")
            return
        }
        // Alias to interstitial show
        activity.runOnUiThread { mgr.showInterstitial(activity) }
    }

    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        val mgr = adMobManagerProvider() ?: return false
        return mgr.isInterstitialReady() // alias to interstitial
    }

    @JavascriptInterface
    fun isInitialized(): Boolean {
        val mgr = adMobManagerProvider() ?: return false
        return mgr.isInitialized()
    }

    @JavascriptInterface
    fun getDiagnostics(): String {
        val mgr = adMobManagerProvider()
        val consentMgr = consentManagerProvider()
        return JSONObject().apply {
            put("adMobInitialized", mgr?.isInitialized() ?: false)
            put("interstitialReady", mgr?.isInterstitialReady() ?: false)
            put("rewardedReady", mgr?.isRewardedReady() ?: false)
            put("consentAvailable", consentMgr?.isConsentAvailable() ?: false)
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
        Log.d(TAG, "JS logEvent: $eventName")
    }

    // ==================== 1-step load-and-show (new) ====================
    @JavascriptInterface
    fun loadInterstitialAndShow() {
        val mgr = adMobManagerProvider() ?: run {
            fireEvent("interstitialFailed", "manager_not_ready")
            return
        }
        activity.runOnUiThread { mgr.loadInterstitialAndShow(activity) }
    }

    @JavascriptInterface
    fun loadRewardedAndShow() {
        // Backward-compat alias — rewarded video removed, now uses interstitial
        val mgr = adMobManagerProvider() ?: run {
            fireEvent("rewardedFailed", "manager_not_ready")
            return
        }
        activity.runOnUiThread { mgr.loadRewardedAndShow(activity) }
    }

    // ==================== Lifecycle (called from MainActivity) ====================
    // No-op: AdMobManager now handles its own foreground tracking via
    // onActivityResumed / onActivityPaused (called directly from MainActivity).

    // ==================== Event dispatch ====================
    internal fun fireEvent(eventType: String, data: String = "") {
        Log.d(TAG, "fireEvent: $eventType data=$data")
        val escaped = data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        activity.runOnUiThread {
            (activity as? com.babelwords.app.MainActivity)?.evalJs(
                "window.onAdBridgeEvent && window.onAdBridgeEvent('$eventType', '$escaped');"
            )
        }
    }
}
