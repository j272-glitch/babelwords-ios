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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Loads and shows AdMob interstitial and rewarded ads.
 * Fires events back to the web app via the eventCallback.
 *
 * Upgraded with features from the LinguaVibe v46 guide:
 *   - Retry backoff with exponential delay (2s → 4s → 8s → 16s → 32s → 60s)
 *   - 30-second interstitial frequency cap
 *   - Duplicate-show guard (isShowingAd flag)
 *   - Pending show (deferred until Activity is ready / ad loads)
 *   - Activity lifecycle tracking (isActivityResumed)
 *   - Consent-aware ad requests (via ConsentManager)
 *   - Test Lab auto-show (unchanged, still safe)
 *
 * Event contract (backward-compatible; must match the web app bridge):
 *   interstitialLoaded / interstitialShown / interstitialClosed / interstitialFailed
 *   rewardedLoaded / rewardedShown / rewardEarned / rewardedClosed / rewardedFailed
 */
class AdMobManager(
    private val context: Context,
    private val eventCallback: (eventType: String, data: String?) -> Unit,
    private val getConsentManager: () -> ConsentManager? = { null },
) {
    private val TAG = "AdMobManager"

    // ==================== Constants ====================
    companion object {
        // Google's official sample ad units — used ONLY inside Firebase Test Lab.
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

        // Retry backoff: 2s → 4s → 8s → 16s → 32s → 60s
        private val RETRY_DELAYS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L)
        private const val MAX_AUTO_SHOW_ATTEMPTS = 3
        private const val MIN_INTERSTITIAL_INTERVAL_MS = 30_000L
    }

    // ==================== Ad State ====================
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var initialized = false
    private var loadingInterstitial = false
    private var loadingRewarded = false

    // ==================== Show Guards ====================
    private var isShowingAd = false
    private var isActivityResumed = true
    private var pendingShowInterstitial = false
    private var pendingShowRewarded = false
    private var lastInterstitialShowTime = 0L
    private var interstitialImpressionFired = false
    private var rewardedImpressionFired = false

    // ==================== Retry Counters ====================
    private var interstitialRetryCount = 0
    private var rewardedRetryCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val retryRunnables = mutableListOf<Runnable>()

    // ==================== Test Lab Auto-show ====================
    private var hasAutoShownInterstitial = false
    private var hasAutoShownRewarded = false
    private var pendingAutoShowRewarded = false
    private var interstitialAutoShowAttempts = 0
    private var rewardedAutoShowAttempts = 0

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
    private val rewardedAdUnitId by lazy {
        if (isTestLab) TEST_REWARDED_ID
        else context.getString(com.babelwords.app.R.string.admob_rewarded_id)
    }

    // ==================== Init ====================
    init {
        initialized = true
        preloadInterstitial()
        preloadRewarded()
    }

    fun isInitialized() = initialized
    fun isInterstitialReady() = interstitialAd != null
    fun isRewardedReady() = rewardedAd != null

    // ==================== Lifecycle ====================
    fun setActivityResumed(resumed: Boolean) {
        isActivityResumed = resumed
        if (resumed) {
            if (pendingShowInterstitial) {
                pendingShowInterstitial = false
                showInterstitial()
            }
            if (pendingShowRewarded) {
                pendingShowRewarded = false
                showRewarded()
            }
        }
    }

    // ==================== Interstitial ====================
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
                    interstitialRetryCount = 0
                    setupInterstitialCallbacks(ad)
                    eventCallback("interstitialLoaded", null)
                    maybeAutoShowInterstitial()
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "Interstitial load failed: ${error.message}")
                    interstitialAd = null
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
                    scheduleRetry(isInterstitial = true)
                }
            }
        )
    }

    private fun setupInterstitialCallbacks(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "✅ Interstitial shown")
                if (isTestLab) hasAutoShownInterstitial = true
                interstitialAd = null
                interstitialImpressionFired = true
                eventCallback("interstitialShown", null)
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                isShowingAd = false
                eventCallback("interstitialClosed", null)
                preloadInterstitial()
                maybeAutoShowRewarded()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial show failed: ${error.message}")
                isShowingAd = false
                interstitialAd = null
                eventCallback("interstitialFailed", error.message)
                preloadInterstitial()
            }
        }
    }

    fun showInterstitial(activity: Activity? = showableActivity()) {
        val safeActivity = activity ?: return
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "showInterstitial called but ad not ready — loading now")
            eventCallback("interstitialFailed", "not_loaded")
            pendingShowInterstitial = true
            preloadInterstitial()
            return
        }
        if (!isActivityResumed) {
            pendingShowInterstitial = true
            return
        }
        if (isShowingAd) {
            Log.w(TAG, "Already showing another ad")
            eventCallback("interstitialFailed", "already_showing")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShowTime < MIN_INTERSTITIAL_INTERVAL_MS) {
            Log.w(TAG, "Interstitial too frequent")
            eventCallback("interstitialFailed", "too_frequent")
            return
        }
        interstitialImpressionFired = false
        isShowingAd = true
        lastInterstitialShowTime = System.currentTimeMillis()
        try {
            ad.show(safeActivity)
        } catch (e: Exception) {
            isShowingAd = false
            lastInterstitialShowTime = 0L
            Log.w(TAG, "Interstitial show threw: ${e.message}")
            eventCallback("interstitialFailed", e.message ?: "show_exception")
            scheduleRetry(isInterstitial = true)
        }
    }

    // ==================== Rewarded ====================
    fun preloadRewarded() {
        if (rewardedAd != null || loadingRewarded) return
        loadingRewarded = true
        Log.d(TAG, "Loading rewarded ad…")

        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        RewardedAd.load(context, rewardedAdUnitId, request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "✅ Rewarded loaded")
                    rewardedAd = ad
                    loadingRewarded = false
                    rewardedRetryCount = 0
                    setupRewardedCallbacks(ad)
                    eventCallback("rewardedLoaded", null)
                    if (pendingAutoShowRewarded) {
                        pendingAutoShowRewarded = false
                        maybeAutoShowRewarded()
                    }
                    if (pendingShowRewarded) {
                        pendingShowRewarded = false
                        showRewarded()
                    }
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "Rewarded load failed: ${error.message}")
                    rewardedAd = null
                    loadingRewarded = false
                    eventCallback("rewardedFailed", error.message)
                    scheduleRetry(isInterstitial = false)
                }
            }
        )
    }

    private fun setupRewardedCallbacks(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "✅ Rewarded shown")
                if (isTestLab) hasAutoShownRewarded = true
                rewardedAd = null
                rewardedImpressionFired = true
                eventCallback("rewardedShown", null)
            }
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded dismissed")
                isShowingAd = false
                eventCallback("rewardedClosed", null)
                preloadRewarded()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded show failed: ${error.message}")
                isShowingAd = false
                rewardedAd = null
                eventCallback("rewardedFailed", error.message)
                if (isTestLab) pendingAutoShowRewarded = true
                preloadRewarded()
            }
        }
    }

    fun showRewarded(activity: Activity? = showableActivity()) {
        val safeActivity = activity ?: return
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "showRewarded called but ad not ready")
            eventCallback("rewardedFailed", "not_loaded")
            pendingShowRewarded = true
            preloadRewarded()
            return
        }
        if (!isActivityResumed) {
            pendingShowRewarded = true
            return
        }
        if (isShowingAd) {
            Log.w(TAG, "Already showing another ad")
            eventCallback("rewardedFailed", "already_showing")
            return
        }
        rewardedImpressionFired = false
        isShowingAd = true
        try {
            ad.show(safeActivity) { rewardItem ->
                val amount = rewardItem.amount.takeIf { it > 0 } ?: 30
                Log.d(TAG, "💰 Reward earned: $amount")
                eventCallback("rewardEarned", amount.toString())
            }
        } catch (e: Exception) {
            isShowingAd = false
            Log.w(TAG, "Rewarded show threw: ${e.message}")
            eventCallback("rewardedFailed", e.message ?: "show_exception")
            scheduleRetry(isInterstitial = false)
        }
    }

    // ==================== Retry ====================
    private fun scheduleRetry(isInterstitial: Boolean) {
        val count = if (isInterstitial) ++interstitialRetryCount else ++rewardedRetryCount
        val delay = if (count <= RETRY_DELAYS.size) RETRY_DELAYS[count - 1] else RETRY_DELAYS.last()
        Log.d(TAG, "Scheduling retry in ${delay}ms (attempt $count)")
        val runnable = Runnable {
            if (isInterstitial) preloadInterstitial() else preloadRewarded()
        }
        retryRunnables.add(runnable)
        handler.postDelayed(runnable, delay)
    }

    fun cancelRetries() {
        retryRunnables.forEach { handler.removeCallbacks(it) }
        retryRunnables.clear()
    }

    // ==================== Activity Safety ====================
    private fun showableActivity(): Activity? {
        val activity = context as? Activity ?: return null
        if (activity.isFinishing || activity.isDestroyed) return null
        return activity
    }

    // ==================== Test Lab Auto-show ====================
    private fun maybeAutoShowInterstitial() {
        if (!isTestLab || hasAutoShownInterstitial) return
        if (interstitialAutoShowAttempts >= MAX_AUTO_SHOW_ATTEMPTS) {
            Log.w(TAG, "🧪 Test Lab: interstitial auto-show gave up after $interstitialAutoShowAttempts attempts")
            return
        }
        val activity = showableActivity() ?: return
        interstitialAutoShowAttempts++
        Log.d(TAG, "🧪 Test Lab: auto-showing interstitial (attempt $interstitialAutoShowAttempts)")
        activity.runOnUiThread { showInterstitial(activity) }
    }

    private fun maybeAutoShowRewarded() {
        if (!isTestLab || hasAutoShownRewarded) return
        if (rewardedAutoShowAttempts >= MAX_AUTO_SHOW_ATTEMPTS) {
            Log.w(TAG, "🧪 Test Lab: rewarded auto-show gave up after $rewardedAutoShowAttempts attempts")
            return
        }
        if (rewardedAd == null) {
            pendingAutoShowRewarded = true
            return
        }
        val activity = showableActivity() ?: return
        rewardedAutoShowAttempts++
        Log.d(TAG, "🧪 Test Lab: auto-showing rewarded (attempt $rewardedAutoShowAttempts)")
        activity.runOnUiThread { showRewarded(activity) }
    }
}
