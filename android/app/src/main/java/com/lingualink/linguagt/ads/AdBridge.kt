package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class AdBridge(
    private val activity: Activity,
    webView: WebView
) : LifecycleEventObserver {
    companion object {
        private const val TAG = "AdBridge"
        
        // PRODUCTION AD UNIT IDs ONLY - No test ads
        private const val INTERSTITIAL_AD_UNIT = "ca-app-pub-9991891515643313/5076005693"
        private const val REWARDED_AD_UNIT = "ca-app-pub-9991891515643313/6313049833"
        private const val REWARDED_INTERSTITIAL_AD_UNIT = "ca-app-pub-9991891515643313/8883372855"
        
        private const val MAX_RETRY_DELAY_MS = 60000L
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        
        // Delay before bringing app to foreground after ad click
        private const val FOREGROUND_DELAY_MS = 1500L
        
        // Ad expiration time (45 minutes per AdMob docs)
        private const val AD_EXPIRY_MS = 45 * 60 * 1000L
        
        // Load timeout (15 seconds)
        private const val LOAD_TIMEOUT_MS = 15000L
    }
    
    // WeakReference to WebView to prevent memory leaks (Fix #4)
    private var webViewRef: WeakReference<WebView> = WeakReference(webView)
    
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
    
    // Fix #41: Prevent concurrent ads
    @Volatile
    private var isShowingAd = false
    
    // Fix #51: Track app foreground state
    @Volatile
    private var isAppInForeground = true
    
    // Fix #24: Ad load timestamps for expiration checking
    private var interstitialLoadTime: Long = 0
    private var rewardedLoadTime: Long = 0
    private var rewardedInterstitialLoadTime: Long = 0
    
    private var interstitialRetryDelay = INITIAL_RETRY_DELAY_MS
    private var rewardedRetryDelay = INITIAL_RETRY_DELAY_MS
    private var rewardedInterstitialRetryDelay = INITIAL_RETRY_DELAY_MS
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consentInformation: ConsentInformation? = null
    
    var onConsentObtained: ((gdprConsent: Boolean) -> Unit)? = null
    
    // Foreground recovery system - handles Play Store redirect
    private var pendingForegroundRunnable: Runnable? = null
    private var adClickedTime: Long = 0
    
    // Fix #25: Timeout runnables for load operations
    private var interstitialTimeoutRunnable: Runnable? = null
    private var rewardedTimeoutRunnable: Runnable? = null
    private var rewardedInterstitialTimeoutRunnable: Runnable? = null
    
    init {
        // Fix #51: Register for app lifecycle events
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    // Fix #51: LifecycleEventObserver implementation
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                isAppInForeground = true
                TestRigorLogger.logAdEvent("App entered foreground")
                // Reload stale ads
                if (!isInterstitialFresh()) loadInterstitialAd()
                if (!isRewardedFresh()) loadRewardedAd()
                if (!isRewardedInterstitialFresh()) loadRewardedInterstitialAd()
            }
            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
                TestRigorLogger.logAdEvent("App entered background")
            }
            else -> {}
        }
    }
    
    // Fix #24: Check if ads are fresh (not expired)
    private fun isInterstitialFresh(): Boolean {
        if (interstitialAd == null) return false
        return System.currentTimeMillis() - interstitialLoadTime < AD_EXPIRY_MS
    }
    
    private fun isRewardedFresh(): Boolean {
        if (rewardedAd == null) return false
        return System.currentTimeMillis() - rewardedLoadTime < AD_EXPIRY_MS
    }
    
    private fun isRewardedInterstitialFresh(): Boolean {
        if (rewardedInterstitialAd == null) return false
        return System.currentTimeMillis() - rewardedInterstitialLoadTime < AD_EXPIRY_MS
    }
    
    // Method to update WebView reference
    fun setWebView(webView: WebView) {
        this.webViewRef = WeakReference(webView)
    }
    
    // Direct production IDs - no test fallback
    private val interstitialId: String
        get() = INTERSTITIAL_AD_UNIT
    
    private val rewardedId: String
        get() = REWARDED_AD_UNIT
    
    private val rewardedInterstitialId: String
        get() = REWARDED_INTERSTITIAL_AD_UNIT
    
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
        
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
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
        val gdprConsent = consentInformation?.consentStatus == ConsentInformation.ConsentStatus.OBTAINED ||
                consentInformation?.consentStatus == ConsentInformation.ConsentStatus.NOT_REQUIRED
        
        // Always invoke consent callback, even if already initialized
        onConsentObtained?.invoke(gdprConsent)
        TestRigorLogger.logAdEvent("Consent callback invoked - GDPR consent: $gdprConsent")
        
        if (isInitialized) return
        
        // Initialize on background thread (per Google recommendation)
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(activity) { initStatus ->
                isInitialized = true
                val adapters = initStatus.adapterStatusMap
                TestRigorLogger.logAdEvent("AdMob initialized with ${adapters.size} adapters")
                
                adapters.forEach { (name, status) ->
                    Log.d(TAG, "Adapter: $name, State: ${status.initializationState}, Latency: ${status.latency}ms")
                }
                
                // Preload ads on main thread
                mainHandler.post {
                    loadInterstitialAd()
                    loadRewardedAd()
                    loadRewardedInterstitialAd()
                    notifyWeb("adMobInitialized", "true")
                }
            }
        }
    }
    
    private fun loadInterstitialAd() {
        if (isLoadingInterstitial) return
        
        // Fix #24: Check if existing ad is still fresh
        if (interstitialAd != null && isInterstitialFresh()) return
        
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
        
        // Fix #25: Set load timeout
        interstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        interstitialTimeoutRunnable = Runnable {
            if (isLoadingInterstitial && interstitialAd == null) {
                TestRigorLogger.logAdEvent("Interstitial load timeout")
                isLoadingInterstitial = false
                callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('timeout', -2)")
                scheduleRetry(AdType.INTERSTITIAL)
            }
        }
        mainHandler.postDelayed(interstitialTimeoutRunnable!!, LOAD_TIMEOUT_MS)
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            activity,
            interstitialId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    interstitialLoadTime = System.currentTimeMillis()
                    interstitialRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Interstitial ad loaded")
                    notifyWeb("interstitialLoaded", "true")
                    // Fix #3: Standardized callback name
                    callJavaScript("window.onInterstitialReady && window.onInterstitialReady()")
                    
                    ad.fullScreenContentCallback = createInterstitialCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    isLoadingInterstitial = false
                    interstitialAd = null
                    // Fix #3: Standardized callback name
                    callJavaScript("window.onInterstitialLoadFailed && window.onInterstitialLoadFailed('${escapeJs(error.message)}', ${error.code})")
                    handleLoadError("Interstitial", error, AdType.INTERSTITIAL)
                }
            }
        )
    }
    
    private fun loadRewardedAd() {
        if (isLoadingRewarded) return
        
        // Fix #24: Check if existing ad is still fresh
        if (rewardedAd != null && isRewardedFresh()) return
        
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
        
        // Fix #25: Set load timeout
        rewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        rewardedTimeoutRunnable = Runnable {
            if (isLoadingRewarded && rewardedAd == null) {
                TestRigorLogger.logAdEvent("Rewarded load timeout")
                isLoadingRewarded = false
                callJavaScript("window.onRewardedLoadFailed && window.onRewardedLoadFailed('timeout', -2)")
                scheduleRetry(AdType.REWARDED)
            }
        }
        mainHandler.postDelayed(rewardedTimeoutRunnable!!, LOAD_TIMEOUT_MS)
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            activity,
            rewardedId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    isLoadingRewarded = false
                    rewardedAd = ad
                    rewardedLoadTime = System.currentTimeMillis()
                    rewardedRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Rewarded ad loaded")
                    notifyWeb("rewardedLoaded", "true")
                    // Fix #3: Standardized callback name
                    callJavaScript("window.onRewardedReady && window.onRewardedReady()")
                    
                    ad.fullScreenContentCallback = createRewardedCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    isLoadingRewarded = false
                    rewardedAd = null
                    // Fix #3: Standardized callback name
                    callJavaScript("window.onRewardedLoadFailed && window.onRewardedLoadFailed('${escapeJs(error.message)}', ${error.code})")
                    handleLoadError("Rewarded", error, AdType.REWARDED)
                }
            }
        )
    }
    
    private fun loadRewardedInterstitialAd() {
        if (isLoadingRewardedInterstitial) return
        
        // Fix #24: Check if existing ad is still fresh
        if (rewardedInterstitialAd != null && isRewardedInterstitialFresh()) return
        
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
        
        // Fix #25: Set load timeout
        rewardedInterstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        rewardedInterstitialTimeoutRunnable = Runnable {
            if (isLoadingRewardedInterstitial && rewardedInterstitialAd == null) {
                TestRigorLogger.logAdEvent("Rewarded interstitial load timeout")
                isLoadingRewardedInterstitial = false
                scheduleRetry(AdType.REWARDED_INTERSTITIAL)
            }
        }
        mainHandler.postDelayed(rewardedInterstitialTimeoutRunnable!!, LOAD_TIMEOUT_MS)
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedInterstitialAd.load(
            activity,
            rewardedInterstitialId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    isLoadingRewardedInterstitial = false
                    rewardedInterstitialAd = ad
                    rewardedInterstitialLoadTime = System.currentTimeMillis()
                    rewardedInterstitialRetryDelay = INITIAL_RETRY_DELAY_MS
                    TestRigorLogger.logAdEvent("Rewarded interstitial ad loaded")
                    notifyWeb("rewardedInterstitialLoaded", "true")
                    
                    ad.fullScreenContentCallback = createRewardedInterstitialCallback()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
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
    
    // ========================================
    // FOREGROUND RECOVERY SYSTEM
    // ========================================
    
    /**
     * Bring the app back to foreground after ad dismissal.
     * Prevents user from being stuck on Play Store after clicking an ad.
     */
    private fun bringAppToForeground() {
        if (activity.isFinishing || activity.isDestroyed) return
        
        val intent = Intent(activity, activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
        TestRigorLogger.logAdEvent("Bringing app to foreground")
    }
    
    /**
     * Schedule foreground recovery when ad is clicked.
     * This handles the case where user is redirected to Play Store and
     * onAdDismissedFullScreenContent never fires until they return.
     */
    private fun scheduleForegroundRecovery() {
        // Cancel any existing scheduled recovery
        cancelForegroundRecovery()
        
        adClickedTime = System.currentTimeMillis()
        TestRigorLogger.logAdEvent("Ad clicked - scheduling foreground recovery in ${FOREGROUND_DELAY_MS}ms")
        
        pendingForegroundRunnable = Runnable {
            val timeSinceClick = System.currentTimeMillis() - adClickedTime
            TestRigorLogger.logAdEvent("Foreground recovery triggered, time since click: ${timeSinceClick}ms")
            bringAppToForeground()
        }
        
        mainHandler.postDelayed(pendingForegroundRunnable!!, FOREGROUND_DELAY_MS)
    }
    
    /**
     * Cancel any pending foreground recovery.
     * Called when user returns naturally or ad is dismissed.
     */
    private fun cancelForegroundRecovery() {
        pendingForegroundRunnable?.let {
            TestRigorLogger.logAdEvent("Cancelling pending foreground recovery")
            mainHandler.removeCallbacks(it)
            pendingForegroundRunnable = null
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
                isShowingAd = false
                interstitialAd?.fullScreenContentCallback = null
                interstitialAd = null
                notifyWeb("interstitialClosed", "")
                // Fix #3: Standardized callback
                callJavaScript("window.onInterstitialClosed && window.onInterstitialClosed()")
                // Cancel pending recovery since user returned naturally
                cancelForegroundRecovery()
                bringAppToForeground()
                loadInterstitialAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Interstitial shown")
                notifyWeb("interstitialShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Interstitial show failed: ${error.message}")
                isShowingAd = false
                interstitialAd?.fullScreenContentCallback = null
                interstitialAd = null
                cancelForegroundRecovery()
                notifyWeb("interstitialShowFailed", error.message)
                // Fix #3: Standardized callback
                callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('${escapeJs(error.message)}', ${error.code})")
                loadInterstitialAd()
            }
            
            override fun onAdImpression() {
                TestRigorLogger.logAdEvent("Interstitial impression")
            }
            
            // CRITICAL: Detect when ad is clicked (about to redirect to Play Store)
            override fun onAdClicked() {
                TestRigorLogger.logAdEvent("Interstitial clicked - scheduling foreground recovery")
                scheduleForegroundRecovery()
            }
        }
    }
    
    private fun createRewardedCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded ad dismissed")
                isShowingAd = false
                rewardedAd?.fullScreenContentCallback = null
                rewardedAd = null
                notifyWeb("rewardedClosed", "")
                // Fix #3: Standardized callback
                callJavaScript("window.onRewardedClosed && window.onRewardedClosed()")
                // Cancel pending recovery since user returned naturally
                cancelForegroundRecovery()
                bringAppToForeground()
                loadRewardedAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded ad shown")
                notifyWeb("rewardedShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Rewarded show failed: ${error.message}")
                isShowingAd = false
                rewardedAd?.fullScreenContentCallback = null
                rewardedAd = null
                cancelForegroundRecovery()
                notifyWeb("rewardedShowFailed", error.message)
                // Fix #3: Standardized callback
                callJavaScript("window.onRewardedFailedToShow && window.onRewardedFailedToShow('${escapeJs(error.message)}', ${error.code})")
                loadRewardedAd()
            }
            
            override fun onAdImpression() {
                TestRigorLogger.logAdEvent("Rewarded impression")
            }
            
            // CRITICAL: Detect when ad is clicked (about to redirect to Play Store)
            override fun onAdClicked() {
                TestRigorLogger.logAdEvent("Rewarded clicked - scheduling foreground recovery")
                scheduleForegroundRecovery()
            }
        }
    }
    
    private fun createRewardedInterstitialCallback(): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded interstitial dismissed")
                isShowingAd = false
                rewardedInterstitialAd?.fullScreenContentCallback = null
                rewardedInterstitialAd = null
                notifyWeb("rewardedInterstitialClosed", "")
                // Cancel pending recovery since user returned naturally
                cancelForegroundRecovery()
                bringAppToForeground()
                loadRewardedInterstitialAd()
            }
            
            override fun onAdShowedFullScreenContent() {
                TestRigorLogger.logAdEvent("Rewarded interstitial shown")
                notifyWeb("rewardedInterstitialShown", "")
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                TestRigorLogger.logAdEvent("Rewarded interstitial show failed: ${error.message}")
                isShowingAd = false
                rewardedInterstitialAd?.fullScreenContentCallback = null
                rewardedInterstitialAd = null
                cancelForegroundRecovery()
                notifyWeb("rewardedInterstitialShowFailed", error.message)
                loadRewardedInterstitialAd()
            }
            
            override fun onAdImpression() {
                TestRigorLogger.logAdEvent("Rewarded interstitial impression")
            }
            
            // CRITICAL: Detect when ad is clicked (about to redirect to Play Store)
            override fun onAdClicked() {
                TestRigorLogger.logAdEvent("Rewarded interstitial clicked - scheduling foreground recovery")
                scheduleForegroundRecovery()
            }
        }
    }
    
    @JavascriptInterface
    fun isAdMobAvailable(): Boolean {
        return isInitialized
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        // Fix #24: Include freshness check
        return interstitialAd != null && isInterstitialFresh()
    }
    
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        // Fix #24: Include freshness check
        return rewardedAd != null && isRewardedFresh()
    }
    
    @JavascriptInterface
    fun isRewardedInterstitialReady(): Boolean {
        // Fix #24: Include freshness check
        return rewardedInterstitialAd != null && isRewardedInterstitialFresh()
    }
    
    @JavascriptInterface
    fun showInterstitial(placement: String): String {
        TestRigorLogger.logAdEvent("showInterstitial called, placement: $placement")
        
        // Fix #51: Check app foreground state
        if (!isAppInForeground) {
            TestRigorLogger.logAdEvent("App in background - cannot show ad")
            return "app_in_background"
        }
        
        // Fix #41: Prevent concurrent ads
        if (isShowingAd) {
            TestRigorLogger.logAdEvent("Already showing an ad")
            return "already_showing"
        }
        
        // Fix #36-39: Activity state checks
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logAdEvent("Cannot show interstitial - activity invalid")
            callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('Activity not available', -1)")
            return "activity_finishing"
        }
        
        // Fix #40: Window focus check
        if (!activity.hasWindowFocus()) {
            TestRigorLogger.logAdEvent("No window focus - cannot show ad")
            return "no_focus"
        }
        
        val ad = interstitialAd
        if (ad == null) {
            TestRigorLogger.logAdEvent("Interstitial not ready")
            callJavaScript("window.onInterstitialFailedToShow && window.onInterstitialFailedToShow('Ad not ready', -1)")
            loadInterstitialAd()
            return "not_ready"
        }
        
        // Fix #24: Check if ad is stale
        if (!isInterstitialFresh()) {
            TestRigorLogger.logAdEvent("Interstitial stale - reloading")
            interstitialAd = null
            loadInterstitialAd()
            return "stale"
        }
        
        isShowingAd = true
        mainHandler.post {
            ad.show(activity)
        }
        return "showing"
    }
    
    @JavascriptInterface
    fun showRewarded(placement: String): String {
        TestRigorLogger.logAdEvent("showRewarded called, placement: $placement")
        
        // Fix #51: Check app foreground state
        if (!isAppInForeground) {
            TestRigorLogger.logAdEvent("App in background - cannot show ad")
            return "app_in_background"
        }
        
        // Fix #41: Prevent concurrent ads
        if (isShowingAd) {
            TestRigorLogger.logAdEvent("Already showing an ad")
            return "already_showing"
        }
        
        // Fix #36-39: Activity state checks
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logAdEvent("Cannot show rewarded - activity invalid")
            callJavaScript("window.onRewardedFailedToShow && window.onRewardedFailedToShow('Activity not available', -1)")
            return "activity_finishing"
        }
        
        // Fix #40: Window focus check
        if (!activity.hasWindowFocus()) {
            TestRigorLogger.logAdEvent("No window focus - cannot show ad")
            return "no_focus"
        }
        
        val ad = rewardedAd
        if (ad == null) {
            TestRigorLogger.logAdEvent("Rewarded ad not ready")
            callJavaScript("window.onRewardedFailedToShow && window.onRewardedFailedToShow('Ad not ready', -1)")
            loadRewardedAd()
            return "not_ready"
        }
        
        // Fix #24: Check if ad is stale
        if (!isRewardedFresh()) {
            TestRigorLogger.logAdEvent("Rewarded stale - reloading")
            rewardedAd = null
            loadRewardedAd()
            return "stale"
        }
        
        isShowingAd = true
        mainHandler.post {
            ad.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type.replace("'", "\\'")
                TestRigorLogger.logAdEvent("User earned reward: $rewardAmount $rewardType")
                
                notifyWeb("rewardedEarned", rewardAmount.toString())
                // Fix #3: Standardized callback
                callJavaScript("window.onRewardEarned && window.onRewardEarned('$rewardType', $rewardAmount)")
            }
        }
        return "showing"
    }
    
    @JavascriptInterface
    fun showRewardedInterstitial(placement: String): String {
        TestRigorLogger.logAdEvent("showRewardedInterstitial called, placement: $placement")
        
        // Fix #51: Check app foreground state
        if (!isAppInForeground) {
            return "app_in_background"
        }
        
        // Fix #41: Prevent concurrent ads
        if (isShowingAd) {
            return "already_showing"
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            return "activity_finishing"
        }
        
        if (!activity.hasWindowFocus()) {
            return "no_focus"
        }
        
        val ad = rewardedInterstitialAd
        if (ad == null) {
            loadRewardedInterstitialAd()
            return "not_ready"
        }
        
        if (!isRewardedInterstitialFresh()) {
            rewardedInterstitialAd = null
            loadRewardedInterstitialAd()
            return "stale"
        }
        
        isShowingAd = true
        mainHandler.post {
            ad.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type.replace("'", "\\'")
                TestRigorLogger.logAdEvent("User earned reward from interstitial: $rewardAmount $rewardType")
                notifyWeb("rewardedInterstitialEarned", rewardAmount.toString())
                callJavaScript("window.onRewardEarned && window.onRewardEarned('$rewardType', $rewardAmount)")
            }
        }
        return "showing"
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
        callJavaScript(js)
    }
    
    // Fix #11: Safe JavaScript call with WebView checks
    private fun callJavaScript(script: String) {
        val webView = webViewRef.get()
        
        if (webView == null) {
            Log.w(TAG, "WebView is null - cannot call: $script")
            return
        }
        
        // Fix #11: Check if WebView is attached to window
        if (!webView.isAttachedToWindow) {
            Log.w(TAG, "WebView not attached - cannot call: $script")
            return
        }
        
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity finishing - cannot call: $script")
            return
        }
        
        mainHandler.post {
            try {
                // CRITICAL: Do NOT use "javascript:" prefix with evaluateJavascript()
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "JS result: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "evaluateJavascript failed: ${e.message}")
            }
        }
    }
    
    private fun escapeJs(str: String?): String {
        return str?.replace("'", "\\'")?.replace("\n", "\\n") ?: ""
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
        
        // Unregister lifecycle observer
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove lifecycle observer: ${e.message}")
        }
        
        // Clear all pending handlers
        mainHandler.removeCallbacksAndMessages(null)
        interstitialTimeoutRunnable = null
        rewardedTimeoutRunnable = null
        rewardedInterstitialTimeoutRunnable = null
        pendingForegroundRunnable = null
        
        // Clear ad references
        interstitialAd?.fullScreenContentCallback = null
        rewardedAd?.fullScreenContentCallback = null
        rewardedInterstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        rewardedAd = null
        rewardedInterstitialAd = null
        
        // Clear WebView reference
        webViewRef.clear()
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
