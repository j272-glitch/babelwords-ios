package com.lingualink.linguagt

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class LinguaLinkApplication : Application(), DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "LinguaLinkApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application initialized - WebView mode for Testrigor compatibility")
        
        // Setup global exception handler
        setupGlobalExceptionHandler()
        
        // Initialize any global settings here
        setupWebViewDefaults()
        
        // Register lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    private fun setupWebViewDefaults() {
        try {
            // Enable WebView debugging for development (disable in production)
            if (BuildConfig.DEBUG) {
                android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                Log.d(TAG, "WebView debugging enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup WebView defaults: ${e.message}")
        }
    }
    
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            TestRigorLogger.logError("UncaughtException_${thread.name}", throwable)
            // Call default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App moved to foreground")
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "App moved to background")
    }
}
