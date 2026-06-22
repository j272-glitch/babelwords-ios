package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
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

    private val interstitialAdUnitId by lazy {
        context.getString(com.babelwords.app.R.string.admob_interstitial_id)
    }
    private val rewardedAdUnitId by lazy {
        context.getString(com.babelwords.app.R.string.admob_rewarded_id)
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
                interstitialAd = null
                eventCallback("interstitialShown", null)
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                eventCallback("interstitialClosed", null)
                preloadInterstitial()
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
}
