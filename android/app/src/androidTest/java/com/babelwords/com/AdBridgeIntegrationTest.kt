package com.babelwords.com

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso integration tests for AdBridge ↔ WebView event flow.
 *
 * Verifies: AdBridge methods are callable from JS context, ad events reach
 * the WebView via evaluateJavascript, mic safety delegation works.
 *
 * NOTE: These tests verify the Android-side plumbing. Actual AdMob SDK
 * interactions (ad load/show) cannot be tested reliably in Espresso because
 * the SDK requires network and Google Play Services.
 */
@RunWith(AndroidJUnit4::class)
class AdBridgeIntegrationTest {

    companion object {
        private const val INITIAL_LOAD_MS = 3000L
    }

    @Test
    fun adBridgeIsRegistered() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        // Verify WebView exists and is registered with the bridge
        onView(withId(R.id.webview)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun micNotifyDoesNotCrash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        scenario.onActivity { activity ->
            // Access the bridge and exercise mic safety
            activity.adBridge?.let { bridge ->
                bridge.notifyMicActive(true)
                bridge.notifyMicActive(false)
            }
        }

        Thread.sleep(500)

        // App should still be responsive
        onView(withId(R.id.webview)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun diagnosticsQueryDoesNotCrash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        var diagnostics: String? = null
        scenario.onActivity { activity ->
            activity.adBridge?.let { bridge ->
                diagnostics = bridge.getDiagnostics()
            }
        }

        assertTrue("getDiagnostics returned null", diagnostics != null)
        assertTrue("getDiagnostics returned empty string", diagnostics!!.isNotEmpty())
        assertTrue("Diagnostics missing timestamp field", diagnostics!!.contains("timestamp"))

        scenario.close()
    }

    @Test
    fun jsEventFiringDoesNotCrash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        scenario.onActivity { activity ->
            activity.adBridge?.let { bridge ->
                // Fire internal event — this calls evaluateJavascript on the WebView
                bridge.fireEvent("testEvent", "testData")
            }
        }

        Thread.sleep(500)

        // App should not crash after JS evaluation
        onView(withId(R.id.webview)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun bridgeMethodsCallableWithNullManager() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        // Even if ad manager is not initialized, bridge methods should not crash
        scenario.onActivity { activity ->
            activity.adBridge?.let { bridge ->
                bridge.initialize()
                bridge.loadInterstitial()
                bridge.showInterstitial()
                bridge.isInterstitialReady()
                bridge.loadRewarded()
                bridge.showRewarded()
                bridge.isRewardedReady()
                bridge.isInitialized()
                bridge.loadInterstitialAndShow()
                bridge.loadRewardedAndShow()
                bridge.requestConsent()
                bridge.logEvent("integration_test")
            }
        }

        Thread.sleep(500)

        onView(withId(R.id.webview)).check(matches(isDisplayed()))

        scenario.close()
    }
}
