import Foundation
import WebKit

/// JavaScript bridge for StoreKit subscriptions. Replaces Android `SubscriptionBridge`.
@MainActor
final class SubscriptionBridge: NSObject {
    private let TAG = "SubscriptionBridge"

    weak var coordinator: WebViewCoordinator?
    var billingManager: BillingManager? {
        didSet {
            billingManager?.eventDispatcher = { [weak self] detail in
                self?.eventDispatcher?(detail)
            }
        }
    }
    var eventDispatcher: (([String: Any]) -> Void)?

    func setup(in webView: WKWebView) {
        webView.configuration.userContentController.add(self, name: "subscriptionBridge")
        injectProxyScript(in: webView)
    }

    private func injectProxyScript(in webView: WKWebView) {
        let script = """
        (function(){
          if (window.LinguaVibeSubscriptionBridge || window.AndroidSubscriptionBridge) return;
          var bridge = {
            purchaseProduct: function(productId, type) {
              window.webkit.messageHandlers.subscriptionBridge.postMessage({
                action: 'purchaseProduct', productId: productId, type: type
              });
            },
            subscribe: function(productId) {
              window.webkit.messageHandlers.subscriptionBridge.postMessage({
                action: 'subscribe', productId: productId
              });
            },
            restorePurchases: function() {
              window.webkit.messageHandlers.subscriptionBridge.postMessage({
                action: 'restorePurchases'
              });
            }
          };
          window.LinguaVibeSubscriptionBridge = bridge;
          window.AndroidSubscriptionBridge = bridge;
        })();
        """
        let userScript = WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        webView.configuration.userContentController.addUserScript(userScript)
    }
}

// MARK: - WKScriptMessageHandler

extension SubscriptionBridge: WKScriptMessageHandler {
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard message.name == "subscriptionBridge",
              let body = message.body as? [String: Any],
              let action = body["action"] as? String else { return }

        let billing = billingManager

        switch action {
        case "purchaseProduct":
            guard let productId = body["productId"] as? String else { return }
            Task {
                await billing?.purchaseProduct(productId)
            }

        case "subscribe":
            guard let productId = body["productId"] as? String else { return }
            Task {
                await billing?.purchaseProduct(productId)
            }

        case "restorePurchases":
            Task {
                await billing?.restorePurchases()
            }

        default:
            print("[\(TAG)] Unknown action: \(action)")
        }
    }
}
