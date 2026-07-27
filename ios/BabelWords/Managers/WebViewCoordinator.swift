import UIKit
import WebKit

/// Coordinates the WKWebView and its JavaScript bridges. Replaces the Android `WebViewConfig` object.
@MainActor
final class WebViewCoordinator: NSObject {
    private let TAG = "WebViewCoordinator"

    private(set) lazy var webView: WKWebView = {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.preferences.javaScriptEnabled = true
        config.defaultWebpagePreferences.allowsContentJavaScript = true

        let contentController = config.userContentController
        contentController.add(self, name: "micBridge")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = false
        webView.isOpaque = true
        webView.backgroundColor = .systemBackground
        webView.scrollView.bounces = false
        return webView
    }()

    let adBridge = AdBridge()
    let subscriptionBridge = SubscriptionBridge()

    var onLoadingChange: ((Bool) -> Void)?
    var onError: ((Error?) -> Void)?
    var onRetry: (() -> Void)?

    private var pageStartCount = 0
    private var lastBaseURL: String?
    private let redirectLoopThreshold = 3

    private(set) var isMicActive = false {
        didSet {
            resetMicWatchdog()
        }
    }
    private var micWatchdogWorkItem: DispatchWorkItem?
    private let micWatchdogInterval: TimeInterval = 45

    override init() {
        super.init()
        adBridge.coordinator = self
        subscriptionBridge.coordinator = self
        adBridge.setup(in: webView)
        subscriptionBridge.setup(in: webView)
    }

    func cleanup() {
        micWatchdogWorkItem?.cancel()
        let contentController = webView.configuration.userContentController
        contentController.removeScriptMessageHandler(forName: "micBridge")
        contentController.removeScriptMessageHandler(forName: "adBridge")
        contentController.removeScriptMessageHandler(forName: "subscriptionBridge")
        contentController.removeAllUserScripts()
    }

    deinit {}

    // MARK: - Navigation

    func load(url: URL) {
        let request = URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 30)
        webView.load(request)
    }

    func loadHTMLString(_ html: String, baseURL: URL? = nil) {
        webView.loadHTMLString(html, baseURL: baseURL)
    }

    func goBack() -> Bool {
        guard webView.canGoBack else { return false }
        webView.goBack()
        return true
    }

    func evaluateJavaScript(_ script: String, completion: ((Any?, Error?) -> Void)? = nil) {
        webView.evaluateJavaScript(script) { result, error in
            completion?(result, error)
        }
    }

    // MARK: - Mic safety

    func setMicState(_ active: Bool) {
        isMicActive = active
    }

    private func resetMicWatchdog() {
        micWatchdogWorkItem?.cancel()
        micWatchdogWorkItem = nil
        guard isMicActive else { return }
        let workItem = DispatchWorkItem { [weak self] in
            self?.isMicActive = false
            print("[WebViewCoordinator] Mic state reset after watchdog timeout")
        }
        micWatchdogWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + micWatchdogInterval, execute: workItem)
    }

    // MARK: - Loading state

    private func handlePageStarted(url: URL?) {
        let base = baseURLString(from: url)
        if base == lastBaseURL {
            pageStartCount += 1
        } else {
            lastBaseURL = base
            pageStartCount = 1
        }

        if pageStartCount >= redirectLoopThreshold {
            print("[WebViewCoordinator] Redirect loop detected")
            webView.stopLoading()
            onLoadingChange?(false)
            onError?(nil)
            return
        }

        onLoadingChange?(true)

        if isMicActive {
            setMicState(false)
        }
    }

    private func handlePageFinished(url: URL?) {
        pageStartCount = 0
        onLoadingChange?(false)
        evaluateJavaScript("window.__iosWebChromeClient = true;")
    }

    private func baseURLString(from url: URL?) -> String? {
        guard let url = url else { return nil }
        return "\(url.scheme ?? "")://\(url.host ?? "")\(url.path)"
    }
}

// MARK: - WKNavigationDelegate

extension WebViewCoordinator: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        handlePageStarted(url: webView.url)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        handlePageFinished(url: webView.url)
    }

    func webView(
        _ webView: WKWebView,
        didFail navigation: WKNavigation!,
        withError error: Error
    ) {
        handleError(error)
    }

    func webView(
        _ webView: WKWebView,
        didFailProvisionalNavigation navigation: WKNavigation!,
        withError error: Error
    ) {
        handleError(error)
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        let requestURL = navigationAction.request.url
        if AppConfig.isNavigationAllowed(url: requestURL) {
            decisionHandler(.allow)
        } else if let url = requestURL, navigationAction.targetFrame?.isMainFrame == true {
            // Open external links in the system browser instead of inside the app.
            decisionHandler(.cancel)
            UIApplication.shared.open(url)
        } else {
            decisionHandler(.cancel)
        }
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationResponse: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        decisionHandler(.allow)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        print("[WebViewCoordinator] WebContent process terminated")
        if isMicActive { setMicState(false) }
        onError?(nil)
    }

    private func handleError(_ error: Error) {
        let nsError = error as NSError
        // Ignore frame-load cancelled errors.
        if nsError.code == NSURLErrorCancelled { return }
        onLoadingChange?(false)
        onError?(error)
    }
}

// MARK: - WKUIDelegate

extension WebViewCoordinator: WKUIDelegate {
    func webView(
        _ webView: WKWebView,
        requestMediaCapturePermissionFor origin: WKSecurityOrigin,
        initiatedByFrame frame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: @escaping (WKPermissionDecision) -> Void
    ) {
        let host = origin.host.lowercased()
        let isTrusted = AppConfig.trustedHosts.contains(host)
        switch type {
        case .microphone, .camera:
            decisionHandler(isTrusted ? .grant : .deny)
        @unknown default:
            decisionHandler(.deny)
        }
    }
}

// MARK: - WKScriptMessageHandler

extension WebViewCoordinator: WKScriptMessageHandler {
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard message.name == "micBridge" else { return }
        guard message.frameInfo.isMainFrame,
              AppConfig.isTrusted(url: webView.url) else {
            print("[\(TAG)] Ignored mic bridge message from untrusted origin or non-main frame")
            return
        }
        if let active = message.body as? Bool {
            setMicState(active)
        }
    }
}
