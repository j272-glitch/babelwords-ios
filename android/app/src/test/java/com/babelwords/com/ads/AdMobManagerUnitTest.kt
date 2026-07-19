package com.babelwords.com.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric unit tests for AdMobManager guard logic (no AdMob SDK network calls).
 *
 * Verifies: destroy invalidation, frequency cap, pendingShow auto-trigger,
 * initialization state, cross-manager flag isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdMobManagerUnitTest {

    private lateinit var context: android.content.Context
    private val collectedEvents = mutableListOf<Pair<String, String?>>()

    private fun eventCallback(event: String, data: String?) {
        collectedEvents.add(event to data)
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        collectedEvents.clear()
        // Reset cross-manager flag
        AdMobManager.isAnyFullscreenAdShowing = false
    }

    @After
    fun tearDown() {
        collectedEvents.clear()
        AdMobManager.isAnyFullscreenAdShowing = false
    }

    @Test
    fun initializedAfterConstruction() {
        val mgr = AdMobManager(context, ::eventCallback)
        assertTrue("Manager should be initialized after construction", mgr.isInitialized())
        mgr.destroy()
    }

    @Test
    fun destroyInvalidatesManager() {
        val mgr = AdMobManager(context, ::eventCallback)
        mgr.destroy()
        assertFalse("isInitialized should be false after destroy", mgr.isInitialized())
        assertFalse("isInterstitialReady should be false after destroy", mgr.isInterstitialReady())
    }

    @Test
    fun interstitialNotReadyWhenNoAdLoaded() {
        val mgr = AdMobManager(context, ::eventCallback)
        assertFalse("No ad loaded → not ready", mgr.isInterstitialReady())
        mgr.destroy()
    }

    @Test
    fun frequencyCapBlocksShowWithin30Seconds() {
        // Seed SharedPreferences with a recent lastShowTime
        val prefs = context.getSharedPreferences("ad_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("interstitial_last_show_time", System.currentTimeMillis()).apply()

        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        mgr.showInterstitial(activity)

        // Verify frequency-cap rejection was signaled
        val rejection = collectedEvents.find { it.first == "interstitialFailed" && it.second == "frequency_capped" }
        assertTrue("Frequency cap should reject show within 30s", rejection != null)

        mgr.destroy()
    }

    @Test
    fun showWithoutCachedAdTriggersPendingShow() {
        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        mgr.showInterstitial(activity)

        // When no ad is cached, show() should set pendingShow and trigger load
        val noAdEvent = collectedEvents.find { it.first == "interstitialFailed" && it.second == "no_cached_ad" }
        assertTrue("Show with no cached ad should signal 'no_cached_ad'", noAdEvent != null)

        mgr.destroy()
    }

    @Test
    fun concurrentShowGuardRejectsDoubleShow() {
        // Manually set the cross-manager flag to simulate another ad showing
        AdMobManager.isAnyFullscreenAdShowing = true

        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        mgr.showInterstitial(activity)

        val rejection = collectedEvents.find { it.first == "interstitialFailed" && it.second == "already_showing" }
        assertTrue("Concurrent show guard should reject when another fullscreen ad is showing", rejection != null)

        AdMobManager.isAnyFullscreenAdShowing = false
        mgr.destroy()
    }

    @Test
    fun onActivityResumedDoesNotCrash() {
        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Should not throw even when pendingShow is false and wasBackgrounded is false
        mgr.onActivityResumed(activity)
        assertTrue(
            "onActivityResumed should complete without crash",
            mgr.isInitialized()
        )

        mgr.destroy()
    }

    @Test
    fun onActivityPausedDoesNotCrash() {
        val mgr = AdMobManager(context, ::eventCallback)
        mgr.onActivityPaused()
        assertTrue(
            "onActivityPaused should complete without crash",
            mgr.isInitialized()
        )
        mgr.destroy()
    }

    @Test
    fun loadInterstitialAndShowWithNoAdDoesNotCrash() {
        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // With no cached ad, this sets pendingShow=true and calls load()
        // In tests, load() hits the retry limit quickly — just verify no crash
        mgr.loadInterstitialAndShow(activity)

        assertTrue(
            "loadInterstitialAndShow should complete without crash",
            mgr.isInitialized()
        )

        mgr.destroy()
    }

    @Test
    fun destroyCancelsPendingShow() {
        val mgr = AdMobManager(context, ::eventCallback)
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()

        // Trigger pendingShow by calling show without a cached ad
        mgr.showInterstitial(activity)
        mgr.destroy()

        // After destroy, isInitialized should be false
        assertFalse("Manager should be destroyed", mgr.isInitialized())
    }

    @Test
    fun networkCallbackRegistrationDoesNotCrash() {
        val mgr = AdMobManager(context, ::eventCallback)
        mgr.registerNetworkCallback()
        mgr.unregisterNetworkCallback()
        assertTrue(
            "Network callback register/unregister should complete",
            mgr.isInitialized()
        )
        mgr.destroy()
    }
}
