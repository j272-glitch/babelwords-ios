package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * Interstitial-only ad manager (v50 — with isDestroyed guards + speed-aware preload).
 *
 * Rewarded video and App Open ads have been removed. All ad triggers use static interstitials.
 *
 * Key methods:
 *   loadInterstitialAndShow()  — primary trigger (loads + shows)
 *   loadRewardedAndShow()      — backward-compat alias → loadInterstitialAndShow()
 *   preloadInterstitial()      — warm cache for faster show
 *   destroy()                  — set isDestroyed, cancel all pending work
 *
 * Foreground ad: shows an interstitial when the app resumes from background
 * (5-minute cooldown). This replaces the removed App Open ad format.
 *
 * Speed-aware: skips preload on slow/metered cellular networks.
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
        // Sample unit — used ONLY in Firebase Test Lab.
        private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val MAX_AUTO_SHOW_ATTEMPTS = 3
        // Foreground interstitial cooldown (5 minutes)
        private const val FOREGROUND_AD_COOLDOWN_MS = 5 * 60 * 1000L
    }

    @Volatile
    private var isDestroyed = false

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

    // Test Lab auto-show
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
        if (isDestroyed || interstitialAd != null || loadingInterstitial) return

        // Speed-aware: skip preload on slow/metered cellular
        if (shouldSkipPreload()) {
            Log.d(TAG, "Skipping preload on slow/metered network")
            return
        }

        loadingInterstitial = true
        Log.d(TAG, "Loading interstitial…")

        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (isDestroyed) {
                        ad.fullScreenContentCallback = null
                        return
                    }
                    Log.d(TAG, "✅ Interstitial loaded")
                    interstitialAd = ad
                    loadingInterstitial = false
                    retryCount = 0
                    ad.fullScreenContentCallback = buildCallback()
                    eventCallback("interstitialLoaded", null)
                    maybeAutoShowInterstitial()
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    if (isDestroyed) return
                    Log.w(TAG, "Interstitial load failed: ${error.message} (code=${error.code})")
                    interstitialAd = null
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
                    scheduleRetry(error.code)
                }
            }
        )
    }

    private fun shouldSkipPreload(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isUnmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        // Only skip if we're on a metered, non-WiFi/non-Ethernet connection
        return !isWifi && !isEthernet && !isUnmetered
    }

    // ==================== Show ====================
    fun showInterstitial(activity: Activity) {
        if (isDestroyed) return
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
        setAudioModeForAd(activity)
        try {
            ad.show(activity)
        } catch (e: Exception) {
            isShowingAd = false
            restoreAudioMode(activity)
            Log.w(TAG, "showInterstitial threw: ${e.message}")
            eventCallback("interstitialFailed", e.message ?: "show_exception")
            preloadInterstitial()
        }
    }

    // ==================== 1-step load-and-show ====================
    fun loadInterstitialAndShow(activity: Activity) {
        if (isDestroyed) return
        val cached = interstitialAd
        if (cached != null) {
            Log.d(TAG, "Using cached interstitial")
            interstitialAd = null
            showInterstitial(activity)
            preloadInterstitial()
            return
        }
        Log.d(TAG, "No cached interstitial — loading fresh")
        if (loadingInterstitial) {
            eventCallback("interstitialFailed", "loading_in_progress")
            return
        }
        loadingInterstitial = true
        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        InterstitialAd.load(context, interstitialAdUnitId, request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    if (isDestroyed) {
                        ad.fullScreenContentCallback = null
                        return
                    }
                    loadingInterstitial = false
                    retryCount = 0
                    ad.fullScreenContentCallback = buildCallback()
                    interstitialAd = ad
                    showInterstitial(activity)
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    if (isDestroyed) return
                    loadingInterstitial = false
                    eventCallback("interstitialFailed", error.message)
                    scheduleRetry(error.code)
                }
            }
        )
    }

    // Backward-compat alias
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
            restoreAudioMode(context as? Activity)
            eventCallback("interstitialClosed", null)
            preloadInterstitial()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.w(TAG, "Interstitial show failed: ${error.message}")
            isShowingAd = false
            restoreAudioMode(context as? Activity)
            interstitialAd = null
            eventCallback("interstitialFailed", error.message)
            preloadInterstitial()
        }
    }

    // ==================== Retry ====================
    private fun scheduleRetry(errorCode: Int) {
        retryRunnable?.let { handler.removeCallbacks(it) }
        val delayMs = when {
            errorCode == -2 && retryCount == 0 -> 0L
            errorCode == -2 -> (2_000L * retryCount).coerceAtMost(30_000L)
            errorCode == 3  -> 30_000L
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
        if (isDestroyed) return
        val now = System.currentTimeMillis()
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

    // ==================== Audio ====================
    private fun setAudioModeForAd(activity: Activity?) {
        val am = activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun restoreAudioMode(activity: Activity?) {
        val am = activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.mode = AudioManager.MODE_NORMAL
    }

    // ==================== Destroy ====================
    fun destroy() {
        isDestroyed = true
        cancelRetries()
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        Log.d(TAG, "Destroyed")
    }

    // ==================== Queries ====================
    fun isInterstitialReady() = !isDestroyed && interstitialAd != null
    fun isRewardedReady() = isInterstitialReady()
    fun isInitialized() = !isDestroyed
}
