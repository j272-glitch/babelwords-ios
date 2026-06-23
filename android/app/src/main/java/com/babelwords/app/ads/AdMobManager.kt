package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Loads and shows AdMob interstitial and rewarded ads.
 * Fires events back to the web app via the eventCallback.
 *
 * eventCallback(eventType: String, data: String?) is called on the main thread.
 *
 * Event contract (must match adbridge.js window.onAdBridgeEvent switch):
 *   interstitialLoaded   → ad ready to show
 *   interstitialShown    → impression registered
 *   interstitialClosed   → dismissed
 *   interstitialFailed   → load or show error (data = error message)
 *   rewardedLoaded       → rewarded ad ready
 *   rewardedShown        → rewarded impression
 *   rewardEarned         → user earned reward (data = amount as string, e.g. "30")
 *   rewardedClosed       → rewarded dismissed
 *   rewardedFailed       → rewarded error
 */
class AdMobManager(
    private val context: Context,
    private val eventCallback: (eventType: String, data: String?) -> Unit,
) {
    private val TAG = "AdMobManager"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var initialized = false
    private var loadingInterstitial = false
    private var loadingRewarded = false

    // Auto-show is for Firebase Test Lab ONLY (so the test video captures ads actually
    // appearing). The "hasAutoShown*" guards are set only AFTER an ad actually renders
    // (onAdShowedFullScreenContent), so a transient show failure can still retry. The
    // attempt counters bound those retries so we can never loop forever.
    private var hasAutoShownInterstitial = false
    private var hasAutoShownRewarded = false
    private var pendingAutoShowRewarded = false
    private var interstitialAutoShowAttempts = 0
    private var rewardedAutoShowAttempts = 0

    /**
     * True only when the app is running inside Firebase Test Lab. Test Lab sets the
     * system setting "firebase.test.lab" to "true" on its devices; it is never set on
     * a real user's phone, so this whole auto-show + test-ad path can never run in prod.
     */
    private val isTestLab: Boolean by lazy {
        runCatching {
            "true".equals(
                Settings.System.getString(context.contentResolver, "firebase.test.lab"),
                ignoreCase = true
            )
        }.getOrDefault(false)
    }

    // In Test Lab we use Google's official sample ad units. Showing real ad units under
    // automated tests can be flagged as invalid traffic, so we never do that.
    private val interstitialAdUnitId by lazy {
        if (isTestLab) TEST_INTERSTITIAL_ID
        else context.getString(com.babelwords.app.R.string.admob_interstitial_id)
    }
    private val rewardedAdUnitId by lazy {
        if (isTestLab) TEST_REWARDED_ID
        else context.getString(com.babelwords.app.R.string.admob_rewarded_id)
    }

    init {
        initialized = true
        preloadInterstitial()
        preloadRewarded()
    }

    fun isInitialized() = initialized
    fun isInterstitialReady() = interstitialAd != null
    fun isRewardedReady() = rewardedAd != null

    fun preloadInterstitial() {
        if (interstitialAd != null || loadingInterstitial) return
        loadingInterstitial = true
        Log.d(TAG, "Loading interstitial…")

        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✅ Interstitial loaded")
                    interstitialAd = ad
                    loadingInterstitial = false
                    setupInterstitialCallbacks(ad)
                    eventCallback("interstitialLoaded", null)
                    maybeAutoShowInterstitial()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial load failed: ${error.message}")
                    interstitialAd = null
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
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
                eventCallback("interstitialShown", null)
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                eventCallback("interstitialClosed", null)
                preloadInterstitial()
                maybeAutoShowRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial show failed: ${error.message}")
                interstitialAd = null
                eventCallback("interstitialFailed", error.message)
                preloadInterstitial()
            }
        }
    }

    fun showInterstitial(activity: Activity) {
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "showInterstitial called but ad not ready — loading now")
            eventCallback("interstitialFailed", "not_loaded")
            preloadInterstitial()
            return
        }
        ad.show(activity)
    }

    fun preloadRewarded() {
        if (rewardedAd != null || loadingRewarded) return
        loadingRewarded = true
        Log.d(TAG, "Loading rewarded ad…")

        val request = AdRequest.Builder().build()
        RewardedAd.load(context, rewardedAdUnitId, request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "✅ Rewarded loaded")
                    rewardedAd = ad
                    loadingRewarded = false
                    setupRewardedCallbacks(ad)
                    eventCallback("rewardedLoaded", null)
                    if (pendingAutoShowRewarded) {
                        pendingAutoShowRewarded = false
                        maybeAutoShowRewarded()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Rewarded load failed: ${error.message}")
                    rewardedAd = null
                    loadingRewarded = false
                    eventCallback("rewardedFailed", error.message)
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
                eventCallback("rewardedShown", null)
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded dismissed")
                eventCallback("rewardedClosed", null)
                preloadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded show failed: ${error.message}")
                rewardedAd = null
                eventCallback("rewardedFailed", error.message)
                // Test Lab: retry the auto-show once the reloaded ad is ready.
                if (isTestLab) pendingAutoShowRewarded = true
                preloadRewarded()
            }
        }
    }

    fun showRewarded(activity: Activity) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "showRewarded called but ad not ready")
            eventCallback("rewardedFailed", "not_loaded")
            preloadRewarded()
            return
        }
        ad.show(activity) { rewardItem ->
            val amount = rewardItem.amount.takeIf { it > 0 } ?: 30
            Log.d(TAG, "💰 Reward earned: $amount")
            eventCallback("rewardEarned", amount.toString())
        }
    }

    /** The host Activity, only if it is safe to show a full-screen ad right now. */
    private fun showableActivity(): Activity? {
        val activity = context as? Activity ?: return null
        if (activity.isFinishing || activity.isDestroyed) return null
        return activity
    }

    /**
     * Test Lab only: show the interstitial automatically, as soon as it loads, so the
     * recorded test video captures an ad actually appearing. `hasAutoShownInterstitial` is
     * only set once the ad truly renders (in onAdShowedFullScreenContent), so a transient
     * show failure still retries — bounded by MAX_AUTO_SHOW_ATTEMPTS so it can't loop.
     */
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

    /**
     * Test Lab only: after the interstitial closes, show the rewarded ad. If it has not
     * finished loading yet, mark it pending and show it the moment it loads. Like the
     * interstitial, the guard is set only on a real render and retries are bounded.
     */
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

    companion object {
        // Google's official sample ad units — used ONLY inside Firebase Test Lab.
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
        // Bounds auto-show retries on transient show failures so we can never loop.
        private const val MAX_AUTO_SHOW_ATTEMPTS = 3
    }
}
