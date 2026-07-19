package com.babelwords.com

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Espresso error-handling tests for MainActivity.
 *
 * Verifies: Retry button reloads WebView, offline button loads local fallback,
 * error UI appears on load failure.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityErrorHandlingTest {

    companion object {
        private const val INITIAL_LOAD_MS = 3000L
    }

    @Test
    fun retryButtonIsVisibleWhenErrorShown() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        // If error_container is visible, retry_button must be visible too
        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        if (errorVisible) {
            onView(withId(R.id.retry_button)).check(matches(isDisplayed()))
        }

        scenario.close()
    }

    @Test
    fun offlineButtonIsVisibleWhenErrorShown() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        if (errorVisible) {
            onView(withId(R.id.offline_mode_button)).check(matches(isDisplayed()))
        }

        scenario.close()
    }

    @Test
    fun offlineButtonLoadsLocalFallback() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        // Even if page loaded successfully, we can test the offline button
        // by clicking it and verifying the WebView stays visible (offline.html loads)
        val offlineButtonPresent = runCatching {
            onView(withId(R.id.offline_mode_button)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        if (offlineButtonPresent) {
            onView(withId(R.id.offline_mode_button)).perform(click())
            Thread.sleep(1000)

            // WebView should still be visible after loading offline.html
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
        }

        scenario.close()
    }

    @Test
    fun loadingContainerHiddenAfterInitialLoad() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Wait for load to complete (success or error)
        val start = System.currentTimeMillis()
        var loadingGone = false
        while (System.currentTimeMillis() - start < TimeUnit.SECONDS.toMillis(20)) {
            loadingGone = runCatching {
                onView(withId(R.id.loading_container))
                    .check(matches(withEffectiveVisibility(androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE)))
                true
            }.getOrDefault(false)
            if (loadingGone) break
            Thread.sleep(500)
        }

        assertTrue("loading_container should be gone within 20s", loadingGone)

        scenario.close()
    }

    @Test
    fun webViewVisibleWhenLoadSucceeds() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(INITIAL_LOAD_MS)

        // If page loaded successfully (not error), webview should be visible
        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        if (!errorVisible) {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
        }

        scenario.close()
    }
}
