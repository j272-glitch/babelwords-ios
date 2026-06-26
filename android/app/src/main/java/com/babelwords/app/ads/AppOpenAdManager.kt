package com.babelwords.app.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * App Open Ad manager for cold-start and warm-resume ad display.
 *
 * App Open ads show on:
 *   - Cold start: when the app launches from a terminated state
 *   - Warm resume: when the app comes back from background
 *
 * The ad is loaded proactively and cached with a freshness check (4 hours).
 * If the ad is stale or was shown, it reloads automatically.
 *
 * Lifecycle tracking: registers an Application.ActivityLifecycleCallbacks to
 * detect when the app comes to foreground. If an ad is available and the app
 * was in background for > N seconds, the ad is shown on the resumed activity.
 */
class AppOpenAdManager(
    private val application: Application,
    private val adUnitId: String,
    private val getConsentManager: () -> ConsentManager?,
    private val eventCallback: (eventType: String, data: String?) -> Unit,
) {
    private val TAG = "AppOpenAdManager"
    private val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L  // 4 hours
    private val BACKGROUND_THRESHOLD_MS = 3000L     // 3s in background triggers show

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var loadTime = 0L

    private var wasBackgrounded = false
    private var backgroundTimestamp = 0L
    private var isShowingAd = false

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {
                    if (wasBackgrounded && !isShowingAd) {
                        val inBackground = System.currentTimeMillis() - backgroundTimestamp
                        if (inBackground >= BACKGROUND_THRESHOLD_MS) {
                            showAdIfAvailable(activity)
                        }
                    }
                    wasBackgrounded = false
                }
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    wasBackgrounded = true
                    backgroundTimestamp = System.currentTimeMillis()
                }
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
    }

    fun loadAd() {
        if (isLoading || isAdAvailable()) return
        isLoading = true
        Log.d(TAG, "Loading App Open ad…")

        val request = getConsentManager()?.buildAdRequest() ?: AdRequest.Builder().build()
        AppOpenAd.load(
            application,
            adUnitId,
            request,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "✅ App Open ad loaded")
                    isLoading = false
                    appOpenAd = ad
                    loadTime = Date().time
                    ad.fullScreenContentCallback = buildCallback()
                }
                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w(TAG, "App Open ad load failed: ${error.message}")
                    isLoading = false
                    eventCallback("appOpenLoadFailed", error.message)
                    scheduleRetry()
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity) {
        if (!isAdAvailable()) {
            loadAd()
            return
        }
        if (isShowingAd) return
        if (activity.isFinishing || activity.isDestroyed) return

        isShowingAd = true
        appOpenAd?.show(activity)
    }

    private fun isAdAvailable(): Boolean {
        val loaded = appOpenAd != null
        val fresh = System.currentTimeMillis() - loadTime < AD_EXPIRY_MS
        return loaded && fresh
    }

    private fun buildCallback() = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "✅ App Open shown")
            eventCallback("appOpenShown", null)
        }
        override fun onAdDismissedFullScreenContent() {
            isShowingAd = false
            appOpenAd = null
            Log.d(TAG, "App Open dismissed")
            eventCallback("appOpenClosed", null)
            loadAd()
        }
        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            isShowingAd = false
            appOpenAd = null
            Log.w(TAG, "App Open show failed: ${error.message}")
            eventCallback("appOpenFailed", error.message)
            loadAd()
        }
    }

    private fun scheduleRetry() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            loadAd()
        }, 5_000L)
    }
}
