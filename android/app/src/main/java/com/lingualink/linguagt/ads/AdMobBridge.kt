package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

/**
 * AdMob-Only Bridge for LinguaVibe
 * 
 * Handles: Banner, Interstitial, Rewarded ads
 * Network: AdMob only (no InMobi)
 * 
 * Usage in MainActivity:
 *   val adBridge = AdMobBridge(this, webView)
 *   webView.addJavascriptInterface(adBridge, "AndroidAdBridge")
 */
class AdMobBridge(
    private val activity: Activity,
    private val webView: WebView
) {
    companion object {
        private const val TAG = "AdMobBridge"
        
        // Default placement IDs (can be overridden by JS)
        private const val DEFAULT_BANNER_ID = "ca-app-pub-9991891515643313/6878126239"
        private const val DEFAULT_INTERSTITIAL_ID = "ca-app-pub-9991891515643313/5076005693"
        private const val DEFAULT_REWARDED_ID = "ca-app-pub-9991891515643313/6313049833"
        
        // Foreground recovery delay after ad click
        private const val FOREGROUND_DELAY_MS = 1500L
    }

    // Current placement IDs (set from JavaScript or use defaults)
    private var currentBannerId = DEFAULT_BANNER_ID
    private var currentInterstitialId = DEFAULT_INTERSTITIAL_ID
    private var currentRewardedId = DEFAULT_REWARDED_ID

    // Ad objects
    private var bannerAd: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    // State tracking
    private var isBannerReady = false
    private var isInterstitialReady = false
    private var isRewardedReady = false
    
    // CONSENT TRACKING - Required for real ads (not test ads)
    private var hasConsent = false
    private var consentChecked = false
    private var consentInfo: ConsentInformation? = null

    // Impression counters
    private var totalImpressions = 0
    private var bannerImpressions = 0
    private var interstitialImpressions = 0
    private var rewardedImpressions = 0

    // Activity reference (weak to prevent leaks)
    private val activityRef = WeakReference(activity)
    
    // Foreground recovery system - handles Play Store redirect
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingForegroundRunnable: Runnable? = null
    private var adClickedTime: Long = 0

    init {
        log("═".repeat(50))
        log("ADMOB BRIDGE INITIALIZED")
        log("═".repeat(50))
        log("Activity: ${activity.javaClass.simpleName}")
        
        // Initialize consent info for checking consent before ad loads
        consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        log("Consent info initialized, status: ${consentInfo?.consentStatus}")
        
        initializeAdMob()
    }

    private fun log(message: String, level: String = "D") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val fullMessage = "[$timestamp] $message"
        when (level) {
            "E" -> Log.e(TAG, fullMessage)
            "W" -> Log.w(TAG, fullMessage)
            else -> Log.d(TAG, fullMessage)
        }
    }
    
    // ==================== Foreground Recovery ====================
    
    /**
     * Bring the app back to foreground after ad interaction.
     * This handles the Play Store redirect issue.
     */
    private fun bringAppToForeground() {
        val act = activityRef.get()
        if (act == null || act.isFinishing || act.isDestroyed) {
            log("Cannot bring to foreground - activity invalid")
            return
        }
        
        log("Bringing app to foreground")
        TestRigorLogger.logAdEvent("AdMobBridge: Bringing app to foreground")
        
        val intent = Intent(act, act::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }
        act.startActivity(intent)
    }
    
    /**
     * Schedule foreground recovery after ad click.
     * Called when user clicks on an ad that may redirect to Play Store.
     */
    private fun scheduleForegroundRecovery() {
        cancelForegroundRecovery()
        
        adClickedTime = System.currentTimeMillis()
        log("Ad clicked - scheduling foreground recovery in ${FOREGROUND_DELAY_MS}ms")
        TestRigorLogger.logAdEvent("AdMobBridge: Scheduling foreground recovery")
        
        pendingForegroundRunnable = Runnable {
            val timeSinceClick = System.currentTimeMillis() - adClickedTime
            log("Foreground recovery triggered after ${timeSinceClick}ms")
            bringAppToForeground()
        }
        
        mainHandler.postDelayed(pendingForegroundRunnable!!, FOREGROUND_DELAY_MS)
    }
    
    /**
     * Cancel any pending foreground recovery.
     * Called when user returns naturally or ad is dismissed.
     */
    private fun cancelForegroundRecovery() {
        pendingForegroundRunnable?.let {
            log("Cancelling pending foreground recovery")
            mainHandler.removeCallbacks(it)
            pendingForegroundRunnable = null
        }
    }

    private fun initializeAdMob() {
        log("Initializing AdMob SDK...")
        try {
            MobileAds.initialize(activity) { initStatus ->
                log("✓ AdMob SDK initialized")
                initStatus.adapterStatusMap.forEach { (adapter, status) ->
                    log("  Adapter: $adapter")
                    log("    State: ${status.initializationState}")
                    log("    Latency: ${status.latency}ms")
                }
                notifyJs("sdkInitialized", "admob", "sdk")
            }
        } catch (e: Exception) {
            log("✗ AdMob init exception: ${e.message}", "E")
            notifyJs("sdkInitFailed", "admob", "sdk", e.message)
        }
    }

    /**
     * Called by AdBridge when UMP consent is obtained.
     * This enables real ad serving (instead of test ads).
     */
    fun onConsentObtained(gdprConsent: Boolean) {
        log("═".repeat(50))
        log("CONSENT OBTAINED: gdprConsent=$gdprConsent")
        log("═".repeat(50))
        hasConsent = gdprConsent
        consentChecked = true
        
        // Now that consent is obtained, preload ads
        if (gdprConsent) {
            log("✓ Consent granted - preloading ads with REAL ad serving")
            loadInterstitialAd()
            loadRewardedAd()
        } else {
            log("⚠ Consent denied - ads may be limited")
        }
    }
    
    /**
     * Check if consent has been obtained before loading ads.
     */
    fun isConsentReady(): Boolean {
        return consentChecked && hasConsent
    }

    private fun isAdMobId(placementId: String): Boolean {
        return placementId.startsWith("ca-app-pub-")
    }

    /**
     * Check consent before loading ads - same flow as startup.
     * This ensures button-triggered ads get real ads (not test ads).
     */
    private fun checkConsentThenLoad(onConsentReady: () -> Unit) {
        val consent = consentInfo ?: run {
            log("⚠️ No consent info, loading anyway")
            onConsentReady()
            return
        }
        
        when (consent.consentStatus) {
            ConsentInformation.ConsentStatus.OBTAINED -> {
                log("✅ Consent already obtained")
                hasConsent = true
                consentChecked = true
                onConsentReady()
            }
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                log("ℹ️ Consent not required (non-EEA)")
                hasConsent = true
                consentChecked = true
                onConsentReady()
            }
            ConsentInformation.ConsentStatus.REQUIRED -> {
                log("📋 Consent required - showing form")
                UserMessagingPlatform.loadConsentForm(activity,
                    { form ->
                        form.show(activity) { error ->
                            if (error == null) {
                                log("✅ Consent form completed")
                                hasConsent = true
                                consentChecked = true
                                onConsentReady()
                            } else {
                                log("❌ Consent form error: ${error.message}")
                                // Still load (non-personalized ads)
                                onConsentReady()
                            }
                        }
                    },
                    { error ->
                        log("❌ Failed to load consent form: ${error.message}")
                        onConsentReady()
                    }
                )
            }
            else -> {
                log("ℹ️ Consent status: ${consent.consentStatus}")
                onConsentReady()
            }
        }
    }

    // ==================== JavaScript Interface Methods ====================

    @JavascriptInterface
    fun loadBanner(placementId: String, position: String) {
        // DISABLED: Banner ads disabled due to inappropriate content
        log("═".repeat(50))
        log("JS → loadBanner() - DISABLED (inappropriate content)")
        log("═".repeat(50))
        notifyJs("adFailed", "admob", "banner", "Banner ads disabled")
    }

    @JavascriptInterface
    fun hideBanner(placementId: String) {
        log("JS → hideBanner()")
        activity.runOnUiThread {
            bannerAd?.visibility = View.GONE
            log("Banner hidden")
        }
    }

    // Auto-show flag for 1-step interstitial (like banner)
    private var autoShowInterstitial = false

    @JavascriptInterface
    fun loadInterstitial(placementId: String) {
        log("JS → loadInterstitial (checking consent first)")
        // DEFAULT: Auto-show when loaded (1-step, like banner)
        activity.runOnUiThread {
            checkConsentThenLoad {
                loadInterstitialWithAutoShow(placementId, true)
            }
        }
    }

    @JavascriptInterface
    fun loadInterstitialOnly(placementId: String) {
        log("JS → loadInterstitialOnly (checking consent first)")
        // 2-step: Load only, call showInterstitial() separately
        activity.runOnUiThread {
            checkConsentThenLoad {
                loadInterstitialWithAutoShow(placementId, false)
            }
        }
    }

    // ORIGINAL INTERFACE: Auto-show on button press (1-step, like startup)
    @JavascriptInterface
    fun preloadInterstitial() {
        log("═".repeat(50))
        log("JS → preloadInterstitial() [ORIGINAL - checking consent first]")
        log("═".repeat(50))
        
        autoShowInterstitial = true  // AUTO-SHOW after load (like startup)
        activity.runOnUiThread {
            checkConsentThenLoad {
                if (isInterstitialReady && interstitialAd != null) {
                    log("  ✓ Interstitial already ready - showing immediately")
                    showInterstitialAd()
                } else {
                    log("  ⏳ Loading interstitial (will auto-show when ready)")
                    loadInterstitialAd()
                }
            }
        }
    }

    private fun loadInterstitialWithAutoShow(placementId: String, autoShow: Boolean) {
        log("═".repeat(50))
        log("loadInterstitialWithAutoShow(placementId=$placementId, autoShow=$autoShow)")
        log("═".repeat(50))

        autoShowInterstitial = autoShow

        if (isAdMobId(placementId)) {
            currentInterstitialId = placementId
            log("  ✓ Using Interstitial ID from JS: $currentInterstitialId")
        }

        loadInterstitialAd()
    }

    @JavascriptInterface
    fun showInterstitial(placementId: String) {
        log("═".repeat(50))
        log("JS → showInterstitial()")
        log("═".repeat(50))

        activity.runOnUiThread {
            showInterstitialAd()
        }
    }

    // ORIGINAL INTERFACE: No-parameter version for web app compatibility
    @JavascriptInterface
    fun showInterstitial() {
        log("JS → showInterstitial() [original interface - no param]")
        activity.runOnUiThread {
            showInterstitialAd()
        }
    }

    // Auto-show flag for 1-step rewarded (like banner)
    private var autoShowRewarded = false
    
    // Track if reward was earned for local ads
    private var localRewardEarned = false

    @JavascriptInterface
    fun loadRewarded(placementId: String) {
        log("JS → loadRewarded (checking consent first)")
        // DEFAULT: Auto-show when loaded (1-step, like banner)
        activity.runOnUiThread {
            checkConsentThenLoad {
                loadRewardedWithAutoShow(placementId, true)
            }
        }
    }

    @JavascriptInterface
    fun loadRewardedOnly(placementId: String) {
        log("JS → loadRewardedOnly (checking consent first)")
        // 2-step: Load only, call showRewarded() separately
        activity.runOnUiThread {
            checkConsentThenLoad {
                loadRewardedWithAutoShow(placementId, false)
            }
        }
    }

    // ORIGINAL INTERFACE: Auto-show on button press (1-step, like startup)
    @JavascriptInterface
    fun preloadRewarded() {
        log("═".repeat(50))
        log("JS → preloadRewarded() [ORIGINAL - checking consent first]")
        log("═".repeat(50))
        
        autoShowRewarded = true  // AUTO-SHOW after load (like startup)
        activity.runOnUiThread {
            checkConsentThenLoad {
                if (isRewardedReady && rewardedAd != null) {
                    log("  ✓ Rewarded already ready - showing immediately")
                    showRewardedAd()
                } else {
                    log("  ⏳ Loading rewarded (will auto-show when ready)")
                    loadRewardedAd()
                }
            }
        }
    }

    // ORIGINAL INTERFACE: Alternative method name
    @JavascriptInterface
    fun preloadRewardedAd() {
        log("JS → preloadRewardedAd() [ORIGINAL alias - checking consent first]")
        preloadRewarded()
    }

    private fun loadRewardedWithAutoShow(placementId: String, autoShow: Boolean) {
        log("═".repeat(50))
        log("loadRewardedWithAutoShow(placementId=$placementId, autoShow=$autoShow)")
        log("═".repeat(50))

        autoShowRewarded = autoShow

        if (isAdMobId(placementId)) {
            currentRewardedId = placementId
            log("  ✓ Using Rewarded ID from JS: $currentRewardedId")
        }

        loadRewardedAd()
    }

    @JavascriptInterface
    fun showRewarded(placementId: String) {
        log("═".repeat(50))
        log("JS → showRewarded()")
        log("═".repeat(50))

        activity.runOnUiThread {
            showRewardedAd()
        }
    }

    // ORIGINAL INTERFACE: No-parameter version for web app compatibility
    @JavascriptInterface
    fun showRewarded() {
        log("JS → showRewarded() [original interface - no param]")
        activity.runOnUiThread {
            showRewardedAd()
        }
    }

    @JavascriptInterface
    fun isInterstitialReady(placementId: String): Boolean {
        log("JS → isInterstitialReady() = $isInterstitialReady")
        return isInterstitialReady
    }

    // ORIGINAL INTERFACE: No-parameter version for web app compatibility
    @JavascriptInterface
    fun isInterstitialReady(): Boolean {
        log("JS → isInterstitialReady() [original] = $isInterstitialReady")
        return isInterstitialReady
    }

    @JavascriptInterface
    fun isRewardedReady(placementId: String): Boolean {
        log("JS → isRewardedReady() = $isRewardedReady")
        return isRewardedReady
    }

    // ORIGINAL INTERFACE: No-parameter version for web app compatibility
    @JavascriptInterface
    fun isRewardedReady(): Boolean {
        log("JS → isRewardedReady() [original] = $isRewardedReady")
        return isRewardedReady
    }

    // ORIGINAL INTERFACE: Alternative method name
    @JavascriptInterface
    fun isRewardedAdReady(): Boolean {
        log("JS → isRewardedAdReady() [ORIGINAL] = $isRewardedReady")
        return isRewardedReady
    }

    @JavascriptInterface
    fun getImpressionCount(): Int {
        return totalImpressions
    }

    @JavascriptInterface
    fun getAdMobImpressions(): Int {
        return totalImpressions
    }

    @JavascriptInterface
    fun getBannerImpressions(): Int {
        return bannerImpressions
    }

    @JavascriptInterface
    fun getInterstitialImpressions(): Int {
        return interstitialImpressions
    }

    @JavascriptInterface
    fun getRewardedImpressions(): Int {
        return rewardedImpressions
    }

    // ==================== Ad Loading Methods ====================

    private fun loadBannerAd(position: String) {
        log("Loading AdMob Banner...")
        log("  Ad Unit ID: $currentBannerId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "banner", "Activity reference lost")
            return
        }

        try {
            bannerAd = AdView(act).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = currentBannerId
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        isBannerReady = true
                        log("✓ BANNER LOADED")
                        notifyJs("adLoaded", "admob", "banner")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isBannerReady = false
                        log("✗ Banner load FAILED: ${error.code} - ${error.message}", "E")
                        notifyJs("adFailed", "admob", "banner", error.message)
                    }

                    override fun onAdImpression() {
                        totalImpressions++
                        bannerImpressions++
                        log("★★★ BANNER IMPRESSION #$bannerImpressions ★★★")
                        notifyJs("adImpression", "admob", "banner")
                    }

                    override fun onAdClicked() {
                        log("Banner clicked")
                        notifyJs("adClicked", "admob", "banner")
                    }
                }
            }

            val adRequest = AdRequest.Builder().build()
            bannerAd?.loadAd(adRequest)
            log("Banner loadAd() called")

            // Add to layout
            val rootView = act.findViewById<ViewGroup>(android.R.id.content)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (position == "top") android.view.Gravity.TOP else android.view.Gravity.BOTTOM
            }
            bannerAd?.layoutParams = params
            rootView.addView(bannerAd)
            log("Banner added to layout at $position")

        } catch (e: Exception) {
            log("✗ Banner exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "banner", e.message ?: "Unknown error")
        }
    }

    private fun loadInterstitialAd() {
        log("Loading AdMob Interstitial...")
        log("  Ad Unit ID: $currentInterstitialId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "interstitial", "Activity reference lost")
            return
        }

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(act, currentInterstitialId, adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialReady = true
                        log("✓ INTERSTITIAL LOADED")

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                totalImpressions++
                                interstitialImpressions++
                                log("★★★ INTERSTITIAL IMPRESSION #$interstitialImpressions ★★★")
                                notifyJs("adImpression", "admob", "interstitial")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isInterstitialReady = false
                                interstitialAd = null
                                log("Interstitial dismissed")
                                notifyJs("adClosed", "admob", "interstitial")
                                // CRITICAL: Emit specific callback for web app Promise resolution
                                emitDirectCallback("if(window.onInterstitialClosed) window.onInterstitialClosed();")
                                loadInterstitialAd() // Preload next
                            }

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                isInterstitialReady = false
                                interstitialAd = null
                                log("✗ Interstitial show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "interstitial", error.message)
                                // CRITICAL: Emit failure callback for web app retry logic
                                val escapedError = error.message.replace("'", "\\'")
                                emitDirectCallback("if(window.onInterstitialFailedToShow) window.onInterstitialFailedToShow('$escapedError');")
                            }

                            override fun onAdClicked() {
                                log("Interstitial clicked")
                                notifyJs("adClicked", "admob", "interstitial")
                            }
                        }

                        notifyJs("adLoaded", "admob", "interstitial")

                        // AUTO-SHOW: If loadInterstitialAndShow() was called, show immediately
                        if (autoShowInterstitial) {
                            log("  ► Auto-showing interstitial...")
                            autoShowInterstitial = false // Reset flag
                            showInterstitialAd()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isInterstitialReady = false
                        autoShowInterstitial = false // Reset flag on failure
                        log("✗ Interstitial load FAILED: ${error.code} - ${error.message}", "E")
                        log("  Domain: ${error.domain}")
                        notifyJs("adFailed", "admob", "interstitial", error.message)
                    }
                })
        } catch (e: Exception) {
            log("✗ Interstitial exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "interstitial", e.message ?: "Unknown error")
        }
    }

    private fun loadRewardedAd() {
        log("Loading AdMob Rewarded...")
        log("  Ad Unit ID: $currentRewardedId")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "rewarded", "Activity reference lost")
            return
        }

        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(act, currentRewardedId, adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isRewardedReady = true
                        log("✓ REWARDED LOADED")

                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdShowedFullScreenContent() {
                                totalImpressions++
                                rewardedImpressions++
                                localRewardEarned = false // Reset on show
                                log("★★★ REWARDED IMPRESSION #$rewardedImpressions ★★★")
                                notifyJs("adImpression", "admob", "rewarded")
                            }

                            override fun onAdDismissedFullScreenContent() {
                                isRewardedReady = false
                                rewardedAd = null
                                log("Rewarded dismissed, localRewardEarned: $localRewardEarned")
                                notifyJs("adClosed", "admob", "rewarded")
                                // CRITICAL: Only emit closed callback if reward wasn't earned
                                if (!localRewardEarned) {
                                    emitDirectCallback("if(window.onRewardedClosed) window.onRewardedClosed();")
                                }
                                loadRewardedAd() // Preload next
                            }

                            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                                isRewardedReady = false
                                rewardedAd = null
                                log("✗ Rewarded show FAILED: ${error.code} - ${error.message}", "E")
                                notifyJs("adFailed", "admob", "rewarded", error.message)
                                // CRITICAL: Emit failure callback for web app retry logic
                                val escapedError = error.message.replace("'", "\\'")
                                emitDirectCallback("if(window.onRewardedFailedToShow) window.onRewardedFailedToShow('$escapedError');")
                            }

                            override fun onAdClicked() {
                                log("Rewarded clicked")
                                notifyJs("adClicked", "admob", "rewarded")
                            }
                        }

                        notifyJs("adLoaded", "admob", "rewarded")

                        // AUTO-SHOW: If loadRewardedAndShow() was called, show immediately
                        if (autoShowRewarded) {
                            log("  ► Auto-showing rewarded...")
                            autoShowRewarded = false // Reset flag
                            showRewardedAd()
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isRewardedReady = false
                        autoShowRewarded = false // Reset flag on failure
                        log("✗ Rewarded load FAILED: ${error.code} - ${error.message}", "E")
                        log("  Domain: ${error.domain}")
                        notifyJs("adFailed", "admob", "rewarded", error.message)
                    }
                })
        } catch (e: Exception) {
            log("✗ Rewarded exception: ${e.message}", "E")
            notifyJs("adFailed", "admob", "rewarded", e.message ?: "Unknown error")
        }
    }

    // ==================== Ad Show Methods ====================

    private fun showInterstitialAd() {
        log("→ Attempting to show interstitial...")
        log("  Ready (local): $isInterstitialReady")
        log("  Object (local): ${interstitialAd != null}")
        log("  Ready (preload): ${AdPreloadManager.isInterstitialReady()}")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "interstitial", "Activity lost")
            return
        }

        // CRITICAL: Check activity lifecycle state before showing
        if (act.isFinishing || act.isDestroyed) {
            log("✗ Activity finishing/destroyed - cannot show ad!", "W")
            notifyJs("adFailed", "admob", "interstitial", "Activity not active")
            return
        }

        // PRIORITY 1: Try AdPreloadManager cached ad first (faster - no bridge latency)
        val preloadedAd = AdPreloadManager.getCachedInterstitial()
        if (preloadedAd != null) {
            log("  ► Using PRELOADED interstitial (fast path)")
            TestRigorLogger.logAdEvent("AdMobBridge: Showing preloaded interstitial")
            
            // Override callbacks to emit JavaScript AND handle foreground recovery
            preloadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    log("Preloaded interstitial dismissed")
                    cancelForegroundRecovery()
                    notifyJs("adClosed", "admob", "interstitial")
                    emitDirectCallback("if(window.onInterstitialClosed) window.onInterstitialClosed();")
                    // CRITICAL: Bring app back to foreground after ad dismissal
                    bringAppToForeground()
                }
                
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    log("Preloaded interstitial failed to show: ${error.message}", "E")
                    cancelForegroundRecovery()
                    notifyJs("adFailed", "admob", "interstitial", error.message)
                    val escapedError = error.message.replace("'", "\\'")
                    emitDirectCallback("if(window.onInterstitialFailedToShow) window.onInterstitialFailedToShow('$escapedError');")
                }
                
                override fun onAdShowedFullScreenContent() {
                    totalImpressions++
                    interstitialImpressions++
                    log("★★★ PRELOADED INTERSTITIAL IMPRESSION #$interstitialImpressions ★★★")
                    notifyJs("adImpression", "admob", "interstitial")
                }
                
                override fun onAdClicked() {
                    log("Preloaded interstitial clicked - scheduling foreground recovery")
                    scheduleForegroundRecovery()
                }
            }
            
            preloadedAd.show(act)
            AdPreloadManager.clearCachedInterstitial()
            log("  ✓ Preloaded show() called successfully")
            return
        }

        // PRIORITY 2: Fallback to local ad
        if (isInterstitialReady && interstitialAd != null) {
            log("  ► Calling local interstitial.show()")
            interstitialAd?.show(act)
            log("  ✓ Local show() called successfully")
        } else {
            // NO CACHED AD - Load with auto-show (same behavior as startup)
            // IMPORTANT: Check consent first to ensure real ads (not test ads)
            log("⏳ No interstitial cached - loading with auto-show (like startup)")
            autoShowInterstitial = true
            checkConsentThenLoad {
                loadInterstitialAd()
            }
        }
    }

    private fun showRewardedAd() {
        log("→ Attempting to show rewarded...")
        log("  Ready (local): $isRewardedReady")
        log("  Object (local): ${rewardedAd != null}")
        log("  Ready (preload): ${AdPreloadManager.isRewardedReady()}")

        val act = activityRef.get()
        if (act == null) {
            log("✗ Activity reference lost!", "E")
            notifyJs("adFailed", "admob", "rewarded", "Activity lost")
            return
        }

        // CRITICAL: Check activity lifecycle state before showing
        if (act.isFinishing || act.isDestroyed) {
            log("✗ Activity finishing/destroyed - cannot show ad!", "W")
            notifyJs("adFailed", "admob", "rewarded", "Activity not active")
            return
        }

        // PRIORITY 1: Try AdPreloadManager cached ad first (faster - no bridge latency)
        val preloadedAd = AdPreloadManager.getCachedRewarded()
        if (preloadedAd != null) {
            log("  ► Using PRELOADED rewarded (fast path)")
            TestRigorLogger.logAdEvent("AdMobBridge: Showing preloaded rewarded")
            
            // Track if reward was earned
            var rewardEarned = false
            
            // Override callbacks to emit JavaScript AND handle foreground recovery
            preloadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    log("Preloaded rewarded dismissed, rewardEarned: $rewardEarned")
                    cancelForegroundRecovery()
                    notifyJs("adClosed", "admob", "rewarded")
                    // If reward wasn't earned, emit closed callback
                    if (!rewardEarned) {
                        emitDirectCallback("if(window.onRewardedClosed) window.onRewardedClosed();")
                    }
                    // CRITICAL: Bring app back to foreground after ad dismissal
                    bringAppToForeground()
                }
                
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    log("Preloaded rewarded failed to show: ${error.message}", "E")
                    cancelForegroundRecovery()
                    notifyJs("adFailed", "admob", "rewarded", error.message)
                    val escapedError = error.message.replace("'", "\\'")
                    emitDirectCallback("if(window.onRewardedFailedToShow) window.onRewardedFailedToShow('$escapedError');")
                }
                
                override fun onAdShowedFullScreenContent() {
                    totalImpressions++
                    rewardedImpressions++
                    log("★★★ PRELOADED REWARDED IMPRESSION #$rewardedImpressions ★★★")
                    notifyJs("adImpression", "admob", "rewarded")
                }
                
                override fun onAdClicked() {
                    log("Preloaded rewarded clicked - scheduling foreground recovery")
                    scheduleForegroundRecovery()
                }
            }
            
            preloadedAd.show(act) { reward ->
                log("★★★ USER EARNED REWARD (PRELOADED) ★★★")
                log("  Type: ${reward.type}")
                log("  Amount: ${reward.amount}")
                rewardEarned = true
                
                // Send through main event channel
                notifyJs("adRewarded", "admob", "rewarded", null, mapOf(
                    "type" to reward.type,
                    "amount" to reward.amount
                ))
                
                // CRITICAL: Emit reward callback through multiple mechanisms
                emitRewardCallback(reward.type, reward.amount)
            }
            AdPreloadManager.clearCachedRewarded()
            log("  ✓ Preloaded show() called successfully")
            return
        }

        // PRIORITY 2: Fallback to local ad
        if (isRewardedReady && rewardedAd != null) {
            log("  ► Calling local rewarded.show()")
            rewardedAd?.show(act) { reward ->
                log("★★★ USER EARNED REWARD (LOCAL) ★★★")
                log("  Type: ${reward.type}")
                log("  Amount: ${reward.amount}")
                localRewardEarned = true
                
                // Send through main event channel
                notifyJs("adRewarded", "admob", "rewarded", null, mapOf(
                    "type" to reward.type,
                    "amount" to reward.amount
                ))
                
                // CRITICAL: Emit reward callback through multiple mechanisms
                emitRewardCallback(reward.type, reward.amount)
            }
            log("  ✓ Local show() called successfully")
        } else {
            // NO CACHED AD - Load with auto-show (same behavior as startup)
            // IMPORTANT: Check consent first to ensure real ads (not test ads)
            log("⏳ No rewarded cached - loading with auto-show (like startup)")
            autoShowRewarded = true
            checkConsentThenLoad {
                loadRewardedAd()
            }
        }
    }

    // ==================== JavaScript Notification ====================

    private fun notifyJs(
        eventType: String,
        network: String,
        adType: String,
        error: String? = null,
        data: Map<String, Any>? = null
    ) {
        val json = JSONObject().apply {
            put("type", eventType)
            put("network", network)
            put("adType", adType)
            put("placementId", when (adType) {
                "banner" -> currentBannerId
                "interstitial" -> currentInterstitialId
                "rewarded" -> currentRewardedId
                else -> ""
            })
            error?.let { put("error", it) }
            data?.let { 
                val dataObj = JSONObject()
                it.forEach { (k, v) -> dataObj.put(k, v) }
                put("data", dataObj)
            }
        }

        val script = "window.onAdEvent && window.onAdEvent('${json.toString().replace("'", "\\'")}')"
        
        log("→ Notifying JS: $eventType [$network] $adType")
        
        activity.runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Emit a direct JavaScript callback to the WebView.
     * Used for specific callback functions like onInterstitialClosed(), onRewardEarned(), etc.
     */
    private fun emitDirectCallback(javascript: String) {
        log("→ Emitting direct callback: $javascript")
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(javascript) { result ->
                    log("  ✓ Callback result: $result")
                }
            } catch (e: Exception) {
                log("  ✗ Callback failed: ${e.message}", "E")
            }
        }
    }
    
    /**
     * Emit reward callback to JavaScript - simple direct call per the guide.
     */
    private fun emitRewardCallback(rewardType: String, rewardAmount: Int) {
        log("═".repeat(50))
        log("EMITTING REWARD CALLBACK")
        log("  Type: $rewardType, Amount: $rewardAmount")
        log("═".repeat(50))
        
        val escapedType = rewardType.replace("'", "\\'")
        val callback = "if(window.onRewardEarned) window.onRewardEarned('$escapedType', $rewardAmount);"
        
        log("→ Callback: $callback")
        
        activity.runOnUiThread {
            try {
                webView.evaluateJavascript(callback) { result ->
                    log("  ✓ Reward callback result: $result")
                    TestRigorLogger.logAdEvent("Reward callback executed, result: $result")
                }
            } catch (e: Exception) {
                log("  ✗ Reward callback failed: ${e.message}", "E")
                TestRigorLogger.logError("Reward callback failed", e)
            }
        }
    }

    // ==================== Cleanup ====================

    fun destroy() {
        log("Destroying AdMobBridge...")
        bannerAd?.destroy()
        bannerAd = null
        interstitialAd = null
        rewardedAd = null
        log("AdMobBridge destroyed")
    }
}
