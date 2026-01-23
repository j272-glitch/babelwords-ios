import UIKit
import WebKit

class WebAppBridge: NSObject, WKScriptMessageHandler {
    
    // MARK: - Properties
    
    private weak var viewController: UIViewController?
    private weak var webView: WKWebView?
    
    private var isPageLoaded = false
    private var deferredOperations: [() -> Void] = []
    private let bridgeLock = NSLock()
    private var lastBridgeCallTime: Date = Date.distantPast
    private let throttleInterval: TimeInterval = 0.1
    
    // MARK: - Initialization
    
    init(viewController: UIViewController, webView: WKWebView) {
        self.viewController = viewController
        self.webView = webView
        super.init()
        
        Logger.log("WebAppBridge initialized")
    }
    
    // MARK: - Page State
    
    func onPageLoaded() {
        isPageLoaded = true
        Logger.log("WebAppBridge: Page loaded, processing \(deferredOperations.count) deferred operations")
        
        bridgeLock.lock()
        let operations = deferredOperations
        deferredOperations.removeAll()
        bridgeLock.unlock()
        
        for operation in operations {
            DispatchQueue.main.async {
                operation()
            }
        }
    }
    
    func onPageUnloaded() {
        isPageLoaded = false
    }
    
    // MARK: - WKScriptMessageHandler
    
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any],
              let action = body["action"] as? String else {
            Logger.log("Invalid message format", level: "E")
            return
        }
        
        // Throttle rapid calls
        if shouldThrottle() {
            return
        }
        
        Logger.log("WebAppBridge: \(action)")
        
        switch action {
        case "showInterstitialAd":
            Logger.logAdEvent("Web app requested interstitial ad")
            // Handled by web app's VAST player for backward compatibility
            
        case "showRewardedAd":
            Logger.logAdEvent("Web app requested rewarded ad")
            // Handled by web app's VAST player for backward compatibility
            
        case "logEvent":
            if let eventName = body["eventName"] as? String {
                Logger.logAdEvent("Web event: \(eventName)")
            }
            
        case "trackTranslation":
            if let count = body["count"] as? Int {
                Logger.logAdEvent("Translation count: \(count)")
                if count >= 5 {
                    Logger.logAdEvent("Translation limit reached: \(count)")
                }
            }
            
        case "grantPremiumAccess":
            if let minutes = body["minutes"] as? Int {
                Logger.log("Granting premium access for \(minutes) minutes")
                showPremiumAccessToast(minutes: minutes)
            }
            
        default:
            Logger.log("Unknown action: \(action)", level: "W")
        }
    }
    
    // MARK: - Private Methods
    
    private func shouldThrottle() -> Bool {
        let now = Date()
        if now.timeIntervalSince(lastBridgeCallTime) < throttleInterval {
            Logger.log("WebAppBridge: Throttling rapid call")
            return true
        }
        lastBridgeCallTime = now
        return false
    }
    
    private func deferUntilPageLoaded(_ operation: @escaping () -> Void) {
        if isPageLoaded {
            DispatchQueue.main.async {
                operation()
            }
        } else {
            bridgeLock.lock()
            deferredOperations.append(operation)
            bridgeLock.unlock()
        }
    }
    
    private func showPremiumAccessToast(minutes: Int) {
        DispatchQueue.main.async { [weak self] in
            guard let vc = self?.viewController else { return }
            
            let alert = UIAlertController(
                title: "Premium Access",
                message: "You have \(minutes) minutes of unlimited translations!",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            
            vc.present(alert, animated: true) {
                // Auto-dismiss after 2 seconds
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    alert.dismiss(animated: true)
                }
            }
        }
    }
    
    private func evaluateJavaScript(_ js: String) {
        deferUntilPageLoaded { [weak self] in
            self?.webView?.evaluateJavaScript(js) { _, error in
                if let error = error {
                    Logger.log("JS evaluation error: \(error.localizedDescription)", level: "E")
                }
            }
        }
    }
}
