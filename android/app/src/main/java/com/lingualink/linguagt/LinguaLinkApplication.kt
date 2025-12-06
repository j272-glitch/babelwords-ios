package com.lingualink.linguagt

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Main Application class for LinguaLink
 * Handles global initialization and lifecycle management
 */
class LinguaLinkApplication : Application(), DefaultLifecycleObserver {

    companion object {
        private const val TAG = "LinguaLinkApplication"
    }

    override fun onCreate() {
        super<Application>.onCreate()  // Fixed: Explicit super type to avoid ambiguity
        Log.d(TAG, "Application initialized - WebView mode for TestRigor compatibility")

        // Setup global exception handler
        setupGlobalExceptionHandler()

        // Initialize any global settings here
        setupWebViewDefaults()

        // Register lifecycle observer to track app foreground/background state
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Configure WebView settings for the entire application
     * TESTRIGOR FIX: Conditional debugging based on build type and test mode
     */
    private fun setupWebViewDefaults() {
        try {
            // Enable WebView debugging for development (disable in production and TestRigor)
            val shouldEnableDebugging = BuildConfig.DEBUG && !isRunningInTestRigor()
            if (shouldEnableDebugging) {
                android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                Log.d(TAG, "WebView debugging enabled")
            } else {
                android.webkit.WebView.setWebContentsDebuggingEnabled(false)
                Log.d(TAG, "WebView debugging disabled (release or TestRigor mode)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup WebView defaults: ${e.message}")
        }
    }
    
    /**
     * Check if running in TestRigor automated testing environment
     */
    private fun isRunningInTestRigor(): Boolean {
        return System.getProperty("testRigor") == "true" ||
               System.getenv("TESTRIGOR") == "true"
    }

    /**
     * Setup global exception handler for better crash reporting
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)

            // Log to TestRigor if logger is available
            try {
                TestRigorLogger.logError("UncaughtException_${thread.name}", throwable)
            } catch (e: Exception) {
                // TestRigorLogger might not be initialized yet
                Log.e(TAG, "Failed to log to TestRigor: ${e.message}")
            }

            // Call default handler to maintain normal crash behavior
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // Lifecycle observer callbacks

    override fun onStart(owner: LifecycleOwner) {
        // No super call needed - DefaultLifecycleObserver default implementation is empty
        Log.d(TAG, "App moved to foreground")
    }

    override fun onStop(owner: LifecycleOwner) {
        // No super call needed - DefaultLifecycleObserver default implementation is empty
        Log.d(TAG, "App moved to background")
    }
}
