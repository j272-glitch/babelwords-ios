package com.lingualink.linguagt.ads

import android.app.Activity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * AdMob-Only Bridge for LinguaVibe
 * 
 * Handles: Banner, Interstitial, Rewarded ads
 * Network: AdMob only (no InMobi)
 * 
 * Usage in MainActivity:
 *   val adBridge = AdMobBridge(this, webView)
 *   webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
 */
class AdMobBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "AdMobBridge"
        
        // Default placement IDs (can be overridden by JS)
        private const val DEFAULT_BANNER_ID = "ca-app-pub-9991891515643313/6878126239"
        private const val DEFAULT_INTERSTITIAL_ID = "ca-app-pub-9991891515643313/5076005693"
        private const val DEFAULT_REWARDED_ID = "ca-app-pub-9991891515643313/6313049833"
    }

    // Current placement IDs (set from JavaScript or use defaults)
    private var currentBannerId = DEFAULT_BANNER_ID
    private var currentInterstitialId = DEFAULT_INTERSTITIAL_ID
    private var currentRewardedId = DEFAULT_REWARDED_ID

    // Ad objects
    private var bannerAd: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // State tracking
    private var isBannerReady = false
    private var isInterstitialReady = false
    private var isRewardedReady = false

    // Impression counters
    private var totalImpressions = 0
    private var bannerImpressions = 0
    private var interstitialImpressions = 0
    private var rewardedImpressions = 0

    // Activity reference (weak to prevent leaks)
    private val activityRef = WeakReference(activity)

    init {
        log("═".repeat(50))
        log("ADMOB BRIDGE INITIALIZED")
        log("═".repeat(50))
        log("Activity: ${activity.javaClass.simpleName}")
        initializeAdMob()
    }

    private fun log(message: String, level: String = "D") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val fullMessage = "[$timestamp] $message"
        when (level) {
            "E" -> Log.e(TAG, fullMessage)
            "W" -> Log.w(TAG, fullMessage)
            else -> Log.d(TAG, fullMessage)
        }
    }

    private fun initializeAdMob() {
        log("Initializing AdMob SDK...")
        try {
            MobileAds.initialize(activity) { initStatus ->
                log("✓ AdMob SDK initialized")
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    log("  Adapter: $adapter")
                    log("    State: ${status.initializationState}")
                    log("    Latency: ${status.latency}ms")
                }
                notifyJs("sdkInitialized", "admob", "sdk")
            }
        } catch (e: Exception) {
            log("✗ AdMob init exception: ${e.message}", "E")
            notifyJs("sdkInitFailed", "admob", "sdk", e.message)
        }
    }

    private fun isAdMobId(placementId: String): Boolean {
        return placementId.startsWith("ca-app-pub-")
    }

    // ==================== JavaScript Interface Methods ====================

    @JavascriptInterface
    fun loadBanner(placementId: String, position: String) {
        log("═".repeat(50))
        log("JS → loadBanner(placementId=$placementId, position=$position)")
        log("═".repeat(50))

        if (isAdMobId(placementId)) {
            currentBannerId = placementId
            log("  ✓ Using Banner ID from JS: $currentBannerId")
        }

        activity.runOnUiThread {
            loadBannerAd(position)
        }
    }

    @JavascriptInterface
    fun hideBanner(placementId: String) {
        log("JS → hideBanner()")
        activity.runOnUiThread {
            bannerAd?.visibility = View.GONE
            log("Banner hidden")
        }
    }

    @JavascriptInterface
    fun loadInterstitial(placementId: String) {
        log("═".repeat(50))
        log("JS → loadInterstitial(placementId=$placementId)")
        log("═".repeat(50))

        if (isAdMobId(placementId)) {
            currentInterstitialId = placementId
            log("  ✓ Using Interstitial ID from JS: $currentInterstitialId")
        }

        activity.runOnUiThread {
            loadInterstitialAd()
        }
    }

    @JavascriptInterface
    fun showInterstitial(placementId: String) {
        log("═".repeat(50))
        log("JS → showInterstitial()")
        log("═".repeat(50))

        activity.runOnUiThread {
            showInterstitialAd()
        }
    }

    @JavascriptInterface
    fun loadRewarded(placementId: String) {
        log("═".repeat(50))
        log("JS → loadRewarded(placementId=$placementId)")
        log("═".repeat(50))

        if (isAdMobId(placementId)) {
            currentRewardedId = placementId
            log("  ✓ Using Rewarded ID from JS: $currentRewardedId")
        }

        activity.runOnUiThread {
            loadRewardedAd()
        }
    }

    @JavascriptInterface
    fun showRewarded(placementId: String) {
        log("═".repeat(50))
        log("JS → showRewarded()")
        log("═".repeat(50))

        activity.runOnUiThread {
            showRewardedAd()
        }
    }

    @JavascriptInterface
    fun isInterstitialReady(placementId: String): Boolean {
        log("JS → isInterstitialReady() = $isInterstitialReady")
        return isInterstitialReady
    }

    @JavascriptInterface
    fun isRewardedReady(placementId: String): Boolean {
        log("JS → isRewardedReady() = $isRewardedReady")
        return isRewardedReady
    }

    @JavascriptInterface
    fun getImpressionCount(): Int {
        return totalImpressions
    }

    @JavascriptInterface
    fun getAdMobImpressions(): Int {
        return totalImpressions
    }

    @JavascriptInterface
    fun getBannerImpressions(): Int {
        return bannerImpressions
    }

    @JavascriptInterface
    fun getInterstitialImpressions(): Int {
        return interstitialImpressions
    }

    @JavascriptInterface
    fun getRewardedImpressions(): Int {
        return rewardedImpressions
    }

    // ==================== Ad Loading Methods ====================

    private fun loadBannerAd(position: String) {
        log("Loading AdMob Banner...")
        log("  Ad Unit ID: $currentBannerId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "banner", "Activity reference lost")
            return
        }

        try {
            bannerAd = AdView(act).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = currentBannerId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isBannerReady = true
                        log("✓ BANNER LOADED")
                        notifyJs("adLoaded", "admob", "banner")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isBannerReady = false
                        log("✗ Banner load FAILED: ${error.code} - ${error.message}", "E")
                        notifyJs("adFailed", "admob", "banner", error.message)
                    }

                    override fun onAdImpression() {
                        totalImpressions++
                        bannerImpressions++
                        log("★★★ BANNER IMPRESSION #$bannerImpressions ★★★")
                        notifyJs("adImpression", "admob", "banner")
                    }

                    override fun onAdClicked() {
                        log("Banner clicked")
                        notifyJs("adClicked", "admob", "banner")
                    }
                }
            }

            val adRequest = AdRequest.Builder().build()
            bannerAd?.loadAd(adRequest)
            log("Banner loadAd() called")

            // Add to layout
            val rootView = act.findViewById<ViewGroup>(android.R.id.content)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (position == "top") android.view.Gravity.TOP else android.view.Gravity.BOTTOM
            }
            bannerAd?.layoutParams = params
            rootView.addView(bannerAd)
            log("Banner added to layout at $position")

        } catch (e: Exception) {
            log("✗ Banner exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "banner", e.message ?: "Unknown error")
        }
    }

    private fun loadInterstitialAd() {
        log("Loading AdMob Interstitial...")
        log("  Ad Unit ID: $currentInterstitialId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "interstitial", "Activity reference lost")
            return
        }

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(act, currentInterstitialId, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialReady = true
                        log("✓ INTERSTITIAL LOADED")

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                totalImpressions++
                                interstitialImpressions++
                                log("★★★ INTERSTITIAL IMPRESSION #$interstitialImpressions ★★★")
                                notifyJs("adImpression", "admob", "interstitial")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isInterstitialReady = false
                                interstitialAd = null
                                log("Interstitial dismissed")
                                notifyJs("adClosed", "admob", "interstitial")
                                loadInterstitialAd() // Preload next
                            }

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                isInterstitialReady = false
                                interstitialAd = null
                                log("✗ Interstitial show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "interstitial", error.message)
                            }

                            override fun onAdClicked() {
                                log("Interstitial clicked")
                                notifyJs("adClicked", "admob", "interstitial")
                            }
                        }

                        notifyJs("adLoaded", "admob", "interstitial")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isInterstitialReady = false
                        log("✗ Interstitial load FAILED: ${error.code} - ${error.message}", "E")
                        log("  Domain: ${error.domain}")
                        notifyJs("adFailed", "admob", "interstitial", error.message)
                    }
                })
        } catch (e: Exception) {
            log("✗ Interstitial exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "interstitial", e.message ?: "Unknown error")
        }
    }

    private fun loadRewardedAd() {
        log("Loading AdMob Rewarded...")
        log("  Ad Unit ID: $currentRewardedId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "rewarded", "Activity reference lost")
            return
        }

        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(act, currentRewardedId, adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isRewardedReady = true
                        log("✓ REWARDED LOADED")

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                totalImpressions++
                                rewardedImpressions++
                                log("★★★ REWARDED IMPRESSION #$rewardedImpressions ★★★")
                                notifyJs("adImpression", "admob", "rewarded")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isRewardedReady = false
                                rewardedAd = null
                                log("Rewarded dismissed")
                                notifyJs("adClosed", "admob", "rewarded")
                                loadRewardedAd() // Preload next
                            }

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                isRewardedReady = false
                                rewardedAd = null
                                log("✗ Rewarded show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "rewarded", error.message)
                            }

                            override fun onAdClicked() {
                                log("Rewarded clicked")
                                notifyJs("adClicked", "admob", "rewarded")
                            }
                        }

                        notifyJs("adLoaded", "admob", "rewarded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isRewardedReady = false
                        log("✗ Rewarded load FAILED: ${error.code} - ${error.message}", "E")
                        log("  Domain: ${error.domain}")
                        notifyJs("adFailed", "admob", "rewarded", error.message)
                    }
                })
        } catch (e: Exception) {
            log("✗ Rewarded exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "rewarded", e.message ?: "Unknown error")
        }
    }

    // ==================== Ad Show Methods ====================

    private fun showInterstitialAd() {
        log("→ Attempting to show interstitial...")
        log("  Ready: $isInterstitialReady")
        log("  Object: ${interstitialAd != null}")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "interstitial", "Activity lost")
            return
        }

        if (isInterstitialReady && interstitialAd != null) {
            log("  ► Calling interstitial.show()")
            interstitialAd?.show(act)
            log("  ✓ show() called successfully")
        } else {
            log("✗ No interstitial ready to show!", "W")
            notifyJs("adFailed", "admob", "interstitial", "Not ready")
        }
    }

    private fun showRewardedAd() {
        log("→ Attempting to show rewarded...")
        log("  Ready: $isRewardedReady")
        log("  Object: ${rewardedAd != null}")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "rewarded", "Activity lost")
            return
        }

        if (isRewardedReady && rewardedAd != null) {
            log("  ► Calling rewarded.show()")
            rewardedAd?.show(act) { reward ->
                log("★★★ USER EARNED REWARD ★★★")
                log("  Type: ${reward.type}")
                log("  Amount: ${reward.amount}")
                notifyJs("adRewarded", "admob", "rewarded", null, mapOf(
                    "type" to reward.type,
                    "amount" to reward.amount
                ))
            }
            log("  ✓ show() called successfully")
        } else {
            log("✗ No rewarded ready to show!", "W")
            notifyJs("adFailed", "admob", "rewarded", "Not ready")
        }
    }

    // ==================== JavaScript Notification ====================

    private fun notifyJs(
        eventType: String,
        network: String,
        adType: String,
        error: String? = null,
        data: Map<String, Any>? = null
    ) {
        val json = JSONObject().apply {
            put("type", eventType)
            put("network", network)
            put("adType", adType)
            put("placementId", when (adType) {
                "banner" -> currentBannerId
                "interstitial" -> currentInterstitialId
                "rewarded" -> currentRewardedId
                else -> ""
            })
            error?.let { put("error", it) }
            data?.let { 
                val dataObj = JSONObject()
                it.forEach { (k, v) -> dataObj.put(k, v) }
                put("data", dataObj)
            }
        }

        val script = "window.onAdEvent && window.onAdEvent('${json.toString().replace("'", "\\'")}')"
        
        log("→ Notifying JS: $eventType [$network] $adType")
        
        activity.runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    // ==================== Cleanup ====================

    fun destroy() {
        log("Destroying AdMobBridge...")
        bannerAd?.destroy()
        bannerAd = null
        interstitialAd = null
        rewardedAd = null
        log("AdMobBridge destroyed")
    }
}
