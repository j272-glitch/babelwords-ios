
package com.lingualink.linguagt

import android.app.Application
import android.util.Log

class LinguaLinkApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d("LinguaLink", "Application initialized - WebView mode for Testrigor compatibility")
        
        // Initialize any global settings here
        setupWebViewDefaults()
    }
    
    private fun setupWebViewDefaults() {
        try {
            // Enable WebView debugging for development
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
            Log.d("LinguaLink", "WebView debugging enabled")
        } catch (e: Exception) {
            Log.e("LinguaLink", "Failed to setup WebView defaults: ${e.message}")
        }
    }
}
