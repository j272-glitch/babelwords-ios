package com.lingualink.linguagt

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.JavascriptInterface
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.lingualink.linguagt.ads.AdMobManager

/**
 * MainActivity with TestRigor enhancements
 * 
 * TESTRIGOR FEATURES:
 * - JavaScript bridge for state inspection
 * - Data attributes on WebView elements
 * - Configurable debounce timing
 * - Test mode support
 * - Comprehensive logging
 */
class MainActivity : BaseActivity() {

    companion object {
        @JvmStatic
        var tracker: UserActivityTracker? = null
        private const val BASE_URL = "https://linguagt.com"

        // TESTRIGOR: Configurable debounce - longer in debug builds
        private val MIC_DEBOUNCE_MS = if (BuildConfig.DEBUG) 1000L else 500L

        // TESTRIGOR: Test mode flag (can be set by instrumentation)
        @JvmStatic
        var isTestMode = false
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

    // TESTRIGOR FIX: Debounce rapid microphone clicks
    private var lastMicClickTime = 0L

    fun getWebView(): WebView = webView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleHandler = LifecycleAwareHandler(this)
        permissionManager = SafePermissionManager(this, isTestMode)
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

    private fun handleDeepLink(intent: Intent?) {
        // TESTRIGOR FIX: Guard against null intents from instrumentation
        if (intent == null) {
            TestRigorLogger.logWarning("Null intent in handleDeepLink - TestRigor instrumentation?")
            loadDefaultUrl()
            return
        }

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
        TestRigorLogger.logMilestone("WebView loadUrl starting")
        val defaultUrl = pendingDeepLinkUrl ?: BASE_URL
        if (::webView.isInitialized && isSafeToUpdateUI()) {
            webView.loadUrl(defaultUrl)
            TestRigorLogger.logMilestone("WebView loadUrl completed: $defaultUrl")
        } else {
            TestRigorLogger.logWarning("Cannot loadUrl - WebView not ready or UI unsafe")
        }
    }

    /**
     * REFACTORED: Now uses SafePermissionManager consistently
     */
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        permissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == 
                           PackageManager.PERMISSION_GRANTED
            TestRigorLogger.logPermission(permission, isGranted, false)
        }

        // Request microphone permission through SafePermissionManager
        if (!permissionManager.hasMicrophonePermission()) {
            permissionManager.requestMicrophonePermission { granted ->
                TestRigorLogger.logPermission(Manifest.permission.RECORD_AUDIO, granted, true)
            }
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

        // TESTRIGOR: Add test inspection bridge
        if (BuildConfig.DEBUG) {
            webView.addJavascriptInterface(TestRigorBridge(), "TestRigorBridge")
            TestRigorLogger.logMilestone("TestRigor JavaScript bridge enabled")
        }

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
                TestRigorLogger.logWarning("Permission canceled by WebView")
                // TESTRIGOR FIX: Clean up permission state when WebView retracts
                if (pendingPermissionRequest == request) {
                    pendingPermissionRequest = null
                    isWaitingForAndroidPermission = false
                    permissionManager.cleanupPendingRequests()
                }
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
     * REFACTORED: Now uses SafePermissionManager consistently
     * TESTRIGOR: Enhanced with test mode support
     */
    @android.annotation.SuppressLint("InlinedApi")
    private fun handleWebViewAudioPermission(request: PermissionRequest) {
        // TESTRIGOR: In test mode, auto-grant without system permission
        if (isTestMode) {
            TestRigorLogger.logMilestone("TEST MODE: Auto-granting WebView permission")
            lifecycleHandler.post {
                try {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    TestRigorLogger.logMilestone("TEST MODE: Audio granted")
                } catch (e: Exception) {
                    TestRigorLogger.logError("TEST MODE: Grant failed", e)
                }
            }
            return
        }

        // TESTRIGOR FIX: Debounce rapid microphone clicks
        val now = System.currentTimeMillis()
        if (now - lastMicClickTime < MIC_DEBOUNCE_MS) {
            TestRigorLogger.logWarning("Mic permission request debounced - too rapid (${now - lastMicClickTime}ms < ${MIC_DEBOUNCE_MS}ms)")
            return
        }
        lastMicClickTime = now

        TestRigorLogger.logMilestone("Handling audio permission request")

        if (permissionManager.hasMicrophonePermission()) {
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
            // Request Android permission first using SafePermissionManager
            TestRigorLogger.logDebug("Requesting Android mic permission")

            pendingPermissionRequest = request
            isWaitingForAndroidPermission = true

            permissionManager.requestMicrophonePermission { granted ->
                handleMicrophonePermissionResult(granted)
            }
        }
    }

    /**
     * REFACTORED: Simplified - now just handles the result
     */
    private fun handleMicrophonePermissionResult(granted: Boolean) {
        TestRigorLogger.logPermission(Manifest.permission.RECORD_AUDIO, granted, true)

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
     * TESTRIGOR ENHANCED: Inject detection script with data attributes
     */
    private fun injectMicrophoneDetectionScript(view: WebView?) {
        val script = """
            (function() {
                console.log('MICROPHONE: Detection loaded');

                // TESTRIGOR: Set initial state markers
                if (!document.body.hasAttribute('data-testrigor-ready')) {
                    document.body.setAttribute('data-testrigor-ready', 'false');
                    document.body.setAttribute('data-testrigor-permission-state', 'unknown');
                    document.body.setAttribute('data-testrigor-mic-clicks', '0');
                }

                if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
                    const original = navigator.mediaDevices.getUserMedia;

                    navigator.mediaDevices.getUserMedia = function(constraints) {
                        console.log('MICROPHONE: getUserMedia called:', JSON.stringify(constraints));

                        // TESTRIGOR: Update state marker
                        document.body.setAttribute('data-testrigor-permission-state', 'requesting');
                        const clicks = parseInt(document.body.getAttribute('data-testrigor-mic-clicks') || '0');
                        document.body.setAttribute('data-testrigor-mic-clicks', (clicks + 1).toString());

                        return original.call(navigator.mediaDevices, constraints)
                            .then(stream => {
                                console.log('MICROPHONE: Stream OK, audio tracks:', stream.getAudioTracks().length);

                                // TESTRIGOR: Update state marker
                                document.body.setAttribute('data-testrigor-permission-state', 'granted');
                                document.body.setAttribute('data-testrigor-stream-active', 'true');

                                return stream;
                            })
                            .catch(error => {
                                console.error('MICROPHONE: Error:', error.name, error.message);

                                // TESTRIGOR: Update state marker
                                document.body.setAttribute('data-testrigor-permission-state', 'denied');
                                document.body.setAttribute('data-testrigor-error', error.name);

                                throw error;
                            });
                    };
                }

                // TESTRIGOR: Mark as ready when DOM loads
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', function() {
                        document.body.setAttribute('data-testrigor-ready', 'true');
                        console.log('TESTRIGOR: Ready');
                    });
                } else {
                    document.body.setAttribute('data-testrigor-ready', 'true');
                    console.log('TESTRIGOR: Ready (already loaded)');
                }

                // TESTRIGOR: Expose state query function
                window.TestRigorState = {
                    getPermissionState: function() {
                        return document.body.getAttribute('data-testrigor-permission-state') || 'unknown';
                    },
                    getMicClicks: function() {
                        return parseInt(document.body.getAttribute('data-testrigor-mic-clicks') || '0');
                    },
                    isReady: function() {
                        return document.body.getAttribute('data-testrigor-ready') === 'true';
                    },
                    isStreamActive: function() {
                        return document.body.getAttribute('data-testrigor-stream-active') === 'true';
                    }
                };
            })();
        """

        view?.evaluateJavascript(script) {
            TestRigorLogger.logDebug("TestRigor detection script injected")
        }
    }

    /**
     * TESTRIGOR: JavaScript bridge for state inspection
     */
    inner class TestRigorBridge {
        @JavascriptInterface
        fun getPermissionState(): String {
            return """
                {
                    "hasMicPermission": ${permissionManager.hasMicrophonePermission()},
                    "isPendingRequest": $isWaitingForAndroidPermission,
                    "webViewInitialized": ${::webView.isInitialized},
                    "isTestMode": $isTestMode,
                    "debounceMs": $MIC_DEBOUNCE_MS,
                    "timeSinceLastClick": ${System.currentTimeMillis() - lastMicClickTime},
                    "activityFinishing": $isFinishing,
                    "activityDestroyed": $isDestroyed
                }
            """.trimIndent()
        }

        @JavascriptInterface
        fun enableTestMode() {
            isTestMode = true
            TestRigorLogger.logMilestone("Test mode ENABLED via JavaScript")
        }

        @JavascriptInterface
        fun disableTestMode() {
            isTestMode = false
            TestRigorLogger.logMilestone("Test mode DISABLED via JavaScript")
        }

        @JavascriptInterface
        fun resetDebounce() {
            lastMicClickTime = 0L
            TestRigorLogger.logDebug("Debounce timer reset via JavaScript")
        }
    }

    /**
     * REFACTORED: Now delegates to SafePermissionManager
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        permissionManager.handlePermissionResult(requestCode, permissions, grantResults) { granted ->
            // Callback handled automatically by SafePermissionManager
        }
    }

    override fun onResume() {
        super.onResume()
        tracker?.sendUserActivity(appId)
        if (::webView.isInitialized) {
            webView.onResume()
        }

        // TESTRIGOR FIX: Re-verify pending permission state on resume
        if (pendingPermissionRequest != null && isWaitingForAndroidPermission) {
            if (permissionManager.hasMicrophonePermission()) {
                TestRigorLogger.logDebug("Permission granted while backgrounded - handling now")
                handleMicrophonePermissionResult(true)
            }
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

        // TESTRIGOR FIX: Clear pending permission state
        pendingPermissionRequest = null
        isWaitingForAndroidPermission = false
        permissionManager.cleanupPendingRequests()

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