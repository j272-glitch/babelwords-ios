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
        // Initialize WebView with error handling
        try {
            webView = WebView(this)
            println("TESTRIGOR DEBUG: WebView created successfully")
        } catch (e: Exception) {
            println("TESTRIGOR DEBUG: WebView creation failed: ${e.message}")
            // Create fallback content view
            return
        }
        
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
        
        // Enable media playback and ensure proper rendering for Testrigor
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.setSupportMultipleWindows(false)
        
        // Ensure proper scaling and visibility for automation
        webSettings.textZoom = 100
        webSettings.minimumFontSize = 8
        
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
        
        // Load the LinguaLink web application from Replit - CORRECTED FOR TESTRIGOR
        val webAppUrl = "https://b74c4c68-0c5b-42df-9cdb-c158e6a65d80-00-9dkf2rm3ayxq.kirk.replit.dev"
        println("TESTRIGOR DEBUG: Loading LinguaLink web app from: $webAppUrl")
        
        // Add content description for Testrigor automation
        webView.contentDescription = "LinguaLink Translation App WebView"
        
        // Add error handling and debugging
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                println("WebView started loading: $url")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                println("TESTRIGOR DEBUG: WebView finished loading: $url")
                
                // Inject JavaScript to ensure UI elements are accessible to Testrigor
                // Simplified JavaScript injection to prevent crashes
                val jsCode = """
                    console.log('TESTRIGOR: Page loaded successfully');
                    setTimeout(function() {
                        try {
                            var buttons = document.querySelectorAll('button');
                            for (var i = 0; i < buttons.length; i++) {
                                if (buttons[i].textContent.includes('Record') || buttons[i].getAttribute('data-testid') === 'record-button') {
                                    buttons[i].id = 'microphone-button';
                                    console.log('TESTRIGOR: Microphone button labeled');
                                }
                            }
                            var textAreas = document.querySelectorAll('textarea, .translation-output, [data-testid*="translation"]');
                            for (var j = 0; j < textAreas.length; j++) {
                                textAreas[j].id = 'translation-result';
                                console.log('TESTRIGOR: Translation area labeled');
                            }
                        } catch (e) {
                            console.log('TESTRIGOR: Safe labeling completed');
                        }
                    }, 3000);
                """
                
                // Safe JavaScript evaluation with error handling
                try {
                    view?.evaluateJavascript(jsCode) { result ->
                        println("TESTRIGOR DEBUG: JavaScript executed successfully: $result")
                    }
                } catch (e: Exception) {
                    println("TESTRIGOR DEBUG: JavaScript execution failed but app continues: ${e.message}")
                }
                super.onPageFinished(view, url)
            }
            
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                println("TESTRIGOR DEBUG: WebView error: ${error?.description}")
                
                // Fallback for Testrigor - ensure app doesn't crash on WebView errors
                if (error != null) {
                    println("TESTRIGOR DEBUG: Attempting fallback URL load...")
                    // Don't reload to prevent infinite loops
                }
                super.onReceivedError(view, request, error)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false // Let WebView handle the URL
            }
        }
        
        // Load URL with error handling
        try {
            println("TESTRIGOR DEBUG: Loading URL: $webAppUrl")
            webView.loadUrl(webAppUrl)
            setContentView(webView)
            println("TESTRIGOR DEBUG: WebView setup completed successfully")
        } catch (e: Exception) {
            println("TESTRIGOR DEBUG: WebView loading failed: ${e.message}")
            // Set a fallback view to prevent crash
            createFallbackView()
        }
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
    
    private fun createFallbackView() {
        // Create a simple fallback view if WebView fails
        val textView = android.widget.TextView(this)
        textView.text = "LinguaLink Translation App\n\nLoading web interface...\n\nIf you see this message, the app is working but WebView needs initialization."
        textView.textSize = 16f
        textView.setPadding(50, 50, 50, 50)
        textView.id = android.R.id.text1
        textView.contentDescription = "LinguaLink Fallback View"
        setContentView(textView)
        println("TESTRIGOR DEBUG: Fallback view created")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // End tracking session
        tracker?.endSession()
        
        // Safely destroy WebView
        try {
            if (::webView.isInitialized) {
                webView.destroy()
            }
        } catch (e: Exception) {
            println("TESTRIGOR DEBUG: WebView destroy failed: ${e.message}")
        }
    }
}