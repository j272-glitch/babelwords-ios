import Foundation
import WebKit

class SubscriptionBridge: NSObject, WKScriptMessageHandler {
    
    static let bridgeName = "iOSSubscriptionBridge"
    
    private weak var webView: WKWebView?
    
    init(webView: WKWebView) {
        self.webView = webView
        super.init()
        
        // Register message handlers
        webView.configuration.userContentController.add(self, name: "subscriptionSubscribe")
        webView.configuration.userContentController.add(self, name: "subscriptionRestore")
        webView.configuration.userContentController.add(self, name: "subscriptionCheck")
        webView.configuration.userContentController.add(self, name: "subscriptionStatus")
        
        // Inject JavaScript interface
        injectJavaScriptInterface()
        
        // Set up callbacks
        setupCallbacks()
        
        print("[SubscriptionBridge] Initialized")
    }
    
    private func injectJavaScriptInterface() {
        let script = """
        window.iOSSubscriptionBridge = {
            subscribe: function(productId) {
                window.webkit.messageHandlers.subscriptionSubscribe.postMessage(productId || 'premium_monthly');
            },
            restorePurchases: function() {
                window.webkit.messageHandlers.subscriptionRestore.postMessage('');
            },
            checkSubscription: function() {
                window.webkit.messageHandlers.subscriptionCheck.postMessage('');
            },
            getSubscriptionStatus: function() {
                window.webkit.messageHandlers.subscriptionStatus.postMessage('');
            }
        };
        
        // Also expose as AndroidSubscriptionBridge for cross-platform compatibility
        window.AndroidSubscriptionBridge = window.iOSSubscriptionBridge;
        
        console.log('[SubscriptionBridge] JavaScript interface injected');
        """
        
        let userScript = WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        webView?.configuration.userContentController.addUserScript(userScript)
    }
    
    private func setupCallbacks() {
        if #available(iOS 15.0, *) {
            // Use modern StoreKit 2
            // Callbacks will be set in MainViewController
        } else {
            // Use legacy StoreKit
            // Callbacks will be set in MainViewController
        }
    }
    
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        switch message.name {
        case "subscriptionSubscribe":
            let productId = message.body as? String ?? "premium_monthly"
            handleSubscribe(productId: productId)
            
        case "subscriptionRestore":
            handleRestorePurchases()
            
        case "subscriptionCheck":
            handleCheckSubscription()
            
        case "subscriptionStatus":
            handleGetSubscriptionStatus()
            
        default:
            print("[SubscriptionBridge] Unknown message: \(message.name)")
        }
    }
    
    private func handleSubscribe(productId: String) {
        print("[SubscriptionBridge] subscribe(\(productId)) called from JS")
        
        if #available(iOS 15.0, *) {
            Task { @MainActor in
                await SubscriptionManager.shared.subscribe(productId: productId)
            }
        } else {
            SubscriptionManagerLegacy.shared.subscribe(productId: productId)
        }
    }
    
    private func handleRestorePurchases() {
        print("[SubscriptionBridge] restorePurchases() called from JS")
        
        if #available(iOS 15.0, *) {
            Task { @MainActor in
                await SubscriptionManager.shared.restorePurchases()
            }
        } else {
            SubscriptionManagerLegacy.shared.restorePurchases()
        }
    }
    
    private func handleCheckSubscription() {
        print("[SubscriptionBridge] checkSubscription() called from JS")
        
        let isPremium: Bool
        if #available(iOS 15.0, *) {
            isPremium = SubscriptionManager.shared.checkSubscription()
        } else {
            isPremium = SubscriptionManagerLegacy.shared.checkSubscription()
        }
        
        notifyWebView(event: "subscription_check_result", data: ["isPremium": isPremium])
    }
    
    private func handleGetSubscriptionStatus() {
        print("[SubscriptionBridge] getSubscriptionStatus() called from JS")
        
        let status: String
        if #available(iOS 15.0, *) {
            status = SubscriptionManager.shared.getSubscriptionStatus()
        } else {
            status = SubscriptionManagerLegacy.shared.getSubscriptionStatus()
        }
        
        notifyWebView(event: "subscription_status_result", data: ["status": status])
    }
    
    // MARK: - Callback Methods (called from SubscriptionManager)
    
    func onSubscriptionPurchased(transactionId: String, productId: String) {
        print("[SubscriptionBridge] Subscription purchased - \(productId)")
        notifyWebView(event: "subscription_purchased", data: [
            "transactionId": transactionId,
            "productId": productId,
            "isPremium": true
        ])
    }
    
    func onSubscriptionRestored(transactionId: String, productId: String) {
        print("[SubscriptionBridge] Subscription restored - \(productId)")
        notifyWebView(event: "subscription_restored", data: [
            "transactionId": transactionId,
            "productId": productId,
            "isPremium": true
        ])
    }
    
    func onSubscriptionError(errorCode: Int, message: String) {
        print("[SubscriptionBridge] Error \(errorCode) - \(message)")
        notifyWebView(event: "subscription_error", data: [
            "errorCode": errorCode,
            "message": message
        ])
    }
    
    func onPremiumStatusChanged(isPremium: Bool) {
        print("[SubscriptionBridge] Premium status changed to \(isPremium)")
        notifyWebView(event: "premium_status_changed", data: [
            "isPremium": isPremium
        ])
    }
    
    private func notifyWebView(event: String, data: [String: Any]) {
        var eventData = data
        eventData["event"] = event
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: eventData),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return
        }
        
        let script = """
        (function() {
            var eventData = \(jsonString);
            if (window.onSubscriptionEvent) {
                window.onSubscriptionEvent(eventData);
            }
            window.dispatchEvent(new CustomEvent('subscription_event', { detail: eventData }));
        })();
        """
        
        DispatchQueue.main.async { [weak self] in
            self?.webView?.evaluateJavaScript(script, completionHandler: nil)
        }
    }
    
    func cleanup() {
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "subscriptionSubscribe")
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "subscriptionRestore")
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "subscriptionCheck")
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "subscriptionStatus")
        print("[SubscriptionBridge] Cleaned up")
    }
}

// MARK: - SubscriptionManager Callback Conformance

@available(iOS 15.0, *)
extension SubscriptionBridge: SubscriptionManager.SubscriptionCallback {
    // Already implemented above
}

extension SubscriptionBridge: SubscriptionManagerLegacy.SubscriptionCallback {
    // Already implemented above
}
