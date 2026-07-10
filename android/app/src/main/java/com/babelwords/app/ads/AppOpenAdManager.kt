package com.babelwords.app.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.babelwords.app.MainActivity

/**
 * App Open ad manager (v50 port from LinguaVibe production system).
 *
 * Shows an App Open ad when the app returns from background (warm-resume ≥5s)
 * with a 4-hour frequency cap backed by SharedPreferences.
 *
 * Guards:
 *   - isDestroyed — checked on every callback to prevent leaks after Activity destroy
 *   - isMicActive — blocks ad show while microphone is recording
 *   - isShowingAd — prevents duplicate concurrent shows
 *   - 15s load timeout — nulls stale ads
 *   - 4h SharedPreferences cap — survives process death
 *
 * Lifecycle: wired to ProcessLifecycleOwner as a DefaultLifecycleObserver.
 */
class AppOpenAdManager(
    private val activity: MainActivity
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val PREFS_NAME = "app_open_ad_prefs"
        private const val PREF_LAST_SHOW = "app_open_last_show_ms"
        private const val LOAD_TIMEOUT_MS = 15_000L
        private const val FREQUENCY_CAP_MS = 4 * 60 * 60 * 1000L   // 4 hours
        private const val BACKGROUND_THRESHOLD_MS = 5_000L       // 5s for warm-resume
        private const val RETRY_NO_FILL_MS = 60_000L
        private const val RETRY_TIMEOUT_MS = 20_000L
        private const val RETRY_NETWORK_MS = 15_000L
    }

    @Volatile
    private var isDestroyed = false

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowingAd = false
    private var loadStartTime = 0L
    private var lastBackgroundTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var loadTimeoutRunnable: Runnable? = null
    private var retryRunnable: Runnable? = null
    private val prefs: SharedPreferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val isTestLab: Boolean by lazy {
        runCatching {
            "true".equals(
                Settings.System.getString(activity.contentResolver, "firebase.test.lab"),
                ignoreCase = true
            )
        }.getOrDefault(false)
    }

    private val adUnitId: String by lazy {
        if (isTestLab) "ca-app-pub-3940256099942544/9251695926"
        else activity.getString(com.babelwords.app.R.string.admob_app_open_id)
    }

    // ==================== Lifecycle ====================
    override fun onStop(owner: LifecycleOwner) {
        lastBackgroundTime = System.currentTimeMillis()
        Log.d(TAG, "App went to background at $lastBackgroundTime")
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isDestroyed) return
        val now = System.currentTimeMillis()
        val inBackground = now - lastBackgroundTime
        if (inBackground >= BACKGROUND_THRESHOLD_MS) {
            Log.d(TAG, "Warm-resume detected ($inBackground ms background)")
            showAdIfAvailable()
        }
    }

    // ==================== Load ====================
    fun loadAd() {
        if (isDestroyed || isLoading || appOpenAd != null) return

        // Frequency cap check
        val lastShow = prefs.getLong(PREF_LAST_SHOW, 0L)
        val now = System.currentTimeMillis()
        if (now - lastShow < FREQUENCY_CAP_MS) {
            Log.d(TAG, "Frequency cap active (${(now - lastShow) / 1000}s since last show)")
            return
        }

        isLoading = true
        loadStartTime = now
        Log.d(TAG, "Loading App Open ad…")

        // Cancel any previous timeout
        loadTimeoutRunnable?.let { handler.removeCallbacks(it) }

        // Set 15s timeout
        val timeout = Runnable {
            if (isLoading) {
                Log.w(TAG, "App Open load timed out after ${LOAD_TIMEOUT_MS}ms")
                isLoading = false
                appOpenAd = null
                scheduleRetry(RETRY_TIMEOUT_MS)
            }
        }
        loadTimeoutRunnable = timeout
        handler.postDelayed(timeout, LOAD_TIMEOUT_MS)

        val request = AdRequest.Builder().build()
        AppOpenAd.load(activity, adUnitId, request,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    if (isDestroyed) {
                        ad.fullScreenContentCallback = null
                        return
                    }
                    Log.d(TAG, "✅ App Open ad loaded")
                    appOpenAd = ad
                    isLoading = false
                    ad.fullScreenContentCallback = buildCallback()
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
                    if (isDestroyed) return
                    Log.w(TAG, "App Open load failed: ${error.message} (code=${error.code})")
                    isLoading = false
                    appOpenAd = null
                    val delay = when (error.code) {
                        3 -> RETRY_NO_FILL_MS   // no fill
                        -2 -> RETRY_TIMEOUT_MS  // timeout
                        else -> RETRY_NETWORK_MS
                    }
                    scheduleRetry(delay)
                }
            }
        )
    }

    // ==================== Show ====================
    fun showAdIfAvailable() {
        if (isDestroyed || isShowingAd) return
        val ad = appOpenAd ?: return

        // Cross-manager: don't show if interstitial is already showing
        if (AdMobManager.isAnyFullscreenAdShowing) {
            Log.d(TAG, "Blocked — interstitial is currently showing")
            return
        }

        // Mic safety: don't interrupt recording
        if (activity.isMicActive) {
            Log.d(TAG, "Blocked — mic is active")
            return
        }

        // Frequency cap
        val lastShow = prefs.getLong(PREF_LAST_SHOW, 0L)
        val now = System.currentTimeMillis()
        if (now - lastShow < FREQUENCY_CAP_MS) {
            Log.d(TAG, "Blocked by frequency cap")
            return
        }

        isShowingAd = true
        AdMobManager.isAnyFullscreenAdShowing = true
        setAudioModeForAd()
        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.w(TAG, "App Open show threw: ${e.message}")
            isShowingAd = false
            AdMobManager.isAnyFullscreenAdShowing = false
            restoreAudioMode()
            appOpenAd = null
            loadAd()
        }
    }

    // ==================== Callbacks ====================
    private fun buildCallback() = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "✅ App Open shown")
            AdMobManager.isAnyFullscreenAdShowing = true
            prefs.edit().putLong(PREF_LAST_SHOW, System.currentTimeMillis()).apply()
            appOpenAd = null
        }
        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "App Open dismissed")
            isShowingAd = false
            AdMobManager.isAnyFullscreenAdShowing = false
            restoreAudioMode()
            loadAd()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.w(TAG, "App Open show failed: ${error.message}")
            isShowingAd = false
            AdMobManager.isAnyFullscreenAdShowing = false
            restoreAudioMode()
            appOpenAd = null
            loadAd()
        }
    }

    // ==================== Retry ====================
    private fun scheduleRetry(delayMs: Long) {
        retryRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { loadAd() }
        retryRunnable = runnable
        handler.postDelayed(runnable, delayMs)
        Log.d(TAG, "Retrying App Open load in ${delayMs}ms")
    }

    // ==================== Audio ====================
    private fun setAudioModeForAd() {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun restoreAudioMode() {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Return to normal mode; the WebView app handles its own audio needs
        am.mode = AudioManager.MODE_NORMAL
    }

    // ==================== Cleanup ====================
    fun cleanup() {
        isDestroyed = true
        loadTimeoutRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        appOpenAd?.fullScreenContentCallback = null
        appOpenAd = null
        AdMobManager.isAnyFullscreenAdShowing = false
        Log.d(TAG, "Cleaned up")
    }
}
