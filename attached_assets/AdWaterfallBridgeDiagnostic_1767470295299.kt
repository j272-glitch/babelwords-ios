package com.lingualink.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.inmobi.ads.InMobiAdRequestStatus
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.sdk.InMobiSdk
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * DIAGNOSTIC VERSION - AdWaterfallBridge with comprehensive logging
 * 
 * This version adds detailed logging at every step to debug:
 * - Why ads aren't loading
 * - Why impressions aren't being recorded
 * - Where the waterfall is failing
 * 
 * Usage: Replace your production AdWaterfallBridge.kt with this file
 * Filter logs: adb logcat -s AD_DIAG:* AdWaterfall:*
 */
class AdWaterfallBridgeDiagnostic(
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "AD_DIAG"
        private const val WATERFALL_TAG = "AdWaterfall"
        
        // InMobi Configuration
        private const val INMOBI_ACCOUNT_ID = "9d81516c365f4acaa52f1fc627370cf9"
        private const val INMOBI_BANNER_PLACEMENT = 1234567890L // Replace with your placement
        private const val INMOBI_INTERSTITIAL_PLACEMENT = 1234567891L // Replace with your placement
        private const val INMOBI_REWARDED_PLACEMENT = 1234567892L // Replace with your placement
        
        // AdMob Configuration (Test IDs - replace with production)
        private const val ADMOB_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
        private const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val ADMOB_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    }
    
    private val activityRef = WeakReference(activity)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    // InMobi ads
    private var inMobiBanner: InMobiBanner? = null
    private var inMobiInterstitial: InMobiInterstitial? = null
    
    // AdMob ads
    private var adMobBanner: AdView? = null
    private var adMobInterstitial: InterstitialAd? = null
    private var adMobRewarded: RewardedAd? = null
    
    // Ready states
    private var isInMobiBannerReady = false
    private var isInMobiInterstitialReady = false
    private var isAdMobBannerReady = false
    private var isAdMobInterstitialReady = false
    private var isAdMobRewardedReady = false
    
    // Impression counters
    private var inmobiImpressions = 0
    private var admobImpressions = 0
    
    // Diagnostic stats
    private var totalLoadRequests = 0
    private var totalShowRequests = 0
    private var successfulLoads = 0
    private var successfulShows = 0
    private var failedLoads = 0
    private var failedShows = 0
    
    init {
        logDiag("=".repeat(60))
        logDiag("DIAGNOSTIC BRIDGE INITIALIZED")
        logDiag("=".repeat(60))
        logDiag("Activity: ${activity.javaClass.simpleName}")
        logDiag("WebView: ${webView.hashCode()}")
        initializeSdks()
    }
    
    private fun logDiag(message: String, level: String = "D") {
        val timestamp = dateFormat.format(Date())
        val fullMessage = "[$timestamp] $message"
        when (level) {
            "E" -> Log.e(TAG, fullMessage)
            "W" -> Log.w(TAG, fullMessage)
            "I" -> Log.i(TAG, fullMessage)
            else -> Log.d(TAG, fullMessage)
        }
    }
    
    private fun logState(context: String) {
        logDiag("--- STATE CHECK: $context ---")
        logDiag("  InMobi Banner Ready: $isInMobiBannerReady")
        logDiag("  InMobi Interstitial Ready: $isInMobiInterstitialReady")
        logDiag("  AdMob Banner Ready: $isAdMobBannerReady")
        logDiag("  AdMob Interstitial Ready: $isAdMobInterstitialReady")
        logDiag("  AdMob Rewarded Ready: $isAdMobRewardedReady")
        logDiag("  InMobi Impressions: $inmobiImpressions")
        logDiag("  AdMob Impressions: $admobImpressions")
        logDiag("  Load Requests: $totalLoadRequests (Success: $successfulLoads, Failed: $failedLoads)")
        logDiag("  Show Requests: $totalShowRequests (Success: $successfulShows, Failed: $failedShows)")
    }
    
    private fun initializeSdks() {
        logDiag(">>> INITIALIZING SDKs <<<")
        
        // Initialize InMobi
        try {
            logDiag("Initializing InMobi SDK...")
            logDiag("  Account ID: $INMOBI_ACCOUNT_ID")
            val consentObject = JSONObject()
            consentObject.put("gdpr", "0")
            InMobiSdk.init(activity, INMOBI_ACCOUNT_ID, consentObject, object : InMobiSdk.SdkInitializationListener {
                override fun onInitializationComplete(error: Error?) {
                    if (error == null) {
                        logDiag("✓ InMobi SDK initialized successfully")
                        logDiag("  SDK Version: ${InMobiSdk.getVersion()}")
                    } else {
                        logDiag("✗ InMobi SDK initialization FAILED: ${error.message}", "E")
                    }
                }
            })
        } catch (e: Exception) {
            logDiag("✗ InMobi init exception: ${e.message}", "E")
        }
        
        // Initialize AdMob
        try {
            logDiag("Initializing AdMob SDK...")
            MobileAds.initialize(activity) { initStatus ->
                logDiag("✓ AdMob SDK initialized")
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    logDiag("  Adapter: $adapter")
                    logDiag("    State: ${status.initializationState}")
                    logDiag("    Latency: ${status.latency}ms")
                    if (status.description.isNotEmpty()) {
                        logDiag("    Desc: ${status.description}")
                    }
                }
            }
        } catch (e: Exception) {
            logDiag("✗ AdMob init exception: ${e.message}", "E")
        }
    }
    
    // ==================== JAVASCRIPT INTERFACE ====================
    
    @JavascriptInterface
    fun loadBanner(placementId: String, position: String) {
        totalLoadRequests++
        logDiag("═".repeat(50))
        logDiag("JS → loadBanner(placementId=$placementId, position=$position)")
        logDiag("═".repeat(50))
        logState("Before loadBanner")
        
        mainHandler.post {
            loadBannerWaterfall(position)
        }
    }
    
    @JavascriptInterface
    fun loadInterstitial(placementId: String) {
        totalLoadRequests++
        logDiag("═".repeat(50))
        logDiag("JS → loadInterstitial(placementId=$placementId)")
        logDiag("═".repeat(50))
        logState("Before loadInterstitial")
        
        mainHandler.post {
            loadInterstitialWaterfall()
        }
    }
    
    @JavascriptInterface
    fun loadRewarded(placementId: String) {
        totalLoadRequests++
        logDiag("═".repeat(50))
        logDiag("JS → loadRewarded(placementId=$placementId)")
        logDiag("═".repeat(50))
        logState("Before loadRewarded")
        
        mainHandler.post {
            loadRewardedWaterfall()
        }
    }
    
    @JavascriptInterface
    fun showBanner(placementId: String) {
        totalShowRequests++
        logDiag("═".repeat(50))
        logDiag("JS → showBanner(placementId=$placementId)")
        logDiag("═".repeat(50))
        logState("Before showBanner")
        
        mainHandler.post {
            showBannerWaterfall()
        }
    }
    
    @JavascriptInterface
    fun hideBanner(placementId: String) {
        logDiag("JS → hideBanner(placementId=$placementId)")
        mainHandler.post {
            inMobiBanner?.visibility = android.view.View.GONE
            adMobBanner?.visibility = android.view.View.GONE
            logDiag("Banner hidden")
        }
    }
    
    @JavascriptInterface
    fun showInterstitial(placementId: String) {
        totalShowRequests++
        logDiag("═".repeat(50))
        logDiag("JS → showInterstitial(placementId=$placementId)")
        logDiag("═".repeat(50))
        logState("Before showInterstitial")
        
        mainHandler.post {
            showInterstitialWaterfall()
        }
    }
    
    @JavascriptInterface
    fun showRewarded(placementId: String) {
        totalShowRequests++
        logDiag("═".repeat(50))
        logDiag("JS → showRewarded(placementId=$placementId)")
        logDiag("═".repeat(50))
        logState("Before showRewarded")
        
        mainHandler.post {
            showRewardedWaterfall()
        }
    }
    
    @JavascriptInterface
    fun isInterstitialReady(placementId: String): Boolean {
        val ready = isInMobiInterstitialReady || isAdMobInterstitialReady
        logDiag("JS → isInterstitialReady($placementId) = $ready")
        logDiag("  InMobi: $isInMobiInterstitialReady, AdMob: $isAdMobInterstitialReady")
        return ready
    }
    
    @JavascriptInterface
    fun isRewardedReady(placementId: String): Boolean {
        val ready = isAdMobRewardedReady
        logDiag("JS → isRewardedReady($placementId) = $ready")
        return ready
    }
    
    @JavascriptInterface
    fun getImpressionStats(): String {
        val stats = JSONObject().apply {
            put("inmobi", inmobiImpressions)
            put("admob", admobImpressions)
            put("total", inmobiImpressions + admobImpressions)
            put("target", 10000)
            put("loadRequests", totalLoadRequests)
            put("showRequests", totalShowRequests)
            put("successfulLoads", successfulLoads)
            put("successfulShows", successfulShows)
            put("failedLoads", failedLoads)
            put("failedShows", failedShows)
        }
        logDiag("JS → getImpressionStats() = $stats")
        return stats.toString()
    }
    
    @JavascriptInterface
    fun getDiagnosticReport(): String {
        logState("Diagnostic Report Requested")
        val report = JSONObject().apply {
            put("timestamp", dateFormat.format(Date()))
            put("inmobiBannerReady", isInMobiBannerReady)
            put("inmobiInterstitialReady", isInMobiInterstitialReady)
            put("admobBannerReady", isAdMobBannerReady)
            put("admobInterstitialReady", isAdMobInterstitialReady)
            put("admobRewardedReady", isAdMobRewardedReady)
            put("inmobiImpressions", inmobiImpressions)
            put("admobImpressions", admobImpressions)
            put("loadRequests", totalLoadRequests)
            put("showRequests", totalShowRequests)
            put("successfulLoads", successfulLoads)
            put("successfulShows", successfulShows)
            put("failedLoads", failedLoads)
            put("failedShows", failedShows)
        }
        return report.toString()
    }
    
    // ==================== WATERFALL LOADING ====================
    
    private fun loadBannerWaterfall(position: String) {
        logDiag("→ WATERFALL STEP 1: Loading InMobi Banner")
        logDiag("  Placement ID: $INMOBI_BANNER_PLACEMENT")
        
        try {
            inMobiBanner = InMobiBanner(activity, INMOBI_BANNER_PLACEMENT).apply {
                setListener(object : BannerAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiBanner, info: com.inmobi.ads.AdMetaInfo) {
                        isInMobiBannerReady = true
                        successfulLoads++
                        logDiag("✓ INMOBI BANNER LOADED")
                        logDiag("  Ad Info: ${info.bidInfo}")
                        notifyJs("adLoaded", "inmobi", "banner")
                    }
                    
                    override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                        isInMobiBannerReady = false
                        logDiag("✗ InMobi banner failed: ${status.statusCode} - ${status.message}", "W")
                        logDiag("→ WATERFALL STEP 2: Falling back to AdMob Banner")
                        loadAdMobBanner(position)
                    }
                    
                    override fun onAdDisplayed(ad: InMobiBanner) {
                        inmobiImpressions++
                        logDiag("★★★ INMOBI BANNER IMPRESSION #$inmobiImpressions ★★★")
                        notifyJs("adImpression", "inmobi", "banner")
                    }
                    
                    override fun onAdClicked(ad: InMobiBanner, params: MutableMap<Any, Any>?) {
                        logDiag("InMobi banner clicked")
                        notifyJs("adClicked", "inmobi", "banner")
                    }
                })
                load()
            }
        } catch (e: Exception) {
            failedLoads++
            logDiag("✗ InMobi banner exception: ${e.message}", "E")
            logDiag("→ WATERFALL STEP 2: Falling back to AdMob Banner")
            loadAdMobBanner(position)
        }
    }
    
    private fun loadAdMobBanner(position: String) {
        logDiag("Loading AdMob Banner...")
        logDiag("  Ad Unit ID: $ADMOB_BANNER_ID")
        
        val act = activityRef.get()
        if (act == null) {
            logDiag("✗ Activity reference lost!", "E")
            failedLoads++
            notifyJs("adFailed", "admob", "banner", "Activity reference lost")
            return
        }
        
        try {
            adMobBanner = AdView(act).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = ADMOB_BANNER_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isAdMobBannerReady = true
                        successfulLoads++
                        logDiag("✓ ADMOB BANNER LOADED")
                        notifyJs("adLoaded", "admob", "banner")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdMobBannerReady = false
                        failedLoads++
                        logDiag("✗ AdMob banner failed: ${error.code} - ${error.message}", "E")
                        logDiag("  Domain: ${error.domain}")
                        logDiag("  Cause: ${error.cause}")
                        notifyJs("adFailed", "admob", "banner", error.message)
                    }
                    
                    override fun onAdImpression() {
                        admobImpressions++
                        logDiag("★★★ ADMOB BANNER IMPRESSION #$admobImpressions ★★★")
                        notifyJs("adImpression", "admob", "banner")
                    }
                    
                    override fun onAdClicked() {
                        logDiag("AdMob banner clicked")
                        notifyJs("adClicked", "admob", "banner")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        } catch (e: Exception) {
            failedLoads++
            logDiag("✗ AdMob banner exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "banner", e.message ?: "Unknown error")
        }
    }
    
    private fun loadInterstitialWaterfall() {
        logDiag("→ WATERFALL STEP 1: Loading InMobi Interstitial")
        logDiag("  Placement ID: $INMOBI_INTERSTITIAL_PLACEMENT")
        
        try {
            inMobiInterstitial = InMobiInterstitial(activity, INMOBI_INTERSTITIAL_PLACEMENT,
                object : InterstitialAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                        isInMobiInterstitialReady = true
                        successfulLoads++
                        logDiag("✓ INMOBI INTERSTITIAL LOADED")
                        logDiag("  isReady(): ${ad.isReady}")
                        notifyJs("adLoaded", "inmobi", "interstitial")
                    }
                    
                    override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isInMobiInterstitialReady = false
                        logDiag("✗ InMobi interstitial failed: ${status.statusCode} - ${status.message}", "W")
                        logDiag("→ WATERFALL STEP 2: Falling back to AdMob Interstitial")
                        loadAdMobInterstitial()
                    }
                    
                    override fun onAdDisplayed(ad: InMobiInterstitial, info: com.inmobi.ads.AdMetaInfo) {
                        inmobiImpressions++
                        successfulShows++
                        logDiag("★★★ INMOBI INTERSTITIAL IMPRESSION #$inmobiImpressions ★★★")
                        notifyJs("adImpression", "inmobi", "interstitial")
                    }
                    
                    override fun onAdDismissed(ad: InMobiInterstitial) {
                        isInMobiInterstitialReady = false
                        logDiag("InMobi interstitial dismissed")
                        notifyJs("adClosed", "inmobi", "interstitial")
                        // Preload next
                        loadInterstitialWaterfall()
                    }
                    
                    override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                        failedShows++
                        isInMobiInterstitialReady = false
                        logDiag("✗ InMobi interstitial display FAILED", "E")
                        notifyJs("adFailed", "inmobi", "interstitial", "Display failed")
                    }
                    
                    override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                        logDiag("InMobi interstitial clicked")
                        notifyJs("adClicked", "inmobi", "interstitial")
                    }
                })
            inMobiInterstitial?.load()
            logDiag("InMobi interstitial load() called")
        } catch (e: Exception) {
            failedLoads++
            logDiag("✗ InMobi interstitial exception: ${e.message}", "E")
            logDiag("→ WATERFALL STEP 2: Falling back to AdMob Interstitial")
            loadAdMobInterstitial()
        }
    }
    
    private fun loadAdMobInterstitial() {
        logDiag("Loading AdMob Interstitial...")
        logDiag("  Ad Unit ID: $ADMOB_INTERSTITIAL_ID")
        
        val act = activityRef.get()
        if (act == null) {
            logDiag("✗ Activity reference lost!", "E")
            failedLoads++
            notifyJs("adFailed", "admob", "interstitial", "Activity reference lost")
            return
        }
        
        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(act, ADMOB_INTERSTITIAL_ID, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        adMobInterstitial = ad
                        isAdMobInterstitialReady = true
                        successfulLoads++
                        logDiag("✓ ADMOB INTERSTITIAL LOADED")
                        
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                admobImpressions++
                                successfulShows++
                                logDiag("★★★ ADMOB INTERSTITIAL IMPRESSION #$admobImpressions ★★★")
                                notifyJs("adImpression", "admob", "interstitial")
                            }
                            
                            override fun onAdDismissedFullScreenContent() {
                                isAdMobInterstitialReady = false
                                adMobInterstitial = null
                                logDiag("AdMob interstitial dismissed")
                                notifyJs("adClosed", "admob", "interstitial")
                                // Preload next
                                loadInterstitialWaterfall()
                            }
                            
                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                failedShows++
                                isAdMobInterstitialReady = false
                                adMobInterstitial = null
                                logDiag("✗ AdMob interstitial show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "interstitial", error.message)
                            }
                            
                            override fun onAdClicked() {
                                logDiag("AdMob interstitial clicked")
                                notifyJs("adClicked", "admob", "interstitial")
                            }
                            
                            override fun onAdImpression() {
                                logDiag("AdMob onAdImpression callback fired")
                            }
                        }
                        
                        notifyJs("adLoaded", "admob", "interstitial")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdMobInterstitialReady = false
                        failedLoads++
                        logDiag("✗ AdMob interstitial load FAILED: ${error.code} - ${error.message}", "E")
                        logDiag("  Domain: ${error.domain}")
                        logDiag("  Cause: ${error.cause}")
                        logDiag("  Response Info: ${error.responseInfo}")
                        notifyJs("adFailed", "admob", "interstitial", error.message)
                    }
                })
        } catch (e: Exception) {
            failedLoads++
            logDiag("✗ AdMob interstitial exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "interstitial", e.message ?: "Unknown error")
        }
    }
    
    private fun loadRewardedWaterfall() {
        logDiag("→ Loading AdMob Rewarded (InMobi rewarded not implemented)")
        logDiag("  Ad Unit ID: $ADMOB_REWARDED_ID")
        
        val act = activityRef.get()
        if (act == null) {
            logDiag("✗ Activity reference lost!", "E")
            failedLoads++
            notifyJs("adFailed", "admob", "rewarded", "Activity reference lost")
            return
        }
        
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(act, ADMOB_REWARDED_ID, adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        adMobRewarded = ad
                        isAdMobRewardedReady = true
                        successfulLoads++
                        logDiag("✓ ADMOB REWARDED LOADED")
                        
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                admobImpressions++
                                successfulShows++
                                logDiag("★★★ ADMOB REWARDED IMPRESSION #$admobImpressions ★★★")
                                notifyJs("adImpression", "admob", "rewarded")
                            }
                            
                            override fun onAdDismissedFullScreenContent() {
                                isAdMobRewardedReady = false
                                adMobRewarded = null
                                logDiag("AdMob rewarded dismissed")
                                notifyJs("adClosed", "admob", "rewarded")
                                // Preload next
                                loadRewardedWaterfall()
                            }
                            
                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                failedShows++
                                isAdMobRewardedReady = false
                                adMobRewarded = null
                                logDiag("✗ AdMob rewarded show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "rewarded", error.message)
                            }
                            
                            override fun onAdClicked() {
                                logDiag("AdMob rewarded clicked")
                                notifyJs("adClicked", "admob", "rewarded")
                            }
                        }
                        
                        notifyJs("adLoaded", "admob", "rewarded")
                    }
                    
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdMobRewardedReady = false
                        failedLoads++
                        logDiag("✗ AdMob rewarded load FAILED: ${error.code} - ${error.message}", "E")
                        logDiag("  Domain: ${error.domain}")
                        logDiag("  Cause: ${error.cause}")
                        notifyJs("adFailed", "admob", "rewarded", error.message)
                    }
                })
        } catch (e: Exception) {
            failedLoads++
            logDiag("✗ AdMob rewarded exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "rewarded", e.message ?: "Unknown error")
        }
    }
    
    // ==================== WATERFALL SHOWING ====================
    
    private fun showBannerWaterfall() {
        logDiag("→ Attempting to show banner...")
        
        when {
            isInMobiBannerReady && inMobiBanner != null -> {
                logDiag("  Showing InMobi banner")
                inMobiBanner?.visibility = android.view.View.VISIBLE
                successfulShows++
            }
            isAdMobBannerReady && adMobBanner != null -> {
                logDiag("  Showing AdMob banner")
                adMobBanner?.visibility = android.view.View.VISIBLE
                successfulShows++
            }
            else -> {
                failedShows++
                logDiag("✗ No banner ready to show!", "W")
                logState("showBanner failed")
            }
        }
    }
    
    private fun showInterstitialWaterfall() {
        logDiag("→ Attempting to show interstitial...")
        logDiag("  InMobi ready: $isInMobiInterstitialReady")
        logDiag("  InMobi object: ${inMobiInterstitial != null}")
        logDiag("  InMobi isReady(): ${inMobiInterstitial?.isReady}")
        logDiag("  AdMob ready: $isAdMobInterstitialReady")
        logDiag("  AdMob object: ${adMobInterstitial != null}")
        
        val act = activityRef.get()
        if (act == null) {
            failedShows++
            logDiag("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "none", "interstitial", "Activity reference lost")
            return
        }
        
        when {
            isInMobiInterstitialReady && inMobiInterstitial?.isReady == true -> {
                logDiag("  ► Calling InMobi interstitial.show()")
                try {
                    inMobiInterstitial?.show()
                    logDiag("  ✓ InMobi show() called successfully")
                } catch (e: Exception) {
                    failedShows++
                    logDiag("  ✗ InMobi show() exception: ${e.message}", "E")
                }
            }
            isAdMobInterstitialReady && adMobInterstitial != null -> {
                logDiag("  ► Calling AdMob interstitial.show()")
                try {
                    adMobInterstitial?.show(act)
                    logDiag("  ✓ AdMob show() called successfully")
                } catch (e: Exception) {
                    failedShows++
                    logDiag("  ✗ AdMob show() exception: ${e.message}", "E")
                }
            }
            else -> {
                failedShows++
                logDiag("✗ No interstitial ready to show!", "W")
                logState("showInterstitial failed")
                notifyJs("adFailed", "none", "interstitial", "No ad ready")
            }
        }
    }
    
    private fun showRewardedWaterfall() {
        logDiag("→ Attempting to show rewarded...")
        logDiag("  AdMob ready: $isAdMobRewardedReady")
        logDiag("  AdMob object: ${adMobRewarded != null}")
        
        val act = activityRef.get()
        if (act == null) {
            failedShows++
            logDiag("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "rewarded", "Activity reference lost")
            return
        }
        
        if (isAdMobRewardedReady && adMobRewarded != null) {
            logDiag("  ► Calling AdMob rewarded.show()")
            try {
                adMobRewarded?.show(act) { reward ->
                    logDiag("★★★ REWARD EARNED: ${reward.amount} ${reward.type} ★★★")
                    notifyJs("adRewarded", "admob", "rewarded", 
                        JSONObject().put("amount", reward.amount).put("type", reward.type).toString())
                }
                logDiag("  ✓ AdMob rewarded show() called successfully")
            } catch (e: Exception) {
                failedShows++
                logDiag("  ✗ AdMob rewarded show() exception: ${e.message}", "E")
            }
        } else {
            failedShows++
            logDiag("✗ No rewarded ad ready to show!", "W")
            logState("showRewarded failed")
            notifyJs("adFailed", "admob", "rewarded", "No ad ready")
        }
    }
    
    // ==================== JS NOTIFICATION ====================
    
    private fun notifyJs(eventType: String, network: String, placementId: String, data: String? = null) {
        val payload = JSONObject().apply {
            put("type", eventType)
            put("network", network)
            put("placementId", placementId)
            put("timestamp", System.currentTimeMillis())
            data?.let { put("data", it) }
        }
        
        val js = "window.dispatchEvent(new CustomEvent('nativeAdEvent', { detail: $payload }));"
        
        logDiag("→ JS: $eventType [$network] $placementId")
        
        mainHandler.post {
            try {
                webView.evaluateJavascript(js, null)
            } catch (e: Exception) {
                logDiag("✗ JS notify failed: ${e.message}", "E")
            }
        }
    }
    
    fun destroy() {
        logDiag("=".repeat(60))
        logDiag("BRIDGE DESTROYED - FINAL STATS")
        logState("Final State")
        logDiag("=".repeat(60))
        
        inMobiBanner?.destroy()
        inMobiInterstitial = null
        adMobBanner?.destroy()
        adMobInterstitial = null
        adMobRewarded = null
    }
}
