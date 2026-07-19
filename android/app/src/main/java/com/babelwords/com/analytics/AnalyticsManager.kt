package com.babelwords.com.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics wrapper for BabelWords.
 *
 * - Singleton, lazy-initialised, thread-safe.
 * - Gracefully degrades when Firebase is not configured (google-services.json missing).
 * - Custom events for translation, mic, ads, billing, and screen views.
 */
object AnalyticsManager {

    private const val TAG = "AnalyticsManager"

    @Volatile
    private var firebaseAnalytics: FirebaseAnalytics? = null

    @Volatile
    internal var isInitialized = false

    /**
     * Initialise Firebase Analytics and Crashlytics.
     * Safe to call multiple times; first call wins.
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                firebaseAnalytics = FirebaseAnalytics.getInstance(context)
                // Enable analytics collection explicitly
                firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
                isInitialized = true
                Log.i(TAG, "📊 Firebase Analytics initialised")
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Analytics unavailable (google-services.json missing?): ${e.message}")
                isInitialized = true  // prevent retry loops
            }
        }
    }

    /** Log a custom event with optional string parameters. */
    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        if (!isInitialized) {
            Log.w(TAG, "logEvent called before init: $name")
            return
        }
        val fa = firebaseAnalytics ?: return
        val bundle = Bundle().apply {
            params.forEach { (k, v) -> putString(k, v) }
        }
        try {
            fa.logEvent(name, bundle)
            Log.d(TAG, "Event: $name params=$params")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log event $name: ${e.message}")
        }
    }

    /** Log a screen-view event (Firebase GA4 recommended). */
    fun logScreenView(screenName: String, screenClass: String) {
        logEvent("screen_view", mapOf(
            FirebaseAnalytics.Param.SCREEN_NAME to screenName,
            FirebaseAnalytics.Param.SCREEN_CLASS to screenClass
        ))
    }

    /** Log a user property (e.g. language pair, premium status). */
    fun setUserProperty(name: String, value: String?) {
        if (!isInitialized) return
        try {
            firebaseAnalytics?.setUserProperty(name, value)
            Log.d(TAG, "User property: $name = $value")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name: ${e.message}")
        }
    }

    /** Log a non-fatal exception to Crashlytics. */
    fun logException(e: Throwable, contextMessage: String? = null) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            contextMessage?.let { crashlytics.log(it) }
            crashlytics.recordException(e)
        } catch (ignored: Exception) {
            // Crashlytics not available — silently ignore
        }
    }

    /** Convenience: app open. */
    fun logAppOpen() = logEvent("app_open")

    /** Convenience: translation started. */
    fun logTranslationStarted(sourceLang: String, targetLang: String) =
        logEvent("translation_started", mapOf("source_lang" to sourceLang, "target_lang" to targetLang))

    /** Convenience: mic activated. */
    fun logMicActivated() = logEvent("mic_activated")

    /** Convenience: ad shown. */
    fun logAdImpression(adUnit: String, adFormat: String) =
        logEvent("ad_impression", mapOf("ad_unit" to adUnit, "ad_format" to adFormat))

    /** Convenience: ad clicked. */
    fun logAdClicked(adUnit: String, adFormat: String) =
        logEvent("ad_clicked", mapOf("ad_unit" to adUnit, "ad_format" to adFormat))

    /** Convenience: ad load failed. */
    fun logAdFailed(adUnit: String, error: String) =
        logEvent("ad_failed", mapOf("ad_unit" to adUnit, "error" to error))

    /** Convenience: billing event. */
    fun logBillingEvent(event: String, productId: String) =
        logEvent(event, mapOf("product_id" to productId))
}
