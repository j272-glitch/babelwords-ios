
package com.lingualink.linguagt

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    
    companion object {
        private const val TAG = "AdBridge"
        private const val INTERSTITIAL_AD_UNIT = "ca-app-pub-9277938970928959/1473642031"
        private const val REWARDED_AD_UNIT = "ca-app-pub-9277938970928959/8777416980"
    }
    
    init {
        MobileAds.initialize(activity) { initStatus ->
            Log.d(TAG, "AdMob initialized: ${initStatus.adapterStatusMap}")
            loadInterstitialAd()
            loadRewardedAd()
        }
    }
    
    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            INTERSTITIAL_AD_UNIT,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    notifyWeb("interstitialLoaded", "true")
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    notifyWeb("interstitialFailed", error.message)
                }
            }
        )
    }
    
    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            REWARDED_AD_UNIT,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    rewardedAd = ad
                    notifyWeb("rewardedLoaded", "true")
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    notifyWeb("rewardedFailed", error.message)
                }
            }
        )
    }
    
    @JavascriptInterface
    fun showInterstitial() {
        activity.runOnUiThread {
            interstitialAd?.let { ad ->
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        notifyWeb("interstitialClosed", "")
                        loadInterstitialAd()
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e(TAG, "Interstitial failed to show: ${adError.message}")
                        notifyWeb("interstitialFailed", adError.message)
                        interstitialAd = null
                    }
                    
                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad shown")
                        notifyWeb("interstitialShown", "")
                        interstitialAd = null
                    }
                }
                ad.show(activity)
            } ?: run {
                Log.w(TAG, "Interstitial ad not ready")
                notifyWeb("interstitialFailed", "Ad not ready")
            }
        }
    }
    
    @JavascriptInterface
    fun showRewarded() {
        activity.runOnUiThread {
            rewardedAd?.let { ad ->
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Rewarded ad dismissed")
                        notifyWeb("rewardedClosed", "")
                        loadRewardedAd()
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                        notifyWeb("rewardedFailed", adError.message)
                        rewardedAd = null
                    }
                    
                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Rewarded ad shown")
                        notifyWeb("rewardedShown", "")
                        rewardedAd = null
                    }
                }
                
                ad.show(activity) { rewardItem ->
                    Log.d(TAG, "User earned reward: ${rewardItem.amount}")
                    notifyWeb("rewardedEarned", rewardItem.amount.toString())
                }
            } ?: run {
                Log.w(TAG, "Rewarded ad not ready")
                notifyWeb("rewardedFailed", "Ad not ready")
            }
        }
    }
    
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        val ready = interstitialAd != null
        Log.d(TAG, "Interstitial ready: $ready")
        return ready
    }
    
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        val ready = rewardedAd != null
        Log.d(TAG, "Rewarded ad ready: $ready")
        return ready
    }
    
    private fun notifyWeb(event: String, data: String) {
        val safeData = data.replace("'", "\\'")
        val js = "if (window.onAdBridgeEvent) { window.onAdBridgeEvent('$event', '$safeData'); }"
        activity.runOnUiThread {
            webView.evaluateJavascript(js) { result ->
                Log.d(TAG, "Notified web: $event -> $result")
            }
        }
    }
}
