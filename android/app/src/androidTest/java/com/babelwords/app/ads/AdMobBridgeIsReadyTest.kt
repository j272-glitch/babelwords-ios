package com.babelwords.com.ads

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babelwords.com.bridge.AdBridge
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * isReady() guard tests (no network dependency).
 *
 * Verifies:
 *   - AdMobManager.isInterstitialReady() is false when destroyed
 *   - AdMobManager.isInterstitialReady() is false when no ad cached
 *   - AdBridge.isInterstitialReady() returns false with null manager
 *   - AdBridge.isRewardedReady() returns false with null manager
 *   - AdBridge.isInitialized() returns false with null manager
 */
@RunWith(AndroidJUnit4::class)
class AdMobBridgeIsReadyTest {

    @Test
    fun isReadyFalseWhenDestroyed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = AdMobManager(context, { _, _ -> })
        mgr.destroy()
        assertFalse("isInterstitialReady should be false after destroy", mgr.isInterstitialReady())
    }

    @Test
    fun isReadyFalseWhenNoAd() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = AdMobManager(context, { _, _ -> })
        assertFalse("isInterstitialReady should be false when no ad loaded", mgr.isInterstitialReady())
        mgr.destroy()
    }

    @Test
    fun bridgeIsInterstitialReadyFalseWhenNoManager() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )
        assertFalse("isInterstitialReady should be false with null manager", bridge.isInterstitialReady())
    }

    @Test
    fun bridgeIsRewardedReadyFalseWhenNoManager() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )
        assertFalse("isRewardedReady should be false with null manager", bridge.isRewardedReady())
    }

    @Test
    fun bridgeIsInitializedFalseWhenNoManager() {
        val activity = Activity()
        val bridge = AdBridge(
            activity = activity,
            adMobManagerProvider = { null },
            consentManagerProvider = { null }
        )
        assertFalse("isInitialized should be false with null manager", bridge.isInitialized())
    }

    @Test
    fun bridgeIsInterstitialReadyTrueWithFreshManager() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mgr = AdMobManager(context, { _, _ -> })
        val bridge = AdBridge(
            activity = Activity(),
            adMobManagerProvider = { mgr },
            consentManagerProvider = { null }
        )
        // Manager is initialized but no ad loaded — should still be false
        assertFalse("isInterstitialReady should be false even with initialized manager (no ad)", bridge.isInterstitialReady())
        mgr.destroy()
    }
}
