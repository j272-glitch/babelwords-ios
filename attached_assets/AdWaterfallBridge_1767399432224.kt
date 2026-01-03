package com.linguavibe.app.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.inmobi.ads.*
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * AdWaterfallBridge.kt
 * 
 * Waterfall Ad Integration for LinguaVibe
 * Primary: InMobi | Fallback: AdMob
 * 
 * Supports: Banner, Interstitial, Rewarded
 * Target: 10,000 InMobi impressions
 * 
 * Flow:
 * 1. Try InMobi first (builds account history)
 * 2. If InMobi fails → fallback to AdMob
 * 3. Track which network served each ad
 */

// =============================================================================
// CONFIGURATION
// =============================================================================

object AdConfig {
    
    // -------------------------------------------------------------------------
    // INMOBI - Primary Network
    // Account: Global Translation
    // -------------------------------------------------------------------------
    object InMobi {
        const val ACCOUNT_ID = "9d81516c365f4acaa52f1fc627370cf9"
        
        object Placements {
            const val BANNER: Long = 10000582111L        // LV-Banner
            const val INTERSTITIAL: Long = 10000582110L  // LV-Interstitial
            const val REWARDED: Long = 10000582112L      // LV-Rewarded
        }
    }
    
    // -------------------------------------------------------------------------
    // ADMOB - Fallback Network
    // Replace with your actual AdMob unit IDs
    // -------------------------------------------------------------------------
    object AdMob {
        // TODO: Replace these with your actual AdMob ad unit IDs
        const val BANNER = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
        const val INTERSTITIAL = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
        const val REWARDED = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
        
        // Test ad unit IDs (use during development)
        object Test {
            const val BANNER = "ca-app-pub-3940256099942544/6300978111"
            const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
            const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
        }
    }
    
    // -------------------------------------------------------------------------
    // SETTINGS
    // -------------------------------------------------------------------------
    
    // Set to true to use AdMob test ads
    const val USE_ADMOB_TEST_ADS = false
    
    // Set to true for debug logging
    const val DEBUG_MODE = true
    
    // Waterfall timeout (ms) - how long to wait for InMobi before trying AdMob
    const val WATERFALL_TIMEOUT = 5000L
    
    // Privacy
    object Privacy {
        var gdprConsent: Boolean = true
        var ccpaDoNotSell: Boolean = false
    }
    
    const val LOG_TAG = "AdWaterfall"
}

// =============================================================================
// AD EVENT
// =============================================================================

data class AdEvent(
    val type: String,
    val placementId: String,
    val network: String = "unknown",
    val data: Map<String, Any>? = null,
    val error: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("type", type)
            put("placementId", placementId)
            put("network", network)
            data?.let { put("data", JSONObject(it)) }
            error?.let { put("error", it) }
        }.toString()
    }
}

// =============================================================================
// NETWORK ENUM
// =============================================================================

enum class AdNetwork {
    INMOBI,
    ADMOB,
    NONE
}

// =============================================================================
// MAIN WATERFALL BRIDGE
// =============================================================================

class AdWaterfallBridge(
    activity: Activity,
    webView: WebView,
    private val rootLayout: FrameLayout
) : DefaultLifecycleObserver {

    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)
    
    // SDK States
    private var isInMobiInitialized = false
    private var isAdMobInitialized = false
    
    // -------------------------------------------------------------------------
    // BANNER STATE
    // -------------------------------------------------------------------------
    private var inMobiBanner: InMobiBanner? = null
    private var adMobBanner: AdView? = null
    private var currentBannerNetwork: AdNetwork = AdNetwork.NONE
    private var bannerPosition: String = "bottom"
    
    // -------------------------------------------------------------------------
    // INTERSTITIAL STATE
    // -------------------------------------------------------------------------
    private var inMobiInterstitial: InMobiInterstitial? = null
    private var adMobInterstitial: InterstitialAd? = null
    private var isInMobiInterstitialReady = false
    private var isAdMobInterstitialReady = false
    
    // -------------------------------------------------------------------------
    // REWARDED STATE
    // -------------------------------------------------------------------------
    private var inMobiRewarded: InMobiInterstitial? = null
    private var adMobRewarded: RewardedAd? = null
    private var isInMobiRewardedReady = false
    private var isAdMobRewardedReady = false
    
    // -------------------------------------------------------------------------
    // IMPRESSION TRACKING
    // -------------------------------------------------------------------------
    private var totalImpressions = 0
    private var inMobiImpressions = 0
    private var adMobImpressions = 0
    private val targetImpressions = 10000

    // ==========================================================================
    // INITIALIZATION
    // ==========================================================================

    init {
        log("Initializing Ad Waterfall Bridge...")
        setupJavaScriptBridge()
        initializeInMobi()
        initializeAdMob()
    }

    private fun initializeInMobi() {
        val activity = activityRef.get() ?: return
        
        val consentObject = JSONObject().apply {
            put("gdpr_consent_available", AdConfig.Privacy.gdprConsent)
            put("gdpr", if (AdConfig.Privacy.gdprConsent) "1" else "0")
        }
        
        InMobiSdk.init(activity, AdConfig.InMobi.ACCOUNT_ID, consentObject, object : SdkInitializationListener {
            override fun onInitializationComplete(error: Error?) {
                if (error == null) {
                    isInMobiInitialized = true
                    log("✅ InMobi SDK initialized")
                    
                    if (AdConfig.DEBUG_MODE) {
                        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG)
                    }
                    
                    // Preload InMobi ads
                    preloadInMobiInterstitial()
                    preloadInMobiRewarded()
                } else {
                    log("❌ InMobi init failed: ${error.message}", isError = true)
                }
            }
        })
    }

    private fun initializeAdMob() {
        val activity = activityRef.get() ?: return
        
        MobileAds.initialize(activity) { initializationStatus ->
            isAdMobInitialized = true
            log("✅ AdMob SDK initialized")
            
            // Preload AdMob ads as fallback
            preloadAdMobInterstitial()
            preloadAdMobRewarded()
        }
    }

    // ==========================================================================
    // JAVASCRIPT BRIDGE
    // ==========================================================================

    @SuppressLint("JavascriptInterface")
    private fun setupJavaScriptBridge() {
        val webView = webViewRef.get() ?: return
        webView.addJavascriptInterface(AdJsBridge(), "AndroidAdBridge")
        log("JavaScript bridge registered: window.AndroidAdBridge")
    }

    inner class AdJsBridge {
        
        @JavascriptInterface
        fun loadBanner(placementId: String, position: String) {
            log("JS → loadBanner(position=$position)")
            runOnUiThread { 
                bannerPosition = position
                loadBannerWaterfall(position) 
            }
        }
        
        @JavascriptInterface
        fun hideBanner(placementId: String) {
            log("JS → hideBanner()")
            runOnUiThread { hideAllBanners() }
        }
        
        @JavascriptInterface
        fun loadInterstitial(placementId: String) {
            log("JS → loadInterstitial()")
            runOnUiThread { 
                preloadInMobiInterstitial()
                preloadAdMobInterstitial()
            }
        }
        
        @JavascriptInterface
        fun showInterstitial(placementId: String) {
            log("JS → showInterstitial()")
            runOnUiThread { showInterstitialWaterfall() }
        }
        
        @JavascriptInterface
        fun loadRewarded(placementId: String) {
            log("JS → loadRewarded()")
            runOnUiThread { 
                preloadInMobiRewarded()
                preloadAdMobRewarded()
            }
        }
        
        @JavascriptInterface
        fun showRewarded(placementId: String) {
            log("JS → showRewarded()")
            runOnUiThread { showRewardedWaterfall() }
        }
        
        @JavascriptInterface
        fun isAdReady(placementId: String): Boolean {
            return when {
                placementId.contains("interstitial", ignoreCase = true) -> 
                    isInMobiInterstitialReady || isAdMobInterstitialReady
                placementId.contains("rewarded", ignoreCase = true) -> 
                    isInMobiRewardedReady || isAdMobRewardedReady
                else -> currentBannerNetwork != AdNetwork.NONE
            }
        }
        
        @JavascriptInterface
        fun getImpressionCount(): Int = totalImpressions
        
        @JavascriptInterface
        fun getInMobiImpressions(): Int = inMobiImpressions
        
        @JavascriptInterface
        fun getAdMobImpressions(): Int = adMobImpressions
        
        @JavascriptInterface
        fun getImpressionProgress(): Float = 
            (inMobiImpressions.toFloat() / targetImpressions) * 100
            
        @JavascriptInterface
        fun trackImpression(eventJson: String) {
            log("JS → trackImpression")
        }
    }

    private fun sendEventToWeb(event: AdEvent) {
        val webView = webViewRef.get() ?: return
        val escapedJson = event.toJson().replace("\\", "\\\\").replace("'", "\\'")
        val js = "javascript:if(window.onAdEvent){window.onAdEvent('$escapedJson');}"
        
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
        
        log("→ Web: ${event.type} [${event.network}]")
    }

    // ==========================================================================
    // BANNER WATERFALL
    // ==========================================================================

    private fun loadBannerWaterfall(position: String) {
        log("Loading banner waterfall...")
        
        // Always try InMobi first
        if (isInMobiInitialized) {
            loadInMobiBanner(position)
        } else if (isAdMobInitialized) {
            // InMobi not ready, go straight to AdMob
            loadAdMobBanner(position)
        } else {
            log("No ad SDK initialized yet", isError = true)
        }
    }

    private fun loadInMobiBanner(position: String) {
        val activity = activityRef.get() ?: return
        
        hideAllBanners()
        log("Trying InMobi banner...")
        
        inMobiBanner = InMobiBanner(activity, AdConfig.InMobi.Placements.BANNER).apply {
            layoutParams = createBannerLayoutParams(activity, position)
            
            setListener(object : BannerAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                    log("✅ InMobi banner loaded")
                    currentBannerNetwork = AdNetwork.INMOBI
                    sendEventToWeb(AdEvent("adLoaded", "banner", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                    log("❌ InMobi banner failed: ${status.message} → Trying AdMob")
                    // Fallback to AdMob
                    loadAdMobBanner(position)
                }
                
                override fun onAdDisplayed(ad: InMobiBanner) {
                    log("InMobi banner displayed")
                    recordImpression(AdNetwork.INMOBI, "banner")
                    sendEventToWeb(AdEvent("adImpression", "banner", "inmobi"))
                }
                
                override fun onAdClicked(ad: InMobiBanner, params: MutableMap<Any, Any>?) {
                    sendEventToWeb(AdEvent("adClicked", "banner", "inmobi"))
                }
            })
            
            setEnableAutoRefresh(true)
            setRefreshInterval(45)
        }
        
        rootLayout.addView(inMobiBanner)
        inMobiBanner?.load()
    }

    private fun loadAdMobBanner(position: String) {
        val activity = activityRef.get() ?: return
        
        hideAllBanners()
        log("Trying AdMob banner...")
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.BANNER
        } else {
            AdConfig.AdMob.BANNER
        }
        
        adMobBanner = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            layoutParams = createBannerLayoutParams(activity, position)
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    log("✅ AdMob banner loaded")
                    currentBannerNetwork = AdNetwork.ADMOB
                    sendEventToWeb(AdEvent("adLoaded", "banner", "admob"))
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("❌ AdMob banner failed: ${error.message}", isError = true)
                    currentBannerNetwork = AdNetwork.NONE
                    sendEventToWeb(AdEvent("adFailed", "banner", "admob", error = error.message))
                }
                
                override fun onAdImpression() {
                    log("AdMob banner impression")
                    recordImpression(AdNetwork.ADMOB, "banner")
                    sendEventToWeb(AdEvent("adImpression", "banner", "admob"))
                }
                
                override fun onAdClicked() {
                    sendEventToWeb(AdEvent("adClicked", "banner", "admob"))
                }
            }
        }
        
        rootLayout.addView(adMobBanner)
        adMobBanner?.loadAd(AdRequest.Builder().build())
    }

    private fun createBannerLayoutParams(context: Context, position: String): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 50)
        ).apply {
            gravity = if (position.equals("top", ignoreCase = true)) Gravity.TOP else Gravity.BOTTOM
        }
    }

    private fun hideAllBanners() {
        inMobiBanner?.let { 
            rootLayout.removeView(it)
            it.destroy()
        }
        inMobiBanner = null
        
        adMobBanner?.let {
            rootLayout.removeView(it)
            it.destroy()
        }
        adMobBanner = null
        
        currentBannerNetwork = AdNetwork.NONE
    }

    // ==========================================================================
    // INTERSTITIAL WATERFALL
    // ==========================================================================

    private fun preloadInMobiInterstitial() {
        if (!isInMobiInitialized || isInMobiInterstitialReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading InMobi interstitial...")
        
        inMobiInterstitial = InMobiInterstitial(
            activity,
            AdConfig.InMobi.Placements.INTERSTITIAL,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("✅ InMobi interstitial loaded")
                    isInMobiInterstitialReady = true
                    sendEventToWeb(AdEvent("adLoaded", "interstitial", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("❌ InMobi interstitial failed: ${status.message}")
                    isInMobiInterstitialReady = false
                    scheduleRetry { preloadInMobiInterstitial() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("InMobi interstitial displayed")
                    recordImpression(AdNetwork.INMOBI, "interstitial")
                    sendEventToWeb(AdEvent("adImpression", "interstitial", "inmobi"))
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("InMobi interstitial dismissed")
                    isInMobiInterstitialReady = false
                    sendEventToWeb(AdEvent("adClosed", "interstitial", "inmobi"))
                    preloadInMobiInterstitial()
                }
                
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    log("❌ InMobi interstitial display failed")
                    isInMobiInterstitialReady = false
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    sendEventToWeb(AdEvent("adClicked", "interstitial", "inmobi"))
                }
            }
        )
        
        inMobiInterstitial?.load()
    }

    private fun preloadAdMobInterstitial() {
        if (!isAdMobInitialized || isAdMobInterstitialReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading AdMob interstitial...")
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.INTERSTITIAL
        } else {
            AdConfig.AdMob.INTERSTITIAL
        }
        
        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    log("✅ AdMob interstitial loaded")
                    adMobInterstitial = ad
                    isAdMobInterstitialReady = true
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            log("AdMob interstitial displayed")
                            recordImpression(AdNetwork.ADMOB, "interstitial")
                            sendEventToWeb(AdEvent("adImpression", "interstitial", "admob"))
                        }
                        
                        override fun onAdDismissedFullScreenContent() {
                            log("AdMob interstitial dismissed")
                            isAdMobInterstitialReady = false
                            adMobInterstitial = null
                            sendEventToWeb(AdEvent("adClosed", "interstitial", "admob"))
                            preloadAdMobInterstitial()
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            log("❌ AdMob interstitial show failed: ${error.message}")
                            isAdMobInterstitialReady = false
                            adMobInterstitial = null
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("❌ AdMob interstitial load failed: ${error.message}")
                    isAdMobInterstitialReady = false
                    scheduleRetry { preloadAdMobInterstitial() }
                }
            }
        )
    }

    private fun showInterstitialWaterfall() {
        val activity = activityRef.get() ?: return
        
        // Try InMobi first
        if (isInMobiInterstitialReady && inMobiInterstitial?.isReady == true) {
            log("Showing InMobi interstitial")
            inMobiInterstitial?.show()
            return
        }
        
        // Fallback to AdMob
        if (isAdMobInterstitialReady && adMobInterstitial != null) {
            log("Showing AdMob interstitial (fallback)")
            adMobInterstitial?.show(activity)
            return
        }
        
        log("No interstitial ready", isError = true)
        sendEventToWeb(AdEvent("adFailed", "interstitial", "none", error = "No ad ready"))
        
        // Preload for next time
        preloadInMobiInterstitial()
        preloadAdMobInterstitial()
    }

    // ==========================================================================
    // REWARDED WATERFALL
    // ==========================================================================

    private fun preloadInMobiRewarded() {
        if (!isInMobiInitialized || isInMobiRewardedReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading InMobi rewarded...")
        
        inMobiRewarded = InMobiInterstitial(
            activity,
            AdConfig.InMobi.Placements.REWARDED,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("✅ InMobi rewarded loaded")
                    isInMobiRewardedReady = true
                    sendEventToWeb(AdEvent("adLoaded", "rewarded", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("❌ InMobi rewarded failed: ${status.message}")
                    isInMobiRewardedReady = false
                    scheduleRetry { preloadInMobiRewarded() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("InMobi rewarded displayed")
                    recordImpression(AdNetwork.INMOBI, "rewarded")
                    sendEventToWeb(AdEvent("adImpression", "rewarded", "inmobi"))
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("InMobi rewarded dismissed")
                    isInMobiRewardedReady = false
                    sendEventToWeb(AdEvent("adClosed", "rewarded", "inmobi"))
                    preloadInMobiRewarded()
                }
                
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    log("❌ InMobi rewarded display failed")
                    isInMobiRewardedReady = false
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    sendEventToWeb(AdEvent("adClicked", "rewarded", "inmobi"))
                }
                
                override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: MutableMap<Any, Any>?) {
                    log("🎁 InMobi reward earned!")
                    sendEventToWeb(AdEvent("adRewarded", "rewarded", "inmobi"))
                    preloadInMobiRewarded()
                }
            }
        )
        
        inMobiRewarded?.load()
    }

    private fun preloadAdMobRewarded() {
        if (!isAdMobInitialized || isAdMobRewardedReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading AdMob rewarded...")
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.REWARDED
        } else {
            AdConfig.AdMob.REWARDED
        }
        
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    log("✅ AdMob rewarded loaded")
                    adMobRewarded = ad
                    isAdMobRewardedReady = true
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            log("AdMob rewarded displayed")
                            recordImpression(AdNetwork.ADMOB, "rewarded")
                            sendEventToWeb(AdEvent("adImpression", "rewarded", "admob"))
                        }
                        
                        override fun onAdDismissedFullScreenContent() {
                            log("AdMob rewarded dismissed")
                            isAdMobRewardedReady = false
                            adMobRewarded = null
                            sendEventToWeb(AdEvent("adClosed", "rewarded", "admob"))
                            preloadAdMobRewarded()
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            log("❌ AdMob rewarded show failed: ${error.message}")
                            isAdMobRewardedReady = false
                            adMobRewarded = null
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("❌ AdMob rewarded load failed: ${error.message}")
                    isAdMobRewardedReady = false
                    scheduleRetry { preloadAdMobRewarded() }
                }
            }
        )
    }

    private fun showRewardedWaterfall() {
        val activity = activityRef.get() ?: return
        
        // Try InMobi first
        if (isInMobiRewardedReady && inMobiRewarded?.isReady == true) {
            log("Showing InMobi rewarded")
            inMobiRewarded?.show()
            return
        }
        
        // Fallback to AdMob
        if (isAdMobRewardedReady && adMobRewarded != null) {
            log("Showing AdMob rewarded (fallback)")
            adMobRewarded?.show(activity) { rewardItem ->
                log("🎁 AdMob reward earned: ${rewardItem.amount} ${rewardItem.type}")
                sendEventToWeb(AdEvent(
                    "adRewarded", 
                    "rewarded", 
                    "admob",
                    data = mapOf("amount" to rewardItem.amount, "type" to rewardItem.type)
                ))
            }
            return
        }
        
        log("No rewarded ready", isError = true)
        sendEventToWeb(AdEvent("adFailed", "rewarded", "none", error = "No ad ready"))
        
        // Preload for next time
        preloadInMobiRewarded()
        preloadAdMobRewarded()
    }

    // ==========================================================================
    // IMPRESSION TRACKING
    // ==========================================================================

    private fun recordImpression(network: AdNetwork, adType: String) {
        totalImpressions++
        
        when (network) {
            AdNetwork.INMOBI -> inMobiImpressions++
            AdNetwork.ADMOB -> adMobImpressions++
            AdNetwork.NONE -> {}
        }
        
        saveImpressionData()
        
        val progress = (inMobiImpressions.toFloat() / targetImpressions * 100).toInt()
        log("📊 Impression #$totalImpressions ($adType via ${network.name})")
        log("   InMobi: $inMobiImpressions | AdMob: $adMobImpressions | Progress: $progress%")
        
        if (inMobiImpressions % 100 == 0 && inMobiImpressions > 0) {
            log("🎯 InMobi milestone: $inMobiImpressions impressions!")
        }
        
        if (inMobiImpressions >= targetImpressions) {
            log("🎉🎉🎉 TARGET REACHED: 10,000 InMobi impressions! 🎉🎉🎉")
        }
    }

    private fun saveImpressionData() {
        val context = activityRef.get() ?: return
        context.getSharedPreferences("ad_waterfall", Context.MODE_PRIVATE)
            .edit()
            .putInt("total_impressions", totalImpressions)
            .putInt("inmobi_impressions", inMobiImpressions)
            .putInt("admob_impressions", adMobImpressions)
            .putLong("last_impression_time", System.currentTimeMillis())
            .apply()
    }

    private fun loadImpressionData() {
        val context = activityRef.get() ?: return
        val prefs = context.getSharedPreferences("ad_waterfall", Context.MODE_PRIVATE)
        totalImpressions = prefs.getInt("total_impressions", 0)
        inMobiImpressions = prefs.getInt("inmobi_impressions", 0)
        adMobImpressions = prefs.getInt("admob_impressions", 0)
        
        log("📊 Loaded impressions - InMobi: $inMobiImpressions | AdMob: $adMobImpressions | Total: $totalImpressions")
    }

    // ==========================================================================
    // LIFECYCLE
    // ==========================================================================

    override fun onCreate(owner: LifecycleOwner) {
        loadImpressionData()
    }

    override fun onResume(owner: LifecycleOwner) {
        inMobiBanner?.resume()
        adMobBanner?.resume()
        
        // Ensure ads are preloaded
        if (!isInMobiInterstitialReady) preloadInMobiInterstitial()
        if (!isAdMobInterstitialReady) preloadAdMobInterstitial()
        if (!isInMobiRewardedReady) preloadInMobiRewarded()
        if (!isAdMobRewardedReady) preloadAdMobRewarded()
    }

    override fun onPause(owner: LifecycleOwner) {
        inMobiBanner?.pause()
        adMobBanner?.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        hideAllBanners()
        inMobiInterstitial = null
        adMobInterstitial = null
        inMobiRewarded = null
        adMobRewarded = null
    }

    // ==========================================================================
    // UTILITIES
    // ==========================================================================

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun runOnUiThread(action: () -> Unit) {
        activityRef.get()?.runOnUiThread(action)
    }

    private fun scheduleRetry(delayMs: Long = 5000, action: () -> Unit) {
        webViewRef.get()?.postDelayed({
            runOnUiThread(action)
        }, delayMs)
    }

    private fun log(message: String, isError: Boolean = false) {
        if (!AdConfig.DEBUG_MODE && !isError) return
        
        val tag = AdConfig.LOG_TAG
        if (isError) {
            Log.e(tag, message)
        } else {
            Log.d(tag, message)
        }
    }
}

// =============================================================================
// GRADLE DEPENDENCIES
// =============================================================================

/*
Add to app/build.gradle:

dependencies {
    // InMobi SDK
    implementation 'com.inmobi.monetization:inmobi-ads-kotlin:10.6.7'
    implementation 'com.squareup.picasso:picasso:2.71828'
    implementation 'androidx.browser:browser:1.7.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    
    // AdMob SDK
    implementation 'com.google.android.gms:play-services-ads:22.6.0'
}
*/

// =============================================================================
// MANIFEST ADDITIONS
// =============================================================================

/*
Add to AndroidManifest.xml:

<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application>
        <!-- AdMob App ID (required) -->
        <meta-data
            android:name="com.google.android.gms.ads.APPLICATION_ID"
            android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" />
        
        <!-- InMobi Activity -->
        <activity
            android:name="com.inmobi.rendering.InMobiAdActivity"
            android:configChanges="keyboardHidden|orientation|keyboard|smallestScreenSize|screenSize|screenLayout"
            android:hardwareAccelerated="true"
            android:theme="@android:style/Theme.NoTitleBar" />
    </application>
</manifest>
*/

// =============================================================================
// USAGE IN MAINACTIVITY
// =============================================================================

/*
class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var adBridge: AdWaterfallBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val rootLayout = findViewById<FrameLayout>(R.id.root_layout)
        webView = findViewById(R.id.webview)
        
        setupWebView()
        
        // Initialize Waterfall Ad Bridge
        adBridge = AdWaterfallBridge(this, webView, rootLayout)
        lifecycle.addObserver(adBridge)
        
        webView.loadUrl("https://linguagt.com")
    }
}
*/
