import Foundation
import WebKit
import UIKit

/// JavaScript bridge for ad operations. Exposed to the web app as `window.AdBridge`.
/// Only processes messages from the trusted main-frame origin.
@MainActor
final class AdBridge: NSObject {
    private let TAG = "AdBridge"

    weak var coordinator: WebViewCoordinator?
    var adMobManager: AdMobManager?
    var consentManager: ConsentManager?

    func setup(in webView: WKWebView) {
        webView.configuration.userContentController.add(self, name: "adBridge")
        injectProxyScript(in: webView)
    }

    private func injectProxyScript(in webView: WKWebView) {
        let script = """
        (function(){
          if (window.AdBridge) return;
          window.AdBridge = {
            notifyMicActive: function(active) {
              window.webkit.messageHandlers.micBridge.postMessage(!!active);
            },
            requestConsent: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'requestConsent'});
            },
            initialize: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'initialize'});
            },
            loadInterstitial: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'loadInterstitial'});
            },
            showInterstitial: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'showInterstitial'});
            },
            isInterstitialReady: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'isInterstitialReady'});
            },
            loadRewarded: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'loadRewarded'});
            },
            showRewarded: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'showRewarded'});
            },
            isRewardedReady: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'isRewardedReady'});
            },
            isInitialized: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'isInitialized'});
            },
            getDiagnostics: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'getDiagnostics'});
            },
            testShowInterstitial: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'testShowInterstitial'});
            },
            logEvent: function(eventName) {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'logEvent', eventName: eventName});
            },
            loadInterstitialAndShow: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'loadInterstitialAndShow'});
            },
            loadRewardedAndShow: function() {
              window.webkit.messageHandlers.adBridge.postMessage({action: 'loadRewardedAndShow'});
            }
          };
        })();
        """
        let userScript = WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        webView.configuration.userContentController.addUserScript(userScript)
    }

    func fireEvent(_ eventType: String, data: String = "") {
        let escaped = data
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
        let script = "window.onAdBridgeEvent && window.onAdBridgeEvent('\(eventType)', '\(escaped)');"
        coordinator?.evaluateJavaScript(script)
    }

    private func topViewController() -> UIViewController? {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = windowScene.windows.first?.rootViewController else { return nil }
        var vc = root
        while let presented = vc.presentedViewController { vc = presented }
        return vc
    }

    private var isTrustedMessage(_ message: WKScriptMessage) -> Bool {
        guard let webView = coordinator?.webView,
              let url = webView.url,
              AppConfig.isTrusted(url: url),
              message.frameInfo.isMainFrame else {
            return false
        }
        return true
    }
}

// MARK: - WKScriptMessageHandler

extension AdBridge: WKScriptMessageHandler {
    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        guard message.name == "adBridge",
              let body = message.body as? [String: Any],
              let action = body["action"] as? String,
              isTrustedMessage(message) else {
            if message.name == "adBridge" {
                print("[\(TAG)] Ignored bridge action from untrusted origin or non-main frame")
            }
            return
        }

        let mgr = adMobManager
        let consent = consentManager

        switch action {
        case "requestConsent":
            guard let vc = topViewController() else {
                fireEvent("consentResolved", data: "false")
                return
            }
            consent?.requestConsent(from: vc) { [weak self] canRequestAds in
                self?.fireEvent("consentResolved", data: canRequestAds ? "true" : "false")
            }

        case "initialize":
            mgr?.preloadInterstitial()
            fireEvent("adMobInitialized", data: "true")

        case "loadInterstitial":
            mgr?.preloadInterstitial()

        case "showInterstitial":
            guard let vc = topViewController() else {
                fireEvent("interstitialFailed", data: "manager_not_ready")
                return
            }
            mgr?.showInterstitial(from: vc)

        case "isInterstitialReady":
            let ready = mgr?.isInterstitialReady() ?? false
            fireEvent("isInterstitialReady", data: ready ? "true" : "false")

        case "loadRewarded":
            mgr?.preloadInterstitial()

        case "showRewarded":
            guard let vc = topViewController() else {
                fireEvent("rewardedFailed", data: "manager_not_ready")
                return
            }
            mgr?.loadRewardedAndShow(from: vc)

        case "isRewardedReady":
            let ready = mgr?.isRewardedReady() ?? false
            fireEvent("isRewardedReady", data: ready ? "true" : "false")

        case "isInitialized":
            let initialized = mgr?.isInitialized() ?? false
            fireEvent("isInitialized", data: initialized ? "true" : "false")

        case "getDiagnostics":
            let diagnostics: [String: Any] = [
                "adMobInitialized": mgr?.isInitialized() ?? false,
                "interstitialReady": mgr?.isInterstitialReady() ?? false,
                "rewardedReady": mgr?.isRewardedReady() ?? false,
                "consentAvailable": consent?.isConsentAvailable() ?? false,
                "timestamp": Int(Date().timeIntervalSince1970 * 1000)
            ]
            if let data = try? JSONSerialization.data(withJSONObject: diagnostics, options: []),
               let json = String(data: data, encoding: .utf8) {
                fireEvent("diagnostics", data: json)
            }

        case "testShowInterstitial":
            guard let vc = topViewController() else {
                fireEvent("interstitialFailed", data: "manager_not_ready")
                return
            }
            mgr?.showInterstitial(from: vc)

        case "logEvent":
            if let eventName = body["eventName"] as? String {
                AnalyticsManager.logEvent(eventName)
            }

        case "loadInterstitialAndShow":
            guard let vc = topViewController() else {
                fireEvent("interstitialFailed", data: "manager_not_ready")
                return
            }
            mgr?.loadInterstitialAndShow(from: vc)

        case "loadRewardedAndShow":
            guard let vc = topViewController() else {
                fireEvent("rewardedFailed", data: "manager_not_ready")
                return
            }
            mgr?.loadRewardedAndShow(from: vc)

        default:
            print("[\(TAG)] Unknown action: \(action)")
        }
    }
}
