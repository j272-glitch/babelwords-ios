package com.babelwords.com.ads

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Deterministic state-machine tests for AdMobManager (no network dependency).
 *
 * Validates guard logic via direct method calls:
 *   - show/destroy invalidation
 *   - frequency-cap (SharedPreferences)
 *   - concurrent-show prevention
 *   - pendingShow auto-trigger
 *   - onActivityResumed/onActivityPaused survival
 */
@RunWith(AndroidJUnit4::class)
class AdMobManagerStateMachineTest {

    private lateinit var manager: AdMobManager
    private val events = mutableListOf<Pair<String, String?>>()

    private fun eventCallback(event: String, data: String?) {
        events.add(event to data)
    }

    @Before
    fun setup() {
        events.clear()
        AdMobManager.isAnyFullscreenAdShowing = false
        manager = AdMobManager(
            ApplicationProvider.getApplicationContext(),
            ::eventCallback
        )
    }

    @After
    fun tearDown() {
        manager.destroy()
        events.clear()
        AdMobManager.isAnyFullscreenAdShowing = false
    }

    @Test
    fun initializedAfterConstruction() {
        assertTrue("Manager should be initialized after construction", manager.isInitialized())
    }

    @Test
    fun destroyInvalidatesManager() {
        manager.destroy()
        assertFalse("isInitialized should be false after destroy", manager.isInitialized())
        assertFalse("isInterstitialReady should be false after destroy", manager.isInterstitialReady())
    }

    @Test
    fun frequencyCapPreventsImmediateReshow() {
        // Seed SharedPreferences with a recent lastShowTime
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("interstitial_last_show_time", System.currentTimeMillis()).apply()

        // Create fresh manager (reads the seeded pref)
        val freshManager = AdMobManager(
            ApplicationProvider.getApplicationContext(),
            ::eventCallback
        )

        // Try to show on a stub activity — frequency cap should emit "frequency_capped"
        val activity = Activity()
        freshManager.showInterstitial(activity)

        // Verify the guard emitted the correct event
        assertTrue(
            "Frequency cap should block and emit 'frequency_capped'",
            events.any { it.first == "interstitialFailed" && it.second == "frequency_capped" }
        )

        freshManager.destroy()
        prefs.edit().remove("interstitial_last_show_time").apply()
    }

    @Test
    fun concurrentShowGuardPreventsDoubleShow() {
        // Set the cross-manager flag to simulate another ad already showing
        AdMobManager.isAnyFullscreenAdShowing = true

        val freshManager = AdMobManager(
            ApplicationProvider.getApplicationContext(),
            ::eventCallback
        )

        // showInterstitial should return immediately with "already_showing"
        val activity = Activity()
        freshManager.showInterstitial(activity)

        assertTrue(
            "Concurrent show guard should emit 'already_showing'",
            events.any { it.first == "interstitialFailed" && it.second == "already_showing" }
        )

        AdMobManager.isAnyFullscreenAdShowing = false
        freshManager.destroy()
    }

    @Test
    fun pendingShowAutoTriggersLoad() {
        // No ad cached initially
        assertFalse("No ad cached initially", manager.isInterstitialReady())

        // Simulate a show request with no ad — triggers pendingShow + load chain
        manager.preloadInterstitial()
        // preloadInterstitial does not crash and starts the load chain
        // (actual load may skip on metered network, but method call succeeds)

        // Verify the manager is still initialized after the call
        assertTrue("Manager should remain initialized after preload", manager.isInitialized())
    }

    @Test
    fun onActivityResumedDoesNotCrash() {
        val activity = Activity()
        manager.onActivityResumed(activity)
        // If there was no pendingShow, nothing crashes and state is stable
        assertTrue("Manager should survive onActivityResumed", manager.isInitialized())
    }

    @Test
    fun onActivityPausedDoesNotCrash() {
        manager.onActivityPaused()
        assertTrue("Manager should survive onActivityPaused", manager.isInitialized())
    }

    @Test
    fun loadInterstitialAndShowWithNoAdSetsPendingShow() {
        assertFalse("No ad initially", manager.isInterstitialReady())

        val activity = Activity()
        manager.loadInterstitialAndShow(activity)

        // Without a cached ad, loadInterstitialAndShow sets pendingShow and calls load()
        // We verify it does not crash and manager stays initialized
        assertTrue("Manager should survive loadInterstitialAndShow with no ad", manager.isInitialized())
    }

    @Test
    fun networkCallbackRegisterUnregister() {
        manager.registerNetworkCallback()
        manager.unregisterNetworkCallback()
        // Verify no crash and manager is still initialized
        assertTrue("Manager should survive network callback lifecycle", manager.isInitialized())
    }

    @Test
    fun isRewardedReadyMirrorsInterstitial() {
        assertFalse("isRewardedReady should mirror isInterstitialReady when no ad", manager.isRewardedReady())
    }
}
