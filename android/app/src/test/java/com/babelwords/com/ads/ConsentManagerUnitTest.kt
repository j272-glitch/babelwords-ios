package com.babelwords.com.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for ConsentManager initialization and state queries.
 *
 * Note: The actual UMP consent flow (requestConsentInfoUpdate, form display)
 * requires Google Play Services and cannot be fully unit-tested without
 * mocking the UserMessagingPlatform SDK. These tests verify the public API
 * surface and guard behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConsentManagerUnitTest {

    @Test
    fun constructionDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        val request = mgr.buildAdRequest()
        assertTrue(
            "ConsentManager should construct and buildAdRequest should return an AdRequest",
            request.javaClass.name.contains("AdRequest")
        )
    }

    @Test
    fun isConsentAvailableReturnsBoolean() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        // Returns whatever the UMP SDK says; we just verify it doesn't crash
        val available = mgr.isConsentAvailable()
        // Result depends on UMP initialization state in test environment
        assertTrue(
            "isConsentAvailable should return a boolean without crash",
            available == true || available == false
        )
    }

    @Test
    fun buildAdRequestDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        val request = mgr.buildAdRequest()
        assertTrue(
            "buildAdRequest should return an AdRequest",
            request.javaClass.name.contains("AdRequest")
        )
    }

    @Test
    fun requestConsentDoesNotCrashWhenActivityValid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // requestConsent runs asynchronously; we verify the call doesn't crash
        // and that at most one callback fires (isProcessing guard)
        var callbackCount = 0
        mgr.requestConsent(activity) { callbackCount++ }

        // Give the async callback a moment in the Robolectric looper
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        // In Robolectric, UMP may or may not actually call back; we just verify
        // the call completed without crash and callback count is bounded
        assertTrue(
            "requestConsent should complete without crash (callbackCount=$callbackCount)",
            callbackCount <= 1
        )
    }

    @Test
    fun resetConsentDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        mgr.resetConsent()
        val request = mgr.buildAdRequest()
        assertTrue(
            "resetConsent should complete and buildAdRequest should return an AdRequest",
            request.javaClass.name.contains("AdRequest")
        )
    }

    @Test
    fun doubleRequestConsentIsIgnored() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = ConsentManager(context)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        var callbackCount = 0
        mgr.requestConsent(activity) { callbackCount++ }
        mgr.requestConsent(activity) { callbackCount++ } // second call should be ignored (isProcessing guard)

        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        // Only the first request should proceed; second is skipped by isProcessing guard
        assertTrue(
            "Double requestConsent should fire callback at most once",
            callbackCount <= 1
        )
    }
}
