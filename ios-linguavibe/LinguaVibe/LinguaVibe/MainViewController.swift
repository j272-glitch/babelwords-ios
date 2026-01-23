import UIKit
import WebKit
import AVFoundation

class MainViewController: UIViewController {
    
    // MARK: - Properties
    
    private var webView: WKWebView!
    private var adMobBridge: AdMobBridge!
    private var webAppBridge: WebAppBridge!
    private var adPreloadManager: AdPreloadManager!
    
    private let baseURL = "https://linguagt.com"
    private var pendingDeepLinkURL: String?
    private var isWebViewFullyLoaded = false
    private var pendingAdReadyEvents: [String] = []
    
    // Permission handling
    private var isWaitingForMicPermission = false
    
    // Retry configuration
    private var loadRetryCount = 0
    private let maxLoadRetries = 5
    private let loadRetryDelay: TimeInterval = 0.5
    
    // MARK: - Lifecycle
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        Logger.log("MainViewController viewDidLoad")
        
        setupWebView()
        setupBridges()
        setupNotificationObservers()
        requestMicrophonePermission()
        
        // Defer ad initialization until WebView is ready
        // This prevents blocking the main thread on startup
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        Logger.log("MainViewController viewWillAppear")
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Logger.log("MainViewController viewDidAppear")
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
        webView?.configuration.userContentController.removeAllUserScripts()
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "iOSBridge")
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "iOSAdBridge")
        Logger.log("MainViewController deinit")
    }
    
    // MARK: - Setup
    
    private func setupWebView() {
        let config = WKWebViewConfiguration()
        
        // Enable JavaScript
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs
        
        // Allow inline media playback
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        
        // Setup user content controller for JS bridges
        let contentController = WKUserContentController()
        config.userContentController = contentController
        
        // Create WebView
        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.bounces = false
        webView.allowsBackForwardNavigationGestures = false
        
        // iOS 16.4+ debugging
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        
        view.addSubview(webView)
        
        Logger.log("WebView setup complete")
    }
    
    private func setupBridges() {
        // Initialize bridges
        adMobBridge = AdMobBridge(viewController: self, webView: webView)
        webAppBridge = WebAppBridge(viewController: self, webView: webView)
        adPreloadManager = AdPreloadManager.shared
        
        // Register JS message handlers
        let contentController = webView.configuration.userContentController
        contentController.add(webAppBridge, name: "iOSBridge")
        contentController.add(adMobBridge, name: "iOSAdBridge")
        
        // Inject JS bridge wrappers for Android compatibility
        let bridgeScript = WKUserScript(
            source: jsBridgeInjectionScript(),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        contentController.addUserScript(bridgeScript)
        
        // Setup ad preload callbacks
        setupAdPreloadCallbacks()
        
        Logger.log("Bridges setup complete")
        
        // Load URL after bridges are ready
        loadDefaultURL()
    }
    
    private func setupAdPreloadCallbacks() {
        adPreloadManager.onInterstitialReady = { [weak self] in
            Logger.logAdEvent("Native preload: Interstitial ready")
            self?.notifyWebViewAdReady(type: "interstitial")
        }
        
        adPreloadManager.onRewardedReady = { [weak self] in
            Logger.logAdEvent("Native preload: Rewarded ready")
            self?.notifyWebViewAdReady(type: "rewarded")
        }
        
        adPreloadManager.onRewardEarned = { [weak self] type, amount in
            Logger.logAdEvent("Native preload: Reward earned - \(type) x \(amount)")
            self?.notifyWebViewRewardEarned(type: type, amount: amount)
        }
    }
    
    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleDeepLinkNotification(_:)),
            name: .handleDeepLink,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidBecomeActive),
            name: .appDidBecomeActive,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: .appWillEnterForeground,
            object: nil
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: .appDidEnterBackground,
            object: nil
        )
    }
    
    // MARK: - JavaScript Bridge Injection
    
    private func jsBridgeInjectionScript() -> String {
        return """
        // iOS Bridge wrapper - provides Android-compatible interface
        (function() {
            console.log('[LinguaVibe iOS] Injecting bridge wrappers...');
            
            // AndroidAdBridge compatibility (maps to iOSAdBridge)
            window.AndroidAdBridge = {
                loadInterstitial: function(placementId) {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'loadInterstitial',
                        placementId: placementId || ''
                    });
                },
                showInterstitial: function(placementId) {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'showInterstitial',
                        placementId: placementId || ''
                    });
                },
                preloadInterstitial: function() {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'preloadInterstitial'
                    });
                },
                loadRewarded: function(placementId) {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'loadRewarded',
                        placementId: placementId || ''
                    });
                },
                showRewarded: function(placementId) {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'showRewarded',
                        placementId: placementId || ''
                    });
                },
                preloadRewarded: function() {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'preloadRewarded'
                    });
                },
                isInterstitialReady: function() {
                    // Synchronous check not available in WKWebView, use callbacks
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'isInterstitialReady'
                    });
                    return false; // Will be updated via callback
                },
                isRewardedReady: function() {
                    window.webkit.messageHandlers.iOSAdBridge.postMessage({
                        action: 'isRewardedReady'
                    });
                    return false;
                }
            };
            
            // AndroidBridge compatibility (maps to iOSBridge)
            window.AndroidBridge = {
                showInterstitialAd: function() {
                    window.webkit.messageHandlers.iOSBridge.postMessage({
                        action: 'showInterstitialAd'
                    });
                },
                showRewardedAd: function() {
                    window.webkit.messageHandlers.iOSBridge.postMessage({
                        action: 'showRewardedAd'
                    });
                },
                isRewardedAdReady: function() {
                    return true; // Always return true, web app handles availability
                },
                logEvent: function(eventName) {
                    window.webkit.messageHandlers.iOSBridge.postMessage({
                        action: 'logEvent',
                        eventName: eventName
                    });
                },
                trackTranslation: function(count) {
                    window.webkit.messageHandlers.iOSBridge.postMessage({
                        action: 'trackTranslation',
                        count: count
                    });
                },
                grantPremiumAccess: function(minutes) {
                    window.webkit.messageHandlers.iOSBridge.postMessage({
                        action: 'grantPremiumAccess',
                        minutes: minutes
                    });
                }
            };
            
            // Mark iOS WebView detection interfaces
            window.iOSWebView = true;
            window.__iOSWKWebView = true;
            
            console.log('[LinguaVibe iOS] Bridge wrappers injected successfully');
        })();
        """
    }
    
    // MARK: - URL Loading
    
    private func loadDefaultURL() {
        let urlString = pendingDeepLinkURL ?? baseURL
        pendingDeepLinkURL = nil
        
        guard let url = URL(string: urlString) else {
            Logger.log("Invalid URL: \(urlString)", level: "E")
            return
        }
        
        Logger.log("Loading URL: \(urlString)")
        let request = URLRequest(url: url)
        webView.load(request)
    }
    
    // MARK: - Deep Link Handling
    
    @objc private func handleDeepLinkNotification(_ notification: Notification) {
        guard let url = notification.userInfo?["url"] as? URL else { return }
        handleDeepLink(url: url)
    }
    
    private func handleDeepLink(url: URL) {
        Logger.log("Handling deep link: \(url.absoluteString)")
        
        guard let host = url.host,
              host == "linguagt.com" || host == "www.linguagt.com" else {
            Logger.log("Invalid deep link host: \(url.host ?? "nil")")
            return
        }
        
        var urlToLoad = baseURL
        if let path = url.path.isEmpty ? nil : url.path {
            urlToLoad += path
        }
        if let query = url.query {
            urlToLoad += "?\(query)"
        }
        
        if webView != nil {
            guard let loadURL = URL(string: urlToLoad) else { return }
            webView.load(URLRequest(url: loadURL))
        } else {
            pendingDeepLinkURL = urlToLoad
        }
    }
    
    // MARK: - App Lifecycle Notifications
    
    @objc private func appDidBecomeActive() {
        Logger.log("App became active - refreshing stale ads if needed")
        adPreloadManager.refreshStaleAds(viewController: self)
    }
    
    @objc private func appWillEnterForeground() {
        Logger.log("App entering foreground")
        adMobBridge.onAppForegroundChange(hasFocus: true)
    }
    
    @objc private func appDidEnterBackground() {
        Logger.log("App entered background")
        adMobBridge.onAppForegroundChange(hasFocus: false)
    }
    
    // MARK: - Microphone Permission
    
    private func requestMicrophonePermission() {
        Logger.log("Requesting microphone permission proactively")
        
        switch AVAudioSession.sharedInstance().recordPermission {
        case .granted:
            Logger.log("Microphone permission already granted")
        case .denied:
            Logger.log("Microphone permission denied")
        case .undetermined:
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                DispatchQueue.main.async {
                    Logger.log("Microphone permission \(granted ? "granted" : "denied")")
                }
            }
        @unknown default:
            break
        }
    }
    
    // MARK: - Ad Ready Notifications
    
    private func notifyWebViewAdReady(type: String) {
        if isWebViewFullyLoaded {
            let js = "window.onNativeAdReady && window.onNativeAdReady('\(type)');"
            webView.evaluateJavaScript(js) { _, error in
                if let error = error {
                    Logger.log("JS eval error: \(error.localizedDescription)", level: "E")
                }
            }
        } else {
            pendingAdReadyEvents.append(type)
        }
    }
    
    private func notifyWebViewRewardEarned(type: String, amount: Int) {
        let js = """
        if (window.onRewardEarned) {
            window.onRewardEarned('\(type)', \(amount));
        }
        if (window.onRewardedComplete) {
            window.onRewardedComplete('\(type)', \(amount));
        }
        """
        webView.evaluateJavaScript(js, completionHandler: nil)
    }
    
    private func flushPendingAdReadyEvents() {
        for type in pendingAdReadyEvents {
            notifyWebViewAdReady(type: type)
        }
        pendingAdReadyEvents.removeAll()
    }
    
    // MARK: - Initialize Ads (called after WebView loads)
    
    private func initializeNativeAdPreload() {
        Logger.log("Initializing Native Ad Preload Manager")
        adPreloadManager.initialize(viewController: self)
    }
}

// MARK: - WKNavigationDelegate

extension MainViewController: WKNavigationDelegate {
    
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        Logger.log("WebView started loading")
        webAppBridge.onPageUnloaded()
    }
    
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        Logger.log("WebView finished loading")
        loadRetryCount = 0
        isWebViewFullyLoaded = true
        webAppBridge.onPageLoaded()
        
        // Flush pending ad ready events
        flushPendingAdReadyEvents()
        
        // Initialize ads AFTER WebView is fully loaded (prevents blocking startup)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            self?.initializeNativeAdPreload()
        }
    }
    
    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        Logger.log("WebView navigation failed: \(error.localizedDescription)", level: "E")
        handleLoadError()
    }
    
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        Logger.log("WebView provisional navigation failed: \(error.localizedDescription)", level: "E")
        handleLoadError()
    }
    
    private func handleLoadError() {
        if loadRetryCount < maxLoadRetries {
            loadRetryCount += 1
            Logger.log("Scheduling retry \(loadRetryCount)/\(maxLoadRetries)")
            DispatchQueue.main.asyncAfter(deadline: .now() + loadRetryDelay) { [weak self] in
                self?.webView.reload()
            }
        } else {
            Logger.log("Max retries reached", level: "E")
        }
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.allow)
            return
        }
        
        let host = url.host ?? ""
        
        // Allow linguagt.com and www.linguagt.com
        if host == "linguagt.com" || host == "www.linguagt.com" || host.isEmpty {
            decisionHandler(.allow)
            return
        }
        
        // Open external links in Safari
        if navigationAction.navigationType == .linkActivated {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
            decisionHandler(.cancel)
            return
        }
        
        decisionHandler(.allow)
    }
}

// MARK: - WKUIDelegate

extension MainViewController: WKUIDelegate {
    
    // Handle JavaScript alerts
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            completionHandler()
        })
        present(alert, animated: true)
    }
    
    // Handle JavaScript confirms
    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in
            completionHandler(false)
        })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
            completionHandler(true)
        })
        present(alert, animated: true)
    }
    
    // Handle microphone permission requests from web content
    func webView(_ webView: WKWebView, requestMediaCapturePermissionFor origin: WKSecurityOrigin, initiatedByFrame frame: WKFrameInfo, type: WKMediaCaptureType, decisionHandler: @escaping (WKPermissionDecision) -> Void) {
        Logger.log("Media capture permission requested: \(type.rawValue)")
        
        if type == .microphone || type == .microphoneAndCamera {
            // Check iOS microphone permission
            switch AVAudioSession.sharedInstance().recordPermission {
            case .granted:
                decisionHandler(.grant)
            case .denied:
                decisionHandler(.deny)
            case .undetermined:
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        decisionHandler(granted ? .grant : .deny)
                    }
                }
            @unknown default:
                decisionHandler(.prompt)
            }
        } else {
            decisionHandler(.prompt)
        }
    }
}
