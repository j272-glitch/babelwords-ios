package com.lingualink.linguagt.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
import com.lingualink.linguagt.TestRigorLogger
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

object AdConfig {
    
    object InMobi {
        const val ACCOUNT_ID = "9d81516c365f4acaa52f1fc627370cf9"
        
        object Placements {
            const val BANNER: Long = 10000582111L
            const val INTERSTITIAL: Long = 10000582110L
            const val REWARDED: Long = 10000582112L
        }
    }
    
    object AdMob {
        const val BANNER = "ca-app-pub-9991891515643313/6878126239"
        const val INTERSTITIAL = "ca-app-pub-9991891515643313/5076005693"
        const val REWARDED = "ca-app-pub-9991891515643313/6313049833"
        
        object Test {
            const val BANNER = "ca-app-pub-3940256099942544/6300978111"
            const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
            const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
        }
    }
    
    const val USE_ADMOB_TEST_ADS = false
    const val DEBUG_MODE = true
    const val WATERFALL_TIMEOUT = 5000L
    
    object Privacy {
        var gdprConsent: Boolean = true
        var ccpaDoNotSell: Boolean = false
    }
    
    const val LOG_TAG = "AdWaterfall"
}

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

enum class AdNetwork {
    INMOBI,
    ADMOB,
    NONE
}

class AdWaterfallBridge(
    activity: Activity,
    webView: WebView,
    private val rootLayout: FrameLayout?
) : DefaultLifecycleObserver {

    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)
    
    private var isInMobiInitialized = false
    private var isAdMobInitialized = false
    
    private var inMobiBanner: InMobiBanner? = null
    private var adMobBanner: AdView? = null
    private var currentBannerNetwork: AdNetwork = AdNetwork.NONE
    private var bannerPosition: String = "bottom"
    
    private var inMobiInterstitial: InMobiInterstitial? = null
    private var adMobInterstitial: InterstitialAd? = null
    private var isInMobiInterstitialReady = false
    private var isAdMobInterstitialReady = false
    
    private var inMobiRewarded: InMobiInterstitial? = null
    private var adMobRewarded: RewardedAd? = null
    private var isInMobiRewardedReady = false
    private var isAdMobRewardedReady = false
    
    private var totalImpressions = 0
    private var inMobiImpressions = 0
    private var adMobImpressions = 0
    private val targetImpressions = 10000
    
    // By-type impression counters
    private var bannerImpressions = 0
    private var interstitialImpressions = 0
    private var rewardedImpressions = 0
    
    // Diagnostic counters
    private var totalLoadRequests = 0
    private var totalShowRequests = 0
    private var successfulLoads = 0
    private var successfulShows = 0
    private var failedLoads = 0
    private var failedShows = 0
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    
    // Current placement IDs (set from JavaScript or use defaults)
    private var currentAdMobBannerId = AdConfig.AdMob.BANNER
    private var currentAdMobInterstitialId = AdConfig.AdMob.INTERSTITIAL
    private var currentAdMobRewardedId = AdConfig.AdMob.REWARDED
    
    private fun isAdMobId(id: String): Boolean = id.startsWith("ca-app-pub-")
    
    private fun getEffectiveAdMobId(jsId: String?, defaultId: String): String {
        return if (!jsId.isNullOrEmpty() && isAdMobId(jsId)) {
            log("  ✓ Using AdMob ID from JS: $jsId")
            jsId
        } else {
            val effectiveId = if (AdConfig.USE_ADMOB_TEST_ADS) {
                when (defaultId) {
                    AdConfig.AdMob.BANNER -> AdConfig.AdMob.Test.BANNER
                    AdConfig.AdMob.INTERSTITIAL -> AdConfig.AdMob.Test.INTERSTITIAL
                    AdConfig.AdMob.REWARDED -> AdConfig.AdMob.Test.REWARDED
                    else -> defaultId
                }
            } else defaultId
            log("  Using default AdMob ID: $effectiveId")
            effectiveId
        }
    }

    init {
        log("═".repeat(50))
        log("ADWATERFALL BRIDGE INITIALIZED")
        log("═".repeat(50))
        loadImpressionData()
        logState("Init")
    }
    
    private fun logState(context: String) {
        log("--- STATE: $context ---")
        log("  InMobi Init: $isInMobiInitialized | AdMob Init: $isAdMobInitialized")
        log("  InMobi Interstitial Ready: $isInMobiInterstitialReady")
        log("  AdMob Interstitial Ready: $isAdMobInterstitialReady")
        log("  InMobi Rewarded Ready: $isInMobiRewardedReady")
        log("  AdMob Rewarded Ready: $isAdMobRewardedReady")
        log("  Impressions - InMobi: $inMobiImpressions | AdMob: $adMobImpressions | Total: $totalImpressions")
        log("  Loads: $totalLoadRequests (OK: $successfulLoads, Fail: $failedLoads)")
        log("  Shows: $totalShowRequests (OK: $successfulShows, Fail: $failedShows)")
        log("  Current IDs:")
        log("    Banner: $currentAdMobBannerId")
        log("    Interstitial: $currentAdMobInterstitialId")
        log("    Rewarded: $currentAdMobRewardedId")
    }

    fun initialize() {
        log("Starting SDK initialization...")
        initializeInMobi()
        initializeAdMob()
    }

    fun initializeWithConsent(gdprConsent: Boolean, ccpaDoNotSell: Boolean) {
        AdConfig.Privacy.gdprConsent = gdprConsent
        AdConfig.Privacy.ccpaDoNotSell = ccpaDoNotSell
        log("Consent updated - GDPR: $gdprConsent, CCPA DoNotSell: $ccpaDoNotSell")
        
        if (!isInMobiInitialized) {
            initializeInMobi()
        }
        if (!isAdMobInitialized) {
            initializeAdMob()
        }
    }

    private fun initializeInMobi() {
        val activity = activityRef.get() ?: return
        
        log("Starting InMobi initialization...")
        log("Account ID: ${AdConfig.InMobi.ACCOUNT_ID}")
        log("GDPR Consent: ${AdConfig.Privacy.gdprConsent}")
        
        // Always enable debug logging during initialization for troubleshooting
        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG)
        
        val consentObject = JSONObject().apply {
            put("gdpr_consent_available", AdConfig.Privacy.gdprConsent)
            put("gdpr", if (AdConfig.Privacy.gdprConsent) "1" else "0")
        }
        
        InMobiSdk.init(activity, AdConfig.InMobi.ACCOUNT_ID, consentObject, object : SdkInitializationListener {
            override fun onInitializationComplete(error: Error?) {
                if (error == null) {
                    isInMobiInitialized = true
                    log("InMobi SDK initialized successfully")
                    TestRigorLogger.logAdEvent("InMobi SDK initialized successfully")
                    
                    // Delay ad preload to ensure SDK is fully ready
                    Handler(Looper.getMainLooper()).postDelayed({
                        preloadInMobiInterstitial()
                        preloadInMobiRewarded()
                    }, 1000)
                } else {
                    log("InMobi init failed: ${error.message}", isError = true)
                    TestRigorLogger.logAdEvent("InMobi init failed: ${error.message}")
                }
            }
        })
    }

    private fun initializeAdMob() {
        val activity = activityRef.get() ?: return
        
        MobileAds.initialize(activity) { initializationStatus ->
            isAdMobInitialized = true
            log("AdMob SDK initialized")
            TestRigorLogger.logAdEvent("AdMob SDK initialized successfully")
            
            preloadAdMobInterstitial()
            preloadAdMobRewarded()
        }
    }

    @JavascriptInterface
    fun loadBanner(position: String) {
        loadBannerWithId(position, "")
    }
    
    @JavascriptInterface
    fun loadBannerWithId(position: String, placementId: String) {
        log("JS -> loadBanner(position=$position, placementId=$placementId)")
        if (isAdMobId(placementId)) {
            currentAdMobBannerId = placementId
            log("  ✓ Using AdMob Banner ID from JS: $currentAdMobBannerId")
        }
        runOnUiThread { 
            bannerPosition = position
            loadBannerWaterfall(position) 
        }
    }
    
    @JavascriptInterface
    fun hideBanner() {
        log("JS -> hideBanner()")
        runOnUiThread { hideAllBanners() }
    }
    
    @JavascriptInterface
    fun loadInterstitial() {
        loadInterstitialWithId("")
    }
    
    @JavascriptInterface
    fun loadInterstitialWithId(placementId: String) {
        totalLoadRequests++
        log("JS -> loadInterstitial(placementId=$placementId) [request #$totalLoadRequests]")
        if (isAdMobId(placementId)) {
            currentAdMobInterstitialId = placementId
            log("  ✓ Using AdMob Interstitial ID from JS: $currentAdMobInterstitialId")
        }
        runOnUiThread { 
            preloadInMobiInterstitial()
            preloadAdMobInterstitial()
        }
    }
    
    @JavascriptInterface
    fun showInterstitial() {
        totalShowRequests++
        log("JS -> showInterstitial() [request #$totalShowRequests]")
        logState("Before showInterstitial")
        runOnUiThread { showInterstitialWaterfall() }
    }
    
    @JavascriptInterface
    fun loadRewarded() {
        loadRewardedWithId("")
    }
    
    @JavascriptInterface
    fun loadRewardedWithId(placementId: String) {
        totalLoadRequests++
        log("JS -> loadRewarded(placementId=$placementId) [request #$totalLoadRequests]")
        if (isAdMobId(placementId)) {
            currentAdMobRewardedId = placementId
            log("  ✓ Using AdMob Rewarded ID from JS: $currentAdMobRewardedId")
        }
        runOnUiThread { 
            preloadInMobiRewarded()
            preloadAdMobRewarded()
        }
    }
    
    @JavascriptInterface
    fun showRewarded() {
        totalShowRequests++
        log("JS -> showRewarded() [request #$totalShowRequests]")
        logState("Before showRewarded")
        runOnUiThread { showRewardedWaterfall() }
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean = isInMobiInterstitialReady || isAdMobInterstitialReady
    
    @JavascriptInterface
    fun isRewardedReady(): Boolean = isInMobiRewardedReady || isAdMobRewardedReady
    
    @JavascriptInterface
    fun getImpressionCount(): Int = totalImpressions
    
    @JavascriptInterface
    fun getInMobiImpressions(): Int = inMobiImpressions
    
    @JavascriptInterface
    fun getAdMobImpressions(): Int = adMobImpressions
    
    @JavascriptInterface
    fun getBannerImpressions(): Int = bannerImpressions
    
    @JavascriptInterface
    fun getInterstitialImpressions(): Int = interstitialImpressions
    
    @JavascriptInterface
    fun getRewardedImpressions(): Int = rewardedImpressions
    
    @JavascriptInterface
    fun getImpressionProgress(): Float = (inMobiImpressions.toFloat() / targetImpressions) * 100
    
    @JavascriptInterface
    fun getImpressionStats(): String {
        return JSONObject().apply {
            put("inmobi", inMobiImpressions)
            put("admob", adMobImpressions)
            put("total", totalImpressions)
            put("target", targetImpressions)
            put("progressPercent", getImpressionProgress())
            put("loadRequests", totalLoadRequests)
            put("showRequests", totalShowRequests)
            put("successfulLoads", successfulLoads)
            put("successfulShows", successfulShows)
            put("failedLoads", failedLoads)
            put("failedShows", failedShows)
            put("byType", JSONObject().apply {
                put("banner", bannerImpressions)
                put("interstitial", interstitialImpressions)
                put("rewarded", rewardedImpressions)
            })
        }.toString()
    }
    
    @JavascriptInterface
    fun getDiagnosticReport(): String {
        logState("Diagnostic Report")
        return JSONObject().apply {
            put("timestamp", dateFormat.format(Date()))
            put("inmobiInitialized", isInMobiInitialized)
            put("admobInitialized", isAdMobInitialized)
            put("inmobiBannerReady", currentBannerNetwork == AdNetwork.INMOBI)
            put("inmobiInterstitialReady", isInMobiInterstitialReady)
            put("inmobiRewardedReady", isInMobiRewardedReady)
            put("admobBannerReady", currentBannerNetwork == AdNetwork.ADMOB)
            put("admobInterstitialReady", isAdMobInterstitialReady)
            put("admobRewardedReady", isAdMobRewardedReady)
            put("inmobiImpressions", inMobiImpressions)
            put("admobImpressions", adMobImpressions)
            put("totalImpressions", totalImpressions)
            put("targetImpressions", targetImpressions)
            put("bannerImpressions", bannerImpressions)
            put("interstitialImpressions", interstitialImpressions)
            put("rewardedImpressions", rewardedImpressions)
            put("loadRequests", totalLoadRequests)
            put("showRequests", totalShowRequests)
            put("successfulLoads", successfulLoads)
            put("successfulShows", successfulShows)
            put("failedLoads", failedLoads)
            put("failedShows", failedShows)
        }.toString()
    }

    private fun sendEventToWeb(event: AdEvent) {
        val webView = webViewRef.get() ?: return
        val escapedJson = event.toJson().replace("\\", "\\\\").replace("'", "\\'")
        val js = "javascript:if(window.onAdEvent){window.onAdEvent('$escapedJson');}"
        
        runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
        
        log("-> Web: ${event.type} [${event.network}]")
    }

    private fun loadBannerWaterfall(position: String) {
        if (rootLayout == null) {
            log("No root layout for banner", isError = true)
            return
        }
        
        log("Loading banner waterfall...")
        
        if (isInMobiInitialized) {
            loadInMobiBanner(position)
        } else if (isAdMobInitialized) {
            loadAdMobBanner(position)
        } else {
            log("No ad SDK initialized yet", isError = true)
        }
    }

    private fun loadInMobiBanner(position: String) {
        val activity = activityRef.get() ?: return
        val layout = rootLayout ?: return
        
        hideAllBanners()
        log("Trying InMobi banner...")
        
        inMobiBanner = InMobiBanner(activity, AdConfig.InMobi.Placements.BANNER).apply {
            layoutParams = createBannerLayoutParams(activity, position)
            
            setListener(object : BannerAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                    log("InMobi banner loaded")
                    currentBannerNetwork = AdNetwork.INMOBI
                    sendEventToWeb(AdEvent("adLoaded", "banner", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                    log("InMobi banner failed: ${status.message} -> Trying AdMob")
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
        
        layout.addView(inMobiBanner)
        inMobiBanner?.load()
    }

    private fun loadAdMobBanner(position: String) {
        val activity = activityRef.get() ?: return
        val layout = rootLayout ?: return
        
        hideAllBanners()
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.BANNER
        } else {
            currentAdMobBannerId
        }
        log("Trying AdMob banner...")
        log("  Ad Unit ID: $adUnitId ${if (currentAdMobBannerId != AdConfig.AdMob.BANNER) "(from JS)" else "(default)"}")
        
        adMobBanner = AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            layoutParams = createBannerLayoutParams(activity, position)
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    log("AdMob banner loaded")
                    currentBannerNetwork = AdNetwork.ADMOB
                    sendEventToWeb(AdEvent("adLoaded", "banner", "admob"))
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("AdMob banner failed: ${error.message}", isError = true)
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
        
        layout.addView(adMobBanner)
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
            rootLayout?.removeView(it)
            it.destroy()
        }
        inMobiBanner = null
        
        adMobBanner?.let {
            rootLayout?.removeView(it)
            it.destroy()
        }
        adMobBanner = null
        
        currentBannerNetwork = AdNetwork.NONE
    }

    private fun preloadInMobiInterstitial() {
        if (!isInMobiInitialized || isInMobiInterstitialReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading InMobi interstitial...")
        
        inMobiInterstitial = InMobiInterstitial(
            activity,
            AdConfig.InMobi.Placements.INTERSTITIAL,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    successfulLoads++
                    log("✓ InMobi interstitial loaded [loads: $successfulLoads]")
                    isInMobiInterstitialReady = true
                    sendEventToWeb(AdEvent("adLoaded", "interstitial", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    failedLoads++
                    log("✗ InMobi interstitial failed [fails: $failedLoads]:", isError = true)
                    log("  Status Code: ${status.statusCode}", isError = true)
                    log("  Message: ${status.message}", isError = true)
                    log("  Placement: ${AdConfig.InMobi.Placements.INTERSTITIAL}", isError = true)
                    TestRigorLogger.logAdEvent("InMobi interstitial failed: ${status.statusCode} - ${status.message}")
                    isInMobiInterstitialReady = false
                    scheduleRetry { preloadInMobiInterstitial() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    successfulShows++
                    log("✓ InMobi interstitial displayed [shows: $successfulShows]")
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
                    failedShows++
                    log("✗ InMobi interstitial display failed [fails: $failedShows]", isError = true)
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
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.INTERSTITIAL
        } else {
            currentAdMobInterstitialId
        }
        log("Preloading AdMob interstitial...")
        log("  Ad Unit ID: $adUnitId ${if (currentAdMobInterstitialId != AdConfig.AdMob.INTERSTITIAL) "(from JS)" else "(default)"}")
        
        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    successfulLoads++
                    log("✓ AdMob interstitial loaded [loads: $successfulLoads]")
                    adMobInterstitial = ad
                    isAdMobInterstitialReady = true
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            successfulShows++
                            log("✓ AdMob interstitial displayed [shows: $successfulShows]")
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
                            failedShows++
                            log("✗ AdMob interstitial show failed [fails: $failedShows]: ${error.message}", isError = true)
                            isAdMobInterstitialReady = false
                            adMobInterstitial = null
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failedLoads++
                    log("✗ AdMob interstitial load failed [fails: $failedLoads]: ${error.message}", isError = true)
                    isAdMobInterstitialReady = false
                    scheduleRetry { preloadAdMobInterstitial() }
                }
            }
        )
    }

    private fun showInterstitialWaterfall() {
        val activity = activityRef.get() ?: return
        
        log("=== SHOW INTERSTITIAL FLOW ===")
        log("InMobi ready: $isInMobiInterstitialReady, isReady(): ${inMobiInterstitial?.isReady()}")
        log("AdMob ready: $isAdMobInterstitialReady")
        
        if (isInMobiInterstitialReady && inMobiInterstitial?.isReady() == true) {
            log("→ Showing InMobi interstitial")
            inMobiInterstitial?.show()
            return
        }
        
        if (isAdMobInterstitialReady && adMobInterstitial != null) {
            log("→ Showing AdMob interstitial (fallback)")
            adMobInterstitial?.show(activity)
            return
        }
        
        log("✗ No interstitial ready to show!", isError = true)
        sendEventToWeb(AdEvent("adFailed", "interstitial", "none", error = "No ad ready"))
        
        preloadInMobiInterstitial()
        preloadAdMobInterstitial()
    }

    private fun preloadInMobiRewarded() {
        if (!isInMobiInitialized || isInMobiRewardedReady) return
        
        val activity = activityRef.get() ?: return
        log("Preloading InMobi rewarded...")
        
        inMobiRewarded = InMobiInterstitial(
            activity,
            AdConfig.InMobi.Placements.REWARDED,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    successfulLoads++
                    log("✓ InMobi rewarded loaded [loads: $successfulLoads]")
                    isInMobiRewardedReady = true
                    sendEventToWeb(AdEvent("adLoaded", "rewarded", "inmobi"))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    failedLoads++
                    log("✗ InMobi rewarded failed [fails: $failedLoads]:", isError = true)
                    log("  Status Code: ${status.statusCode}", isError = true)
                    log("  Message: ${status.message}", isError = true)
                    log("  Placement: ${AdConfig.InMobi.Placements.REWARDED}", isError = true)
                    TestRigorLogger.logAdEvent("InMobi rewarded failed: ${status.statusCode} - ${status.message}")
                    isInMobiRewardedReady = false
                    scheduleRetry { preloadInMobiRewarded() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    successfulShows++
                    log("✓ InMobi rewarded displayed [shows: $successfulShows]")
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
                    failedShows++
                    log("✗ InMobi rewarded display failed [fails: $failedShows]", isError = true)
                    isInMobiRewardedReady = false
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    sendEventToWeb(AdEvent("adClicked", "rewarded", "inmobi"))
                }
                
                override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: MutableMap<Any, Any>?) {
                    log("InMobi reward earned!")
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
        
        val adUnitId = if (AdConfig.USE_ADMOB_TEST_ADS) {
            AdConfig.AdMob.Test.REWARDED
        } else {
            currentAdMobRewardedId
        }
        log("Preloading AdMob rewarded...")
        log("  Ad Unit ID: $adUnitId ${if (currentAdMobRewardedId != AdConfig.AdMob.REWARDED) "(from JS)" else "(default)"}")
        
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    successfulLoads++
                    log("✓ AdMob rewarded loaded [loads: $successfulLoads]")
                    adMobRewarded = ad
                    isAdMobRewardedReady = true
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            successfulShows++
                            log("✓ AdMob rewarded displayed [shows: $successfulShows]")
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
                            failedShows++
                            log("✗ AdMob rewarded show failed [fails: $failedShows]: ${error.message}", isError = true)
                            isAdMobRewardedReady = false
                            adMobRewarded = null
                        }
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failedLoads++
                    log("✗ AdMob rewarded load failed [fails: $failedLoads]: ${error.message}", isError = true)
                    isAdMobRewardedReady = false
                    scheduleRetry { preloadAdMobRewarded() }
                }
            }
        )
    }

    private fun showRewardedWaterfall() {
        val activity = activityRef.get() ?: return
        
        log("=== SHOW REWARDED FLOW ===")
        log("InMobi ready: $isInMobiRewardedReady, isReady(): ${inMobiRewarded?.isReady()}")
        log("AdMob ready: $isAdMobRewardedReady")
        
        if (isInMobiRewardedReady && inMobiRewarded?.isReady() == true) {
            log("→ Showing InMobi rewarded")
            inMobiRewarded?.show()
            return
        }
        
        if (isAdMobRewardedReady && adMobRewarded != null) {
            log("→ Showing AdMob rewarded (fallback)")
            adMobRewarded?.show(activity) { rewardItem ->
                log("AdMob reward earned: ${rewardItem.amount} ${rewardItem.type}")
                sendEventToWeb(AdEvent(
                    "adRewarded", 
                    "rewarded", 
                    "admob",
                    data = mapOf("amount" to rewardItem.amount, "type" to rewardItem.type)
                ))
            }
            return
        }
        
        log("✗ No rewarded ready to show!", isError = true)
        sendEventToWeb(AdEvent("adFailed", "rewarded", "none", error = "No ad ready"))
        
        preloadInMobiRewarded()
        preloadAdMobRewarded()
    }

    private fun recordImpression(network: AdNetwork, adType: String) {
        totalImpressions++
        
        when (network) {
            AdNetwork.INMOBI -> inMobiImpressions++
            AdNetwork.ADMOB -> adMobImpressions++
            AdNetwork.NONE -> {}
        }
        
        // Track by ad type
        when (adType.lowercase()) {
            "banner" -> bannerImpressions++
            "interstitial" -> interstitialImpressions++
            "rewarded" -> rewardedImpressions++
        }
        
        saveImpressionData()
        
        val progress = (inMobiImpressions.toFloat() / targetImpressions * 100).toInt()
        log("Impression #$totalImpressions ($adType via ${network.name})")
        log("   InMobi: $inMobiImpressions | AdMob: $adMobImpressions | Progress: $progress%")
        log("   By Type - Banner: $bannerImpressions | Interstitial: $interstitialImpressions | Rewarded: $rewardedImpressions")
        TestRigorLogger.logAdEvent("Impression: $adType via ${network.name} - InMobi: $inMobiImpressions/$targetImpressions")
        
        if (inMobiImpressions % 100 == 0 && inMobiImpressions > 0) {
            log("InMobi milestone: $inMobiImpressions impressions!")
        }
        
        if (inMobiImpressions >= targetImpressions) {
            log("TARGET REACHED: 10,000 InMobi impressions!")
            TestRigorLogger.logMilestone("InMobi 10K impressions target reached!")
        }
    }

    private fun saveImpressionData() {
        val context = activityRef.get() ?: return
        context.getSharedPreferences("ad_waterfall", Context.MODE_PRIVATE)
            .edit()
            .putInt("total_impressions", totalImpressions)
            .putInt("inmobi_impressions", inMobiImpressions)
            .putInt("admob_impressions", adMobImpressions)
            .putInt("banner_impressions", bannerImpressions)
            .putInt("interstitial_impressions", interstitialImpressions)
            .putInt("rewarded_impressions", rewardedImpressions)
            .putLong("last_impression_time", System.currentTimeMillis())
            .apply()
    }

    private fun loadImpressionData() {
        val context = activityRef.get() ?: return
        val prefs = context.getSharedPreferences("ad_waterfall", Context.MODE_PRIVATE)
        totalImpressions = prefs.getInt("total_impressions", 0)
        inMobiImpressions = prefs.getInt("inmobi_impressions", 0)
        adMobImpressions = prefs.getInt("admob_impressions", 0)
        bannerImpressions = prefs.getInt("banner_impressions", 0)
        interstitialImpressions = prefs.getInt("interstitial_impressions", 0)
        rewardedImpressions = prefs.getInt("rewarded_impressions", 0)
        
        log("Loaded impressions - InMobi: $inMobiImpressions | AdMob: $adMobImpressions | Total: $totalImpressions")
        log("  By Type - Banner: $bannerImpressions | Interstitial: $interstitialImpressions | Rewarded: $rewardedImpressions")
    }

    override fun onCreate(owner: LifecycleOwner) {
        loadImpressionData()
    }

    override fun onResume(owner: LifecycleOwner) {
        inMobiBanner?.resume()
        adMobBanner?.resume()
        
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
        
        val timestamp = dateFormat.format(Date())
        val tag = AdConfig.LOG_TAG
        val fullMessage = "[$timestamp] $message"
        
        if (isError) {
            Log.e(tag, fullMessage)
        } else {
            Log.d(tag, fullMessage)
        }
    }
}
