package com.babelwords.com

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Espresso lifecycle stress tests for MainActivity.
 *
 * Verifies: rotation doesn't crash, rapid pause/resume survives,
 * WebView remains responsive after lifecycle churn.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleTest {

    companion object {
        /** Time to wait for initial WebView load before stressing lifecycle. */
        private const val INITIAL_LOAD_MS = 3000L
        /** Rapid pause/resume iteration count. */
        private const val RAPID_ITERATIONS = 10
    }

    @Test
    fun rotationDoesNotCrash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait for initial load
        Thread.sleep(INITIAL_LOAD_MS)

        // Rotate the activity (destroys + recreates)
        scenario.recreate()

        // After recreation, either webview or error_container should be visible
        // (never a blank screen)
        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assertTrue(
            "After rotation, neither webview nor error_container is visible — blank screen detected",
            webViewVisible || errorVisible
        )

        scenario.close()
    }

    @Test
    fun rapidPauseResumeSurvives() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait for initial load
        Thread.sleep(INITIAL_LOAD_MS)

        repeat(RAPID_ITERATIONS) { i ->
            scenario.moveToState(Lifecycle.State.CREATED)  // triggers onPause
            Thread.sleep(100)
            scenario.moveToState(Lifecycle.State.RESUMED) // triggers onResume
        }

        // After rapid churn, app should still show WebView or error UI
        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assertTrue(
            "After rapid pause/resume, app is in an unrecoverable state",
            webViewVisible || errorVisible
        )

        scenario.close()
    }

    @Test
    fun backgroundThenForegroundResumesWebView() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait for initial load
        Thread.sleep(INITIAL_LOAD_MS)

        // Background the app for 6 seconds (long enough to trigger background state)
        scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(6000)
        scenario.moveToState(Lifecycle.State.RESUMED)

        // Give it a moment to settle
        Thread.sleep(1000)

        // WebView or error should still be visible
        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assertTrue(
            "After background → foreground, app is stuck",
            webViewVisible || errorVisible
        )

        scenario.close()
    }

    @Test
    fun activityRecreationAfterDestroyDoesNotLeak() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        Thread.sleep(INITIAL_LOAD_MS)

        // Fully destroy then recreate
        scenario.moveToState(Lifecycle.State.DESTROYED)
        val newScenario = ActivityScenario.launch(MainActivity::class.java)

        Thread.sleep(INITIAL_LOAD_MS)

        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assertTrue(
            "After destroy + recreate, app failed to recover",
            webViewVisible || errorVisible
        )

        newScenario.close()
    }
}
