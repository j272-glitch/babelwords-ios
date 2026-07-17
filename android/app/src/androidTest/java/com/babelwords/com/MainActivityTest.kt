package com.babelwords.com

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Instrumentation test for MainActivity WebView loading behaviour.
 *
 * Catches the "stuck splash screen" bug:
 * - loading_container must be GONE within 15 seconds of app launch
 * - Either the WebView content loads OR the error screen appears
 * - Never stuck on loading spinner forever
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    companion object {
        private const val TAG = "MainActivityTest"
        /** Maximum time to wait for the loading overlay to disappear (seconds). */
        private const val LOADING_TIMEOUT_SEC = 15L
        /** Poll interval while waiting for loading to finish (ms). */
        private const val POLL_MS = 500L
    }

    @Test
    fun loadingContainerHidesWithinTimeout() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            // Wait up to LOADING_TIMEOUT_SEC for loading_container to become GONE
            val start = System.currentTimeMillis()
            var isLoadingGone = false
            while (System.currentTimeMillis() - start < TimeUnit.SECONDS.toMillis(LOADING_TIMEOUT_SEC)) {
                try {
                    onView(withId(R.id.loading_container))
                        .check(matches(withEffectiveVisibility(Visibility.GONE)))
                    isLoadingGone = true
                    break
                } catch (_: Throwable) {
                    Thread.sleep(POLL_MS)
                }
            }

            // If still visible after timeout, test fails
            if (!isLoadingGone) {
                throw AssertionError(
                    "❌ loading_container is still visible after ${LOADING_TIMEOUT_SEC}s. " +
                    "The app is stuck on the splash screen (WebView page did not finish loading)."
                )
            }

            // Loading is gone — verify at least ONE of these is true:
            // a) webview is visible (page loaded successfully)
            // b) error_container is visible (page failed but error UI shown, not stuck)
            val webViewVisible = runCatching {
                onView(withId(R.id.webview))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                true
            }.getOrDefault(false)

            val errorVisible = runCatching {
                onView(withId(R.id.error_container))
                    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
                true
            }.getOrDefault(false)

            if (!webViewVisible && !errorVisible) {
                throw AssertionError(
                    "❌ loading_container is gone but neither webview nor error_container is visible. " +
                    "App may be showing a blank screen."
                )
            }

            android.util.Log.i(TAG, "✅ Test passed: loading_container hidden, app state = ${
                if (webViewVisible) "webview_visible" else "error_visible"
            }")
        } finally {
            scenario.close()
        }
    }

    @Test
    fun appContextHasCorrectPackage() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assert(appContext.packageName == "com.babelwords.com") {
            "Package mismatch: expected com.babelwords.com, got ${appContext.packageName}"
        }
    }
}
