package com.lingualink.linguagt.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.inmobi.ads.*
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import org.json.JSONObject
import java.lang.ref.WeakReference

object InMobiConfig {
    const val ACCOUNT_ID = "9d81516c365f4acaa52f1fc627370cf9"
    
    object Placements {
        const val BANNER: Long = 10000582111L
        const val INTERSTITIAL: Long = 10000582110L
        const val REWARDED: Long = 10000582112L
    }
    
    object Privacy {
        var gdprConsent: Boolean = true
        var ccpaDoNotSell: Boolean = false
    }
    
    const val TEST_MODE = false
    const val LOG_TAG = "InMobiAds"
}

class InMobiAdBridge(
    activity: Activity,
    private val webView: WebView,
    private val rootLayout: FrameLayout? = null
) : DefaultLifecycleObserver {

    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)
    
    private var isInitialized = false
    
    private var bannerAd: InMobiBanner? = null
    private var interstitialAd: InMobiInterstitial? = null
    private var rewardedAd: InMobiInterstitial? = null
    
    private var isBannerVisible = false
    private var isInterstitialReady = false
    private var isRewardedReady = false
    
    private var totalImpressions = 0
    private val targetImpressions = 10000
    
    companion object {
        private const val TAG = "InMobiAdBridge"
    }

    fun initialize() {
        log("Initializing InMobi Ad Bridge...")
        initializeInMobiSdk()
    }

    private fun initializeInMobiSdk() {
        val activity = activityRef.get() ?: run {
            log("Activity is null, cannot initialize SDK", isError = true)
            return
        }
        
        val consentObject = JSONObject().apply {
            put("gdpr_consent_available", InMobiConfig.Privacy.gdprConsent)
            put("gdpr", if (InMobiConfig.Privacy.gdprConsent) "1" else "0")
        }
        
        InMobiSdk.init(activity, InMobiConfig.ACCOUNT_ID, consentObject, object : SdkInitializationListener {
            override fun onInitializationComplete(error: Error?) {
                if (error == null) {
                    isInitialized = true
                    log("InMobi SDK initialized successfully")
                    log("Account: ${InMobiConfig.ACCOUNT_ID}")
                    
                    if (InMobiConfig.TEST_MODE) {
                        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG)
                        log("TEST MODE ON - Impressions will NOT count!")
                    } else {
                        log("PRODUCTION MODE - Impressions will count toward 10K")
                    }
                    
                    preloadInterstitial()
                    preloadRewarded()
                    
                    sendEventToWeb("sdkInitialized", "")
                } else {
                    log("SDK initialization failed: ${error.message}", isError = true)
                    sendEventToWeb("sdkInitFailed", "", error.message)
                }
            }
        })
    }

    fun updateConsent(gdprConsent: Boolean, ccpaDoNotSell: Boolean) {
        InMobiConfig.Privacy.gdprConsent = gdprConsent
        InMobiConfig.Privacy.ccpaDoNotSell = ccpaDoNotSell
        log("Consent updated - GDPR: $gdprConsent, CCPA DoNotSell: $ccpaDoNotSell")
    }

    @JavascriptInterface
    fun showInterstitial(): Boolean {
        val activity = activityRef.get() ?: return false
        if (!isInitialized || !isInterstitialReady) {
            log("Interstitial not ready")
            return false
        }
        
        activity.runOnUiThread {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    log("Activity not in valid state for ad display")
                    return@runOnUiThread
                }
                interstitialAd?.show()
            } catch (e: Exception) {
                log("Error showing interstitial: ${e.message}", isError = true)
            }
        }
        return true
    }

    @JavascriptInterface
    fun showRewarded(): Boolean {
        val activity = activityRef.get() ?: return false
        if (!isInitialized || !isRewardedReady) {
            log("Rewarded not ready")
            return false
        }
        
        activity.runOnUiThread {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    log("Activity not in valid state for ad display")
                    return@runOnUiThread
                }
                rewardedAd?.show()
            } catch (e: Exception) {
                log("Error showing rewarded: ${e.message}", isError = true)
            }
        }
        return true
    }

    @JavascriptInterface
    fun isInterstitialReady(): Boolean = isInterstitialReady

    @JavascriptInterface
    fun isRewardedAdReady(): Boolean = isRewardedReady

    @JavascriptInterface
    fun getImpressionCount(): Int = totalImpressions

    @JavascriptInterface
    fun getImpressionProgress(): Float = 
        (totalImpressions.toFloat() / targetImpressions) * 100

    @JavascriptInterface
    fun getDiagnostics(): String {
        return JSONObject().apply {
            put("sdkInitialized", isInitialized)
            put("interstitialReady", isInterstitialReady)
            put("rewardedReady", isRewardedReady)
            put("bannerVisible", isBannerVisible)
            put("totalImpressions", totalImpressions)
            put("targetImpressions", targetImpressions)
            put("progressPercent", getImpressionProgress())
            put("testMode", InMobiConfig.TEST_MODE)
            put("accountId", InMobiConfig.ACCOUNT_ID)
        }.toString()
    }

    private fun preloadInterstitial() {
        if (!isInitialized) {
            log("SDK not initialized", isError = true)
            return
        }
        
        val activity = activityRef.get() ?: return
        
        interstitialAd = InMobiInterstitial(
            activity,
            InMobiConfig.Placements.INTERSTITIAL,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Interstitial loaded")
                    isInterstitialReady = true
                    sendEventToWeb("adLoaded", "interstitial")
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("Interstitial failed: ${status.message}", isError = true)
                    isInterstitialReady = false
                    sendEventToWeb("adFailed", "interstitial", status.message)
                    
                    runOnUiThreadDelayed(5000) { preloadInterstitial() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Interstitial displayed")
                    recordImpression("interstitial")
                    sendEventToWeb("adImpression", "interstitial")
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    log("Interstitial clicked")
                    sendEventToWeb("adClicked", "interstitial")
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("Interstitial dismissed")
                    isInterstitialReady = false
                    sendEventToWeb("adClosed", "interstitial")
                    preloadInterstitial()
                }
            }
        )
        
        interstitialAd?.load()
    }

    private fun preloadRewarded() {
        if (!isInitialized) {
            log("SDK not initialized", isError = true)
            return
        }
        
        val activity = activityRef.get() ?: return
        
        rewardedAd = InMobiInterstitial(
            activity,
            InMobiConfig.Placements.REWARDED,
            object : InterstitialAdEventListener() {
                override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Rewarded loaded")
                    isRewardedReady = true
                    sendEventToWeb("adLoaded", "rewarded")
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("Rewarded failed: ${status.message}", isError = true)
                    isRewardedReady = false
                    sendEventToWeb("adFailed", "rewarded", status.message)
                    
                    runOnUiThreadDelayed(5000) { preloadRewarded() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Rewarded displayed")
                    recordImpression("rewarded")
                    sendEventToWeb("adImpression", "rewarded")
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    log("Rewarded clicked")
                    sendEventToWeb("adClicked", "rewarded")
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("Rewarded dismissed")
                    isRewardedReady = false
                    sendEventToWeb("adClosed", "rewarded")
                    preloadRewarded()
                }
                
                override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: MutableMap<Any, Any>?) {
                    log("Reward earned: $rewards")
                    sendEventToWeb("rewardEarned", "rewarded", null, rewards)
                }
            }
        )
        
        rewardedAd?.load()
    }

    fun showBanner(position: String = "bottom") {
        if (!isInitialized) {
            log("SDK not initialized", isError = true)
            return
        }
        
        val activity = activityRef.get() ?: return
        val container = rootLayout ?: return
        
        hideBanner()
        
        activity.runOnUiThread {
            bannerAd = InMobiBanner(activity, InMobiConfig.Placements.BANNER).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(activity, 50)
                ).apply {
                    gravity = if (position == "top") Gravity.TOP else Gravity.BOTTOM
                }
                
                setListener(object : BannerAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                        log("Banner loaded")
                        isBannerVisible = true
                        sendEventToWeb("adLoaded", "banner")
                    }
                    
                    override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                        log("Banner failed: ${status.message}", isError = true)
                        isBannerVisible = false
                        sendEventToWeb("adFailed", "banner", status.message)
                    }
                    
                    override fun onAdDisplayed(ad: InMobiBanner) {
                        log("Banner displayed")
                        recordImpression("banner")
                        sendEventToWeb("adImpression", "banner")
                    }
                    
                    override fun onAdClicked(ad: InMobiBanner, params: MutableMap<Any, Any>?) {
                        log("Banner clicked")
                        sendEventToWeb("adClicked", "banner")
                    }
                })
                
                setEnableAutoRefresh(true)
                setRefreshInterval(45)
            }
            
            container.addView(bannerAd)
            bannerAd?.load()
        }
    }

    fun hideBanner() {
        val activity = activityRef.get() ?: return
        val container = rootLayout ?: return
        
        activity.runOnUiThread {
            bannerAd?.let { banner ->
                container.removeView(banner)
                banner.destroy()
            }
            bannerAd = null
            isBannerVisible = false
        }
    }

    private fun recordImpression(adType: String) {
        totalImpressions++
        log("Impression recorded: $adType (Total: $totalImpressions / $targetImpressions)")
        
        if (totalImpressions == targetImpressions) {
            log("TARGET REACHED: 10,000 impressions!")
            sendEventToWeb("targetReached", adType)
        }
    }

    private fun sendEventToWeb(type: String, adType: String, error: String? = null, data: MutableMap<Any, Any>? = null) {
        val webView = webViewRef.get() ?: return
        val activity = activityRef.get() ?: return
        
        val json = JSONObject().apply {
            put("type", type)
            put("adType", adType)
            error?.let { put("error", it) }
            data?.let { put("data", JSONObject(it.mapKeys { entry -> entry.key.toString() })) }
        }
        
        val escapedJson = json.toString().replace("'", "\\'")
        val js = "javascript:if(window.onInMobiAdEvent){window.onInMobiAdEvent('$escapedJson');}"
        
        activity.runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        activityRef.get()?.runOnUiThread(action)
    }

    private fun runOnUiThreadDelayed(delayMs: Long, action: () -> Unit) {
        activityRef.get()?.let { activity ->
            android.os.Handler(activity.mainLooper).postDelayed(action, delayMs)
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun log(message: String, isError: Boolean = false) {
        if (isError) {
            Log.e(TAG, message)
        } else {
            Log.d(TAG, message)
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        bannerAd?.destroy()
        bannerAd = null
        interstitialAd = null
        rewardedAd = null
        isBannerVisible = false
        isInterstitialReady = false
        isRewardedReady = false
    }

    fun release() {
        bannerAd?.destroy()
        bannerAd = null
        interstitialAd = null
        rewardedAd = null
    }
}
