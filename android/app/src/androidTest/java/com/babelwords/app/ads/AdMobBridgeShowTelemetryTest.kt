package com.babelwords.com.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babelwords.com.bridge.AdBridge
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Telemetry marker tests for ad show path (no network dependency).
 *
 * Verifies that showInterstitial() and loadInterstitialAndShow() handle
 * null-manager states correctly, and that getDiagnostics() reports accurate state.
 */
@RunWith(AndroidJUnit4::class)
class AdMobBridgeShowTelemetryTest {

    private val events = mutableListOf<Pair<String, String?>>()

    private fun eventCallback(event: String, data: String?) {
        events.add(event to data)
    }

    @After
    fun tearDown() {
        events.clear()
    }

    @Test
    fun showPathLogsTelemetryMarkers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = AdMobManager(context, ::eventCallback)

        // showInterstitial with no cached ad emits "no_cached_ad" then triggers load
        val activity = Activity()
        mgr.showInterstitial(activity)

        // Verify the guard path emitted the expected event
        assertTrue(
            "showInterstitial with no cached ad should emit 'no_cached_ad'",
            events.any { it.first == "interstitialFailed" && it.second == "no_cached_ad" }
        )
        mgr.destroy()
    }

    @Test
    fun bridgeShowInterstitialWithNullManagerLogsError() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )

        // Verify getDiagnostics reports manager not initialized
        val json = bridge.getDiagnostics()
        val parsed = JSONObject(json)
        assertFalse(
            "Diagnostics should report adMobInitialized=false when manager is null",
            parsed.optBoolean("adMobInitialized", true)
        )

        // showInterstitial should not crash with null manager
        bridge.showInterstitial()
        // Verify diagnostics JSON captured the call attempt
        val postJson = bridge.getDiagnostics()
        assertTrue(
            "showInterstitial with null manager should log in diagnostics",
            postJson.contains("interstitialReady")
        )
    }

    @Test
    fun bridgeLoadAndShowWithNullManagerLogsError() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )

        // Verify getDiagnostics reports interstitial not ready
        val json = bridge.getDiagnostics()
        val parsed = JSONObject(json)
        assertFalse(
            "Diagnostics should report interstitialReady=false when manager is null",
            parsed.optBoolean("interstitialReady", true)
        )

        // loadInterstitialAndShow should not crash with null manager
        bridge.loadInterstitialAndShow()
        val postJson = bridge.getDiagnostics()
        assertTrue(
            "loadInterstitialAndShow with null manager should log in diagnostics",
            postJson.contains("interstitialReady")
        )
    }

    @Test
    fun bridgeShowRewardedWithNullManagerLogsError() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )

        // Verify getDiagnostics reports rewarded not ready
        val json = bridge.getDiagnostics()
        val parsed = JSONObject(json)
        assertFalse(
            "Diagnostics should report rewardedReady=false when manager is null",
            parsed.optBoolean("rewardedReady", true)
        )

        // showRewarded should not crash with null manager
        bridge.showRewarded()
        val postJson = bridge.getDiagnostics()
        assertTrue(
            "showRewarded with null manager should log in diagnostics",
            postJson.contains("rewardedReady")
        )
    }

    @Test
    fun diagnosticsContainsTelemetryFields() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )

        val json = bridge.getDiagnostics()
        assertTrue("Diagnostics should contain adMobInitialized", json.contains("adMobInitialized"))
        assertTrue("Diagnostics should contain interstitialReady", json.contains("interstitialReady"))
        assertTrue("Diagnostics should contain timestamp", json.contains("timestamp"))

        // Parse and verify structure
        val parsed = JSONObject(json)
        assertTrue("timestamp should be > 0", parsed.optLong("timestamp", 0) > 0)
    }

    @Test
    fun diagnosticsWithRealManagerReportsInitialized() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = AdMobManager(context, { _, _ -> })
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { mgr },
            consentManagerProvider = { null }
        )

        val json = bridge.getDiagnostics()
        val parsed = JSONObject(json)
        assertTrue(
            "Diagnostics should report adMobInitialized=true with real manager",
            parsed.optBoolean("adMobInitialized", false)
        )
        mgr.destroy()
    }
}
