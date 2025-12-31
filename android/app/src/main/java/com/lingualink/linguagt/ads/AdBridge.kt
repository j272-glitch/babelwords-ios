package com.lingualink.linguagt.ads

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.lingualink.linguagt.TestRigorLogger

class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "AdBridge"
        
        private const val INTERSTITIAL_AD_UNIT = "ca-app-pub-9277938970928959/1473642031"
        private const val REWARDED_AD_UNIT = "ca-app-pub-9277938970928959/8777416980"
        private const val REWARDED_INTERSTITIAL_AD_UNIT = "ca-app-pub-9277938970928959/6843749135"
        
        private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
        private const val TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
        
        private const val USE_TEST_ADS = false
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var isLoadingInterstitial = false
    
    @Volatile
    private var isLoadingRewarded = false
    
    private val interstitialId: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL else INTERSTITIAL_AD_UNIT
    
    private val rewardedId: String
        get() = if (USE_TEST_ADS) TEST_REWARDED else REWARDED_AD_UNIT
    
    private val rewardedInterstitialId: String
        get() = if (USE_TEST_ADS) TEST_REWARDED_INTERSTITIAL else REWARDED_INTERSTITIAL_AD_UNIT
    
    fun initialize() {
        if (isInitialized) {
            TestRigorLogger.logAdEvent("AdBridge already initialized")
            return
        }
        
        TestRigorLogger.logAdEvent("Initializing AdMob SDK...")
        
        MobileAds.initialize(activity) { initStatus ->
            isInitialized = true
            val adapters = initStatus.adapterStatusMap
            TestRigorLogger.logAdEvent("AdMob initialized with ${adapters.size} adapters")
            
            adapters.forEach { (name, status) ->
                Log.d(TAG, "Adapter: $name, State: ${status.initializationState}, Latency: ${status.latency}ms")
            }
            
            loadInterstitialAd()
            loadRewardedAd()
            loadRewardedInterstitialAd()
            
            notifyWeb("adMobInitialized", "true")
        }
    }
    
    private fun loadInterstitialAd() {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        
        TestRigorLogger.logAdEvent("Loading interstitial ad...")
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            activity,
            interstitialId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    TestRigorLogger.logAdEvent("Interstitial ad loaded")
                    notifyWeb("interstitialLoaded", "true")
                    
                    ad.fullScreenContentCallback = createInterstitialCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    TestRigorLogger.logAdEvent("Interstitial failed: ${error.message} (code: ${error.code})")
                    notifyWeb("interstitialFailed", error.message)
                }
            }
        )
    }
    
    private fun loadRewardedAd() {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        
        TestRigorLogger.logAdEvent("Loading rewarded ad...")
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            activity,
            rewardedId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoadingRewarded = false
                    rewardedAd = ad
                    TestRigorLogger.logAdEvent("Rewarded ad loaded")
                    notifyWeb("rewardedLoaded", "true")
                    
                    ad.fullScreenContentCallback = createRewardedCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    TestRigorLogger.logAdEvent("Rewarded failed: ${error.message} (code: ${error.code})")
                    notifyWeb("rewardedFailed", error.message)
                }
            }
        )
    }
    
    private fun loadRewardedInterstitialAd() {
        TestRigorLogger.logAdEvent("Loading rewarded interstitial ad...")
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedInterstitialAd.load(
            activity,
            rewardedInterstitialId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    TestRigorLogger.logAdEvent("Rewarded interstitial ad loaded")
                    notifyWeb("rewardedInterstitialLoaded", "true")
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                    TestRigorLogger.logAdEvent("Rewarded interstitial failed: ${error.message}")
                }
            }
        )
    }
    
    private fun createInterstitialCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Interstitial dismissed")
                interstitialAd = null
                notifyWeb("interstitialClosed", "")
                loadInterstitialAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Interstitial shown")
                notifyWeb("interstitialShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Interstitial show failed: ${error.message}")
                interstitialAd = null
                notifyWeb("interstitialShowFailed", error.message)
                loadInterstitialAd()
            }
            
            override fun onAdImpression() {
                TestRigorLogger.logAdEvent("Interstitial impression")
            }
            
            override fun onAdClicked() {
                TestRigorLogger.logAdEvent("Interstitial clicked")
            }
        }
    }
    
    private fun createRewardedCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded ad dismissed")
                rewardedAd = null
                notifyWeb("rewardedClosed", "")
                loadRewardedAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded ad shown")
                notifyWeb("rewardedShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Rewarded show failed: ${error.message}")
                rewardedAd = null
                notifyWeb("rewardedShowFailed", error.message)
                loadRewardedAd()
            }
        }
    }
    
    @JavascriptInterface
    fun isAdMobAvailable(): Boolean {
        return isInitialized
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        return interstitialAd != null
    }
    
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        return rewardedAd != null
    }
    
    @JavascriptInterface
    fun isRewardedInterstitialReady(): Boolean {
        return rewardedInterstitialAd != null
    }
    
    @JavascriptInterface
    fun showInterstitial(placement: String) {
        TestRigorLogger.logAdEvent("showInterstitial called, placement: $placement")
        
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                TestRigorLogger.logAdEvent("Cannot show interstitial - activity invalid")
                notifyWeb("interstitialFailed", "Activity not available")
                return@runOnUiThread
            }
            
            interstitialAd?.let { ad ->
                ad.show(activity)
            } ?: run {
                TestRigorLogger.logAdEvent("Interstitial not ready")
                notifyWeb("interstitialFailed", "Ad not ready")
                loadInterstitialAd()
            }
        }
    }
    
    @JavascriptInterface
    fun showRewarded(placement: String) {
        TestRigorLogger.logAdEvent("showRewarded called, placement: $placement")
        
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                TestRigorLogger.logAdEvent("Cannot show rewarded - activity invalid")
                notifyWeb("rewardedFailed", "Activity not available")
                return@runOnUiThread
            }
            
            rewardedAd?.let { ad ->
                ad.show(activity) { rewardItem ->
                    val rewardAmount = rewardItem.amount
                    val rewardType = rewardItem.type
                    TestRigorLogger.logAdEvent("User earned reward: $rewardAmount $rewardType")
                    
                    notifyWeb("rewardedEarned", rewardAmount.toString())
                    
                    activity.runOnUiThread {
                        webView.evaluateJavascript("if(window.onRewardEarned) window.onRewardEarned($rewardAmount);", null)
                    }
                }
            } ?: run {
                TestRigorLogger.logAdEvent("Rewarded ad not ready")
                notifyWeb("rewardedFailed", "Ad not ready")
                loadRewardedAd()
            }
        }
    }
    
    @JavascriptInterface
    fun showRewardedInterstitial(placement: String) {
        TestRigorLogger.logAdEvent("showRewardedInterstitial called, placement: $placement")
        
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                notifyWeb("rewardedInterstitialFailed", "Activity not available")
                return@runOnUiThread
            }
            
            rewardedInterstitialAd?.let { ad ->
                ad.show(activity) { rewardItem ->
                    val rewardAmount = rewardItem.amount
                    TestRigorLogger.logAdEvent("User earned reward from interstitial: $rewardAmount")
                    notifyWeb("rewardedInterstitialEarned", rewardAmount.toString())
                    activity.runOnUiThread {
                        webView.evaluateJavascript("if(window.onRewardEarned) window.onRewardEarned($rewardAmount);", null)
                    }
                }
            } ?: run {
                notifyWeb("rewardedInterstitialFailed", "Ad not ready")
                loadRewardedInterstitialAd()
            }
        }
    }
    
    @JavascriptInterface
    fun preloadAds() {
        TestRigorLogger.logAdEvent("Preloading ads...")
        activity.runOnUiThread {
            loadInterstitialAd()
            loadRewardedAd()
            loadRewardedInterstitialAd()
        }
    }
    
    @JavascriptInterface
    fun getAdStatus(): String {
        val status = mapOf(
            "initialized" to isInitialized,
            "interstitialReady" to (interstitialAd != null),
            "rewardedReady" to (rewardedAd != null),
            "rewardedInterstitialReady" to (rewardedInterstitialAd != null)
        )
        return status.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
    
    private fun notifyWeb(event: String, data: String) {
        val safeData = data.replace("'", "\\'").replace("\"", "\\\"")
        val js = "if(window.onAdBridgeEvent) window.onAdBridgeEvent('$event', '$safeData');"
        
        activity.runOnUiThread {
            try {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    webView.evaluateJavascript(js, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify web: ${e.message}")
            }
        }
    }
    
    fun cleanup() {
        TestRigorLogger.logAdEvent("AdBridge cleanup")
        interstitialAd = null
        rewardedAd = null
        rewardedInterstitialAd = null
    }
}
