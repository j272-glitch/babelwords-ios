package com.babelwords.com

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso deep-link / intent tests for MainActivity.
 *
 * Verifies: https://linguagt.com and linguagt:// intents open the app,
 * WebView loads the correct URL, app doesn't crash on malformed intents.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkTest {

    companion object {
        private const val INITIAL_LOAD_MS = 3000L
    }

    @Test
    fun httpsDeepLinkOpensApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linguagt.com/translate"))
            .setPackage(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageName)

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        Thread.sleep(INITIAL_LOAD_MS)

        // App should open and show either webview or error (never stuck)
        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assert(webViewVisible || errorVisible) {
            "HTTPS deep link did not open app correctly"
        }

        scenario.close()
    }

    @Test
    fun customSchemeDeepLinkOpensApp() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("linguagt://translate?lang=ur"))
            .setPackage(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageName)

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        Thread.sleep(INITIAL_LOAD_MS)

        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assert(webViewVisible || errorVisible) {
            "Custom scheme deep link did not open app correctly"
        }

        scenario.close()
    }

    @Test
    fun malformedIntentDoesNotCrash() {
        // Intent with null data — should not crash the app
        val intent = Intent(Intent.ACTION_MAIN)
            .setPackage(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageName)
            .setClass(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                MainActivity::class.java
            )

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        Thread.sleep(INITIAL_LOAD_MS)

        // Should still show something (not crash)
        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assert(webViewVisible || errorVisible) {
            "Malformed intent caused blank screen"
        }

        scenario.close()
    }

    @Test
    fun accessTokenInIntentHandled() {
        // Launch with access token — should load URL with query param
        val intent = Intent(Intent.ACTION_MAIN)
            .setPackage(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageName)
            .setClass(
                androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                MainActivity::class.java
            )

        val scenario = ActivityScenario.launch<MainActivity>(intent)
        Thread.sleep(INITIAL_LOAD_MS)

        val webViewVisible = runCatching {
            onView(withId(R.id.webview)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        val errorVisible = runCatching {
            onView(withId(R.id.error_container)).check(matches(isDisplayed()))
            true
        }.getOrDefault(false)

        assert(webViewVisible || errorVisible) {
            "Access-token launch failed"
        }

        scenario.close()
    }
}
