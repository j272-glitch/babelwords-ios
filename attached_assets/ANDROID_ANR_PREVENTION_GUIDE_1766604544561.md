# Android ANR Prevention Guide for LinguaLink

This guide documents common ANR (Application Not Responding) scenarios and their solutions for the LinguaLink Android WebView application.

## Overview

ANRs occur when the main UI thread is blocked for more than 5 seconds. In WebView-based applications, this commonly happens during:
- WebView initialization
- Network operations
- JavaScript bridge calls
- Ad loading operations

---

## 1. WebView Initialization ANR

### Problem
WebView's first initialization triggers Chromium's network stack setup, which can block the main thread for several seconds.

### Stack Trace Pattern
```
at android.webkit.WebView.<init>
at com.lingualink.linguagt.MainActivity.setupWebViewForConversationMode
at com.lingualink.linguagt.MainActivity.onCreate
```

### Solution: Async WebView Initialization

```kotlin
class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private lateinit var loadingView: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        loadingView = findViewById(R.id.loading_view)
        
        // Show loading state immediately
        loadingView.visibility = View.VISIBLE
        
        // Defer WebView setup to next frame
        window.decorView.post {
            setupWebViewForConversationMode()
        }
    }
    
    private fun setupWebViewForConversationMode() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Create WebView
                webView = WebView(this@MainActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                
                // Configure WebView settings
                configureWebViewSettings()
                
                // Add to layout
                findViewById<ViewGroup>(R.id.webview_container).addView(webView)
                
                // Load URL
                webView?.loadUrl("https://your-app-url.com")
                
            } finally {
                loadingView.visibility = View.GONE
            }
        }
    }
}
```

### Solution: Pre-warm WebView in Application Class

```kotlin
class LinguaLinkApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Pre-initialize WebView provider on background thread
        Thread {
            try {
                Looper.prepare()
                WebView(this).destroy()
            } catch (e: Exception) {
                Log.w("App", "WebView pre-warm failed: ${e.message}")
            }
        }.start()
    }
}
```

---

## 2. Network Callback Registration ANR

### Problem
`ConnectivityManager.registerDefaultNetworkCallback()` can block during WebView initialization.

### Stack Trace Pattern
```
at android.net.ConnectivityManager.registerDefaultNetworkCallback
at org.chromium.android_webview.AwContentsLifecycleNotifier.onFirstWebViewCreated
```

### Solution: Background Network Monitoring

```kotlin
class NetworkMonitor(private val context: Context) {
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    fun startMonitoring(onNetworkChange: (Boolean) -> Unit) {
        // Run on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                    as ConnectivityManager
                
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        onNetworkChange(true)
                    }
                    
                    override fun onLost(network: Network) {
                        onNetworkChange(false)
                    }
                }
                
                connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                Log.e("NetworkMonitor", "Failed to register callback: ${e.message}")
            }
        }
    }
    
    fun stopMonitoring() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.w("NetworkMonitor", "Failed to unregister: ${e.message}")
        }
    }
}
```

---

## 3. JavaScript Bridge ANR

### Problem
Synchronous JavaScript calls from native code can block if the WebView is busy.

### Solution: Async JavaScript Evaluation

```kotlin
class SafeJavaScriptBridge(private val webView: WebView?) {
    private val handler = Handler(Looper.getMainLooper())
    private val jsTimeout = 5000L // 5 second timeout
    
    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        if (webView == null) {
            callback?.invoke(null)
            return
        }
        
        var completed = false
        
        // Set timeout
        val timeoutRunnable = Runnable {
            if (!completed) {
                completed = true
                Log.w("JSBridge", "JavaScript evaluation timed out: $script")
                callback?.invoke(null)
            }
        }
        handler.postDelayed(timeoutRunnable, jsTimeout)
        
        // Execute JavaScript
        handler.post {
            try {
                webView.evaluateJavascript(script) { result ->
                    if (!completed) {
                        completed = true
                        handler.removeCallbacks(timeoutRunnable)
                        callback?.invoke(result)
                    }
                }
            } catch (e: Exception) {
                if (!completed) {
                    completed = true
                    handler.removeCallbacks(timeoutRunnable)
                    Log.e("JSBridge", "JavaScript evaluation failed: ${e.message}")
                    callback?.invoke(null)
                }
            }
        }
    }
}
```

---

## 4. Ad Loading ANR

### Problem
Ad SDK initialization and loading can block the main thread.

### Solution: Deferred Ad Initialization

```kotlin
class AdManager(private val context: Context) {
    private var isInitialized = false
    private val initLock = Object()
    
    fun initializeAsync(onComplete: () -> Unit) {
        if (isInitialized) {
            onComplete()
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            // Delay ad initialization until after app is responsive
            delay(2000)
            
            try {
                MobileAds.initialize(context) { status ->
                    synchronized(initLock) {
                        isInitialized = true
                    }
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("AdManager", "Ad init failed: ${e.message}")
                onComplete()
            }
        }
    }
    
    fun loadInterstitialAsync(
        adUnitId: String,
        onLoaded: (InterstitialAd?) -> Unit
    ) {
        if (!isInitialized) {
            Log.w("AdManager", "Ads not initialized yet")
            onLoaded(null)
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    onLoaded(ad)
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AdManager", "Ad failed to load: ${error.message}")
                    onLoaded(null)
                }
            }
        )
    }
}
```

---

## 5. WebView URL Loading ANR

### Problem
Loading complex URLs with heavy JavaScript can cause temporary unresponsiveness.

### Solution: Progressive Loading with Timeout

```kotlin
class SafeWebViewLoader(private val webView: WebView?) {
    private val handler = Handler(Looper.getMainLooper())
    private var loadingTimeout: Runnable? = null
    
    fun loadUrlWithTimeout(
        url: String,
        timeoutMs: Long = 30000,
        onTimeout: () -> Unit
    ) {
        if (webView == null) {
            onTimeout()
            return
        }
        
        // Cancel any existing timeout
        loadingTimeout?.let { handler.removeCallbacks(it) }
        
        // Set new timeout
        loadingTimeout = Runnable {
            Log.w("WebViewLoader", "URL loading timed out: $url")
            webView.stopLoading()
            onTimeout()
        }
        handler.postDelayed(loadingTimeout!!, timeoutMs)
        
        // Set WebViewClient to detect completion
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loadingTimeout?.let { handler.removeCallbacks(it) }
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                loadingTimeout?.let { handler.removeCallbacks(it) }
                Log.e("WebViewLoader", "Page load error: ${error?.description}")
            }
        }
        
        webView.loadUrl(url)
    }
    
    fun cancelLoading() {
        loadingTimeout?.let { handler.removeCallbacks(it) }
        webView?.stopLoading()
    }
}
```

---

## 6. StrictMode for Development

Enable StrictMode during development to catch potential ANRs early:

```kotlin
class LinguaLinkApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            // Detect all main thread violations
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyFlashScreen() // Visual indicator
                    .build()
            )
            
            // Detect VM violations
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
```

---

## 7. Complete MainActivity Example

```kotlin
class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private lateinit var loadingView: ProgressBar
    private lateinit var errorView: View
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var adManager: AdManager
    private lateinit var jsBridge: SafeJavaScriptBridge
    
    private var isWebViewReady = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views immediately
        loadingView = findViewById(R.id.loading_progress)
        errorView = findViewById(R.id.error_view)
        
        // Show loading state
        loadingView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        
        // Initialize managers (non-blocking)
        networkMonitor = NetworkMonitor(this)
        adManager = AdManager(this)
        
        // Start network monitoring in background
        networkMonitor.startMonitoring { isConnected ->
            runOnUiThread {
                handleNetworkChange(isConnected)
            }
        }
        
        // Defer heavy initialization
        window.decorView.post {
            initializeWebViewAsync()
        }
        
        // Initialize ads after delay
        adManager.initializeAsync {
            Log.d("MainActivity", "Ads initialized")
        }
    }
    
    private fun initializeWebViewAsync() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                // Create and configure WebView
                webView = WebView(this@MainActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    visibility = View.INVISIBLE // Hide until loaded
                }
                
                configureWebViewSettings()
                setupJavaScriptInterface()
                
                // Add to container
                findViewById<FrameLayout>(R.id.webview_container).addView(webView)
                
                // Initialize JS bridge
                jsBridge = SafeJavaScriptBridge(webView)
                
                // Load URL with timeout
                SafeWebViewLoader(webView).loadUrlWithTimeout(
                    url = BuildConfig.APP_URL,
                    timeoutMs = 30000
                ) {
                    showError("Failed to load. Please check your connection.")
                }
                
                isWebViewReady = true
                
            } catch (e: Exception) {
                Log.e("MainActivity", "WebView init failed: ${e.message}")
                showError("Failed to initialize. Please restart the app.")
            }
        }
    }
    
    private fun configureWebViewSettings() {
        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            
            // Performance optimizations
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                runOnUiThread {
                    loadingView.visibility = View.GONE
                    webView?.visibility = View.VISIBLE
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    runOnUiThread {
                        showError("Connection error. Please try again.")
                    }
                }
            }
        }
        
        webView?.webChromeClient = WebChromeClient()
    }
    
    private fun setupJavaScriptInterface() {
        webView?.addJavascriptInterface(
            WebAppInterface(this, adManager),
            "Android"
        )
    }
    
    private fun handleNetworkChange(isConnected: Boolean) {
        if (!isConnected && isWebViewReady) {
            showError("No internet connection")
        } else if (isConnected && errorView.visibility == View.VISIBLE) {
            errorView.visibility = View.GONE
            webView?.reload()
        }
    }
    
    private fun showError(message: String) {
        loadingView.visibility = View.GONE
        webView?.visibility = View.INVISIBLE
        errorView.visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_message).text = message
    }
    
    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }
    
    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }
    
    override fun onDestroy() {
        networkMonitor.stopMonitoring()
        
        // Clean up WebView properly
        webView?.let { wv ->
            wv.stopLoading()
            wv.clearHistory()
            wv.clearCache(true)
            wv.loadUrl("about:blank")
            wv.onPause()
            wv.removeAllViews()
            wv.destroyDrawingCache()
            wv.destroy()
        }
        webView = null
        
        super.onDestroy()
    }
    
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
```

---

## 8. Monitoring and Debugging

### ANR Detection in Production

```kotlin
class ANRWatchdog : Thread() {
    private val timeout = 5000L // 5 seconds
    private var tick = 0L
    private val handler = Handler(Looper.getMainLooper())
    
    private val ticker = Runnable {
        tick = System.currentTimeMillis()
    }
    
    override fun run() {
        while (!isInterrupted) {
            tick = 0L
            handler.post(ticker)
            
            try {
                sleep(timeout)
            } catch (e: InterruptedException) {
                return
            }
            
            if (tick == 0L) {
                // Main thread didn't respond in time
                Log.e("ANRWatchdog", "ANR detected!")
                Log.e("ANRWatchdog", getMainThreadStackTrace())
                
                // Report to analytics
                reportANR()
            }
        }
    }
    
    private fun getMainThreadStackTrace(): String {
        val mainThread = Looper.getMainLooper().thread
        return mainThread.stackTrace.joinToString("\n") { 
            "  at $it" 
        }
    }
    
    private fun reportANR() {
        // Send to your analytics service
    }
}
```

### Usage

```kotlin
class LinguaLinkApplication : Application() {
    private var anrWatchdog: ANRWatchdog? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Start ANR watchdog in release builds
        if (!BuildConfig.DEBUG) {
            anrWatchdog = ANRWatchdog().apply { start() }
        }
    }
    
    override fun onTerminate() {
        anrWatchdog?.interrupt()
        super.onTerminate()
    }
}
```

---

## Summary Checklist

- [ ] WebView initialization deferred with `post()` or coroutines
- [ ] WebView pre-warmed in Application class
- [ ] Network callbacks registered on background thread
- [ ] JavaScript evaluation has timeouts
- [ ] Ad SDK initialization delayed 2+ seconds
- [ ] URL loading has timeouts
- [ ] StrictMode enabled in debug builds
- [ ] ANR watchdog for production monitoring
- [ ] Proper WebView cleanup in onDestroy
- [ ] Loading states shown during async operations

---

## Related Documentation

- [ADMOB_ANDROID_INTEGRATION.md](./ADMOB_ANDROID_INTEGRATION.md) - Ad integration guide
- [ANDROID_CRASH_PREVENTION_GUIDE.md](./ANDROID_CRASH_PREVENTION_GUIDE.md) - 44 crash prevention fixes
