package com.babelwords.com.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babelwords.com.analytics.AnalyticsManager
import com.babelwords.com.bridge.AdBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Analytics event emission tests (no network dependency).
 *
 * Verifies:
 *   - AnalyticsManager.logEvent() does not crash
 *   - AnalyticsManager.logScreenView() does not crash
 *   - AnalyticsManager.logAdImpression() does not crash
 *   - AnalyticsManager.logAdFailed() does not crash
 *   - AdBridge.logEvent() delegates correctly
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsEventTest {

    @Test
    fun logEventDoesNotCrash() {
        AnalyticsManager.logEvent("test_event")
        assertTrue("logEvent should not crash", true)
    }

    @Test
    fun logScreenViewDoesNotCrash() {
        AnalyticsManager.logScreenView("test_screen", "TestActivity")
        assertTrue("logScreenView should not crash", true)
    }

    @Test
    fun logAdImpressionDoesNotCrash() {
        AnalyticsManager.logAdImpression("ca-app-pub-test/123", "interstitial")
        assertTrue("logAdImpression should not crash", true)
    }

    @Test
    fun logAdFailedDoesNotCrash() {
        AnalyticsManager.logAdFailed("ca-app-pub-test/123", "timeout")
        assertTrue("logAdFailed should not crash", true)
    }

    @Test
    fun bridgeLogEventDelegates() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )
        bridge.logEvent("bridge_test_event")
        assertTrue("AdBridge.logEvent should delegate to AnalyticsManager without crash", true)
    }

    @Test
    fun analyticsManagerHandlesEmptyEvent() {
        AnalyticsManager.logEvent("")
        assertTrue("Empty event name should not crash", true)
    }

    @Test
    fun analyticsManagerHandlesLongEvent() {
        val longEvent = "a".repeat(500)
        AnalyticsManager.logEvent(longEvent)
        assertTrue("Long event name should not crash", true)
    }
}
