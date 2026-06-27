package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Interstitial-only ad manager.
 *
 * Rewarded video and App Open ads have been removed per the v2026-06-26 ad
 * strategy change. All ad triggers now use static interstitials.
 *
 * Key methods:
 *   loadInterstitialAndShow()  — primary trigger (loads + shows)
 *   loadRewardedAndShow()      — backward-compat alias → loadInterstitialAndShow()
 *   preloadInterstitial()      — warm cache for faster show
 *
 * Foreground ad: shows an interstitial when the app resumes from background
 * (5-minute cooldown). This replaces the removed App Open ad format.
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
        // Sample unit — used ONLY in Firebase Test Lab (never on real devices).
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val MAX_AUTO_SHOW_ATTEMPTS = 3
        // Foreground interstitial cooldown (5 minutes)
        private const val FOREGROUND_AD_COOLDOWN_MS = 5 * 60 * 1000L
    }

    private var interstitialAd: InterstitialAd? = null
    private var loadingInterstitial = false
    private var isShowingAd = false

    // Retry state
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    // Foreground ad timing
    private var lastForegroundAdTime = 0L
    private var wasBackgrounded = false
    private var backgroundTimestamp = 0L

    // Test Lab auto-show (for Test Lab video proof only)
    private var hasAutoShownInterstitial = false
    private var interstitialAutoShowAttempts = 0

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

    // ==================== Preload ====================
    fun preloadInterstitial() {
        if (interstitialAd != null || loadingInterstitial) return
        loadingInterstitial = true
        Log.d(TAG, "Loading interstitial…")

        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✅ Interstitial loaded")
                    interstitialAd = ad
                    loadingInterstitial = false
                    retryCount = 0
                    ad.fullScreenContentCallback = buildCallback()
                    eventCallback("interstitialLoaded", null)
                    maybeAutoShowInterstitial()
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "Interstitial load failed: ${error.message} (code=${error.code})")
                    interstitialAd = null
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
                    scheduleRetry(error.code)
                }
            }
        )
    }

    // ==================== Show ====================
    fun showInterstitial(activity: Activity) {
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "showInterstitial: not ready — loading now")
            eventCallback("interstitialFailed", "not_loaded")
            preloadInterstitial()
            return
        }
        if (isShowingAd) {
            Log.w(TAG, "showInterstitial: already showing")
            eventCallback("interstitialFailed", "already_showing")
            return
        }
        isShowingAd = true
        try {
            ad.show(activity)
        } catch (e: Exception) {
            isShowingAd = false
            Log.w(TAG, "showInterstitial threw: ${e.message}")
            eventCallback("interstitialFailed", e.message ?: "show_exception")
            preloadInterstitial()
        }
    }

    // ==================== 1-step load-and-show ====================
    fun loadInterstitialAndShow(activity: Activity) {
        val cached = interstitialAd
        if (cached != null) {
            Log.d(TAG, "Using cached interstitial")
            interstitialAd = null
            showInterstitial(activity)
            preloadInterstitial() // load next one immediately
            return
        }
        // No cache — load fresh and show when ready
        Log.d(TAG, "No cached interstitial — loading fresh")
        if (loadingInterstitial) {
            // Already loading; the onAdLoaded callback will auto-show via pending flag
            // Not implemented here because we don't store pending activity ref
            // For simplicity: just tell the caller to try again shortly
            eventCallback("interstitialFailed", "loading_in_progress")
            return
        }
        // Load with auto-show on completion
        loadingInterstitial = true
        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    retryCount = 0
                    ad.fullScreenContentCallback = buildCallback()
                    interstitialAd = ad // store briefly
                    showInterstitial(activity)
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
                    scheduleRetry(error.code)
                }
            }
        )
    }

    // Backward-compat alias — web app still calls this for "Watch ad for more"
    fun loadRewardedAndShow(activity: Activity) = loadInterstitialAndShow(activity)

    // ==================== Callbacks ====================
    private fun buildCallback() = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "✅ Interstitial shown")
            if (isTestLab) hasAutoShownInterstitial = true
            interstitialAd = null
            eventCallback("interstitialShown", null)
        }
        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "Interstitial dismissed")
            isShowingAd = false
            eventCallback("interstitialClosed", null)
            preloadInterstitial()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.w(TAG, "Interstitial show failed: ${error.message}")
            isShowingAd = false
            interstitialAd = null
            eventCallback("interstitialFailed", error.message)
            preloadInterstitial()
        }
    }

    // ==================== Retry ====================
    private fun scheduleRetry(errorCode: Int) {
        retryRunnable?.let { handler.removeCallbacks(it) }

        val delayMs = when {
            errorCode == -2 && retryCount == 0 -> 0L      // first timeout: retry immediately
            errorCode == -2 -> (2_000L * retryCount).coerceAtMost(30_000L)
            errorCode == 3  -> 30_000L                     // no fill: wait 30s
            else             -> 5_000L
        }
        retryCount++
        if (retryCount > 5) {
            retryCount = 0
            Log.w(TAG, "Retry exhausted after 5 attempts")
            return
        }
        Log.d(TAG, "Retrying interstitial load in ${delayMs}ms (attempt $retryCount)")
        val runnable = Runnable { preloadInterstitial() }
        retryRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    fun cancelRetries() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    // ==================== Foreground Ad ====================
    fun onActivityResumed(activity: Activity) {
        val now = System.currentTimeMillis()

        // Show foreground interstitial if app was backgrounded long enough
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

        // If we were just loading, nothing else to do
    }

    fun onActivityPaused() {
        wasBackgrounded = true
        backgroundTimestamp = System.currentTimeMillis()
    }

    // ==================== Test Lab Auto-show ====================
    private fun maybeAutoShowInterstitial() {
        if (!isTestLab || hasAutoShownInterstitial) return
        if (interstitialAutoShowAttempts >= MAX_AUTO_SHOW_ATTEMPTS) {
            Log.w(TAG, "🧪 Test Lab: interstitial auto-show gave up")
            return
        }
        val activity = context as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        interstitialAutoShowAttempts++
        Log.d(TAG, "🧪 Test Lab: auto-showing interstitial (attempt $interstitialAutoShowAttempts)")
        activity.runOnUiThread { showInterstitial(activity) }
    }

    // ==================== Queries ====================
    fun isInterstitialReady() = interstitialAd != null
    fun isRewardedReady() = isInterstitialReady() // backward compat
    fun isInitialized() = true
}
