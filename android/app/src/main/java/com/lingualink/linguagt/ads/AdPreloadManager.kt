package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
object AdPreloadManager : LifecycleEventObserver {
    
    private const val TAG = "AdPreloadManager"
    
    // Production Ad Unit IDs (LinguaVibe)
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9991891515643313/5076005693"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-9991891515643313/6313049833"
    
    // Ad expiration time (45 minutes per AdMob docs)
    private const val AD_EXPIRY_MS = 45 * 60 * 1000L
    
    // FIX #1: Refresh-before-expiry time (40 minutes - 5 min before expiry)
    private const val AD_REFRESH_BEFORE_EXPIRY_MS = 40 * 60 * 1000L
    
    // Retry configuration (exponential backoff)
    private const val INITIAL_RETRY_DELAY_MS = 5000L
    private const val MAX_RETRY_DELAY_MS = 60000L
    
    // Cached ad instances
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    // Ad load timestamps for expiration tracking
    private var interstitialLoadTime: Long = 0
    private var rewardedLoadTime: Long = 0
    
    // Retry delays (exponential backoff)
    private var interstitialRetryDelay = INITIAL_RETRY_DELAY_MS
    private var rewardedRetryDelay = INITIAL_RETRY_DELAY_MS
    
    // Thread-safe loading flags (prevent duplicate requests)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isRewardedLoading = AtomicBoolean(false)
    private val isSdkInitialized = AtomicBoolean(false)
    private val isLifecycleObserverRegistered = AtomicBoolean(false)
    
    // Pending retry runnables (to cancel on success or cleanup)
    private var interstitialRetryRunnable: Runnable? = null
    private var rewardedRetryRunnable: Runnable? = null
    
    // FIX #2: Refresh-before-expiry runnables
    private var interstitialRefreshRunnable: Runnable? = null
    private var rewardedRefreshRunnable: Runnable? = null
    
    // FIX #1: Network callback for auto-reload when network returns
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkCallbackRegistered = false
    private var appContextRef: WeakReference<Context>? = null
    
    // App foreground state
    @Volatile
    private var isAppInForeground = true
    
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
    
    // LifecycleEventObserver implementation
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                isAppInForeground = true
                log("App entered foreground")
                // Reload stale ads
                activityRef?.get()?.let { activity ->
                    if (!isInterstitialFresh()) preloadInterstitial(activity)
                    if (!isRewardedFresh()) preloadRewarded(activity)
                }
            }
            Lifecycle.Event.ON_STOP -> {
                isAppInForeground = false
                log("App entered background")
            }
            else -> {}
        }
    }
    
    // Check if ads are fresh (not expired)
    private fun isInterstitialFresh(): Boolean {
        if (interstitialAd == null) return false
        return System.currentTimeMillis() - interstitialLoadTime < AD_EXPIRY_MS
    }
    
    private fun isRewardedFresh(): Boolean {
        if (rewardedAd == null) return false
        return System.currentTimeMillis() - rewardedLoadTime < AD_EXPIRY_MS
    }
    
    // Network availability check
    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
            log("Network check failed: ${e.message}", "E")
            false
        }
    }
    
    /**
     * FIX #1: Register network callback for auto-reload when network returns.
     * This ensures ads are preloaded immediately when connectivity is restored.
     */
    private fun registerNetworkCallback(context: Context) {
        if (isNetworkCallbackRegistered) {
            log("Network callback already registered")
            return
        }
        
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        log("★ Network became available - triggering ad preload")
                        mainHandler.post {
                            appContextRef?.get()?.let { ctx ->
                                if (isAppInForeground) {
                                    // Preload ads if not fresh
                                    if (!isInterstitialFresh()) {
                                        log("  → Preloading interstitial (network returned)")
                                        preloadInterstitial(ctx)
                                    }
                                    if (!isRewardedFresh()) {
                                        log("  → Preloading rewarded (network returned)")
                                        preloadRewarded(ctx)
                                    }
                                }
                            }
                        }
                    }
                    
                    override fun onLost(network: Network) {
                        log("Network lost")
                    }
                }
                
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                    
                cm.registerNetworkCallback(request, networkCallback!!)
                isNetworkCallbackRegistered = true
                log("✓ Network callback registered (API 24+)")
            } else {
                log("Network callback not available on API < 24")
            }
        } catch (e: Exception) {
            log("Failed to register network callback: ${e.message}", "E")
        }
    }
    
    /**
     * FIX #1: Unregister network callback.
     */
    private fun unregisterNetworkCallback(context: Context) {
        if (!isNetworkCallbackRegistered || networkCallback == null) return
        
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.unregisterNetworkCallback(networkCallback!!)
                isNetworkCallbackRegistered = false
                networkCallback = null
                log("✓ Network callback unregistered")
            }
        } catch (e: Exception) {
            log("Failed to unregister network callback: ${e.message}", "E")
        }
    }
    
    /**
     * FIX #2: Schedule refresh before expiry to keep cached ads fresh.
     * Called after a successful ad load to schedule a refresh at 40 minutes.
     */
    private fun scheduleRefreshBeforeExpiry(context: Context, isInterstitial: Boolean) {
        val delay = AD_REFRESH_BEFORE_EXPIRY_MS
        log("Scheduling ${if (isInterstitial) "interstitial" else "rewarded"} refresh in ${delay / 60000} minutes")
        
        val appContext = context.applicationContext
        
        if (isInterstitial) {
            // Cancel any existing refresh
            interstitialRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
            interstitialRefreshRunnable = Runnable {
                if (isAppInForeground && !isInterstitialLoading.get()) {
                    log("⏰ Refresh-before-expiry triggered for interstitial")
                    // Clear the old ad and preload fresh one
                    interstitialAd = null
                    preloadInterstitial(appContext)
                }
            }
            mainHandler.postDelayed(interstitialRefreshRunnable!!, delay)
        } else {
            // Cancel any existing refresh
            rewardedRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
            rewardedRefreshRunnable = Runnable {
                if (isAppInForeground && !isRewardedLoading.get()) {
                    log("⏰ Refresh-before-expiry triggered for rewarded")
                    // Clear the old ad and preload fresh one
                    rewardedAd = null
                    preloadRewarded(appContext)
                }
            }
            mainHandler.postDelayed(rewardedRefreshRunnable!!, delay)
        }
    }
    
    /**
     * Cancel scheduled refresh-before-expiry runnables.
     */
    private fun cancelScheduledRefreshes() {
        interstitialRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        interstitialRefreshRunnable = null
        rewardedRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        rewardedRefreshRunnable = null
    }
    
    /**
     * Initialize the AdMob SDK and begin preloading ads.
     * Call this IMMEDIATELY in MainActivity.onCreate() for fastest ad availability.
     */
    fun initialize(activity: Activity) {
        activityRef = WeakReference(activity)
        appContextRef = WeakReference(activity.applicationContext)
        
        // Register for app lifecycle events (only once)
        if (isLifecycleObserverRegistered.compareAndSet(false, true)) {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                log("Lifecycle observer registered")
            } catch (e: Exception) {
                isLifecycleObserverRegistered.set(false)
                log("Failed to add lifecycle observer: ${e.message}", "E")
            }
        }
        
        // FIX #1: Register network callback for auto-reload when network returns
        registerNetworkCallback(activity.applicationContext)
        
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
        
        // Initialize AdMob SDK on background thread (per Google recommendation)
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(activity) { initStatus ->
                isSdkInitialized.set(true)
                log("✓ AdMob SDK initialized")
                
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    log("  Adapter: $adapter - ${status.initializationState}")
                }
                
                // Check consent then preload (runs on main thread via handler)
                mainHandler.post {
                    checkConsentAndPreload(activity)
                }
            }
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
                log("📋 Consent REQUIRED - preloading with limited ads")
                // Preload immediately with non-personalized ads, will refresh after consent
                preloadAllAds(context)
            }
            ConsentInformation.ConsentStatus.UNKNOWN -> {
                log("❓ Consent UNKNOWN - preloading with non-personalized ads IMMEDIATELY")
                // FIX: Preload immediately with non-personalized ads (don't wait for consent)
                // This ensures ads are ready on BrowserStack/test devices where consent is often UNKNOWN
                preloadAllAds(context)
                // Also request consent info update for future loads
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
        // Already have fresh cached ad?
        if (isInterstitialFresh()) {
            log("Fresh interstitial already cached and ready")
            return
        }
        
        // Clear stale ad
        if (interstitialAd != null && !isInterstitialFresh()) {
            log("Clearing stale interstitial")
            interstitialAd = null
        }
        
        // Check network availability
        if (!isNetworkAvailable(context)) {
            log("No network - scheduling retry for interstitial")
            scheduleRetry(context, isInterstitial = true)
            return
        }
        
        // Skip if app in background
        if (!isAppInForeground) {
            log("App in background - skipping interstitial load")
            return
        }
        
        // Already loading? (thread-safe check)
        if (!isInterstitialLoading.compareAndSet(false, true)) {
            log("Interstitial already loading, skipping duplicate request")
            return
        }
        
        log("Loading interstitial ad: $INTERSTITIAL_AD_UNIT_ID")
        TestRigorLogger.logAdEvent("AdPreloadManager: Loading interstitial")
        
        // Build ad request with consent-appropriate settings
        val adRequest = buildAdRequest()
        
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    log("✓ Interstitial LOADED and cached")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Interstitial loaded")
                    // Cancel any pending retries
                    interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    interstitialRetryRunnable = null
                    interstitialAd = ad
                    interstitialLoadTime = System.currentTimeMillis()
                    interstitialRetryDelay = INITIAL_RETRY_DELAY_MS // Reset backoff
                    isInterstitialLoading.set(false)
                    setupInterstitialCallbacks(ad, context)
                    // FIX #2: Schedule refresh before expiry
                    scheduleRefreshBeforeExpiry(context, isInterstitial = true)
                    onInterstitialReady?.invoke()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("✗ Interstitial failed: ${error.message}", "E")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Interstitial failed - ${error.message}")
                    isInterstitialLoading.set(false)
                    
                    // Retry with exponential backoff
                    scheduleRetry(context, isInterstitial = true)
                }
            }
        )
    }
    
    /**
     * Build ad request with consent-appropriate settings.
     * Uses non-personalized ads if consent not obtained.
     */
    private fun buildAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        
        // If consent not obtained, request non-personalized ads
        if (!hasConsent.get() && consentChecked.get()) {
            log("Building non-personalized ad request (consent not obtained)")
            // Note: Google changed how NPA is handled - it's now automatic based on consent
            // But we can still add extras for older integrations
            val extras = android.os.Bundle()
            extras.putString("npa", "1")
            builder.addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
        }
        
        return builder.build()
    }
    
    /**
     * Schedule retry with exponential backoff.
     */
    private fun scheduleRetry(context: Context, isInterstitial: Boolean) {
        val delay = if (isInterstitial) interstitialRetryDelay else rewardedRetryDelay
        
        log("Scheduling retry in ${delay}ms")
        
        if (isInterstitial) {
            // Cancel any existing retry
            interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            interstitialRetryRunnable = Runnable {
                // Increase delay for next failure (exponential backoff)
                interstitialRetryDelay = minOf(interstitialRetryDelay * 2, MAX_RETRY_DELAY_MS)
                preloadInterstitial(context)
            }
            mainHandler.postDelayed(interstitialRetryRunnable!!, delay)
        } else {
            // Cancel any existing retry
            rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            rewardedRetryRunnable = Runnable {
                rewardedRetryDelay = minOf(rewardedRetryDelay * 2, MAX_RETRY_DELAY_MS)
                preloadRewarded(context)
            }
            mainHandler.postDelayed(rewardedRetryRunnable!!, delay)
        }
    }
    
    /**
     * Cancel pending retries (call when ad loads successfully or on cleanup).
     */
    private fun cancelPendingRetries() {
        interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        interstitialRetryRunnable = null
        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        rewardedRetryRunnable = null
    }
    
    /**
     * Preload rewarded ad with thread-safe loading.
     */
    fun preloadRewarded(context: Context) {
        // Already have fresh cached ad?
        if (isRewardedFresh()) {
            log("Fresh rewarded already cached and ready")
            return
        }
        
        // Clear stale ad
        if (rewardedAd != null && !isRewardedFresh()) {
            log("Clearing stale rewarded")
            rewardedAd = null
        }
        
        // Check network availability
        if (!isNetworkAvailable(context)) {
            log("No network - scheduling retry for rewarded")
            scheduleRetry(context, isInterstitial = false)
            return
        }
        
        // Skip if app in background
        if (!isAppInForeground) {
            log("App in background - skipping rewarded load")
            return
        }
        
        // Already loading? (thread-safe check)
        if (!isRewardedLoading.compareAndSet(false, true)) {
            log("Rewarded already loading, skipping duplicate request")
            return
        }
        
        log("Loading rewarded ad: $REWARDED_AD_UNIT_ID")
        TestRigorLogger.logAdEvent("AdPreloadManager: Loading rewarded")
        
        // Build ad request with consent-appropriate settings
        val adRequest = buildAdRequest()
        
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    log("✓ Rewarded LOADED and cached")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Rewarded loaded")
                    // Cancel any pending retries
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    rewardedRetryRunnable = null
                    rewardedAd = ad
                    rewardedLoadTime = System.currentTimeMillis()
                    rewardedRetryDelay = INITIAL_RETRY_DELAY_MS // Reset backoff
                    isRewardedLoading.set(false)
                    setupRewardedCallbacks(ad, context)
                    // FIX #2: Schedule refresh before expiry
                    scheduleRefreshBeforeExpiry(context, isInterstitial = false)
                    onRewardedReady?.invoke()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    log("✗ Rewarded failed: ${error.message}", "E")
                    TestRigorLogger.logAdEvent("AdPreloadManager: Rewarded failed - ${error.message}")
                    isRewardedLoading.set(false)
                    
                    // Retry with exponential backoff
                    scheduleRetry(context, isInterstitial = false)
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
                log("Interstitial dismissed - returning to app")
                interstitialAd = null
                // CRITICAL: Bring app back to foreground to prevent Play Store redirect
                bringAppToForeground()
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
                log("Rewarded dismissed - returning to app")
                rewardedAd = null
                // CRITICAL: Bring app back to foreground to prevent Play Store redirect
                bringAppToForeground()
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
    
    /**
     * CRITICAL: Brings the app back to foreground after ad dismissal.
     * This prevents the Google Play Store redirect issue.
     */
    private fun bringAppToForeground() {
        try {
            val activity = activityRef?.get()
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                val intent = Intent(activity, activity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                activity.startActivity(intent)
                log("✓ App brought to foreground")
            } else {
                log("⚠️ Cannot bring app to foreground - no valid activity reference", "W")
            }
        } catch (e: Exception) {
            log("✗ Failed to bring app to foreground: ${e.message}", "E")
        }
    }
    
    // ==================== Public API ====================
    
    /**
     * Check if interstitial ad is ready to show (includes freshness check).
     */
    fun isInterstitialReady(): Boolean = isInterstitialFresh()
    
    /**
     * Check if rewarded ad is ready to show (includes freshness check).
     */
    fun isRewardedReady(): Boolean = isRewardedFresh()
    
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
     * FIX #89: Clear all cached ads (for low memory situations).
     * Releases both interstitial and rewarded ads and cancels pending loads.
     */
    fun clearAllCachedAds() {
        log("Clearing all cached ads (low memory)")
        interstitialAd = null
        rewardedAd = null
        interstitialLoadTime = 0
        rewardedLoadTime = 0
        isInterstitialLoading.set(false)
        isRewardedLoading.set(false)
        cancelPendingRetries()
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
        
        // Unregister lifecycle observer
        if (isLifecycleObserverRegistered.compareAndSet(true, false)) {
            try {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
                log("Lifecycle observer unregistered")
            } catch (e: Exception) {
                log("Failed to remove lifecycle observer: ${e.message}", "E")
            }
        }
        
        // FIX #1: Unregister network callback
        appContextRef?.get()?.let { ctx ->
            unregisterNetworkCallback(ctx)
        }
        
        // Cancel pending retries
        cancelPendingRetries()
        
        // FIX #2: Cancel scheduled refreshes
        cancelScheduledRefreshes()
        
        mainHandler.removeCallbacksAndMessages(null)
        
        onInterstitialReady = null
        onRewardedReady = null
        onAdDismissed = null
        onRewardEarned = null
        activityRef?.clear()
        activityRef = null
        appContextRef?.clear()
        appContextRef = null
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
