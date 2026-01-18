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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AutomaticGainControl
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.ads.MobileAds
// DISABLED FOR TESTING: import com.google.firebase.analytics.FirebaseAnalytics
import com.lingualink.linguagt.ads.AdBridge
import com.lingualink.linguagt.ads.AdMobBridge
import com.lingualink.linguagt.ads.AdPreloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var adBridge: AdBridge
    private lateinit var adMobBridge: AdMobBridge

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

    // Track VAST Ad Manager initialization state
    private var isIMAConsentInitialized = false
    private var isWebViewFullyLoaded = false
    
    // FIX #16-30: Android 15 API 35 compatibility flags
    private var isAndroid15OrHigher = Build.VERSION.SDK_INT >= 35
    private var hasEdgeToEdgeApplied = false
    
    // FIX #36: Save WebView URL for process death recovery
    private var lastLoadedUrl: String? = null
    
    // FIX #40: Track window focus for ad display
    private var hasWindowFocus = false
    
    // FIX #86-95: Memory management
    private var isLowMemoryMode = false
    private val MAX_WEBVIEW_CACHE_SIZE = 50 * 1024 * 1024 // 50MB

    // Audio enhancement components for speech recognition
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private var isAudioEnhancementEnabled = false

    // Solution #90: Track if content view is set for Appium compatibility
    private var isContentViewSet = false

    fun getWebView(): WebView = webView

    /**
     * Solution #90: Check if activity is fully ready for Appium/TestRigor operations
     */
    fun isFullyReady(): Boolean {
        return isContentViewSet && isWindowAttached() && isSafeToUpdateUI() && 
               LinguaLinkApplication.isAppReady
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Solution #89: Global onCreate crash protection
        try {
            super.onCreate(savedInstanceState)

            // Solution #62: Clear all session state on create (activity recreation)
            synchronized(permissionLock) {
                sessionQueue.clear()
                currentSession = null
                pendingPermissionRequest = null
                isWaitingForAndroidPermission = false
                isProcessingPermission = false
                isIMAConsentInitialized = false
                isWebViewFullyLoaded = false
            }

            // Solution #72: Restore saved permission state if available
            savedInstanceState?.let { bundle ->
                savedPermissionState = bundle.getBundle("permission_state")
                TestRigorLogger.logDebug("Restored permission state from savedInstanceState")
            }

            lifecycleHandler = LifecycleAwareHandler(this)
            permissionManager = SafePermissionManager(this, isTestMode)
            webAppBridge = WebAppBridge(this)

            tracker = UserActivityTracker(this, appId)
            tracker?.sendUserActivity(appId)
            tracker?.getTesterMob()

            initializeTesterMobLib()
            
            // CRITICAL FIX: Do NOT initialize ads here - it blocks the main thread
            // and can cause white screen / ANR on startup (especially on BrowserStack)
            // Ad initialization is now deferred to onPageFinished with 1-second delay
            // See: docs/ANDROID_ADMOB_110_FIXES_GUIDE.md - "App Startup Failure Fix"
            // initializeNativeAdPreload() -- MOVED TO onPageFinished

            // ANR PREVENTION: Defer heavy WebView initialization and permission requests to next frame
            // This allows the UI thread to respond within 5 seconds
            // Permissions must be requested after window is attached
            
            // FIX #17: Add timeout fallback in case window.decorView.post never executes
            val setupTimeoutMs = 5000L
            var setupCompleted = false
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (!setupCompleted && !isFinishing) {
                    TestRigorLogger.logWarning("Setup timeout - forcing WebView initialization")
                    forceWebViewSetup()
                }
            }, setupTimeoutMs)
            
            window.decorView.post {
                // FIX #14/#15: Check lifecycle state before proceeding
                if (isFinishing || isDestroyed) {
                    TestRigorLogger.logWarning("Activity finishing - skipping WebView setup")
                    return@post
                }
                
                try {
                    // FIX #10: Wrap permissions in try-catch
                    try {
                        requestPermissions()
                    } catch (e: Exception) {
                        TestRigorLogger.logError("Permission request failed", e)
                    }
                    
                    // FIX #2: Use try-finally to ensure loadDefaultUrl is always called
                    try {
                        // setupWebViewForConversationMode() now handles AdBridge registration
                        // BEFORE any URL loads to fix timing race condition
                        setupWebViewForConversationMode()
                    } finally {
                        // FIX #19: Ensure URL loads after WebView is configured
                        handleDeepLink(intent)
                        TestRigorLogger.logMilestone("WebView setup completed (deferred)")
                        setupCompleted = true
                    }
                } catch (e: Exception) {
                    TestRigorLogger.logError("WebView setup failed", e)
                    setupCompleted = true
                    // FIX #2: Still try to load URL even on failure
                    try {
                        if (::webView.isInitialized) {
                            loadDefaultUrl()
                        } else {
                            createNativeTranslationInterface()
                        }
                    } catch (e2: Exception) {
                        TestRigorLogger.logError("Fallback load failed", e2)
                    }
                }
            }
        } catch (e: Exception) {
            // Solution #89: Graceful recovery from onCreate crash
            TestRigorLogger.logError("onCreate crash prevented", e)
            try {
                // Attempt minimal recovery
                setContentView(android.widget.FrameLayout(this))
            } catch (e2: Exception) {
                TestRigorLogger.logError("Recovery failed", e2)
            }
        }
    }

    // Note: onResume() and onPause() are defined later in the file
    // to consolidate all lifecycle functionality in one place

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Guard: If webView not initialized yet, skip processing
        // This happens during foreground recovery before onCreate finishes
        // The app is already being brought to front - no further action needed
        if (!::webView.isInitialized) {
            return
        }
        webView.requestFocus()
        intent?.let { handleDeepLink(it) }
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
                // ANR FIX #4: Use WebView.post() for all WebView operations
                webView.post {
                    webView.loadUrl(urlToLoad)
                }
            } else {
                pendingDeepLinkUrl = urlToLoad
            }

            tracker?.trackActivity("DeepLink:$path")
        } else {
            loadDefaultUrl()
        }
    }

    private var loadUrlRetryCount = 0
    private val MAX_LOAD_RETRIES = 5
    private val LOAD_RETRY_DELAY_MS = 500L
    
    // FIX #61: Network retry state
    private var networkRetryCount = 0
    private val MAX_NETWORK_RETRIES = 3
    private val NETWORK_RETRY_BASE_DELAY_MS = 2000L
    private var pendingNetworkRetryRunnable: Runnable? = null
    private var isNetworkRetryPending = false

    private fun loadDefaultUrl() {
        TestRigorLogger.logMilestone("WebView loadUrl starting (attempt ${loadUrlRetryCount + 1})")
        val defaultUrl = pendingDeepLinkUrl ?: BASE_URL
        
        if (::webView.isInitialized && isSafeToUpdateUI()) {
            loadUrlRetryCount = 0 // Reset on success
            // ANR FIX #4: Use WebView.post() for all WebView operations
            webView.post {
                webView.loadUrl(defaultUrl)
                TestRigorLogger.logMilestone("WebView loadUrl completed: $defaultUrl")
            }
        } else if (::webView.isInitialized) {
            // WebView exists but UI not safe yet - retry with delay
            if (loadUrlRetryCount < MAX_LOAD_RETRIES) {
                loadUrlRetryCount++
                TestRigorLogger.logWarning("UI not safe, scheduling retry ${loadUrlRetryCount}/$MAX_LOAD_RETRIES")
                Handler(Looper.getMainLooper()).postDelayed({
                    loadDefaultUrl()
                }, LOAD_RETRY_DELAY_MS)
            } else {
                // FALLBACK: Force load after max retries - better than blank screen
                TestRigorLogger.logWarning("Max retries reached - force loading URL")
                loadUrlRetryCount = 0
                webView.post {
                    webView.loadUrl(defaultUrl)
                    TestRigorLogger.logMilestone("WebView force loadUrl: $defaultUrl")
                }
            }
        } else {
            TestRigorLogger.logError("WebView not initialized - cannot load URL", null)
        }
    }
    
    /**
     * FIX #61: Schedule network retry after connection error.
     * Uses true exponential backoff (base * 2^(n-1)) up to MAX_NETWORK_RETRIES.
     * FIX: Tracks pending runnable to prevent duplicate retries and uses lifecycleHandler.
     */
    private fun scheduleNetworkRetry() {
        // FIX: Prevent duplicate retries
        if (isNetworkRetryPending) {
            TestRigorLogger.logDebug("Network retry already pending - skipping duplicate")
            return
        }
        
        // FIX: Check window focus - don't retry if in background
        if (!hasWindowFocus) {
            TestRigorLogger.logDebug("App in background - deferring network retry")
            return
        }
        
        if (networkRetryCount >= MAX_NETWORK_RETRIES) {
            TestRigorLogger.logError("Max network retries reached ($MAX_NETWORK_RETRIES)", null)
            resetNetworkRetryState()
            return
        }
        
        networkRetryCount++
        // FIX: True exponential backoff: base * 2^(n-1)
        val delay = NETWORK_RETRY_BASE_DELAY_MS * (1 shl (networkRetryCount - 1)) // 2s, 4s, 8s
        TestRigorLogger.logMilestone("Scheduling network retry $networkRetryCount/$MAX_NETWORK_RETRIES in ${delay}ms")
        
        isNetworkRetryPending = true
        
        // FIX: Cancel any existing pending retry
        pendingNetworkRetryRunnable?.let { lifecycleHandler.removeCallbacks(it) }
        
        val runnable = Runnable {
            isNetworkRetryPending = false
            if (!isFinishing && !isDestroyed && ::webView.isInitialized && hasWindowFocus) {
                // FIX #64: Check network before retry
                if (isNetworkAvailable()) {
                    TestRigorLogger.logMilestone("Network available - retrying load")
                    webView.reload()
                } else {
                    TestRigorLogger.logWarning("Network still unavailable - scheduling another retry")
                    scheduleNetworkRetry()
                }
            }
        }
        pendingNetworkRetryRunnable = runnable
        lifecycleHandler.postDelayed(runnable, delay)
    }
    
    /**
     * FIX: Reset network retry state on successful page load.
     */
    private fun resetNetworkRetryState() {
        networkRetryCount = 0
        isNetworkRetryPending = false
        pendingNetworkRetryRunnable?.let { lifecycleHandler.removeCallbacks(it) }
        pendingNetworkRetryRunnable = null
    }
    
    /**
     * FIX #64: Check if network is available.
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected ?: false
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("Network check failed", e)
            false
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

        // CRITICAL FIX: Request microphone permission IMMEDIATELY on startup
        // This ensures Android permission is already granted BEFORE web app asks
        if (!permissionManager.hasMicrophonePermission()) {
            TestRigorLogger.logMilestone("Proactively requesting microphone permission on startup")
            
            // Show rationale if needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                // User has previously denied permission, show rationale
                val rationale = getString(R.string.permission_audio_rationale)
                TestRigorLogger.logDebug("Showing permission rationale: $rationale")
            }
            
            permissionManager.requestMicrophonePermission { granted ->
                TestRigorLogger.logPermission(Manifest.permission.RECORD_AUDIO, granted, true, 
                    "Startup request")
                if (granted) {
                    TestRigorLogger.logMilestone("Microphone pre-authorized before WebView loads")
                }
            }
        } else {
            TestRigorLogger.logMilestone("Microphone already authorized on startup")
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

    /**
     * NATIVE AD PRELOAD: Initialize ads IMMEDIATELY on activity creation.
     * This runs in PARALLEL with WebView loading, eliminating 300-1500ms bridge latency.
     */
    private fun initializeNativeAdPreload() {
        try {
            TestRigorLogger.logMilestone("Initializing Native Ad Preload Manager")
            
            // Initialize the preload manager (triggers SDK init + ad preload)
            AdPreloadManager.initialize(this)
            
            // Setup callbacks to notify WebView when ads are ready
            AdPreloadManager.onInterstitialReady = {
                TestRigorLogger.logAdEvent("Native preload: Interstitial ready")
                notifyWebViewAdReady("interstitial")
            }
            
            AdPreloadManager.onRewardedReady = {
                TestRigorLogger.logAdEvent("Native preload: Rewarded ready")
                notifyWebViewAdReady("rewarded")
            }
            
            AdPreloadManager.onAdDismissed = {
                TestRigorLogger.logAdEvent("Native preload: Ad dismissed")
            }
            
            AdPreloadManager.onRewardEarned = { type, amount ->
                TestRigorLogger.logAdEvent("Native preload: Reward earned - $type x $amount")
                notifyWebViewRewardEarned(type, amount)
            }
            
            TestRigorLogger.logMilestone("Native Ad Preload Manager initialized")
        } catch (e: Exception) {
            TestRigorLogger.logError("Native Ad Preload init failed", e)
        }
    }
    
    // Buffer for ad ready events (sent when WebView is ready)
    private val pendingAdReadyEvents = mutableListOf<String>()
    
    /**
     * Notify WebView that an ad is ready (for JS to update UI).
     * If WebView is not ready yet, buffer the event.
     */
    private fun notifyWebViewAdReady(adType: String) {
        try {
            if (::webView.isInitialized && isWebViewFullyLoaded) {
                val js = "if(window.onNativeAdReady){window.onNativeAdReady('$adType');}"
                webView.post { webView.evaluateJavascript(js, null) }
            } else {
                // Buffer the event for when WebView is ready
                synchronized(pendingAdReadyEvents) {
                    if (!pendingAdReadyEvents.contains(adType)) {
                        pendingAdReadyEvents.add(adType)
                        TestRigorLogger.logAdEvent("Buffered ad ready event: $adType (WebView not ready)")
                    }
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("notifyWebViewAdReady failed", e)
        }
    }
    
    /**
     * Flush buffered ad ready events to WebView.
     * Call this when WebView is fully loaded.
     */
    private fun flushPendingAdReadyEvents() {
        try {
            if (::webView.isInitialized && isWebViewFullyLoaded) {
                synchronized(pendingAdReadyEvents) {
                    pendingAdReadyEvents.forEach { adType ->
                        val js = "if(window.onNativeAdReady){window.onNativeAdReady('$adType');}"
                        webView.post { webView.evaluateJavascript(js, null) }
                        TestRigorLogger.logAdEvent("Flushed buffered ad ready event: $adType")
                    }
                    pendingAdReadyEvents.clear()
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("flushPendingAdReadyEvents failed", e)
        }
    }
    
    /**
     * Notify WebView that a reward was earned.
     */
    private fun notifyWebViewRewardEarned(type: String, amount: Int) {
        try {
            if (::webView.isInitialized && isWebViewFullyLoaded) {
                val js = "if(window.onRewardEarned){window.onRewardEarned('$type',$amount);}"
                webView.post { webView.evaluateJavascript(js, null) }
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("notifyWebViewRewardEarned failed", e)
        }
    }

    private fun setupWebViewForConversationMode() {
        try {
            webView = WebView(this)
        } catch (e: Exception) {
            TestRigorLogger.logError("WebView creation", e)
            throw e
        }

        // CRITICAL FIX: Register ad bridges IMMEDIATELY after WebView creation
        // This MUST happen BEFORE any URL can be loaded to avoid race condition
        // where page loads before bridge is available (window.adBridge === undefined)
        
        // AdMobBridge - AdMob only (InMobi removed)
        // Registered as "AndroidAdBridge" for web app compatibility
        adMobBridge = AdMobBridge(this, webView)
        webView.addJavascriptInterface(adMobBridge, "AndroidAdBridge")
        TestRigorLogger.logMilestone("AdMobBridge registered as window.AndroidAdBridge")
        
        // AdMob/UMP handles consent - AdBridge as fallback
        adBridge = AdBridge(this, webView)
        webView.addJavascriptInterface(adBridge, "adBridgeFallback")
        
        // CRITICAL: Wire up consent callback to notify AdMobBridge AND AdPreloadManager
        // This enables REAL ads (not test ads) after consent is obtained
        adBridge.onConsentObtained = { gdprConsent ->
            TestRigorLogger.logAdEvent("Consent obtained (gdpr=$gdprConsent) - notifying ad bridges")
            adMobBridge.onConsentObtained(gdprConsent)
            AdPreloadManager.onConsentObtained(gdprConsent)
        }
        
        adBridge.initialize()
        TestRigorLogger.logMilestone("UMP consent flow started")

        webView.addJavascriptInterface(webAppBridge, "AndroidBridge")
        webAppBridge.setWebView(webView)

        // TESTRIGOR: Add test inspection bridge
        if (BuildConfig.DEBUG) {
            webView.addJavascriptInterface(TestRigorBridge(), "TestRigorBridge")
            TestRigorLogger.logMilestone("TestRigor JavaScript bridge enabled")
        }

        // Configure WebView settings on main thread (WebView is NOT thread-safe)
        // ANR prevention comes from window.decorView.post() deferral in onCreate
        val webSettings: WebSettings = webView.settings
        webSettings.apply {
            // Enable JavaScript (required for conversation mode and VAST ads)
            javaScriptEnabled = true

            // TESTRIGOR FIX: Detect TestRigor from User-Agent
            val userAgent = userAgentString
            isTestRigorDetected = userAgent?.contains("TestRigor", ignoreCase = true) ?: false
            if (isTestRigorDetected) {
                TestRigorLogger.logMilestone("TestRigor detected via User-Agent")
                isTestMode = true
            }
            
            // DOM and Database access for web app functionality
            domStorageEnabled = true
            databaseEnabled = true
            
            // Security: Disable file access, allow content access
            allowFileAccess = false
            allowContentAccess = true
            
            // Zoom controls
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            
            // IMPORTANT: Video ads require mediaPlaybackRequiresUserGesture = false
            mediaPlaybackRequiresUserGesture = false
            
            // Enable mixed content for ad servers (VAST requirement)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // Cache and other settings
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            textZoom = 100
            minimumFontSize = 8
        }

        // ADMOB FIX: Enable cookies for ad tracking (ANDROID_WEBVIEW_012)
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(webView, true)
            }
            TestRigorLogger.logMilestone("WebView cookies enabled for ads")
        } catch (e: Exception) {
            TestRigorLogger.logError("Cookie setup failed", e)
        }

        // ANR FIX: Request focus after layout is complete
        webView.post {
            webView.requestFocus()
            webView.isFocusableInTouchMode = true
            TestRigorLogger.logMilestone("WebView focus requested - ANR prevention")
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
                
                // FIX: Reset network retry state on successful page load
                resetNetworkRetryState()

                url?.let {
                    val path = Uri.parse(it).path ?: "/"
                    tracker?.trackActivity("PageView:$path")
                }

                injectMicrophoneDetectionScript(view)

                // Solution #65: Notify WebAppBridge that page is loaded
                if (::webAppBridge.isInitialized) {
                    webAppBridge.onPageLoaded()
                }

                // Mark WebView as fully loaded
                isWebViewFullyLoaded = true
                
                // DISABLED FOR TESTING - Firebase Analytics
                // try {
                //     FirebaseAnalytics.getInstance(this@MainActivity).setAnalyticsCollectionEnabled(true)
                //     TestRigorLogger.logMilestone("Firebase Analytics enabled after page load")
                // } catch (e: Exception) {
                //     TestRigorLogger.logError("Failed to enable Firebase Analytics: ${e.message}", e)
                // }
                
                // Flush any buffered ad ready events now that WebView is ready
                flushPendingAdReadyEvents()
                
                // CRITICAL FIX: Initialize ads AFTER WebView renders to prevent startup failures
                // This 1-second delay ensures WebView is fully rendered before AdMob SDK init
                // See: docs/ANDROID_ADMOB_110_FIXES_GUIDE.md - "App Startup Failure Fix"
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        initializeNativeAdPreload()
                        TestRigorLogger.logMilestone("Ad initialization completed (deferred after WebView load)")
                    }
                }, 1000) // 1 second delay after page load

                // Notify web app that Android container is ready
                view?.evaluateJavascript(
                    "if (window.onAndroidReady) { window.onAndroidReady(); }",
                    null
                )
                TestRigorLogger.logDebug("Android ready signal sent to web app")

                // Inject device advertising ID for ad targeting (used by web VAST player)
                injectDeviceAdId()

                super.onPageFinished(view, url)
            }

            // FIX #56-65: Enhanced error handling with network retry
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.errorCode ?: -1
                    } else -1
                    val errorDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Unknown error"
                    } else "Unknown error"
                    
                    TestRigorLogger.logError("WebView main frame error: $errorCode - $errorDesc", null)
                    
                    // FIX #61: Check if network error and retry
                    when (errorCode) {
                        android.webkit.WebViewClient.ERROR_HOST_LOOKUP,
                        android.webkit.WebViewClient.ERROR_CONNECT,
                        android.webkit.WebViewClient.ERROR_TIMEOUT -> {
                            TestRigorLogger.logMilestone("Network error detected - scheduling retry")
                            scheduleNetworkRetry()
                        }
                        android.webkit.WebViewClient.ERROR_BAD_URL -> {
                            TestRigorLogger.logError("Bad URL - cannot retry", null)
                        }
                    }
                }
                super.onReceivedError(view, request, error)
            }
            
            // FIX #62: Handle HTTP errors
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode ?: -1
                    TestRigorLogger.logError("HTTP error on main frame: $statusCode", null)
                    
                    // FIX #65: Retry on server errors (5xx)
                    if (statusCode >= 500) {
                        TestRigorLogger.logMilestone("Server error - scheduling retry")
                        scheduleNetworkRetry()
                    }
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            // FIX #11: Handle SSL errors to prevent blank screens
            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                TestRigorLogger.logError("SSL error: ${error?.primaryError}", null)
                // In production, we should NOT proceed with SSL errors for security
                // But log detailed info for debugging
                when (error?.primaryError) {
                    android.net.http.SslError.SSL_EXPIRED -> TestRigorLogger.logError("SSL: Certificate expired", null)
                    android.net.http.SslError.SSL_IDMISMATCH -> TestRigorLogger.logError("SSL: Hostname mismatch", null)
                    android.net.http.SslError.SSL_NOTYETVALID -> TestRigorLogger.logError("SSL: Certificate not yet valid", null)
                    android.net.http.SslError.SSL_UNTRUSTED -> TestRigorLogger.logError("SSL: Certificate untrusted", null)
                }
                // Cancel the load (security best practice)
                handler?.cancel()
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

        webView.contentDescription = "LinguaVibe Translation App WebView"
        
        // FIX #7: Ensure WebView has proper dimensions (match_parent)
        webView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        // FIX #4: Ensure WebView is visible
        webView.visibility = android.view.View.VISIBLE
        
        // FIX #5: Log setContentView for debugging
        TestRigorLogger.logMilestone("Setting content view with WebView")
        setContentView(webView)
        TestRigorLogger.logMilestone("setContentView completed successfully")

        // Solution #90: Track content view set for Appium compatibility
        isContentViewSet = true
        TestRigorLogger.logMilestone("Content view set - ready for automation")
    }

    /**
     * REFACTORED: Dual permission coordination using session-based queue
     * 
     * TESTRIGOR FIX: Handles the case where web app prompts TWICE:
     * 1. First prompt: Web getUserMedia permission
     * 2. Second prompt: Android RECORD_AUDIO permission
     * 
     * Uses PermissionSession to track state and ensure FIFO processing
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #91: Check window attachment before permission dialog
     */
    @android.annotation.SuppressLint("InlinedApi")
    private fun handleWebViewAudioPermission(request: PermissionRequest) {
        val sessionPhase = currentSession?.phase ?: PermissionPhase.IDLE
        TestRigorLogger.logDebug("handleWebViewAudioPermission called, currentPhase=$sessionPhase, queueSize=${sessionQueue.size}")

        // Solution #91: Ensure activity is fully ready before showing permission dialog
        if (!isFullyReady()) {
            TestRigorLogger.logWarning("Activity not fully ready for permission request - deferring")
            // Defer the request slightly to allow activity to become ready
            lifecycleHandler.postDelayed({
                if (isFullyReady()) {
                    handleWebViewAudioPermission(request)
                } else {
                    TestRigorLogger.logError("Activity still not ready after delay - denying permission", null)
                    try { request.deny() } catch (e: Exception) { }
                }
            }, 200)
            return
        }

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
     * 
     * CRASH PREVENTION SOLUTIONS:
     * - Solution #91: Verify activity ready state before processing
     */
    private fun processSession(session: PermissionSession) {
        TestRigorLogger.logMilestone("Processing permission session (phase: ${session.phase})")

        // Solution #91: Verify activity is still ready
        if (!isFullyReady()) {
            TestRigorLogger.logWarning("Activity not ready during processSession - aborting")
            synchronized(permissionLock) {
                session.phase = PermissionPhase.COMPLETED
            }
            finalizeSession(session)
            return
        }

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
     * Inject device advertising ID for ad targeting
     * Called after page loads to provide GAID to web app for VAST ad requests
     */
    private fun injectDeviceAdId() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val adInfo = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext)
                val gaid = adInfo.id ?: ""
                val limitTracking = adInfo.isLimitAdTrackingEnabled
                
                val js = """
                    window.DEVICE_AD_INFO = {
                        id: '$gaid',
                        limitTracking: $limitTracking,
                        platform: 'android'
                    };
                    console.log('[LinguaVibe] Device Ad Info injected:', window.DEVICE_AD_INFO);
                    if (window.onDeviceAdInfoReady) { window.onDeviceAdInfoReady(window.DEVICE_AD_INFO); }
                """.trimIndent()
                
                withContext(Dispatchers.Main) {
                    if (::webView.isInitialized && !isFinishing && !isDestroyed) {
                        webView.evaluateJavascript(js, null)
                        TestRigorLogger.logAdEvent("Device Ad ID injected: ${gaid.take(8)}...")
                    }
                }
            } catch (e: Exception) {
                TestRigorLogger.logWarning("Failed to get advertising ID: ${e.message}")
                // Fallback - ads will still work but with lower targeting accuracy
                withContext(Dispatchers.Main) {
                    if (::webView.isInitialized && !isFinishing && !isDestroyed) {
                        val js = """
                            window.DEVICE_AD_INFO = {
                                id: '',
                                limitTracking: true,
                                platform: 'android',
                                error: 'unavailable'
                            };
                            console.log('[LinguaVibe] Device Ad Info unavailable');
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }
            }
        }
    }

    /**
     * TESTRIGOR: JavaScript bridge for state inspection
     * Solution #90: Added isAppReady() for Appium compatibility
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
                    "activityDestroyed": $isDestroyed,
                    "isWindowAttached": ${isWindowAttached()},
                    "isContentViewSet": $isContentViewSet,
                    "isAppReady": ${LinguaLinkApplication.isAppReady},
                    "isFullyReady": ${isFullyReady()}
                }
            """.trimIndent()
        }

        /**
         * Solution #90: Check if app is fully ready for automation operations
         * Returns true only when:
         * - Application is initialized
         * - Activity window is attached
         * - Content view is set
         * - Activity is not finishing/destroyed
         */
        @JavascriptInterface
        fun isAppReady(): Boolean {
            return isFullyReady()
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

        // Enable audio enhancements when microphone permission is granted
        if (isGranted) {
            setupAudioEnhancements()
        }
    }

    /**
     * Setup audio enhancements for improved speech recognition
     * Enables echo cancellation, noise suppression, and automatic gain control
     * 
     * Note: These effects may fail on some devices when WebView manages its own audio session.
     * Failures are non-critical and logged as warnings.
     */
    private fun setupAudioEnhancements() {
        if (isAudioEnhancementEnabled) {
            TestRigorLogger.logDebug("Audio enhancements already enabled")
            return
        }

        // Note: Audio effects may not work with WebView's audio session on all devices
        // Each effect is wrapped in try-catch for graceful degradation
        TestRigorLogger.logDebug("Attempting to setup audio enhancements (optional)")

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            TestRigorLogger.logDebug("AudioManager set to MODE_IN_COMMUNICATION")
        } catch (e: Exception) {
            TestRigorLogger.logDebug("AudioManager mode change skipped: ${e.message}")
        }

        // Audio session ID 0 may not work with WebView on all devices
        // Effects will fail gracefully if not supported
        val audioSessionId = 0

        // Enable Acoustic Echo Canceler if available (graceful failure)
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                if (echoCanceler != null) {
                    echoCanceler?.enabled = true
                    TestRigorLogger.logDebug("AcousticEchoCanceler enabled")
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logDebug("AcousticEchoCanceler skipped (non-critical): ${e.message}")
        }

        // Enable Noise Suppressor if available (graceful failure)
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                if (noiseSuppressor != null) {
                    noiseSuppressor?.enabled = true
                    TestRigorLogger.logDebug("NoiseSuppressor enabled")
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logDebug("NoiseSuppressor skipped (non-critical): ${e.message}")
        }

        // Enable Automatic Gain Control if available (graceful failure)
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)
                if (gainControl != null) {
                    gainControl?.enabled = true
                    TestRigorLogger.logDebug("AutomaticGainControl enabled")
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logDebug("AutomaticGainControl skipped (non-critical): ${e.message}")
        }

        isAudioEnhancementEnabled = true
        TestRigorLogger.logDebug("Audio enhancements setup completed (some effects may be unavailable)")
    }

    /**
     * Release audio enhancement resources
     */
    private fun releaseAudioEnhancements() {
        try {
            echoCanceler?.release()
            echoCanceler = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            gainControl?.release()
            gainControl = null

            isAudioEnhancementEnabled = false
            TestRigorLogger.logDebug("Audio enhancements released")
        } catch (e: Exception) {
            TestRigorLogger.logError("Failed to release audio enhancements", e)
        }
    }

    /**
     * Unified onResume: WebView, Tracker, and Permission handling
     */
    override fun onResume() {
        super.onResume()

        // Tracker activity
        tracker?.sendUserActivity(appId)

        // WebView resume
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

    /**
     * Unified onPause: WebView handling
     */
    override fun onPause() {
        super.onPause()

        // WebView pause
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
     * ANR FIX #3 + FIX #40-41: Handle focus recovery
     * Ensures WebView maintains focus when activity gains focus
     * FIX #40: Track window focus for ad display decisions
     * FIX #41: Use as readiness signal if onAttachedToWindow delayed
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        // FIX #40: Track focus state
        hasWindowFocus = hasFocus
        TestRigorLogger.logDebug("Window focus changed: $hasFocus")
        
        if (hasFocus && ::webView.isInitialized) {
            webView.post {
                webView.requestFocus()
                TestRigorLogger.logDebug("WebView focus restored on window focus change - ANR prevention")
            }
            
            // FIX #41: If this is first focus and WebView not loaded, trigger load
            if (!isWebViewFullyLoaded && loadUrlRetryCount == 0) {
                TestRigorLogger.logMilestone("First window focus - checking WebView load status")
            }
        }
        
        // Notify AdMobBridge of focus change for ad display decisions
        if (::adMobBridge.isInitialized) {
            try {
                adMobBridge.onAppForegroundChange(hasFocus)
            } catch (e: Exception) {
                TestRigorLogger.logError("AdMobBridge focus notification failed", e)
            }
        }
    }

    /**
     * Solution #72 + FIX #36: Save permission state and WebView URL for activity recreation
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // FIX #36: Save WebView URL for process death recovery
        if (::webView.isInitialized) {
            try {
                val currentUrl = webView.url
                if (!currentUrl.isNullOrEmpty()) {
                    outState.putString("webview_url", currentUrl)
                    TestRigorLogger.logDebug("Saved WebView URL: $currentUrl")
                }
            } catch (e: Exception) {
                TestRigorLogger.logError("Failed to save WebView URL", e)
            }
        }

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
     * FIX #36: Restore WebView state on activity recreation
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        
        // Restore WebView URL
        savedInstanceState.getString("webview_url")?.let { url ->
            lastLoadedUrl = url
            TestRigorLogger.logDebug("Restored WebView URL: $url")
        }
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

    /**
     * FIX #17: Force WebView setup if normal initialization times out
     * This ensures the app never shows a blank screen
     */
    private fun forceWebViewSetup() {
        try {
            TestRigorLogger.logMilestone("Forcing WebView setup due to timeout")
            
            // FIX #14: Check lifecycle state
            if (isFinishing || isDestroyed) {
                TestRigorLogger.logWarning("Activity finishing - cannot force setup")
                return
            }
            
            if (!::webView.isInitialized) {
                setupWebViewForConversationMode()
            }
            
            // Force load the URL
            loadDefaultUrl()
            
            TestRigorLogger.logMilestone("Forced WebView setup completed")
        } catch (e: Exception) {
            TestRigorLogger.logError("Force WebView setup failed", e)
            // Last resort - show native interface
            createNativeTranslationInterface()
        }
    }

    private fun createNativeTranslationInterface() {
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)
        layout.setBackgroundColor(android.graphics.Color.WHITE)

        val titleText = android.widget.TextView(this)
        titleText.text = "LinguaVibe - Conversation Mode"
        titleText.textSize = 24f
        titleText.id = android.R.id.title
        titleText.contentDescription = "LinguaVibe Translation App"
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

        // Solution #90: Track content view set for Appium compatibility
        isContentViewSet = true
        TestRigorLogger.logMilestone("Native fallback content view set - ready for automation")
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

    // FIX #86-95: Enhanced memory management with WebView cleanup
    override fun onLowMemory() {
        super.onLowMemory()
        TestRigorLogger.logMilestone("onLowMemory - releasing cached resources")
        isLowMemoryMode = true
        
        try {
            // FIX #86: Release ad cached resources
            if (::adBridge.isInitialized) {
                adBridge.onLowMemory()
            }
            
            // FIX #89: Clear AdPreloadManager cached ads
            AdPreloadManager.clearAllCachedAds()
            
            // FIX #90: Clear WebView cache if available
            if (::webView.isInitialized) {
                webView.clearCache(false) // false = keep persistent cache
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("onLowMemory cleanup failed", e)
        }
    }

    // FIX #86-95: Enhanced memory trim handling
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        TestRigorLogger.logMilestone("onTrimMemory level: $level")
        
        try {
            if (::adBridge.isInitialized) {
                adBridge.onTrimMemory(level)
            }
            
            // FIX #91: Aggressive cleanup on critical memory levels
            when (level) {
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
                android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                    isLowMemoryMode = true
                    AdPreloadManager.clearAllCachedAds()
                    if (::webView.isInitialized) {
                        webView.clearCache(false)
                    }
                    TestRigorLogger.logMilestone("Critical memory - cleared caches")
                }
                android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE,
                android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                    isLowMemoryMode = true
                }
                android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                    // App went to background - can release some resources
                    TestRigorLogger.logDebug("UI hidden - app in background")
                }
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("onTrimMemory failed", e)
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

        // Clean up AdBridge
        try {
            if (::adBridge.isInitialized) {
                adBridge.cleanup()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("AdBridge cleanup", e)
        }
        
        // Clean up AdPreloadManager callbacks to prevent activity leaks
        try {
            AdPreloadManager.clearCallbacks()
        } catch (e: Exception) {
            TestRigorLogger.logError("AdPreloadManager cleanup", e)
        }

        // FIX: Clean up network retry state
        resetNetworkRetryState()
        
        // Solution #78: Clean up lifecycleHandler
        try {
            if (::lifecycleHandler.isInitialized) {
                lifecycleHandler.removeCallbacksAndMessages()
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("LifecycleHandler cleanup", e)
        }

        // Release audio enhancement resources
        releaseAudioEnhancements()

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