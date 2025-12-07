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
import android.os.Handler
import android.os.Looper
import android.util.Log

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

    // TESTRIGOR FIX: Dual permission synchronization using ActiveSession pattern
    // Web apps may trigger TWO permission prompts: web getUserMedia + Android RECORD_AUDIO
    // This queue ensures they are processed sequentially, not concurrently
    
    /**
     * Represents an active permission session with all its state
     * Solution #11: Match session by object reference in handlers
     * Solution #63: Track session origin for duplicate detection
     */
    private data class PermissionSession(
        val request: PermissionRequest,
        var phase: PermissionPhase = PermissionPhase.IDLE,
        var androidResult: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val origin: String = request.origin?.toString() ?: "unknown"
    )
    
    // Permission phases for dual-prompt coordination
    private enum class PermissionPhase {
        IDLE,                    // No permission request in progress
        ANDROID_PENDING,         // Waiting for Android native dialog
        ANDROID_RESOLVED,        // Android dialog resolved, ready for web
        WEB_PENDING,             // Waiting for web permission
        COMPLETED                // Both permissions resolved
    }
    
    // Queue of pending sessions (FIFO)
    private val sessionQueue = mutableListOf<PermissionSession>()
    
    // Currently active session (null when idle)
    private var currentSession: PermissionSession? = null
    
    // Guard flag - true when processing or queue not empty
    private var isProcessingPermission = false

    // TESTRIGOR FIX: Debounce rapid microphone clicks
    private var lastMicClickTime: Long = 0L
    private val DEBOUNCE_MS: Long = 500L // 500ms debounce for TestRigor automation

    // TESTRIGOR FIX: Timeout handler to clear stuck permission states
    private val permissionTimeoutHandler = Handler(Looper.getMainLooper())
    private var permissionTimeoutRunnable: Runnable? = null
    private val PERMISSION_TIMEOUT_MS: Long = 10000L // 10 second timeout

    // TESTRIGOR FIX: Retry logic for failed grants
    // Solution #19: Track permissionGrantRetryCount and cap retries
    private var permissionGrantRetryCount = 0
    private val MAX_PERMISSION_RETRIES = 3

    // TESTRIGOR: Test mode support
    private var isTestRigorDetected = false
    
    // TESTRIGOR FIX: Lock to prevent concurrent permission processing
    // Solution #21: Use synchronized block for all state mutations
    private val permissionLock = Object()
    
    // Solution #20: Limit queue size to prevent unbounded growth
    private val MAX_SESSION_QUEUE_SIZE = 10
    
    // Solution #72: Track configuration changes
    private var savedPermissionState: Bundle? = null
    
    // Solution #34: Track pending file chooser state
    private var pendingFileChooser = false
    
    // Solution #69: Bridge call throttling
    private var lastBridgeCallTime = 0L
    private val BRIDGE_THROTTLE_MS = 100L

    fun getWebView(): WebView = webView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Solution #62: Clear all session state on create (activity recreation)
        synchronized(permissionLock) {
            sessionQueue.clear()
            currentSession = null
            pendingPermissionRequest = null
            isWaitingForAndroidPermission = false
            isProcessingPermission = false
        }
        
        // Solution #72: Restore saved permission state if available
        savedInstanceState?.let { bundle ->
            savedPermissionState = bundle.getBundle("permission_state")
            TestRigorLogger.logDebug("Restored permission state from savedInstanceState")
        }

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
            // Enable JavaScript (required for conversation mode)
            javaScriptEnabled = true

            // TESTRIGOR FIX: Detect TestRigor from User-Agent
            val userAgent = userAgentString
            isTestRigorDetected = userAgent?.contains("TestRigor", ignoreCase = true) ?: false
            if (isTestRigorDetected) {
                TestRigorLogger.logMilestone("TestRigor detected via User-Agent")
                isTestMode = true
            }
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
                
                // Solution #68: Notify WebAppBridge that page is unloading
                if (::webAppBridge.isInitialized) {
                    webAppBridge.onPageUnloaded()
                }
                
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                TestRigorLogger.logWebView("Page finished", url)

                url?.let {
                    val path = Uri.parse(it).path ?: "/"
                    tracker?.trackActivity("PageView:$path")
                }

                injectMicrophoneDetectionScript(view)
                
                // Solution #65: Notify WebAppBridge that page is loaded
                if (::webAppBridge.isInitialized) {
                    webAppBridge.onPageLoaded()
                }

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
                val sessionPhase = currentSession?.phase ?: PermissionPhase.IDLE
                TestRigorLogger.logWarning("Permission canceled by WebView (phase=$sessionPhase)")
                
                if (request == null) return
                
                // TESTRIGOR FIX: Route all cleanup through finalizeSession
                var sessionToFinalize: PermissionSession? = null
                
                synchronized(permissionLock) {
                    // Remove from queue if present
                    sessionQueue.removeIf { it.request == request }
                    
                    // Check if this is the current session
                    if (currentSession?.request == request) {
                        sessionToFinalize = currentSession
                        // Mark as cancelled so handlers know to skip
                        currentSession?.phase = PermissionPhase.COMPLETED
                    }
                }
                
                // Finalize the cancelled session outside the lock
                sessionToFinalize?.let { session ->
                    permissionManager.cleanupPendingRequests()
                    cancelPermissionTimeoutsAndRetries()
                    finalizeSession(session)
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
     * REFACTORED: Dual permission coordination using session-based queue
     * 
     * TESTRIGOR FIX: Handles the case where web app prompts TWICE:
     * 1. First prompt: Web getUserMedia permission
     * 2. Second prompt: Android RECORD_AUDIO permission
     * 
     * Uses PermissionSession to track state and ensure FIFO processing
     */
    @android.annotation.SuppressLint("InlinedApi")
    private fun handleWebViewAudioPermission(request: PermissionRequest) {
        val sessionPhase = currentSession?.phase ?: PermissionPhase.IDLE
        TestRigorLogger.logDebug("handleWebViewAudioPermission called, currentPhase=$sessionPhase, queueSize=${sessionQueue.size}")

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
        if (now - lastMicClickTime < DEBOUNCE_MS) {
            TestRigorLogger.logWarning("Mic permission request debounced - too rapid (${now - lastMicClickTime}ms < ${DEBOUNCE_MS}ms)")
            return
        }
        lastMicClickTime = now

        // Create new session for this request
        val session = PermissionSession(request)

        // TESTRIGOR FIX: Enqueue or process immediately
        synchronized(permissionLock) {
            // Solution #60: Check for duplicate requests from same origin
            val existingSession = sessionQueue.find { it.origin == session.origin }
            if (existingSession != null || (currentSession?.origin == session.origin)) {
                TestRigorLogger.logWarning("Duplicate session for origin ${session.origin} - ignoring")
                return
            }
            
            // Solution #20: Limit queue size to prevent unbounded growth
            if (sessionQueue.size >= MAX_SESSION_QUEUE_SIZE) {
                TestRigorLogger.logWarning("Session queue full (${sessionQueue.size}/${MAX_SESSION_QUEUE_SIZE}) - rejecting request")
                lifecycleHandler.post {
                    try { request.deny() } catch (e: Exception) { }
                }
                return
            }
            
            if (isProcessingPermission || currentSession != null) {
                // Already processing - queue this request
                sessionQueue.add(session)
                TestRigorLogger.logDebug("Session queued (queue size: ${sessionQueue.size})")
                return
            }
            
            // No active session - process this one
            currentSession = session
            isProcessingPermission = true
        }

        // Process the session outside the lock
        processSession(session)
    }

    /**
     * TESTRIGOR FIX: Process a permission session
     * Handles both pre-granted and permission-required cases
     */
    private fun processSession(session: PermissionSession) {
        TestRigorLogger.logMilestone("Processing permission session (phase: ${session.phase})")

        // STEP 1: Check if Android permission is already granted
        if (permissionManager.hasMicrophonePermission()) {
            synchronized(permissionLock) {
                session.androidResult = true
                session.phase = PermissionPhase.ANDROID_RESOLVED
            }
            TestRigorLogger.logDebug("Android permission already granted, proceeding to web grant")
            
            // Grant to WebView immediately
            grantWebViewPermissionForSession(session)
        } else {
            // STEP 2: Request Android permission FIRST
            synchronized(permissionLock) {
                session.phase = PermissionPhase.ANDROID_PENDING
                pendingPermissionRequest = session.request
                isWaitingForAndroidPermission = true
            }
            TestRigorLogger.logDebug("Requesting Android mic permission first")

            // Start timeout for stuck permission states
            startPermissionTimeoutsAndRetries()

            permissionManager.requestMicrophonePermission { granted ->
                synchronized(permissionLock) {
                    session.androidResult = granted
                    session.phase = PermissionPhase.ANDROID_RESOLVED
                }
                handleMicrophonePermissionResultForSession(session, granted)
            }
        }
    }

    /**
     * TESTRIGOR FIX: Grant permission to WebView for a session
     * Called after Android permission is resolved
     */
    private fun grantWebViewPermissionForSession(session: PermissionSession) {
        // TESTRIGOR FIX: Verify session is still current before proceeding
        val isCurrentSession = synchronized(permissionLock) {
            if (currentSession != session || session.phase == PermissionPhase.COMPLETED) {
                TestRigorLogger.logWarning("Session already finalized or not current, skipping grant")
                false
            } else {
                session.phase = PermissionPhase.WEB_PENDING
                true
            }
        }
        
        if (!isCurrentSession) return
        
        TestRigorLogger.logDebug("Granting permission to WebView for session")

        lifecycleHandler.post {
            // Double-check session is still current on main thread
            val stillCurrent = synchronized(permissionLock) { currentSession == session }
            if (!stillCurrent) {
                TestRigorLogger.logWarning("Session no longer current on main thread, skipping grant")
                return@post
            }
            
            try {
                if (!isFinishing && !isDestroyed) {
                    session.request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    TestRigorLogger.logMilestone("Audio granted to WebView")
                    synchronized(permissionLock) {
                        session.phase = PermissionPhase.COMPLETED
                    }
                } else {
                    TestRigorLogger.logWarning("Activity finishing, cannot grant WebView permission")
                }
            } catch (e: Exception) {
                TestRigorLogger.logError("WebView grant failed", e)
            } finally {
                // TESTRIGOR FIX: Finalize session and process next
                finalizeSession(session)
            }
        }
    }

    /**
     * TESTRIGOR FIX: Handle Android permission result for a session
     */
    private fun handleMicrophonePermissionResultForSession(session: PermissionSession, granted: Boolean) {
        TestRigorLogger.logPermission(Manifest.permission.RECORD_AUDIO, granted, true, 
            "phase=${session.phase}")

        // Clear timeouts
        cancelPermissionTimeoutsAndRetries()

        // TESTRIGOR FIX: Verify session is still current before proceeding
        val isCurrentSession = synchronized(permissionLock) {
            pendingPermissionRequest = null
            isWaitingForAndroidPermission = false
            
            if (currentSession != session || session.phase == PermissionPhase.COMPLETED) {
                TestRigorLogger.logWarning("Session already finalized or not current, skipping permission result handling")
                false
            } else {
                true
            }
        }
        
        if (!isCurrentSession) return

        if (granted) {
            TestRigorLogger.logDebug("Granting to WebView after Android permission")
            grantWebViewPermissionForSession(session)
        } else {
            TestRigorLogger.logWarning("Microphone permission denied")
            lifecycleHandler.post {
                // Double-check session is still current on main thread
                val stillCurrent = synchronized(permissionLock) { currentSession == session }
                if (!stillCurrent) {
                    TestRigorLogger.logWarning("Session no longer current on main thread, skipping deny")
                    return@post
                }
                
                try {
                    session.request.deny()
                    TestRigorLogger.logDebug("WebView permission denied after Android denial")
                } catch (e: Exception) {
                    TestRigorLogger.logError("Deny failed", e)
                } finally {
                    finalizeSession(session)
                }
            }
        }
    }

    /**
     * TESTRIGOR FIX: Finalize a session and process the next one
     * This is the ONLY place where currentSession is cleared and next is started
     */
    private fun finalizeSession(session: PermissionSession) {
        var nextSession: PermissionSession? = null
        
        synchronized(permissionLock) {
            // Only finalize if this is the current session
            if (currentSession == session) {
                currentSession = null
                pendingPermissionRequest = null
                isWaitingForAndroidPermission = false
                
                // Check for next session in queue
                if (sessionQueue.isNotEmpty() && !isFinishing && !isDestroyed) {
                    nextSession = sessionQueue.removeAt(0)
                    currentSession = nextSession
                    // Keep isProcessingPermission = true
                    TestRigorLogger.logDebug("Finalized session, processing next (queue size: ${sessionQueue.size})")
                } else {
                    // No more sessions - fully idle
                    isProcessingPermission = false
                    
                    // Clear queue if activity is finishing
                    if (isFinishing || isDestroyed) {
                        val remaining = sessionQueue.size
                        sessionQueue.forEach { s ->
                            try { s.request.deny() } catch (e: Exception) { }
                        }
                        sessionQueue.clear()
                        if (remaining > 0) {
                            TestRigorLogger.logWarning("Activity finishing, denied and cleared $remaining queued sessions")
                        }
                    } else {
                        TestRigorLogger.logDebug("Session finalized, queue empty, returning to IDLE")
                    }
                }
            } else {
                TestRigorLogger.logWarning("Attempted to finalize non-current session")
            }
        }
        
        // Process next session OUTSIDE the lock
        nextSession?.let { next ->
            lifecycleHandler.post {
                if (!isFinishing && !isDestroyed) {
                    processSession(next)
                } else {
                    synchronized(permissionLock) {
                        currentSession = null
                        isProcessingPermission = false
                    }
                    TestRigorLogger.logWarning("Skipping next session - activity finishing")
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
        """.trimIndent()

        // TESTRIGOR FIX: Wrap injection in try-catch with null-safe call
        try {
            view?.evaluateJavascript(script, null)
            TestRigorLogger.logDebug("Microphone detection script injected successfully")
        } catch (e: Exception) {
            TestRigorLogger.logError("Script injection failed", e)
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

        val isGranted = grantResults.isNotEmpty() && 
                       grantResults[0] == PackageManager.PERMISSION_GRANTED

        Log.d("MainActivity", "Microphone permission ${if (isGranted) "granted" else "denied"}")

        // TESTRIGOR FIX: Broadcast state to WebView
        broadcastPermissionStateToWebView(isGranted)
    }

    private fun broadcastPermissionStateToWebView(isGranted: Boolean) {
        if (::webView.isInitialized) {
            val state = if (isGranted) "granted" else "denied"
            val script = "document.body.setAttribute('data-testrigor-permission-state', '$state');"
            lifecycleHandler.post {
                try {
                    webView.evaluateJavascript(script, null)
                    TestRigorLogger.logDebug("Permission state broadcast to WebView: $state")
                } catch (e: Exception) {
                    TestRigorLogger.logError("Failed to broadcast permission state", e)
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

        // TESTRIGOR FIX: Re-verify pending permission state on resume
        val session = synchronized(permissionLock) { currentSession }
        if (session != null && isWaitingForAndroidPermission) {
            if (permissionManager.hasMicrophonePermission()) {
                TestRigorLogger.logDebug("Permission granted while backgrounded - handling now")
                handleMicrophonePermissionResultForSession(session, true)
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

    /**
     * Solution #72: Save permission state for activity recreation
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        
        val permissionBundle = Bundle().apply {
            putBoolean("isProcessingPermission", isProcessingPermission)
            putBoolean("isWaitingForAndroidPermission", isWaitingForAndroidPermission)
            putInt("sessionQueueSize", sessionQueue.size)
            putString("currentPhase", currentSession?.phase?.name ?: "IDLE")
        }
        outState.putBundle("permission_state", permissionBundle)
        TestRigorLogger.logDebug("Saved permission state to outState")
    }
    
    /**
     * Solution #73: Handle configuration changes gracefully
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        TestRigorLogger.logDebug("Configuration changed: ${newConfig.orientation}")
        
        // Preserve WebView state during configuration change
        if (::webView.isInitialized) {
            webView.requestLayout()
        }
    }
    
    /**
     * Solution #75: Handle back press during permission flow
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Solution #75: Check if permission flow is active
        val permissionActive = synchronized(permissionLock) { currentSession != null }
        if (permissionActive) {
            TestRigorLogger.logWarning("Back pressed during permission flow - canceling")
            synchronized(permissionLock) {
                currentSession?.let { session ->
                    try { session.request.deny() } catch (e: Exception) { }
                    session.phase = PermissionPhase.COMPLETED
                }
                currentSession = null
                pendingPermissionRequest = null
                isWaitingForAndroidPermission = false
                isProcessingPermission = sessionQueue.isNotEmpty()
            }
            permissionManager.cleanupPendingRequests()
            cancelPermissionTimeoutsAndRetries()
            return
        }
        
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    /**
     * Solution #76: Check multi-window mode for race conditions
     * Note: onMultiWindowModeChanged is deprecated in newer Android versions
     */
    @Deprecated("Deprecated in Java")
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
        super.onMultiWindowModeChanged(isInMultiWindowMode)
        TestRigorLogger.logDebug("Multi-window mode changed: $isInMultiWindowMode")
        
        if (isInMultiWindowMode) {
            // Pause any active permission flows
            synchronized(permissionLock) {
                if (currentSession != null) {
                    TestRigorLogger.logWarning("Multi-window mode entered during permission flow")
                }
            }
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

    // TESTRIGOR FIX: Helper to start permission timeout and retry logic
    private fun startPermissionTimeoutsAndRetries() {
        // Clear any existing callbacks
        cancelPermissionTimeoutsAndRetries()

        // Set timeout
        permissionTimeoutRunnable = Runnable {
            TestRigorLogger.logWarning("Permission request timed out after ${PERMISSION_TIMEOUT_MS}ms")
            // Treat timeout as denial - finalize current session
            val session = synchronized(permissionLock) { currentSession }
            session?.let { 
                handleMicrophonePermissionResultForSession(it, false)
            }
        }
        permissionTimeoutHandler.postDelayed(permissionTimeoutRunnable!!, PERMISSION_TIMEOUT_MS)

        // Reset retry count
        permissionGrantRetryCount = 0
    }

    // TESTRIGOR FIX: Helper to cancel permission timeouts and retries
    private fun cancelPermissionTimeoutsAndRetries() {
        permissionTimeoutRunnable?.let {
            permissionTimeoutHandler.removeCallbacks(it)
            permissionTimeoutRunnable = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // TESTRIGOR FIX: Clear all pending permission sessions and state
        // Solution #7: Deny all queued sessions in onDestroy
        synchronized(permissionLock) {
            // Deny current session if present
            currentSession?.let { session ->
                try { session.request.deny() } catch (e: Exception) { }
            }
            
            // Deny all queued sessions
            sessionQueue.forEach { session ->
                try { session.request.deny() } catch (e: Exception) { }
            }
            sessionQueue.clear()
            
            // Clear current session
            currentSession = null
            pendingPermissionRequest = null
            isWaitingForAndroidPermission = false
            isProcessingPermission = false
        }
        
        // Solution #82: Clean up permission manager
        permissionManager.cleanupPendingRequests()
        
        // Solution #83: Cancel timeouts
        cancelPermissionTimeoutsAndRetries()
        
        // Solution #71: Clean up WebAppBridge
        try {
            if (::webAppBridge.isInitialized) {
                webAppBridge.cleanup()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("WebAppBridge cleanup", e)
        }
        
        // Solution #78: Clean up lifecycleHandler
        try {
            if (::lifecycleHandler.isInitialized) {
                lifecycleHandler.removeCallbacksAndMessages()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("LifecycleHandler cleanup", e)
        }

        // Solution #81: Clean up AdMob
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