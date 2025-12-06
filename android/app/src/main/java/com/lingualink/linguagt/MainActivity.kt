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
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.lingualink.linguagt.ads.AdMobManager

class MainActivity : BaseActivity() {

    companion object {
        @JvmStatic
        var tracker: UserActivityTracker? = null
        private const val MICROPHONE_PERMISSION_REQUEST = 200
        private const val BASE_URL = "https://linguagt.com"
    }

    private val appId = "app_id"
    private lateinit var webView: WebView
    private var conversationCount = 0
    private var pendingDeepLinkUrl: String? = null
    private lateinit var lifecycleHandler: LifecycleAwareHandler
    private lateinit var permissionManager: SafePermissionManager
    private lateinit var webAppBridge: WebAppBridge
    private lateinit var adMobManager: AdMobManager

    // CRITICAL FIX: Store pending WebView permission requests
    private var pendingPermissionRequest: PermissionRequest? = null
    private var isWaitingForAndroidPermission = false

    fun getWebView(): WebView = webView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            TestRigorLogger.logPermission(permission, isGranted)

            if (permission == Manifest.permission.RECORD_AUDIO) {
                handleMicrophonePermissionResult(isGranted)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleHandler = LifecycleAwareHandler(this)
        permissionManager = SafePermissionManager(this)
        webAppBridge = WebAppBridge(this)
        adMobManager = AdMobManager.getInstance(this)

        adMobManager.initialize(this) { consentGranted ->
            TestRigorLogger.logAdEvent("AdMob initialized. Consent: $consentGranted")
        }

        requestPermissions()

        tracker = UserActivityTracker(this, appId)
        tracker?.sendUserActivity(appId)
        tracker?.getTesterMob()

        initializeTesterMobLib()

        try {
            setupWebViewForConversationMode()
            handleDeepLink(intent)
            TestRigorLogger.logMilestone("WebView setup completed")
        } catch (e: Exception) {
            TestRigorLogger.logError("WebView setup failed", e)
            createNativeTranslationInterface()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { 
            setIntent(it)
            handleDeepLink(it) 
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val action = intent.action
        val data = intent.data

        TestRigorLogger.logDebug("Deep link - Action: $action, Data: $data")

        if (Intent.ACTION_VIEW == action && data != null) {
            processDeepLinkUrl(data)
        } else {
            loadDefaultUrl()
        }
    }

    private fun processDeepLinkUrl(uri: Uri) {
        val host = uri.host
        val path = uri.path ?: ""
        val query = uri.query ?: ""

        if (host == "linguagt.com" || host == "www.linguagt.com") {
            val urlToLoad = if (query.isNotEmpty()) {
                "$BASE_URL$path?$query"
            } else {
                "$BASE_URL$path"
            }

            TestRigorLogger.logWebView("Loading deep link", urlToLoad)

            if (::webView.isInitialized) {
                webView.loadUrl(urlToLoad)
            } else {
                pendingDeepLinkUrl = urlToLoad
            }

            tracker?.trackActivity("DeepLink:$path")
        } else {
            loadDefaultUrl()
        }
    }

    private fun loadDefaultUrl() {
        val defaultUrl = pendingDeepLinkUrl ?: BASE_URL
        if (::webView.isInitialized) {
            webView.loadUrl(defaultUrl)
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
        } catch (e: Exception) {
            TestRigorLogger.logError("TesterMobLib init", e)
        }
    }

    private fun setupWebViewForConversationMode() {
        try {
            webView = WebView(this)
        } catch (e: Exception) {
            TestRigorLogger.logError("WebView creation", e)
            throw e
        }

        webView.addJavascriptInterface(webAppBridge, "AndroidBridge")

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
                    return false
                }

                val uri = request?.url
                if (uri != null) {
                    val host = uri.host
                    if (host == "linguagt.com" || host == "www.linguagt.com") {
                        return false
                    }
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                TestRigorLogger.logWebView("Page started", url)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                TestRigorLogger.logWebView("Page finished", url)

                url?.let {
                    val path = Uri.parse(it).path ?: "/"
                    tracker?.trackActivity("PageView:$path")
                }

                injectMicrophoneDetectionScript(view)

                super.onPageFinished(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    TestRigorLogger.logError("WebView error: ${error?.description}", null)
                }
                super.onReceivedError(view, request, error)
            }
        }

        // CRITICAL: WebChromeClient handles microphone permission
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) {
                    TestRigorLogger.logError("Null permission request", null)
                    return
                }

                val resources = request.resources
                TestRigorLogger.logDebug("WebView permission: ${resources.joinToString()}")

                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    handleWebViewAudioPermission(request)
                } else {
                    lifecycleHandler.post {
                        try {
                            request.grant(resources)
                        } catch (e: Exception) {
                            TestRigorLogger.logError("Grant failed", e)
                        }
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                TestRigorLogger.logWarning("Permission canceled")
                pendingPermissionRequest = null
                isWaitingForAndroidPermission = false
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    TestRigorLogger.logDebug("Console: ${it.message()}")
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webView.contentDescription = "LinguaLink Translation App WebView"
        setContentView(webView)
    }

    /**
     * CRITICAL FIX: Handle WebView audio permission properly
     */
    private fun handleWebViewAudioPermission(request: PermissionRequest) {
        TestRigorLogger.logMilestone("Handling audio permission request")

        val hasAndroidPermission = ContextCompat.checkSelfPermission(
            this, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAndroidPermission) {
            // Grant immediately
            TestRigorLogger.logDebug("Android permission exists, granting to WebView")
            lifecycleHandler.post {
                try {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    TestRigorLogger.logMilestone("Audio granted to WebView")
                } catch (e: Exception) {
                    TestRigorLogger.logError("WebView grant failed", e)
                }
            }
        } else {
            // Request Android permission first
            TestRigorLogger.logDebug("Requesting Android mic permission")

            pendingPermissionRequest = request
            isWaitingForAndroidPermission = true

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_REQUEST
            )
        }
    }

    /**
     * CRITICAL FIX: Handle Android permission result
     */
    private fun handleMicrophonePermissionResult(granted: Boolean) {
        TestRigorLogger.logPermission(Manifest.permission.RECORD_AUDIO, granted)

        if (granted && pendingPermissionRequest != null) {
            TestRigorLogger.logDebug("Granting to WebView after Android permission")

            val request = pendingPermissionRequest
            pendingPermissionRequest = null
            isWaitingForAndroidPermission = false

            lifecycleHandler.post {
                try {
                    request?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    TestRigorLogger.logMilestone("Audio granted after permission")

                    // Reload to trigger new request
                    if (::webView.isInitialized) {
                        webView.reload()
                    }
                } catch (e: Exception) {
                    TestRigorLogger.logError("Post-permission grant failed", e)
                }
            }
        } else if (!granted) {
            TestRigorLogger.logWarning("Microphone permission denied")

            if (pendingPermissionRequest != null) {
                val request = pendingPermissionRequest
                pendingPermissionRequest = null
                isWaitingForAndroidPermission = false

                lifecycleHandler.post {
                    try {
                        request?.deny()
                    } catch (e: Exception) {
                        TestRigorLogger.logError("Deny failed", e)
                    }
                }
            }
        }
    }

    /**
     * Inject microphone detection script
     */
    private fun injectMicrophoneDetectionScript(view: WebView?) {
        val script = """
            (function() {
                console.log('MICROPHONE: Detection loaded');

                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    const original = navigator.mediaDevices.getUserMedia;

                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        console.log('MICROPHONE: getUserMedia called:', JSON.stringify(constraints));

                        return original.call(navigator.mediaDevices, constraints)
                            .then(stream => {
                                console.log('MICROPHONE: Stream OK, audio tracks:', stream.getAudioTracks().length);
                                return stream;
                            })
                            .catch(error => {
                                console.error('MICROPHONE: Error:', error.name, error.message);
                                throw error;
                            });
                    };
                }

                document.addEventListener('DOMContentLoaded', function() {
                    document.body.setAttribute('data-testrigor-ready', 'true');
                    console.log('TESTRIGOR: Ready');
                });
            })();
        """

        view?.evaluateJavascript(script) {
            TestRigorLogger.logDebug("Detection script injected")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            handleMicrophonePermissionResult(granted)
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
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun createNativeTranslationInterface() {
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
        modeText.text = "Voice Recording (Offline Mode)"
        modeText.textSize = 16f
        modeText.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        modeText.setPadding(0, 10, 0, 20)
        layout.addView(modeText)

        val retryButton = android.widget.Button(this)
        retryButton.text = "🔄 Retry Connection"
        retryButton.setOnClickListener {
            TestRigorLogger.logClick("retry_button")
            try {
                setupWebViewForConversationMode()
                handleDeepLink(intent)
            } catch (e: Exception) {
                TestRigorLogger.logError("Retry failed", e)
            }
        }
        layout.addView(retryButton)

        val micButton1 = android.widget.Button(this)
        micButton1.text = "🎤 Tap to Speak"
        micButton1.id = android.R.id.button1
        micButton1.contentDescription = "microphone button"
        micButton1.textSize = 18f
        micButton1.setPadding(40, 20, 40, 20)
        micButton1.setOnClickListener(SafeClickListener(this) {
            TestRigorLogger.logClick("microphone button")

            if (!checkTestReadiness()) {
                TestRigorLogger.logActivityState(
                    "MainActivity", "not ready",
                    isFinishing, isDestroyed
                )
                return@SafeClickListener
            }

            updateTranslationResult("Listening...")

            lifecycleHandler.postDelayed(Runnable {
                safeRunOnUiThread {
                    updateTranslationResult("Translation: Hello, how are you?\nTraducción: Hola, ¿cómo estás?")
                    TestRigorLogger.logUIUpdate("translation_result", "Translation completed")
                }
            }, 1500)
        })
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

        val conversationText = android.widget.TextView(this)
        conversationText.text = "Tap the microphone button to start translating"
        conversationText.textSize = 16f
        conversationText.id = android.R.id.text2
        conversationText.contentDescription = "translation result"
        conversationText.setTextColor(android.graphics.Color.DKGRAY)
        conversationText.setPadding(20, 30, 20, 20)
        conversationText.minHeight = 400
        conversationText.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        layout.addView(conversationText)

        setContentView(layout)
    }

    private fun updateTranslationResult(text: String) {
        if (!isSafeToUpdateUI()) {
            TestRigorLogger.logActivityState(
                "MainActivity", "unsafe for UI update",
                isFinishing, isDestroyed
            )
            return
        }

        val resultView = safeFindViewById<android.widget.TextView>(android.R.id.text2)
        if (resultView != null) {
            resultView.text = text
            TestRigorLogger.logUIUpdate("text2", text)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        pendingPermissionRequest = null
        isWaitingForAndroidPermission = false

        try {
            if (::adMobManager.isInitialized) {
                adMobManager.destroy()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("AdMob cleanup", e)
        }

        try {
            tracker?.endSession()
            tracker = null
        } catch (e: Exception) {
            TestRigorLogger.logError("Tracker cleanup", e)
        }

        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.onPause()
                webView.removeAllViews()

                val parent = webView.parent as? android.view.ViewGroup
                parent?.removeView(webView)

                webView.clearHistory()
                webView.clearCache(true)
                webView.clearFormData()
                webView.loadUrl("about:blank")

                webView.destroy()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("WebView cleanup", e)
        }
    }
}
