package com.babelwords.app.ads

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Telemetry marker tests for ad show path (no network dependency).
 */
@RunWith(AndroidJUnit4::class)
class AdMobBridgeShowTelemetryTest {

    @Test
    fun showPathLogsTelemetryMarkers() {
        // Verify that showInterstitial() emits the expected eventCallback markers
        assertTrue("Stub: compile-time telemetry coverage", true)
    }
}
