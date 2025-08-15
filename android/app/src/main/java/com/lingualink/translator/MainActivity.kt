package com.lingualink.translator

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : BaseActivity() {
    
    companion object {
        @JvmStatic
        var tracker: UserActivityTracker? = null
    }
    
    private val appId = "app_id" // copy your app id from dashboard
    
    private lateinit var webView: WebView
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            if (isGranted) {
                // Permission granted
            } else {
                // Permission denied - handle accordingly
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request necessary permissions
        requestPermissions()
        
        // Initialize UserActivityTracker
        tracker = UserActivityTracker(this, appId)
        tracker?.sendUserActivity(appId)
        tracker?.getTesterMob()
        
        // Initialize TesterMobLib (if it has initialization methods)
        initializeTesterMobLib()
        
        // Set up WebView to load the LinguaLink web app
        setupWebView()
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun initializeTesterMobLib() {
        // Initialize TesterMobLib here
        // This will depend on the specific API provided by TesterMobLib.aar
        try {
            // Start tracking session
            tracker?.startSession()
            tracker?.trackActivity("MainActivity")
            
            // Additional TesterMobLib initialization
            // TesterMobLib.initialize(this)
            // TesterMobLib.setConfiguration(config)
            
            println("TesterMobLib and UserActivityTracker initialized successfully")
        } catch (e: Exception) {
            println("Failed to initialize TesterMobLib: ${e.message}")
        }
    }
    
    private fun setupWebView() {
        webView = WebView(this)
        
        // Enable JavaScript and other web features
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        
        // Enable media playback
        webSettings.mediaPlaybackRequiresUserGesture = false
        
        // Set up WebView clients
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false // Let WebView handle the URL
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        
        // Load the LinguaLink web application from Replit
        val webAppUrl = "https://b74c4c68-0c5b-42df-9cdb-c158e6a65d80-00-9dkf2rm3ayxq.kirk.replit.dev"
        println("Loading LinguaLink web app from: $webAppUrl")
        
        // Add error handling and debugging
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                println("WebView started loading: $url")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                println("WebView finished loading: $url")
                super.onPageFinished(view, url)
            }
            
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                println("WebView error: ${error?.description}")
                super.onReceivedError(view, request, error)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false // Let WebView handle the URL
            }
        }
        
        webView.loadUrl(webAppUrl)
        
        setContentView(webView)
    }
    
    override fun onResume() {
        super.onResume()
        tracker?.sendUserActivity(appId)
    }
    
    override fun onStart() {
        super.onStart()
        tracker?.startTracking()
    }
    
    override fun onStop() {
        super.onStop()
        tracker?.stopTracking()
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // End tracking session
        tracker?.endSession()
        
        webView.destroy()
    }
}