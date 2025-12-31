package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.ump.*
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
        
        private const val MAX_RETRY_DELAY_MS = 60000L
        private const val INITIAL_RETRY_DELAY_MS = 1000L
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
    
    @Volatile
    private var isLoadingRewardedInterstitial = false
    
    @Volatile
    private var consentObtained = false
    
    private var interstitialRetryDelay = INITIAL_RETRY_DELAY_MS
    private var rewardedRetryDelay = INITIAL_RETRY_DELAY_MS
    private var rewardedInterstitialRetryDelay = INITIAL_RETRY_DELAY_MS
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consentInformation: ConsentInformation? = null
    
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
        
        if (!isPlayServicesAvailable()) {
            TestRigorLogger.logAdEvent("Google Play Services not available - ads disabled")
            notifyWeb("adMobInitFailed", "Play Services unavailable")
            return
        }
        
        TestRigorLogger.logAdEvent("Initializing AdMob SDK...")
        
        mainHandler.post {
            try {
                requestConsent()
            } catch (e: Exception) {
                TestRigorLogger.logError("Consent request failed", e)
                initializeMobileAds()
            }
        }
    }
    
    private fun isPlayServicesAvailable(): Boolean {
        return try {
            val resultCode = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(activity)
            val available = resultCode == ConnectionResult.SUCCESS
            TestRigorLogger.logAdEvent("Play Services check: $available (code: $resultCode)")
            available
        } catch (e: Exception) {
            TestRigorLogger.logError("Play Services check failed", e)
            false
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected ?: false
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("Network check failed", e)
            false
        }
    }
    
    private fun requestConsent() {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        
        consentInformation = ConsentInformation.getInstance(activity)
        consentInformation?.requestConsentInfoUpdate(
            activity,
            params,
            {
                TestRigorLogger.logAdEvent("Consent info updated, status: ${consentInformation?.consentStatus}")
                
                if (consentInformation?.isConsentFormAvailable == true) {
                    loadConsentForm()
                } else {
                    consentObtained = true
                    initializeMobileAds()
                }
            },
            { formError ->
                TestRigorLogger.logAdEvent("Consent info update failed: ${formError.message}")
                consentObtained = true
                initializeMobileAds()
            }
        )
    }
    
    private fun loadConsentForm() {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { consentForm ->
                val status = consentInformation?.consentStatus
                if (status == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) { formError ->
                        formError?.let {
                            TestRigorLogger.logAdEvent("Consent form error: ${it.message}")
                        }
                        consentObtained = true
                        initializeMobileAds()
                    }
                } else {
                    consentObtained = true
                    initializeMobileAds()
                }
            },
            { formError ->
                TestRigorLogger.logAdEvent("Consent form load failed: ${formError.message}")
                consentObtained = true
                initializeMobileAds()
            }
        )
    }
    
    private fun initializeMobileAds() {
        if (isInitialized) return
        
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
        
        if (!isNetworkAvailable()) {
            TestRigorLogger.logAdEvent("No network - skipping interstitial load")
            scheduleRetry(AdType.INTERSTITIAL)
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logAdEvent("Activity invalid - skipping interstitial load")
            return
        }
        
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
                    interstitialRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Interstitial ad loaded")
                    notifyWeb("interstitialLoaded", "true")
                    
                    ad.fullScreenContentCallback = createInterstitialCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    handleLoadError("Interstitial", error, AdType.INTERSTITIAL)
                }
            }
        )
    }
    
    private fun loadRewardedAd() {
        if (isLoadingRewarded || rewardedAd != null) return
        
        if (!isNetworkAvailable()) {
            TestRigorLogger.logAdEvent("No network - skipping rewarded load")
            scheduleRetry(AdType.REWARDED)
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logAdEvent("Activity invalid - skipping rewarded load")
            return
        }
        
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
                    rewardedRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Rewarded ad loaded")
                    notifyWeb("rewardedLoaded", "true")
                    
                    ad.fullScreenContentCallback = createRewardedCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewarded = false
                    rewardedAd = null
                    handleLoadError("Rewarded", error, AdType.REWARDED)
                }
            }
        )
    }
    
    private fun loadRewardedInterstitialAd() {
        if (isLoadingRewardedInterstitial || rewardedInterstitialAd != null) return
        
        if (!isNetworkAvailable()) {
            TestRigorLogger.logAdEvent("No network - skipping rewarded interstitial load")
            scheduleRetry(AdType.REWARDED_INTERSTITIAL)
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logAdEvent("Activity invalid - skipping rewarded interstitial load")
            return
        }
        
        isLoadingRewardedInterstitial = true
        TestRigorLogger.logAdEvent("Loading rewarded interstitial ad...")
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedInterstitialAd.load(
            activity,
            rewardedInterstitialId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    isLoadingRewardedInterstitial = false
                    rewardedInterstitialAd = ad
                    rewardedInterstitialRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Rewarded interstitial ad loaded")
                    notifyWeb("rewardedInterstitialLoaded", "true")
                    
                    ad.fullScreenContentCallback = createRewardedInterstitialCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingRewardedInterstitial = false
                    rewardedInterstitialAd = null
                    handleLoadError("Rewarded Interstitial", error, AdType.REWARDED_INTERSTITIAL)
                }
            }
        )
    }
    
    private fun handleLoadError(adType: String, error: LoadAdError, type: AdType) {
        val errorMessage = getErrorDescription(error.code)
        TestRigorLogger.logAdEvent("$adType failed: ${error.message} (code: ${error.code}) - $errorMessage")
        
        when (error.code) {
            AdRequest.ERROR_CODE_INTERNAL_ERROR -> {
                notifyWeb("${adType.lowercase().replace(" ", "")}Failed", "Internal error")
                scheduleRetry(type)
            }
            AdRequest.ERROR_CODE_NETWORK_ERROR -> {
                notifyWeb("${adType.lowercase().replace(" ", "")}Failed", "Network error")
                scheduleRetry(type)
            }
            AdRequest.ERROR_CODE_NO_FILL -> {
                notifyWeb("${adType.lowercase().replace(" ", "")}Failed", "No ad available")
                scheduleRetry(type)
            }
            AdRequest.ERROR_CODE_INVALID_REQUEST -> {
                notifyWeb("${adType.lowercase().replace(" ", "")}Failed", "Invalid request")
            }
            else -> {
                notifyWeb("${adType.lowercase().replace(" ", "")}Failed", error.message)
                scheduleRetry(type)
            }
        }
    }
    
    private fun getErrorDescription(code: Int): String {
        return when (code) {
            AdRequest.ERROR_CODE_INTERNAL_ERROR -> "Internal SDK error"
            AdRequest.ERROR_CODE_NETWORK_ERROR -> "Network connectivity issue"
            AdRequest.ERROR_CODE_NO_FILL -> "No ad inventory available"
            AdRequest.ERROR_CODE_INVALID_REQUEST -> "Invalid ad request configuration"
            else -> "Unknown error (code: $code)"
        }
    }
    
    private enum class AdType {
        INTERSTITIAL, REWARDED, REWARDED_INTERSTITIAL
    }
    
    private fun scheduleRetry(type: AdType) {
        if (activity.isFinishing || activity.isDestroyed) return
        
        val delay = when (type) {
            AdType.INTERSTITIAL -> {
                val d = interstitialRetryDelay
                interstitialRetryDelay = (interstitialRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                d
            }
            AdType.REWARDED -> {
                val d = rewardedRetryDelay
                rewardedRetryDelay = (rewardedRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                d
            }
            AdType.REWARDED_INTERSTITIAL -> {
                val d = rewardedInterstitialRetryDelay
                rewardedInterstitialRetryDelay = (rewardedInterstitialRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                d
            }
        }
        
        TestRigorLogger.logAdEvent("Scheduling ${type.name} retry in ${delay}ms")
        
        mainHandler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                when (type) {
                    AdType.INTERSTITIAL -> loadInterstitialAd()
                    AdType.REWARDED -> loadRewardedAd()
                    AdType.REWARDED_INTERSTITIAL -> loadRewardedInterstitialAd()
                }
            }
        }, delay)
    }
    
    private fun createInterstitialCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Interstitial dismissed")
                interstitialAd?.fullScreenContentCallback = null
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
                interstitialAd?.fullScreenContentCallback = null
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
                rewardedAd?.fullScreenContentCallback = null
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
                rewardedAd?.fullScreenContentCallback = null
                rewardedAd = null
                notifyWeb("rewardedShowFailed", error.message)
                loadRewardedAd()
            }
        }
    }
    
    private fun createRewardedInterstitialCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded interstitial dismissed")
                rewardedInterstitialAd?.fullScreenContentCallback = null
                rewardedInterstitialAd = null
                notifyWeb("rewardedInterstitialClosed", "")
                loadRewardedInterstitialAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded interstitial shown")
                notifyWeb("rewardedInterstitialShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Rewarded interstitial show failed: ${error.message}")
                rewardedInterstitialAd?.fullScreenContentCallback = null
                rewardedInterstitialAd = null
                notifyWeb("rewardedInterstitialShowFailed", error.message)
                loadRewardedInterstitialAd()
            }
            
            override fun onAdImpression() {
                TestRigorLogger.logAdEvent("Rewarded interstitial impression")
            }
            
            override fun onAdClicked() {
                TestRigorLogger.logAdEvent("Rewarded interstitial clicked")
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
        
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                TestRigorLogger.logAdEvent("Cannot show interstitial - activity invalid")
                notifyWeb("interstitialFailed", "Activity not available")
                return@post
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
        
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                TestRigorLogger.logAdEvent("Cannot show rewarded - activity invalid")
                notifyWeb("rewardedFailed", "Activity not available")
                return@post
            }
            
            rewardedAd?.let { ad ->
                ad.show(activity) { rewardItem ->
                    val rewardAmount = rewardItem.amount
                    val rewardType = rewardItem.type
                    TestRigorLogger.logAdEvent("User earned reward: $rewardAmount $rewardType")
                    
                    notifyWeb("rewardedEarned", rewardAmount.toString())
                    
                    mainHandler.post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            webView.evaluateJavascript("if(window.onRewardEarned) window.onRewardEarned($rewardAmount);", null)
                        }
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
        
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                notifyWeb("rewardedInterstitialFailed", "Activity not available")
                return@post
            }
            
            rewardedInterstitialAd?.let { ad ->
                ad.show(activity) { rewardItem ->
                    val rewardAmount = rewardItem.amount
                    TestRigorLogger.logAdEvent("User earned reward from interstitial: $rewardAmount")
                    notifyWeb("rewardedInterstitialEarned", rewardAmount.toString())
                    mainHandler.post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            webView.evaluateJavascript("if(window.onRewardEarned) window.onRewardEarned($rewardAmount);", null)
                        }
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
        mainHandler.post {
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
            "rewardedInterstitialReady" to (rewardedInterstitialAd != null),
            "consentObtained" to consentObtained,
            "networkAvailable" to isNetworkAvailable(),
            "playServicesAvailable" to isPlayServicesAvailable()
        )
        return status.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
    
    @JavascriptInterface
    fun getDiagnostics(): String {
        return runDiagnostics().joinToString(";") { "${it.checkId}:${it.passed}:${it.description}" }
    }
    
    @JavascriptInterface
    fun showPrivacyOptions() {
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            
            UserMessagingPlatform.loadConsentForm(
                activity,
                { consentForm ->
                    consentForm.show(activity) { formError ->
                        formError?.let {
                            TestRigorLogger.logAdEvent("Privacy options error: ${it.message}")
                        }
                    }
                },
                { formError ->
                    TestRigorLogger.logAdEvent("Privacy options load failed: ${formError.message}")
                }
            )
        }
    }
    
    private fun notifyWeb(event: String, data: String) {
        val safeData = data.replace("'", "\\'").replace("\"", "\\\"")
        val js = "if(window.onAdBridgeEvent) window.onAdBridgeEvent('$event', '$safeData');"
        
        mainHandler.post {
            try {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    webView.evaluateJavascript(js, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify web: ${e.message}")
            }
        }
    }
    
    fun onLowMemory() {
        TestRigorLogger.logAdEvent("Low memory - releasing ads")
        interstitialAd?.fullScreenContentCallback = null
        rewardedAd?.fullScreenContentCallback = null
        rewardedInterstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        rewardedAd = null
        rewardedInterstitialAd = null
    }
    
    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            TestRigorLogger.logAdEvent("Trim memory level $level - releasing ads")
            onLowMemory()
        }
    }
    
    fun cleanup() {
        TestRigorLogger.logAdEvent("AdBridge cleanup")
        mainHandler.removeCallbacksAndMessages(null)
        interstitialAd?.fullScreenContentCallback = null
        rewardedAd?.fullScreenContentCallback = null
        rewardedInterstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        rewardedAd = null
        rewardedInterstitialAd = null
    }
    
    data class DiagnosticResult(
        val checkId: String,
        val passed: Boolean,
        val description: String
    )
    
    fun runDiagnostics(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        
        results.add(DiagnosticResult(
            "SDK_001",
            isInitialized,
            "MobileAds initialized"
        ))
        
        results.add(DiagnosticResult(
            "SDK_003",
            isPlayServicesAvailable(),
            "Play Services available"
        ))
        
        results.add(DiagnosticResult(
            "NET_001",
            isNetworkAvailable(),
            "Network connected"
        ))
        
        results.add(DiagnosticResult(
            "CONSENT_001",
            consentObtained,
            "Consent obtained"
        ))
        
        results.add(DiagnosticResult(
            "INTERSTITIAL",
            interstitialAd != null,
            "Interstitial ready"
        ))
        
        results.add(DiagnosticResult(
            "REWARDED",
            rewardedAd != null,
            "Rewarded ready"
        ))
        
        results.add(DiagnosticResult(
            "LIFECYCLE",
            !activity.isFinishing && !activity.isDestroyed,
            "Activity valid"
        ))
        
        return results
    }
}
