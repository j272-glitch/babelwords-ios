
package com.lingualink.linguagt.ads

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.lingualink.linguagt.TestRigorLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware ad manager that ensures ads are only shown when Activity is in proper state
 */
class LifecycleAwareAdManager(
    private val activity: AppCompatActivity
) : DefaultLifecycleObserver {
    
    private var isInForeground = true
    private var pendingInterstitialShow: (() -> Unit)? = null
    private var pendingRewardedShow: (() -> Unit)? = null
    
    init {
        activity.lifecycle.addObserver(this)
    }
    
    override fun onResume(owner: LifecycleOwner) {
        isInForeground = true
        TestRigorLogger.logAdEvent("✅ Activity RESUMED - ready to show ads")
        
        // Show pending ads if any
        pendingInterstitialShow?.invoke()
        pendingInterstitialShow = null
        
        pendingRewardedShow?.invoke()
        pendingRewardedShow = null
    }
    
    override fun onPause(owner: LifecycleOwner) {
        isInForeground = false
        TestRigorLogger.logAdEvent("⏸️ Activity PAUSED - will queue ads")
    }
    
    /**
     * Show interstitial with lifecycle checks
     */
    fun showInterstitial(ad: InterstitialAd, onShown: () -> Unit, onFailed: (String) -> Unit) {
        if (!isInForeground || !activity.hasWindowFocus()) {
            TestRigorLogger.logWarning("❌ Activity not in foreground or doesn't have focus, queuing interstitial")
            pendingInterstitialShow = { showInterstitial(ad, onShown, onFailed) }
            return
        }
        
        activity.lifecycleScope.launch(Dispatchers.Main) {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                TestRigorLogger.logAdEvent("🎬 Activity RESUMED - showing interstitial NOW")
                ad.show(activity)
                onShown()
            } else {
                TestRigorLogger.logWarning("❌ Activity not resumed (state: ${activity.lifecycle.currentState}), skipping ad")
                onFailed("Activity not in RESUMED state")
            }
        }
    }
    
    /**
     * Show rewarded ad with lifecycle checks
     */
    fun showRewarded(ad: RewardedAd, onShown: () -> Unit, onFailed: (String) -> Unit, onRewarded: (Int) -> Unit) {
        if (!isInForeground || !activity.hasWindowFocus()) {
            TestRigorLogger.logWarning("❌ Activity not in foreground or doesn't have focus, queuing rewarded ad")
            pendingRewardedShow = { showRewarded(ad, onShown, onFailed, onRewarded) }
            return
        }
        
        activity.lifecycleScope.launch(Dispatchers.Main) {
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                TestRigorLogger.logAdEvent("🎬 Activity RESUMED - showing rewarded ad NOW")
                ad.show(activity) { rewardItem ->
                    onRewarded(rewardItem.amount)
                }
                onShown()
            } else {
                TestRigorLogger.logWarning("❌ Activity not resumed (state: ${activity.lifecycle.currentState}), skipping ad")
                onFailed("Activity not in RESUMED state")
            }
        }
    }
    
    fun destroy() {
        activity.lifecycle.removeObserver(this)
        pendingInterstitialShow = null
        pendingRewardedShow = null
    }
}
