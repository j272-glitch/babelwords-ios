package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Ad Preload Manager - Eliminates WebView Bridge Latency
 * 
 * Strategy: Trigger ad requests directly in native layer IMMEDIATELY on activity creation,
 * running in PARALLEL with WebView loading. This saves 300-1500ms of bridge communication time.
 * 
 * Flow:
 * 1. MainActivity.onCreate() → AdPreloadManager.initialize()
 * 2. Ads preload immediately (parallel to WebView)
 * 3. When button pressed → ad already cached & ready
 * 4. After ad shown → automatically preload next
 */
object AdPreloadManager {
    
    private const val TAG = "AdPreloadManager"
    
    // Production Ad Unit IDs (LinguaVibe)
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9991891515643313/5076005693"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-9991891515643313/6313049833"
    
    // Cached ad instances
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    // Thread-safe loading flags (prevent duplicate requests)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isRewardedLoading = AtomicBoolean(false)
    private val isSdkInitialized = AtomicBoolean(false)
    
    // Consent tracking
    private var consentInfo: ConsentInformation? = null
    private var hasConsent = AtomicBoolean(false)
    private var consentChecked = AtomicBoolean(false)
    
    // Callbacks for external notification
    var onInterstitialReady: (() -> Unit)? = null
    var onRewardedReady: (() -> Unit)? = null
    var onAdDismissed: (() -> Unit)? = null
    var onRewardEarned: ((type: String, amount: Int) -> Unit)? = null
    
    // Activity reference (weak to prevent leaks)
    private var activityRef: WeakReference<Activity>? = null
    
    // Main thread handler for retry scheduling
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Retry delay on failure (30 seconds)
    private const val RETRY_DELAY_MS = 30000L
    
    /**
     * Initialize the AdMob SDK and begin preloading ads.
     * Call this IMMEDIATELY in MainActivity.onCreate() for fastest ad availability.
     */
    fun initialize(activity: Activity) {
        activityRef = WeakReference(activity)
        
        if (isSdkInitialized.get()) {
            log("SDK already initialized, checking consent and preloading")
            checkConsentAndPreload(activity)
            return
        }
        
        log("═".repeat(50))
        log("NATIVE AD PRELOAD MANAGER INITIALIZING")
        log("═".repeat(50))
        
        // DIAGNOSTIC LOGGING - Debug test ads vs production
        logDiagnosticInfo(activity)
        
        // Initialize consent info
        consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        log("Consent status: ${consentInfo?.consentStatus}")
        
        // Initialize AdMob SDK
        MobileAds.initialize(activity) { initStatus ->
            isSdkInitialized.set(true)
            log("✓ AdMob SDK initialized")
            
            initStatus.adapterStatusMap.forEach { (adapter, status) ->
                log("  Adapter: $adapter - ${status.initializationState}")
            }
            
            // Check consent then preload
            checkConsentAndPreload(activity)
        }
    }
    
    /**
     * Check consent status and preload ads if consent obtained.
     */
    private fun checkConsentAndPreload(context: Context) {
        val consent = consentInfo ?: run {
            log("⚠️ No consent info available, preloading anyway")
            preloadAllAds(context)
            return
        }
        
        when (consent.consentStatus) {
            ConsentInformation.ConsentStatus.OBTAINED -> {
                log("✅ Consent already OBTAINED")
                hasConsent.set(true)
                consentChecked.set(true)
                preloadAllAds(context)
            }
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                log("ℹ️ Consent NOT_REQUIRED (non-EEA)")
                hasConsent.set(true)
                consentChecked.set(true)
                preloadAllAds(context)
            }
            ConsentInformation.ConsentStatus.REQUIRED -> {
                log("📋 Consent REQUIRED - waiting for consent flow")
                // Don't preload yet - wait for consent from AdBridge
            }
            ConsentInformation.ConsentStatus.UNKNOWN -> {
                log("❓ Consent UNKNOWN - requesting consent info")
                requestConsentInfo(context)
            }
            else -> {
                log("⚠️ Consent status: ${consent.consentStatus}")
                preloadAllAds(context)
            }
        }
    }
    
    /**
     * Request consent info update.
     */
    private fun requestConsentInfo(context: Context) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        
        consentInfo?.requestConsentInfoUpdate(
            context as? Activity ?: return,
            params,
            {
                log("Consent info updated: ${consentInfo?.consentStatus}")
                checkConsentAndPreload(context)
            },
            { error ->
                log("Consent info update failed: ${error.message}")
                // Proceed with preload anyway
                preloadAllAds(context)
            }
        )
    }
    
    /**
     * Called when consent is obtained (from AdBridge callback).
     */
    fun onConsentObtained(gdprConsent: Boolean) {
        log("═".repeat(50))
        log("CONSENT OBTAINED: gdprConsent=$gdprConsent")
        log("═".repeat(50))
        
        hasConsent.set(gdprConsent)
        consentChecked.set(true)
        
        if (gdprConsent) {
            activityRef?.get()?.let { activity ->
                log("✓ Consent granted - preloading ads with REAL ad serving")
                preloadAllAds(activity)
            }
        }
    }
    
    /**
     * Preload all ad types (interstitial + rewarded).
     */
    fun preloadAllAds(context: Context) {
        log("Preloading all ads...")
        TestRigorLogger.logAdEvent("AdPreloadManager: Preloading all ads")
        preloadInterstitial(context)
        preloadRewarded(context)
    }
    
    /**
     * Preload interstitial ad with thread-safe loading.
     */
    fun preloadInterstitial(context: Context) {
        // Already have cached ad?
        if (interstitialAd != null) {
            log("Interstitial already cached and ready")
            return
        }
        
        // Already loading? (thread-safe check)
        if (!isInterstitialLoading.compareAndSet(false, true)) {
            log("Interstitial already loading, skipping duplicate request")
            return
        }
        
        log("Loading interstitial ad: $INTERSTITIAL_AD_UNIT_ID")
        TestRigorLogger.logAdEvent("AdPreloadManager: Loading interstitial")
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    log("✓ Interstitial LOADED and cached")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Interstitial loaded")
                    interstitialAd = ad
                    isInterstitialLoading.set(false)
                    setupInterstitialCallbacks(ad, context)
                    onInterstitialReady?.invoke()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("✗ Interstitial failed: ${error.message}", "E")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Interstitial failed - ${error.message}")
                    isInterstitialLoading.set(false)
                    
                    // Retry after delay
                    mainHandler.postDelayed({
                        preloadInterstitial(context)
                    }, RETRY_DELAY_MS)
                }
            }
        )
    }
    
    /**
     * Preload rewarded ad with thread-safe loading.
     */
    fun preloadRewarded(context: Context) {
        // Already have cached ad?
        if (rewardedAd != null) {
            log("Rewarded already cached and ready")
            return
        }
        
        // Already loading? (thread-safe check)
        if (!isRewardedLoading.compareAndSet(false, true)) {
            log("Rewarded already loading, skipping duplicate request")
            return
        }
        
        log("Loading rewarded ad: $REWARDED_AD_UNIT_ID")
        TestRigorLogger.logAdEvent("AdPreloadManager: Loading rewarded")
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    log("✓ Rewarded LOADED and cached")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Rewarded loaded")
                    rewardedAd = ad
                    isRewardedLoading.set(false)
                    setupRewardedCallbacks(ad, context)
                    onRewardedReady?.invoke()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("✗ Rewarded failed: ${error.message}", "E")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Rewarded failed - ${error.message}")
                    isRewardedLoading.set(false)
                    
                    // Retry after delay
                    mainHandler.postDelayed({
                        preloadRewarded(context)
                    }, RETRY_DELAY_MS)
                }
            }
        )
    }
    
    /**
     * Setup interstitial callbacks for auto-reload after display.
     * IMPORTANT: Use WeakReference to avoid lifecycle leaks after configuration changes.
     */
    private fun setupInterstitialCallbacks(ad: InterstitialAd, context: Context) {
        // Use applicationContext to avoid Activity lifecycle issues
        val appContext = context.applicationContext
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                log("Interstitial dismissed - preloading next")
                interstitialAd = null
                onAdDismissed?.invoke()
                // Use applicationContext for reload to avoid dead Activity reference
                preloadInterstitial(appContext)
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                log("Interstitial failed to show: ${error.message}", "E")
                interstitialAd = null
                preloadInterstitial(appContext)
            }
            
            override fun onAdShowedFullScreenContent() {
                log("★★★ INTERSTITIAL IMPRESSION ★★★")
                TestRigorLogger.logAdEvent("AdPreloadManager: Interstitial impression")
            }
        }
    }
    
    /**
     * Setup rewarded callbacks for auto-reload after display.
     * IMPORTANT: Use WeakReference to avoid lifecycle leaks after configuration changes.
     */
    private fun setupRewardedCallbacks(ad: RewardedAd, context: Context) {
        // Use applicationContext to avoid Activity lifecycle issues
        val appContext = context.applicationContext
        
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                log("Rewarded dismissed - preloading next")
                rewardedAd = null
                onAdDismissed?.invoke()
                // Use applicationContext for reload to avoid dead Activity reference
                preloadRewarded(appContext)
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                log("Rewarded failed to show: ${error.message}", "E")
                rewardedAd = null
                preloadRewarded(appContext)
            }
            
            override fun onAdShowedFullScreenContent() {
                log("★★★ REWARDED IMPRESSION ★★★")
                TestRigorLogger.logAdEvent("AdPreloadManager: Rewarded impression")
            }
        }
    }
    
    // ==================== Public API ====================
    
    /**
     * Check if interstitial ad is ready to show.
     */
    fun isInterstitialReady(): Boolean = interstitialAd != null
    
    /**
     * Check if rewarded ad is ready to show.
     */
    fun isRewardedReady(): Boolean = rewardedAd != null
    
    /**
     * Get cached interstitial ad (for AdMobBridge to use).
     */
    fun getCachedInterstitial(): InterstitialAd? = interstitialAd
    
    /**
     * Get cached rewarded ad (for AdMobBridge to use).
     */
    fun getCachedRewarded(): RewardedAd? = rewardedAd
    
    /**
     * Show interstitial ad.
     * @return true if ad was shown, false if not ready
     */
    fun showInterstitial(activity: Activity): Boolean {
        val ad = interstitialAd ?: run {
            log("Interstitial not ready - triggering preload")
            preloadInterstitial(activity)
            return false
        }
        
        log("Showing interstitial ad")
        ad.show(activity)
        return true
    }
    
    /**
     * Show rewarded ad.
     * @return true if ad was shown, false if not ready
     */
    fun showRewarded(activity: Activity): Boolean {
        val ad = rewardedAd ?: run {
            log("Rewarded not ready - triggering preload")
            preloadRewarded(activity)
            return false
        }
        
        log("Showing rewarded ad")
        ad.show(activity) { reward ->
            log("User earned reward: ${reward.type} x ${reward.amount}")
            onRewardEarned?.invoke(reward.type, reward.amount)
        }
        return true
    }
    
    /**
     * Clear cached interstitial (for AdMobBridge after showing).
     */
    fun clearCachedInterstitial() {
        interstitialAd = null
    }
    
    /**
     * Clear cached rewarded (for AdMobBridge after showing).
     */
    fun clearCachedRewarded() {
        rewardedAd = null
    }
    
    /**
     * Get ad status for debugging.
     */
    fun getAdStatus(): Map<String, Any> = mapOf(
        "interstitialReady" to isInterstitialReady(),
        "rewardedReady" to isRewardedReady(),
        "sdkInitialized" to isSdkInitialized.get(),
        "hasConsent" to hasConsent.get(),
        "consentChecked" to consentChecked.get(),
        "interstitialLoading" to isInterstitialLoading.get(),
        "rewardedLoading" to isRewardedLoading.get()
    )
    
    /**
     * Clear callbacks to prevent activity leaks.
     * Call this in Activity.onDestroy() to release references.
     */
    fun clearCallbacks() {
        log("Clearing callbacks to prevent activity leaks")
        onInterstitialReady = null
        onRewardedReady = null
        onAdDismissed = null
        onRewardEarned = null
        activityRef?.clear()
        activityRef = null
    }
    
    /**
     * Update activity reference (call from Activity.onCreate after recreation).
     */
    fun updateActivityRef(activity: Activity) {
        activityRef = WeakReference(activity)
        log("Activity reference updated")
    }
    
    /**
     * DIAGNOSTIC LOGGING - Debug why test ads vs production ads
     * Logs all relevant configuration for troubleshooting.
     */
    private fun logDiagnosticInfo(context: Context) {
        log("╔══════════════════════════════════════════════════════════╗")
        log("║         AD DIAGNOSTIC INFO - CHECK FOR TEST ADS          ║")
        log("╠══════════════════════════════════════════════════════════╣")
        log("║ Package Name: ${context.packageName}")
        log("║ Interstitial Ad Unit: $INTERSTITIAL_AD_UNIT_ID")
        log("║ Rewarded Ad Unit: $REWARDED_AD_UNIT_ID")
        log("║ BuildConfig.DEBUG: ${com.lingualink.linguagt.BuildConfig.DEBUG}")
        log("║ Build Type: ${com.lingualink.linguagt.BuildConfig.BUILD_TYPE}")
        log("║ Version: ${com.lingualink.linguagt.BuildConfig.VERSION_NAME} (${com.lingualink.linguagt.BuildConfig.VERSION_CODE})")
        log("╠══════════════════════════════════════════════════════════╣")
        log("║ CHECKLIST IF SEEING TEST ADS:")
        log("║ 1. App published on Play Store? (test ads until published)")
        log("║ 2. Ad units created >48 hours ago?")
        log("║ 3. AdMob account approved?")
        log("║ 4. App linked in AdMob console?")
        log("║ 5. SHA-256 matches release keystore?")
        log("║ 6. No test device registered in AdMob console?")
        log("╚══════════════════════════════════════════════════════════╝")
        
        // Log consent info
        val consent = consentInfo
        if (consent != null) {
            log("Consent Status: ${consent.consentStatus}")
            log("Can Request Ads: ${consent.canRequestAds()}")
        } else {
            log("Consent Info: Not yet available")
        }
    }
    
    private fun log(message: String, level: String = "D") {
        val fullMessage = "[AdPreloadManager] $message"
        when (level) {
            "E" -> Log.e(TAG, fullMessage)
            "W" -> Log.w(TAG, fullMessage)
            else -> Log.d(TAG, fullMessage)
        }
    }
}
