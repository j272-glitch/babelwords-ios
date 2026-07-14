package com.babelwords.com.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * UMP (User Messaging Platform) consent manager for GDPR/EEA compliance.
 *
 * Handles consent info collection, form display, and building ad requests with
 * the appropriate consent flags (personalized vs. non-personalized).
 *
 * Must be initialized before loading any ads. The consent flow runs asynchronously;
 * onConsentReady callback fires when consent is resolved (or consent form is
 * not needed for the user's region).
 *
 * Flow:
 *   1. requestConsentInfo() — updates consent status with Google's servers
 *   2. If a form is available → loadAndShowConsentFormIfRequired()
 *   3. onConsentReady(true) ↔ ad requests can proceed
 *      ↔ if consent is not obtained, ads will be requested with non-personalized flag
 *
 * The app does NOT block ads on consent — it adjusts the request personalization.
 * Unknown/declined consent → npa=1 (non-personalized) ads still serve.
 */
class ConsentManager(private val context: Context) {
    private val TAG = "ConsentManager"

    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    private var consentForm: ConsentForm? = null
    private var isProcessing = false

    /**
     * Request consent info update, then show the form if needed.
     * [onConsentReady] fires with true when consent is resolved (or not needed).
     * Even if consent is not obtained, you can still load ads (non-personalized).
     */
    fun requestConsent(activity: Activity, onConsentReady: (Boolean) -> Unit) {
        if (isProcessing) return
        isProcessing = true

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info updated successfully
                isProcessing = false
                val canRequestAds = consentInformation.canRequestAds()
                Log.d(TAG, "Consent info updated. canRequestAds=$canRequestAds, formAvailable=${consentInformation.isConsentFormAvailable}")

                if (consentInformation.isConsentFormAvailable) {
                    loadAndShowConsentForm(activity, onConsentReady)
                } else {
                    onConsentReady(true)
                }
            },
            { requestConsentError: FormError ->
                isProcessing = false
                Log.w(TAG, "Consent info update failed: ${requestConsentError.message}")
                // Allow ads anyway on consent failure (non-personalized fallback)
                onConsentReady(true)
            }
        )
    }

    private fun loadAndShowConsentForm(activity: Activity, onConsentReady: (Boolean) -> Unit) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { form: ConsentForm ->
                consentForm = form
                val consentStatus = consentInformation.consentStatus
                if (consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    form.show(activity) { formError: FormError? ->
                        if (formError != null) {
                            Log.w(TAG, "Consent form show error: ${formError.message}")
                        }
                        Log.d(TAG, "Consent form dismissed. canRequestAds=${consentInformation.canRequestAds()}")
                        onConsentReady(consentInformation.canRequestAds())
                    }
                } else {
                    // Form available but consent already obtained (or not needed)
                    onConsentReady(true)
                }
            },
            { formError: FormError ->
                Log.w(TAG, "Consent form load failed: ${formError.message}")
                onConsentReady(true)
            }
        )
    }

    /**
     * Reset consent for testing. Call this only in debug builds.
     */
    fun resetConsent() {
        consentInformation.reset()
    }

    /**
     * Build an AdRequest with appropriate consent flags.
     * Returns the request; the caller passes it to AdMob.load().
     */
    fun buildAdRequest(): com.google.android.gms.ads.AdRequest {
        val canRequestAds = consentInformation.canRequestAds()
        val consentStatus = consentInformation.consentStatus

        // UMP SDK 3.x + play-services-ads 24.x automatically applies consent flags
        // to all AdRequests via the stored consent string. Manual npa=1 bundle is
        // no longer needed (and AdMobAdapter class is unavailable in 24.x).
        return if (canRequestAds && consentStatus == ConsentInformation.ConsentStatus.OBTAINED) {
            Log.d(TAG, "Building ad request (consent obtained)")
            com.google.android.gms.ads.AdRequest.Builder().build()
        } else {
            Log.d(TAG, "Building ad request (non-personalized via UMP consent string)")
            com.google.android.gms.ads.AdRequest.Builder().build()
        }
    }

    fun isConsentAvailable(): Boolean = consentInformation.isConsentFormAvailable
}
