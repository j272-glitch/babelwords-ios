package com.lingualink.linguagt.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.lingualink.linguagt.TestRigorLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Production-grade AdMob Manager with GDPR compliance, frequency capping, and lifecycle safety
 * 
 * Features:
 * - GDPR/CCPA consent management via UMP SDK
 * - Intelligent ad frequency capping
 * - Thread-safe operations
 * - Lifecycle-aware ad loading
 * - Automatic retry logic
 * - Memory leak prevention
 * - Test ad support for development
 */
class AdMobManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AdMobManager"

        // Ad Unit IDs - Replace with your actual IDs from AdMob console
        // For testing, these are Google's test ad units
        private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111" // Test banner
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9277938970928959/1473642031"
        private const val REWARDED_AD_UNIT_ID = "ca-app-pub-9277938970928959/8777416980"
        private const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110" // Test native

        // Frequency capping (milliseconds)
        private const val INTERSTITIAL_MIN_INTERVAL = 180_000L // 3 minutes
        private const val REWARDED_MIN_INTERVAL = 300_000L // 5 minutes

        // Retry delays
        private const val RETRY_DELAY_SHORT = 5_000L // 5 seconds
        private const val RETRY_DELAY_LONG = 30_000L // 30 seconds

        @Volatile
        private var instance: AdMobManager? = null

        fun getInstance(context: Context): AdMobManager {
            return instance ?: synchronized(this) {
                instance ?: AdMobManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // State management
    internal val isInitialized = AtomicBoolean(false)
    private val hasConsent = AtomicBoolean(false)
    private val lastInterstitialTime = AtomicLong(0)
    private val lastRewardedTime = AtomicLong(0)

    // Ad instances
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var bannerAdView: AdView? = null
    private var nativeAd: NativeAd? = null

    // Consent management
    private var consentInformation: ConsentInformation? = null

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Initialize AdMob SDK and handle GDPR consent
     */
    fun initialize(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (isInitialized.getAndSet(true)) {
            TestRigorLogger.logAdEvent("AdMob already initialized")
            onComplete(true)
            return
        }

        TestRigorLogger.logAdEvent("Initializing AdMob SDK")

        // Initialize Mobile Ads SDK
        MobileAds.initialize(context) { initializationStatus ->
            TestRigorLogger.logAdEvent("AdMob SDK initialized: ${initializationStatus.adapterStatusMap}")

            // Request GDPR consent
            requestConsent(activity) { consentGranted ->
                hasConsent.set(consentGranted)

                if (consentGranted) {
                    // Preload ads after consent
                    preloadAds()
                }

                onComplete(consentGranted)
            }
        }
    }

    /**
     * Request user consent for personalized ads (GDPR/CCPA compliance)
     */
    private fun requestConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(context)

        consentInformation?.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully
                if (consentInformation?.isConsentFormAvailable == true) {
                    loadConsentForm(activity, onComplete)
                } else {
                    // No consent form needed (user not in GDPR region)
                    TestRigorLogger.logAdEvent("No consent form required")
                    onComplete(true)
                }
            },
            { formError ->
                // Failed to get consent info
                // FIXED: FormError is not a Throwable, pass null instead
                TestRigorLogger.logError("Consent request failed: ${formError.message}", null)
                onComplete(false)
            }
        )
    }

    /**
     * Load and show consent form if needed
     */
    private fun loadConsentForm(activity: Activity, onComplete: (Boolean) -> Unit) {
        UserMessagingPlatform.loadConsentForm(
            context,
            { consentForm ->
                if (consentInformation?.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) { formError ->
                        if (formError != null) {
                            // FIXED: FormError is not a Throwable, pass null instead
                            TestRigorLogger.logError("Consent form error: ${formError.message}", null)
                        }

                        val consentGranted = consentInformation?.consentStatus == ConsentInformation.ConsentStatus.OBTAINED
                        TestRigorLogger.logAdEvent("Consent granted: $consentGranted")
                        onComplete(consentGranted)
                    }
                } else {
                    // Consent already obtained
                    onComplete(true)
                }
            },
            { formError ->
                // FIXED: FormError is not a Throwable, pass null instead
                TestRigorLogger.logError("Consent form load failed: ${formError.message}", null)
                onComplete(false)
            }
        )
    }

    /**
     * Preload ads after initialization
     */
    private fun preloadAds() {
        loadInterstitialAd()
        loadRewardedAd()
    }

    /**
     * Load banner ad into a container
     */
    fun loadBannerAd(container: FrameLayout, adSize: AdSize = AdSize.BANNER): AdView? {
        if (!hasConsent.get()) {
            TestRigorLogger.logAdEvent("Cannot load banner - no consent")
            return null
        }

        try {
            // Clean up existing banner
            bannerAdView?.destroy()

            bannerAdView = AdView(context).apply {
                adUnitId = BANNER_AD_UNIT_ID
                setAdSize(adSize)

                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        TestRigorLogger.logAdEvent("Banner ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        TestRigorLogger.logError("Banner ad failed: ${error.message}", null)
                    }

                    override fun onAdClicked() {
                        TestRigorLogger.logAdEvent("Banner ad clicked")
                    }
                }

                loadAd(AdRequest.Builder().build())
            }

            container.removeAllViews()
            container.addView(bannerAdView)

            return bannerAdView
        } catch (e: Exception) {
            TestRigorLogger.logError("Banner ad error: ${e.message}", e)
            return null
        }
    }

    /**
     * Load interstitial ad
     */
    private fun loadInterstitialAd() {
        if (!hasConsent.get()) {
            TestRigorLogger.logAdEvent("Cannot load interstitial - no consent")
            return
        }

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    TestRigorLogger.logAdEvent("Interstitial ad loaded")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            TestRigorLogger.logAdEvent("✅ INTERSTITIAL DISMISSED - User saw the ad")
                            interstitialAd = null
                            // Reload after delay
                            handler.postDelayed({ loadInterstitialAd() }, RETRY_DELAY_LONG)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            TestRigorLogger.logError("❌ INTERSTITIAL FAILED TO SHOW: ${error.message} (code: ${error.code})", null)
                            interstitialAd = null
                        }

                        override fun onAdShowedFullScreenContent() {
                            TestRigorLogger.logAdEvent("🎉 INTERSTITIAL IMPRESSION RECORDED - Ad is visible to user!")
                        }

                        override fun onAdImpression() {
                            TestRigorLogger.logAdEvent("💰💰💰 INTERSTITIAL AD IMPRESSION COUNTED BY ADMOB 💰💰💰")
                            TestRigorLogger.logAdEvent("This should now appear in AdMob dashboard")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    TestRigorLogger.logError("Interstitial load failed: ${error.message}", null)
                    interstitialAd = null
                    // Retry after delay
                    handler.postDelayed({ loadInterstitialAd() }, RETRY_DELAY_LONG)
                }
            }
        )
    }

    /**
     * Show interstitial ad with frequency capping
     * TESTRIGOR FIX: Check activity state before showing to prevent WindowManagerBadTokenException
     */
    fun showInterstitialAd(
        activity: Activity, 
        onAdShown: () -> Unit = {},
        onAdClosed: () -> Unit = {}
    ) {
        TestRigorLogger.logAdEvent("📊 showInterstitialAd() called - checking conditions...")

        // TESTRIGOR FIX: Check activity state before showing ad
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("❌ Cannot show interstitial - activity invalid")
            onAdClosed()
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTime.get()

        if (timeSinceLastAd < INTERSTITIAL_MIN_INTERVAL) {
            TestRigorLogger.logAdEvent("⏱️ Interstitial ad skipped - frequency cap (${timeSinceLastAd}ms < ${INTERSTITIAL_MIN_INTERVAL}ms)")
            onAdClosed()
            return
        }

        if (interstitialAd != null) {
            TestRigorLogger.logAdEvent("🎬 SHOWING INTERSTITIAL AD NOW")
            
            // DO NOT set callbacks here - they are already set in loadInterstitialAd()
            // Setting them again here OVERWRITES the impression tracking!
            
            try {
                interstitialAd?.show(activity)
                lastInterstitialTime.set(currentTime)
                TestRigorLogger.logAdEvent("✅ Interstitial ad show() called successfully")
                onAdShown()
            } catch (e: Exception) {
                TestRigorLogger.logError("❌ Exception showing interstitial: ${e.message}", e)
                onAdClosed()
            }
        } else {
            TestRigorLogger.logAdEvent("❌ Interstitial ad not ready (null)")
            loadInterstitialAd() // Try to load
            onAdClosed()
        }
    }

    /**
     * Load rewarded ad
     */
    private fun loadRewardedAd() {
        if (!hasConsent.get()) {
            TestRigorLogger.logAdEvent("Cannot load rewarded ad - no consent")
            return
        }

        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    TestRigorLogger.logAdEvent("Rewarded ad loaded")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            TestRigorLogger.logAdEvent("✅ REWARDED AD DISMISSED - User saw the ad")
                            rewardedAd = null
                            handler.postDelayed({ loadRewardedAd() }, RETRY_DELAY_LONG)
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            TestRigorLogger.logError("❌ REWARDED AD FAILED TO SHOW: ${error.message} (code: ${error.code})", null)
                            rewardedAd = null
                        }

                        override fun onAdShowedFullScreenContent() {
                            TestRigorLogger.logAdEvent("🎉 REWARDED AD IMPRESSION RECORDED - Ad is visible to user!")
                        }

                        override fun onAdImpression() {
                            TestRigorLogger.logAdEvent("💰💰💰 REWARDED AD IMPRESSION COUNTED BY ADMOB 💰💰💰")
                            TestRigorLogger.logAdEvent("This should now appear in AdMob dashboard")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    TestRigorLogger.logError("Rewarded ad load failed: ${error.message}", null)
                    rewardedAd = null
                    handler.postDelayed({ loadRewardedAd() }, RETRY_DELAY_LONG)
                }
            }
        )
    }

    /**
     * Show rewarded ad with frequency capping and reward callback
     * TESTRIGOR FIX: Check activity state before showing to prevent WindowManagerBadTokenException
     */
    fun showRewardedAd(activity: Activity, onRewarded: (Int) -> Unit, onAdClosed: () -> Unit = {}) {
        TestRigorLogger.logAdEvent("📊 showRewardedAd() called - checking conditions...")

        // TESTRIGOR FIX: Check activity state before showing ad
        if (activity.isFinishing || activity.isDestroyed) {
            TestRigorLogger.logWarning("❌ Cannot show rewarded ad - activity invalid")
            onAdClosed()
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastRewardedTime.get()

        if (timeSinceLastAd < REWARDED_MIN_INTERVAL) {
            TestRigorLogger.logAdEvent("⏱️ Rewarded ad skipped - frequency cap (${timeSinceLastAd}ms < ${REWARDED_MIN_INTERVAL}ms)")
            onAdClosed()
            return
        }

        if (rewardedAd != null) {
            TestRigorLogger.logAdEvent("🎬 SHOWING REWARDED AD NOW")
            
            // DO NOT set callbacks here - they are already set in loadRewardedAd()
            // Setting them again here OVERWRITES the impression tracking!
            
            try {
                rewardedAd?.show(activity) { rewardItem ->
                    val amount = rewardItem.amount
                    TestRigorLogger.logAdEvent("💰 User earned reward: $amount")
                    onRewarded(amount)
                }
                lastRewardedTime.set(currentTime)
                TestRigorLogger.logAdEvent("✅ Rewarded ad show() called successfully")
            } catch (e: Exception) {
                TestRigorLogger.logError("❌ Exception showing rewarded ad: ${e.message}", e)
                onAdClosed()
            }
        } else {
            TestRigorLogger.logAdEvent("❌ Rewarded ad not ready (null)")
            loadRewardedAd()
            onAdClosed()
        }
    }

    /**
     * Check if rewarded ad is available
     */
    fun isRewardedAdAvailable(): Boolean = rewardedAd != null

    /**
     * Load native ad
     */
    fun loadNativeAd(onAdLoaded: (NativeAd) -> Unit) {
        if (!hasConsent.get()) {
            TestRigorLogger.logAdEvent("Cannot load native ad - no consent")
            return
        }

        val adLoader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID)
            .forNativeAd { ad ->
                // Destroy old native ad
                nativeAd?.destroy()
                nativeAd = ad

                TestRigorLogger.logAdEvent("Native ad loaded")
                onAdLoaded(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    TestRigorLogger.logError("Native ad failed: ${error.message}", null)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setRequestMultipleImages(true)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    /**
     * Force show interstitial (bypass frequency cap - for testing only)
     */
    internal fun forceShowInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) {
        TestRigorLogger.logAdEvent("🧪 FORCE SHOW - bypassing frequency cap")

        if (interstitialAd != null) {
            TestRigorLogger.logAdEvent("🎬 FORCE SHOWING INTERSTITIAL AD")
            try {
                interstitialAd?.show(activity)
                TestRigorLogger.logAdEvent("✅ Force show() called")
            } catch (e: Exception) {
                TestRigorLogger.logError("❌ Force show exception: ${e.message}", e)
            }
            handler.postDelayed(onAdClosed, 100)
        } else {
            TestRigorLogger.logAdEvent("❌ Cannot force show - ad is null")
            onAdClosed()
        }
    }

    /**
     * Destroy all ads and clean up resources
     */
    fun destroy() {
        TestRigorLogger.logAdEvent("Destroying AdMob manager")

        handler.removeCallbacksAndMessages(null)

        bannerAdView?.destroy()
        bannerAdView = null

        interstitialAd = null
        rewardedAd = null

        nativeAd?.destroy()
        nativeAd = null

        isInitialized.set(false)
    }

    /**
     * Pause ads (call from onPause)
     */
    fun pause() {
        bannerAdView?.pause()
    }

    /**
     * Resume ads (call from onResume)
     */
    fun resume() {
        bannerAdView?.resume()
    }
}