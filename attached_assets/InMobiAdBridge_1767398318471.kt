package com.linguavibe.app.ads

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import java.util.concurrent.ConcurrentHashMap

/**
 * InMobiAdBridge.kt
 * 
 * Android WebView Container Integration for LinguaVibe
 * Supports: Banner, Interstitial, Rewarded ads
 * Target: 10,000 impressions for InMobi activation
 * 
 * Account: Global Translation (9d81516c365f4acaa52f1fc627370cf9)
 * 
 * Usage:
 * 1. Add to your MainActivity's onCreate after WebView setup:
 *    val adBridge = InMobiAdBridge(this, webView, rootLayout)
 *    lifecycle.addObserver(adBridge)
 * 
 * 2. Web app calls ads via window.AndroidAdBridge methods
 */

// =============================================================================
// CONFIGURATION - LINGUAVIBE PRODUCTION
// =============================================================================

object InMobiConfig {
    // Global Translation Account
    const val ACCOUNT_ID = "9d81516c365f4acaa52f1fc627370cf9"
    
    // LinguaVibe Placement IDs
    object Placements {
        const val BANNER: Long = 10000582111L        // LV-Banner
        const val INTERSTITIAL: Long = 10000582110L  // LV-Interstitial
        const val REWARDED: Long = 10000582112L      // LV-Rewarded
    }
    
    // Privacy settings - adjust based on user consent
    object Privacy {
        var gdprConsent: Boolean = true
        var coppaCompliance: Boolean = false
        var doNotSell: Boolean = false  // CCPA
    }
    
    // Set to false for production to count real impressions
    const val TEST_MODE = false
    const val LOG_TAG = "InMobiAdBridge"
}

// =============================================================================
// DATA CLASSES
// =============================================================================

data class AdEvent(
    val type: String,
    val placementId: String,
    val data: Map<String, Any>? = null,
    val error: String? = null
) {
    fun toJson(): String {
        val json = JSONObject().apply {
            put("type", type)
            put("placementId", placementId)
            data?.let { put("data", JSONObject(it)) }
            error?.let { put("error", it) }
        }
        return json.toString()
    }
}

// =============================================================================
// MAIN AD BRIDGE CLASS
// =============================================================================

class InMobiAdBridge(
    activity: Activity,
    webView: WebView,
    private val rootLayout: FrameLayout
) : DefaultLifecycleObserver {

    private val activityRef = WeakReference(activity)
    private val webViewRef = WeakReference(webView)
    
    private var isInitialized = false
    
    // Ad instances
    private var bannerAd: InMobiBanner? = null
    private var interstitialAd: InMobiInterstitial? = null
    private var rewardedAd: InMobiInterstitial? = null  // Rewarded uses InterstitialAd
    
    // Ready states
    private var isBannerLoaded = false
    private var isInterstitialReady = false
    private var isRewardedReady = false
    
    // Impression tracking
    private var totalImpressions = 0
    
    companion object {
        private const val TARGET_IMPRESSIONS = 10000
    }

    init {
        setupJavaScriptBridge()
        initializeInMobiSdk()
    }

    // =========================================================================
    // SDK INITIALIZATION
    // =========================================================================
    
    private fun initializeInMobiSdk() {
        val activity = activityRef.get() ?: return
        
        val consentObject = JSONObject().apply {
            put("gdpr_consent_available", InMobiConfig.Privacy.gdprConsent)
            put("gdpr", if (InMobiConfig.Privacy.gdprConsent) "1" else "0")
        }
        
        InMobiSdk.init(activity, InMobiConfig.ACCOUNT_ID, consentObject, object : SdkInitializationListener {
            override fun onInitializationComplete(error: Error?) {
                if (error == null) {
                    log("✅ InMobi SDK initialized - Account: ${InMobiConfig.ACCOUNT_ID}")
                    isInitialized = true
                    
                    if (InMobiConfig.TEST_MODE) {
                        InMobiSdk.setLogLevel(InMobiSdk.LogLevel.DEBUG)
                        log("⚠️ TEST MODE ENABLED - Impressions will NOT count toward 10K target")
                    }
                    
                    // Preload ads
                    preloadInterstitial()
                    preloadRewarded()
                } else {
                    log("❌ SDK init failed: ${error.message}", true)
                    sendEventToWeb(AdEvent("sdkInitFailed", "", error = error.message))
                }
            }
        })
    }

    // =========================================================================
    // JAVASCRIPT BRIDGE
    // =========================================================================
    
    @SuppressLint("JavascriptInterface")
    private fun setupJavaScriptBridge() {
        val webView = webViewRef.get() ?: return
        webView.addJavascriptInterface(AdJsBridge(), "AndroidAdBridge")
        log("JavaScript bridge registered as window.AndroidAdBridge")
    }
    
    inner class AdJsBridge {
        
        @JavascriptInterface
        fun loadBanner(placementId: String, position: String) {
            log("JS → loadBanner($placementId, $position)")
            runOnUiThread { loadBannerAd(position) }
        }
        
        @JavascriptInterface
        fun loadInterstitial(placementId: String) {
            log("JS → loadInterstitial($placementId)")
            runOnUiThread { preloadInterstitial() }
        }
        
        @JavascriptInterface
        fun showInterstitial(placementId: String) {
            log("JS → showInterstitial($placementId)")
            runOnUiThread { showInterstitialAd() }
        }
        
        @JavascriptInterface
        fun loadRewarded(placementId: String) {
            log("JS → loadRewarded($placementId)")
            runOnUiThread { preloadRewarded() }
        }
        
        @JavascriptInterface
        fun showRewarded(placementId: String) {
            log("JS → showRewarded($placementId)")
            runOnUiThread { showRewardedAd() }
        }
        
        @JavascriptInterface
        fun hideBanner(placementId: String) {
            log("JS → hideBanner($placementId)")
            runOnUiThread { hideBannerAd() }
        }
        
        @JavascriptInterface
        fun isAdReady(placementId: String): Boolean {
            return when {
                placementId.contains("interstitial", ignoreCase = true) -> isInterstitialReady
                placementId.contains("rewarded", ignoreCase = true) -> isRewardedReady
                else -> isBannerLoaded
            }
        }
        
        @JavascriptInterface
        fun getImpressionCount(): Int = totalImpressions
        
        @JavascriptInterface
        fun getImpressionProgress(): Float = 
            (totalImpressions.toFloat() / TARGET_IMPRESSIONS) * 100
    }
    
    private fun sendEventToWeb(event: AdEvent) {
        val webView = webViewRef.get() ?: return
        val escapedJson = event.toJson().replace("'", "\\'")
        val js = "javascript:if(window.onAdEvent){window.onAdEvent('$escapedJson');}"
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    // =========================================================================
    // BANNER ADS - Placement: 10000582111
    // =========================================================================
    
    private fun loadBannerAd(position: String) {
        if (!isInitialized) {
            log("SDK not initialized", true)
            sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.BANNER.toString(), 
                error = "SDK not initialized"))
            return
        }
        
        val activity = activityRef.get() ?: return
        
        // Remove existing banner
        hideBannerAd()
        
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
                    isBannerLoaded = true
                    sendEventToWeb(AdEvent("adLoaded", InMobiConfig.Placements.BANNER.toString()))
                }
                
                override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                    log("Banner failed: ${status.message}", true)
                    isBannerLoaded = false
                    sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.BANNER.toString(),
                        error = status.message))
                }
                
                override fun onAdDisplayed(ad: InMobiBanner) {
                    log("Banner displayed")
                    recordImpression("banner")
                    sendEventToWeb(AdEvent("adImpression", InMobiConfig.Placements.BANNER.toString()))
                }
                
                override fun onAdClicked(ad: InMobiBanner, params: MutableMap<Any, Any>?) {
                    log("Banner clicked")
                    sendEventToWeb(AdEvent("adClicked", InMobiConfig.Placements.BANNER.toString()))
                }
            })
            
            setEnableAutoRefresh(true)
            setRefreshInterval(45)  // 45 seconds for max impressions
        }
        
        rootLayout.addView(bannerAd)
        bannerAd?.load()
    }
    
    private fun hideBannerAd() {
        bannerAd?.let { banner ->
            rootLayout.removeView(banner)
            banner.destroy()
        }
        bannerAd = null
        isBannerLoaded = false
    }

    // =========================================================================
    // INTERSTITIAL ADS - Placement: 10000582110
    // =========================================================================
    
    private fun preloadInterstitial() {
        if (!isInitialized) {
            log("SDK not initialized", true)
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
                    sendEventToWeb(AdEvent("adLoaded", InMobiConfig.Placements.INTERSTITIAL.toString()))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("Interstitial failed: ${status.message}", true)
                    isInterstitialReady = false
                    sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.INTERSTITIAL.toString(),
                        error = status.message))
                    
                    // Retry after 5 seconds
                    runOnUiThreadDelayed(5000) { preloadInterstitial() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Interstitial displayed")
                    recordImpression("interstitial")
                    sendEventToWeb(AdEvent("adImpression", InMobiConfig.Placements.INTERSTITIAL.toString()))
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    log("Interstitial clicked")
                    sendEventToWeb(AdEvent("adClicked", InMobiConfig.Placements.INTERSTITIAL.toString()))
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("Interstitial dismissed")
                    isInterstitialReady = false
                    sendEventToWeb(AdEvent("adClosed", InMobiConfig.Placements.INTERSTITIAL.toString()))
                    preloadInterstitial()
                }
                
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    log("Interstitial display failed", true)
                    isInterstitialReady = false
                    sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.INTERSTITIAL.toString(),
                        error = "Display failed"))
                }
            }
        )
        
        interstitialAd?.load()
    }
    
    private fun showInterstitialAd(): Boolean {
        return if (isInterstitialReady && interstitialAd?.isReady == true) {
            interstitialAd?.show()
            true
        } else {
            log("Interstitial not ready", true)
            sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.INTERSTITIAL.toString(),
                error = "Not ready"))
            preloadInterstitial()
            false
        }
    }

    // =========================================================================
    // REWARDED ADS - Placement: 10000582112
    // =========================================================================
    
    private fun preloadRewarded() {
        if (!isInitialized) {
            log("SDK not initialized", true)
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
                    sendEventToWeb(AdEvent("adLoaded", InMobiConfig.Placements.REWARDED.toString()))
                }
                
                override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                    log("Rewarded failed: ${status.message}", true)
                    isRewardedReady = false
                    sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.REWARDED.toString(),
                        error = status.message))
                    
                    runOnUiThreadDelayed(5000) { preloadRewarded() }
                }
                
                override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                    log("Rewarded displayed")
                    recordImpression("rewarded")
                    sendEventToWeb(AdEvent("adImpression", InMobiConfig.Placements.REWARDED.toString()))
                }
                
                override fun onAdClicked(ad: InMobiInterstitial, params: MutableMap<Any, Any>?) {
                    log("Rewarded clicked")
                    sendEventToWeb(AdEvent("adClicked", InMobiConfig.Placements.REWARDED.toString()))
                }
                
                override fun onAdDismissed(ad: InMobiInterstitial) {
                    log("Rewarded dismissed")
                    isRewardedReady = false
                    sendEventToWeb(AdEvent("adClosed", InMobiConfig.Placements.REWARDED.toString()))
                    preloadRewarded()
                }
                
                override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                    log("Rewarded display failed", true)
                    isRewardedReady = false
                    sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.REWARDED.toString(),
                        error = "Display failed"))
                }
                
                override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: MutableMap<Any, Any>?) {
                    log("🎁 Reward earned!")
                    sendEventToWeb(AdEvent("adRewarded", InMobiConfig.Placements.REWARDED.toString(),
                        data = mapOf("rewards" to (rewards?.toString() ?: ""))))
                }
            }
        )
        
        rewardedAd?.load()
    }
    
    private fun showRewardedAd(): Boolean {
        return if (isRewardedReady && rewardedAd?.isReady == true) {
            rewardedAd?.show()
            true
        } else {
            log("Rewarded not ready", true)
            sendEventToWeb(AdEvent("adFailed", InMobiConfig.Placements.REWARDED.toString(),
                error = "Not ready"))
            preloadRewarded()
            false
        }
    }

    // =========================================================================
    // IMPRESSION TRACKING
    // =========================================================================
    
    private fun recordImpression(adType: String) {
        totalImpressions++
        persistImpressionCount()
        
        val progress = (totalImpressions.toFloat() / TARGET_IMPRESSIONS * 100).toInt()
        log("📊 Impression #$totalImpressions ($adType) - $progress% to 10K")
        
        if (totalImpressions % 100 == 0) {
            log("🎯 Milestone: $totalImpressions impressions!")
        }
        
        if (totalImpressions == TARGET_IMPRESSIONS) {
            log("🎉 TARGET REACHED: 10,000 impressions!")
        }
    }
    
    private fun persistImpressionCount() {
        val context = activityRef.get() ?: return
        context.getSharedPreferences("inmobi_ads", Context.MODE_PRIVATE)
            .edit()
            .putInt("total_impressions", totalImpressions)
            .putLong("last_impression_time", System.currentTimeMillis())
            .apply()
    }
    
    private fun loadImpressionCount() {
        val context = activityRef.get() ?: return
        totalImpressions = context.getSharedPreferences("inmobi_ads", Context.MODE_PRIVATE)
            .getInt("total_impressions", 0)
        log("Loaded impression count: $totalImpressions")
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================
    
    override fun onCreate(owner: LifecycleOwner) {
        loadImpressionCount()
    }
    
    override fun onResume(owner: LifecycleOwner) {
        bannerAd?.resume()
        if (!isInterstitialReady) preloadInterstitial()
        if (!isRewardedReady) preloadRewarded()
    }
    
    override fun onPause(owner: LifecycleOwner) {
        bannerAd?.pause()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        hideBannerAd()
        interstitialAd = null
        rewardedAd = null
        isInterstitialReady = false
        isRewardedReady = false
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================
    
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        activityRef.get()?.runOnUiThread(action)
    }
    
    private fun runOnUiThreadDelayed(delayMs: Long, action: () -> Unit) {
        webViewRef.get()?.postDelayed({ runOnUiThread(action) }, delayMs)
    }
    
    private fun log(message: String, isError: Boolean = false) {
        if (isError) {
            Log.e(InMobiConfig.LOG_TAG, message)
        } else {
            Log.d(InMobiConfig.LOG_TAG, message)
        }
    }
}

// =============================================================================
// GRADLE DEPENDENCIES - Add to app/build.gradle
// =============================================================================

/*
dependencies {
    // InMobi SDK
    implementation 'com.inmobi.monetization:inmobi-ads-kotlin:10.6.7'
    
    // Required dependencies
    implementation 'com.squareup.picasso:picasso:2.71828'
    implementation 'androidx.browser:browser:1.7.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
}

android {
    defaultConfig {
        // Required for InMobi
        multiDexEnabled true
    }
}
*/

// =============================================================================
// MANIFEST ADDITIONS - Add to AndroidManifest.xml
// =============================================================================

/*
<manifest>
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <application
        android:hardwareAccelerated="true">
        
        <activity
            android:name="com.inmobi.rendering.InMobiAdActivity"
            android:configChanges="keyboardHidden|orientation|keyboard|smallestScreenSize|screenSize|screenLayout"
            android:hardwareAccelerated="true"
            android:resizeableActivity="false"
            android:theme="@android:style/Theme.NoTitleBar" />
            
    </application>
</manifest>
*/

// =============================================================================
// PROGUARD RULES - Add to proguard-rules.pro
// =============================================================================

/*
-keepattributes SourceFile,LineNumberTable
-keep class com.inmobi.** { *; }
-dontwarn com.inmobi.**
-keep class com.squareup.picasso.** { *; }
-dontwarn com.squareup.picasso.**
*/

// =============================================================================
// USAGE IN MAINACTIVITY
// =============================================================================

/*
class MainActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var adBridge: InMobiAdBridge
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val rootLayout = findViewById<FrameLayout>(R.id.root_layout)
        webView = findViewById(R.id.webview)
        
        setupWebView()
        
        // Initialize InMobi Ad Bridge
        adBridge = InMobiAdBridge(this, webView, rootLayout)
        lifecycle.addObserver(adBridge)
        
        webView.loadUrl("https://linguagt.com")
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
        }
    }
}
*/
