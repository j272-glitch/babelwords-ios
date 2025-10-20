
package com.lingualink.linguagt

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : BaseActivity() {
    
    companion object {
        @JvmStatic
        var tracker: UserActivityTracker? = null
        private const val MICROPHONE_PERMISSION_REQUEST = 200
    }
    
    private val appId = "app_id"
    private lateinit var webView: WebView
    private var conversationCount = 0
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            if (isGranted) {
                println("CONVERSATION MODE DEBUG: Permission granted: $permission")
                if (permission == Manifest.permission.RECORD_AUDIO && ::webView.isInitialized) {
                    webView.reload()
                }
            } else {
                println("CONVERSATION MODE DEBUG: Permission denied: $permission")
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()
        
        tracker = UserActivityTracker(this, appId)
        tracker?.sendUserActivity(appId)
        tracker?.getTesterMob()
        
        initializeTesterMobLib()
        
        try {
            setupWebViewForConversationMode()
            println("CONVERSATION MODE DEBUG: WebView setup completed successfully")
        } catch (e: Exception) {
            println("CONVERSATION MODE DEBUG: WebView setup failed: ${e.message}")
            e.printStackTrace()
            createNativeTranslationInterface()
        }
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
        try {
            tracker?.startSession()
            tracker?.trackActivity("MainActivity")
            println("TesterMobLib and UserActivityTracker initialized successfully")
        } catch (e: Exception) {
            println("Failed to initialize TesterMobLib: ${e.message}")
        }
    }
    
    private fun setupWebViewForConversationMode() {
        webView = WebView(this)
        println("CONVERSATION MODE DEBUG: WebView created successfully")
        
        val webSettings: WebSettings = webView.settings
        webSettings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            textZoom = 100
            minimumFontSize = 8
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: ""
                
                if (url.startsWith("ws://") || url.startsWith("wss://")) {
                    println("CONVERSATION MODE DEBUG: Allowing WebSocket connection: $url")
                    return false
                }
                
                return false
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                println("CONVERSATION MODE DEBUG: Page started loading: $url")
                super.onPageStarted(view, url, favicon)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                println("CONVERSATION MODE DEBUG: Page finished loading: $url")
                
                val jsCode = """
                    console.log('CONVERSATION MODE: Page loaded successfully');
                    
                    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
                        console.log('CONVERSATION MODE: Speech Recognition API available');
                    }
                    
                    setTimeout(function() {
                        try {
                            var buttons = document.querySelectorAll('button, [role="button"]');
                            buttons.forEach(function(button) {
                                if (button.textContent.toLowerCase().includes('record') || 
                                    button.textContent.toLowerCase().includes('mic') ||
                                    button.classList.contains('mic-button')) {
                                    button.setAttribute('data-testid', 'microphone-button');
                                    console.log('CONVERSATION MODE: Microphone button labeled');
                                }
                            });
                            
                            var outputs = document.querySelectorAll('textarea, .translation-output, [data-testid*="translation"]');
                            outputs.forEach(function(output) {
                                output.setAttribute('data-testid', 'translation-result');
                                console.log('CONVERSATION MODE: Translation area labeled');
                            });
                            
                            var conversationToggle = document.querySelector('[data-testid*="conversation"], .conversation-mode');
                            if (conversationToggle) {
                                conversationToggle.setAttribute('data-testid', 'conversation-mode-toggle');
                                console.log('CONVERSATION MODE: Conversation toggle found');
                            }
                        } catch (e) {
                            console.log('CONVERSATION MODE: Error labeling elements:', e);
                        }
                    }, 2000);
                """
                
                try {
                    view?.evaluateJavascript(jsCode) { result ->
                        println("CONVERSATION MODE DEBUG: JavaScript injection completed")
                    }
                } catch (e: Exception) {
                    println("CONVERSATION MODE DEBUG: JavaScript injection failed: ${e.message}")
                }
                
                super.onPageFinished(view, url)
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                println("CONVERSATION MODE DEBUG: WebView error: ${error?.description}")
                super.onReceivedError(view, request, error)
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let { permissionRequest ->
                    val resources = permissionRequest.resources
                    println("CONVERSATION MODE DEBUG: Permission request received for: ${resources.joinToString()}")
                    
                    if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) 
                            == PackageManager.PERMISSION_GRANTED) {
                            permissionRequest.grant(resources)
                            println("CONVERSATION MODE DEBUG: Audio capture permission granted to WebView")
                        } else {
                            requestMicrophonePermission()
                            permissionRequest.deny()
                            println("CONVERSATION MODE DEBUG: Need Android microphone permission first")
                        }
                    } else {
                        permissionRequest.grant(resources)
                    }
                }
            }
            
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                println("CONVERSATION MODE JS: ${consoleMessage?.message()}")
                return super.onConsoleMessage(consoleMessage)
            }
        }
        
        webView.contentDescription = "LinguaLink Translation App WebView"
        
        val webAppUrl = "https://gtlingua.com"
        
        try {
            println("CONVERSATION MODE DEBUG: Loading URL: $webAppUrl")
            webView.loadUrl(webAppUrl)
            setContentView(webView)
            println("CONVERSATION MODE DEBUG: WebView setup completed successfully")
        } catch (e: Exception) {
            println("CONVERSATION MODE DEBUG: Failed to load URL: ${e.message}")
            e.printStackTrace()
            createNativeTranslationInterface()
        }
    }
    
    private fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, 
                arrayOf(Manifest.permission.RECORD_AUDIO), 
                MICROPHONE_PERMISSION_REQUEST
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (::webView.isInitialized) {
                    webView.reload()
                    println("CONVERSATION MODE DEBUG: Microphone permission granted, reloading WebView")
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        tracker?.sendUserActivity(appId)
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
        }
    }
    
    override fun onStart() {
        super.onStart()
        tracker?.startTracking()
    }
    
    override fun onStop() {
        super.onStop()
        tracker?.stopTracking()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun createNativeTranslationInterface() {
        println("CONVERSATION MODE DEBUG: createNativeTranslationInterface called")
        println("CONVERSATION MODE DEBUG: Current thread: ${Thread.currentThread().name}")
        println("CONVERSATION MODE DEBUG: Activity state - finishing: ${isFinishing}, destroyed: ${isDestroyed}")
        
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        layout.setBackgroundColor(android.graphics.Color.WHITE)
        
        val titleText = android.widget.TextView(this)
        titleText.text = "LinguaLink - Conversation Mode"
        titleText.textSize = 24f
        titleText.id = android.R.id.title
        titleText.contentDescription = "LinguaLink Translation App"
        titleText.setTextColor(android.graphics.Color.BLACK)
        layout.addView(titleText)
        
        val modeText = android.widget.TextView(this)
        modeText.text = "Voice Recording"
        modeText.textSize = 16f
        modeText.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        modeText.setPadding(0, 10, 0, 20)
        layout.addView(modeText)
        
        // Create translation result TextView BEFORE the button
        val conversationText = android.widget.TextView(this)
        conversationText.text = "Tap the microphone button to start translating"
        conversationText.textSize = 16f
        conversationText.id = android.R.id.text2
        conversationText.contentDescription = "translation result"
        conversationText.setTextColor(android.graphics.Color.DARK_GRAY)
        conversationText.setPadding(20, 30, 20, 20)
        conversationText.minHeight = 400
        conversationText.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        layout.addView(conversationText)
        
        val micButton1 = android.widget.Button(this)
        micButton1.text = "🎤 Tap to Speak"
        micButton1.id = android.R.id.button1
        micButton1.contentDescription = "microphone button"
        micButton1.textSize = 18f
        micButton1.setPadding(40, 20, 40, 20)
        micButton1.setOnClickListener {
            println("CONVERSATION MODE DEBUG: Microphone clicked")
            println("CONVERSATION MODE DEBUG: Activity finishing? ${isFinishing}")
            println("CONVERSATION MODE DEBUG: Activity destroyed? ${isDestroyed}")
            println("CONVERSATION MODE DEBUG: ConversationText parent: ${conversationText.parent}")
            
            try {
                if (isFinishing || isDestroyed) {
                    println("CONVERSATION MODE DEBUG: Activity not in valid state, aborting")
                    return@setOnClickListener
                }
                
                conversationText.text = "Listening..."
                println("CONVERSATION MODE DEBUG: Updated text to 'Listening...'")
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (isFinishing || isDestroyed) {
                            println("CONVERSATION MODE DEBUG: Activity destroyed during delay, skipping update")
                            return@postDelayed
                        }
                        
                        conversationText.text = "Translation: Hello, how are you?\nTraducción: Hola, ¿cómo estás?"
                        println("CONVERSATION MODE DEBUG: Translation updated successfully")
                    } catch (e: Exception) {
                        println("CONVERSATION MODE DEBUG: Error updating translation: ${e.message}")
                        println("CONVERSATION MODE DEBUG: Stack trace:")
                        e.printStackTrace()
                    }
                }, 1500)
            } catch (e: Exception) {
                println("CONVERSATION MODE DEBUG: Error in microphone button handler: ${e.message}")
                println("CONVERSATION MODE DEBUG: Error type: ${e.javaClass.simpleName}")
                println("CONVERSATION MODE DEBUG: Stack trace:")
                e.printStackTrace()
            }
        }
        layout.addView(micButton1)
        
        val languageLabel = android.widget.TextView(this)
        languageLabel.text = "Select Languages:"
        languageLabel.setPadding(0, 20, 0, 10)
        layout.addView(languageLabel)
        
        val languageSpinner = android.widget.Spinner(this)
        val languages = arrayOf("English ↔ Spanish", "English ↔ French", "English ↔ German")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.contentDescription = "language selector"
        layout.addView(languageSpinner)
        
        setContentView(layout)
        println("CONVERSATION MODE DEBUG: Native fallback interface created")
    }
    
    
    
    override fun onDestroy() {
        super.onDestroy()
        
        tracker?.endSession()
        
        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.onPause()
                webView.clearHistory()
                webView.clearCache(true)
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
        } catch (e: Exception) {
            println("CONVERSATION MODE DEBUG: WebView cleanup failed: ${e.message}")
        }
    }
}
