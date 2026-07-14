package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.babelwords.app.BabelWordsApplication
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified interstitial ad manager (v53 — single pipeline with AtomicBoolean guard,
 * pendingShow auto-show, pre-resume throttle, network callback, session-token safety).
 *
 * All interstitial load/show state lives here — no dual-pipeline duplication.
 *
 * Key methods:
 *   loadInterstitialAndShow()  — primary trigger (loads + shows)
 *   loadRewardedAndShow()      — backward-compat alias → loadInterstitialAndShow()
 *   preloadInterstitial()      — warm cache for faster show
 *   showInterstitial()         — show cached ad (or load+show with pendingShow)
 *   destroy()                  — set isDestroyed, cancel all pending work
 *
 * Events sent to the web app (window.onAdBridgeEvent):
 *   interstitialLoaded / interstitialShown / interstitialClosed / interstitialFailed
 */
class AdMobManager(
    private val context: Context,
    private val eventCallback: (eventType: String, data: String?) -> Unit,
    private val getConsentManager: () -> ConsentManager? = { null },
) {
    private val TAG = "AdMobManager"

    companion object {
        // Sample unit — used ONLY in Firebase Test Lab.
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val MAX_AUTO_SHOW_ATTEMPTS = 3
        // Foreground interstitial cooldown (5 minutes)
        private const val FOREGROUND_AD_COOLDOWN_MS = 5 * 60 * 1000L
        // Ad freshness: 45 min expiry, refresh if older than 40 min
        private const val AD_EXPIRY_MS = 45 * 60 * 1000L
        private const val AD_REFRESH_THRESHOLD_MS = 40 * 60 * 1000L
        // Load throttle: 30s minimum between loads
        private const val LOAD_THROTTLE_MS = 30_000L
        // Load timeout: 15s
        private const val LOAD_TIMEOUT_MS = 15_000L
        // Retry backoff: 5s → 60s
        private const val RETRY_INITIAL_MS = 5_000L
        private const val RETRY_MAX_MS = 60_000L

        // Cross-manager: shared flag so AppOpenAdManager can avoid showing
        // when interstitial is already showing (and vice versa).
        @Volatile
        var isAnyFullscreenAdShowing = false
    }

    // ==================== Lifecycle / Safety ====================
    @Volatile
    private var isDestroyed = false
    private var sessionId = AtomicLong(0)   // incremented on destroy; callbacks check it

    // ==================== Ad State ====================
    private var interstitialAd: InterstitialAd? = null
    private var loadTime = 0L
    private var lastLoadTime = 0L
    private var lastShowTime = 0L

    // v53: AtomicBoolean CAS guard prevents duplicate InterstitialAd.load() calls
    private val isLoading = AtomicBoolean(false)
    @Volatile
    private var isShowingAd = false

    // v53: pendingShow — set when show() finds stale/no ad; onAdLoaded auto-shows it
    @Volatile
    private var pendingShow = false

    @Volatile
    private var isActivityResumed = false

    // ==================== Retry / Timeout ====================
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null
    private var loadTimeoutRunnable: Runnable? = null

    // ==================== Foreground ====================
    private var lastForegroundAdTime = 0L
    private var wasBackgrounded = false
    private var backgroundTimestamp = 0L

    // ==================== Test Lab ====================
    private var hasAutoShownInterstitial = false
    private var interstitialAutoShowAttempts = 0

    // ==================== Network Callback ====================
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val isTestLab: Boolean by lazy {
        runCatching {
            "true".equals(
                Settings.System.getString(context.contentResolver, "firebase.test.lab"),
                ignoreCase = true
            )
        }.getOrDefault(false)
    }

    private val interstitialAdUnitId by lazy {
        if (isTestLab) TEST_INTERSTITIAL_ID
        else context.getString(com.babelwords.app.R.string.admob_interstitial_id)
    }

    // ==================== Freshness ====================
    private fun isFresh() = interstitialAd != null &&
        (System.currentTimeMillis() - loadTime) < AD_EXPIRY_MS

    private fun shouldRefresh() = interstitialAd != null &&
        (System.currentTimeMillis() - loadTime) >= AD_REFRESH_THRESHOLD_MS

    // ==================== Preload (public) ====================
    fun preloadInterstitial() {
        if (isDestroyed) return
        load(null, isPreload = true)
    }

    // ==================== Show ====================
    fun showInterstitial(activity: Activity) {
        if (isDestroyed) return
        val expectedSession = sessionId.get()

        if (isShowingAd || isAnyFullscreenAdShowing) {
            Log.w(TAG, "showInterstitial: already showing (local=$isShowingAd global=$isAnyFullscreenAdShowing)")
            eventCallback("interstitialFailed", "already_showing")
            return
        }

        // Frequency cap
        val now = System.currentTimeMillis()
        if (now - lastShowTime < 30_000L) {
            Log.w(TAG, "showInterstitial: frequency cap (30s)")
            eventCallback("interstitialFailed", "frequency_capped")
            return
        }

        // Staleness guard: refresh before showing
        if (interstitialAd != null && !isFresh()) {
            Log.w(TAG, "Ad stale — reloading with auto-show")
            eventCallback("interstitialFailed", "ad_stale_reloading")
            pendingShow = true
            load(activity)
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "No cached ad — triggering load with auto-show")
            eventCallback("interstitialFailed", "no_cached_ad")
            pendingShow = true
            load(activity)
            return
        }

        isShowingAd = true
        isAnyFullscreenAdShowing = true
        lastShowTime = now
        setAudioModeForAd(activity)
        try {
            ad.show(activity)
        } catch (e: Exception) {
            isShowingAd = false
            isAnyFullscreenAdShowing = false
            restoreAudioMode(activity)
            Log.w(TAG, "showInterstitial threw: ${e.message}")
            eventCallback("interstitialFailed", e.message ?: "show_exception")
            // Increment session to invalidate any pending callbacks
            sessionId.incrementAndGet()
            load(null)
        }
    }

    // ==================== 1-step load-and-show ====================
    fun loadInterstitialAndShow(activity: Activity) {
        if (isDestroyed) return
        val cached = interstitialAd
        if (cached != null && isFresh()) {
            Log.d(TAG, "Using cached interstitial")
            // Do NOT null interstitialAd here — let showInterstitial() consume it
            // and the callback null it after successful show.
            showInterstitial(activity)
            load(null)  // refresh after show
            return
        }
        Log.d(TAG, "No cached/fresh interstitial — loading fresh with auto-show")
        pendingShow = true
        load(activity)
    }

    // Backward-compat alias
    fun loadRewardedAndShow(activity: Activity) = loadInterstitialAndShow(activity)

    // ==================== Core Load (single-flight) ====================
    private fun load(activity: Activity?, isPreload: Boolean = false) {
        if (isDestroyed) return
        val expectedSession = sessionId.get()

        // v53: AtomicBoolean CAS guard
        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Already loading — skipping duplicate request")
            return
        }

        // Throttle: only advance lastLoadTime when activity is resumed
        // (pre-resume loads don't consume the throttle budget)
        val now = System.currentTimeMillis()
        if (isActivityResumed && now - lastLoadTime < LOAD_THROTTLE_MS) {
            Log.d(TAG, "Load throttled (${(now - lastLoadTime) / 1000}s since last)")
            isLoading.set(false)
            // Schedule a retry after throttle clears
            val delay = LOAD_THROTTLE_MS - (now - lastLoadTime)
            val runnable = Runnable { load(activity, isPreload) }
            retryRunnable = runnable
            handler.postDelayed(runnable, delay)
            return
        }

        if (isActivityResumed) {
            lastLoadTime = now
        }

        Log.d(TAG, "Loading interstitial…")
        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()

        // 15s timeout
        loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            if (isLoading.get()) {
                Log.w(TAG, "Interstitial load timed out after ${LOAD_TIMEOUT_MS}ms")
                isLoading.set(false)
                interstitialAd = null
                scheduleRetry(RETRY_INITIAL_MS, expectedSession)
            }
        }
        loadTimeoutRunnable = timeout
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS)

        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    if (isDestroyed || sessionId.get() != expectedSession) {
                        ad.fullScreenContentCallback = null
                        isLoading.set(false)
                        return
                    }
                    Log.d(TAG, "✅ Interstitial loaded")
                    interstitialAd = ad
                    loadTime = System.currentTimeMillis()
                    isLoading.set(false)
                    retryCount = 0
                    ad.fullScreenContentCallback = buildCallback(expectedSession)
                    eventCallback("interstitialLoaded", null)

                    // v53: auto-show if pendingShow was set
                    if (pendingShow) {
                        pendingShow = false
                        val act = activity ?: (context as? Activity)
                        if (act != null && !act.isFinishing && !act.isDestroyed) {
                            showInterstitial(act)
                        }
                    } else {
                        maybeAutoShowInterstitial()
                    }
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    if (isDestroyed || sessionId.get() != expectedSession) {
                        isLoading.set(false)
                        return
                    }
                    Log.w(TAG, "Interstitial load failed: ${error.message} (code=${error.code})")
                    interstitialAd = null
                    isLoading.set(false)
                    eventCallback("interstitialFailed", error.message)

                    val delay = when {
                        error.code == -2 && retryCount == 0 -> 0L
                        error.code == -2 -> (RETRY_INITIAL_MS * (1 shl retryCount)).coerceAtMost(RETRY_MAX_MS)
                        error.code == 3 -> 30_000L
                        else -> RETRY_INITIAL_MS
                    }
                    retryCount++
                    if (retryCount > 5) {
                        retryCount = 0
                        Log.w(TAG, "Retry exhausted after 5 attempts")
                        return
                    }
                    scheduleRetry(delay, expectedSession)
                }
            }
        )
    }

    // ==================== Callbacks ====================
    private fun buildCallback(expectedSession: Long) = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            if (sessionId.get() != expectedSession) return
            Log.d(TAG, "✅ Interstitial shown")
            isAnyFullscreenAdShowing = true
            if (isTestLab) hasAutoShownInterstitial = true
            interstitialAd = null
            eventCallback("interstitialShown", null)
        }
        override fun onAdDismissedFullScreenContent() {
            if (sessionId.get() != expectedSession) return
            Log.d(TAG, "Interstitial dismissed")
            isShowingAd = false
            isAnyFullscreenAdShowing = false
            restoreAudioMode(context as? Activity)
            eventCallback("interstitialClosed", null)
            load(null)
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            if (sessionId.get() != expectedSession) return
            Log.w(TAG, "Interstitial show failed: ${error.message}")
            isShowingAd = false
            isAnyFullscreenAdShowing = false
            restoreAudioMode(context as? Activity)
            interstitialAd = null
            eventCallback("interstitialFailed", error.message)
            load(null)
        }
    }

    // ==================== Retry ====================
    private fun scheduleRetry(delayMs: Long, expectedSession: Long) {
        retryRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!isDestroyed && sessionId.get() == expectedSession) {
                load(null)
            }
        }
        retryRunnable = runnable
        handler.postDelayed(runnable, delayMs)
        Log.d(TAG, "Retrying interstitial load in ${delayMs}ms (attempt $retryCount)")
    }

    fun cancelRetries() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
        loadTimeoutRunnable = null
    }

    // ==================== Foreground Ad ====================
    fun onActivityResumed(activity: Activity) {
        if (isDestroyed) return
        isActivityResumed = true
        val now = System.currentTimeMillis()

        // Execute any deferred pendingShow
        if (pendingShow) {
            pendingShow = false
            showInterstitial(activity)
            return
        }

        if (wasBackgrounded) {
            val inBackground = now - backgroundTimestamp
            wasBackgrounded = false
            if (inBackground >= 3_000L && now - lastForegroundAdTime >= FOREGROUND_AD_COOLDOWN_MS) {
                lastForegroundAdTime = now
                Log.d(TAG, "Showing foreground interstitial")
                loadInterstitialAndShow(activity)
                return
            }
        }
    }

    fun onActivityPaused() {
        isActivityResumed = false
        wasBackgrounded = true
        backgroundTimestamp = System.currentTimeMillis()
    }

    // ==================== Network Callback ====================
    fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                if (isDestroyed) return
                // Auto-reload if we have a stale ad or no ad at all.
                // CRITICAL: NetworkCallback runs on ConnectivityThread (background).
                // InterstitialAd.load() requires main thread — must dispatch via handler.
                if (interstitialAd == null || !isFresh()) {
                    Log.d(TAG, "Network available — auto-reloading interstitial (dispatch to main thread)")
                    handler.post { load(null) }
                }
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
        Log.d(TAG, "Network callback registered")
    }

    fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(callback)
            networkCallback = null
        }
    }

    // ==================== Test Lab Auto-show ====================
    private fun maybeAutoShowInterstitial() {
        if (!isTestLab) return
        // Fail-safe: gate auto-show on successful test-device registration.
        // If registration failed, suppress auto-show to avoid real-creative impressions
        // (invalid traffic). Prefer no impression over a risky one.
        if (!BabelWordsApplication.isTestDeviceRegistrationActive) {
            Log.w(TAG, "🧪 Test Lab: auto-show suppressed — test-device registration not active")
            return
        }
        if (hasAutoShownInterstitial) return
        if (interstitialAutoShowAttempts >= MAX_AUTO_SHOW_ATTEMPTS) {
            Log.w(TAG, "🧪 Test Lab: interstitial auto-show gave up")
            return
        }
        val activity = context as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        interstitialAutoShowAttempts++
        Log.i(TAG, "🯪 Test Lab: auto-showing interstitial (attempt $interstitialAutoShowAttempts)")
        activity.runOnUiThread { showInterstitial(activity) }
    }

    // ==================== Audio ====================
    private fun setAudioModeForAd(activity: Activity?) {
        val am = activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun restoreAudioMode(activity: Activity?) {
        val am = activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_NORMAL
    }

    // ==================== Destroy ====================
    fun destroy() {
        isDestroyed = true
        sessionId.incrementAndGet()   // invalidate all pending callbacks
        cancelRetries()
        unregisterNetworkCallback()
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        isLoading.set(false)
        pendingShow = false
        isAnyFullscreenAdShowing = false
        isShowingAd = false
        Log.d(TAG, "Destroyed")
    }

    // ==================== Queries ====================
    fun isInterstitialReady() = !isDestroyed && interstitialAd != null && isFresh()
    fun isRewardedReady() = isInterstitialReady()
    fun isInitialized() = !isDestroyed
}
