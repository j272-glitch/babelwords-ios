package com.babelwords.com

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.babelwords.com.analytics.AnalyticsManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Application class for BabelWords.
 *
 * Handles Firebase Test Lab detection and AdMob test-device registration
 * so Test Lab runs use test creatives (not real ads = no invalid traffic).
 */
class BabelWordsApplication : Application() {

    companion object {
        private const val TAG = "BabelWordsApp"

        /**
         * True ONLY after the AdMob test-device registration has SUCCESSFULLY applied on
         * Firebase Test Lab. Auto-show must gate on this (not merely on Test Lab detection):
         * if registration failed, we must NOT show ads, otherwise the run could render REAL
         * creatives = invalid traffic. Fail-safe: prefer no impression over a risky impression.
         */
        @Volatile
        var isTestDeviceRegistrationActive = false
            private set
    }

    override fun onCreate() {
        try {
            super.onCreate()
            Log.d(TAG, "Application initialized")

            // Initialise Firebase Analytics (gracefully degrades if not configured)
            AnalyticsManager.init(this)
            AnalyticsManager.logAppOpen()

            // Must run BEFORE the first ad load; preload manager initializes later
            configureAdMobTestDeviceForTestLab()

            // ... rest of onCreate (WebView setup, exception handler, etc.)
        } catch (e: Exception) {
            Log.e(TAG, "Application onCreate failed", e)
        }
    }

    private fun configureAdMobTestDeviceForTestLab() {
        try {
            val isTestLab = "true".equals(
                Settings.System.getString(contentResolver, "firebase.test.lab"),
                ignoreCase = true
            )
            if (!isTestLab) {
                Log.d(TAG, "Not a Test Lab device — skipping test-device registration")
                return
            }

            // Deliberate override: for a one-off real-fill verification, the CI can pass
            // REAL_ADS_ON_TEST_LAB=true to skip the test-device registration. This makes
            // Test Lab render REAL creatives on the production units. Use only briefly.
            if (BuildConfig.REAL_ADS_ON_TEST_LAB) {
                Log.w(TAG, "REAL_ADS_ON_TEST_LAB is true — Test Lab will use REAL creatives (not test). " +
                        "This is a one-off fill check; do NOT leave enabled.")
                isTestDeviceRegistrationActive = true
                return
            }

            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
            )
            isTestDeviceRegistrationActive = true
            Log.i(TAG, "🧪 Firebase Test Lab detected — registered as AdMob test device " +
                    "(real ad units, test creatives, zero invalid traffic)")
        } catch (e: Exception) {
            // Never let ad config crash startup. Leave isTestDeviceRegistrationActive=false so
            // auto-show is suppressed (fail-safe): better no impression than a real-creative one.
            isTestDeviceRegistrationActive = false
            Log.e(TAG, "🧪 Test Lab test-device registration FAILED — auto-show suppressed " +
                    "(invalid-traffic safeguard)", e)
        }
    }
}
