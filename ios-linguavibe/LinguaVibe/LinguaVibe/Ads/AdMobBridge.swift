import UIKit
import WebKit
import GoogleMobileAds
import UserMessagingPlatform

class AdMobBridge: NSObject, WKScriptMessageHandler {
    
    // MARK: - Constants
    
    private static let interstitialAdUnitID = "ca-app-pub-9991891515643313/5076005693"
    private static let rewardedAdUnitID = "ca-app-pub-9991891515643313/6313049833"
    
    private static let adExpiryMS: TimeInterval = 45 * 60 // 45 minutes
    private static let loadTimeoutMS: TimeInterval = 15 // 15 seconds
    private static let foregroundDelayMS: TimeInterval = 1.5
    private static let initialRetryDelay: TimeInterval = 5
    private static let maxRetryDelay: TimeInterval = 60
    
    // MARK: - Properties
    
    private weak var viewController: UIViewController?
    private weak var webView: WKWebView?
    
    private var interstitialAd: GADInterstitialAd?
    private var rewardedAd: GADRewardedAd?
    
    private var isInterstitialReady = false
    private var isRewardedReady = false
    private var isInterstitialLoading = false
    private var isRewardedLoading = false
    private var isShowingAd = false
    private var isAppInForeground = true
    
    private var interstitialLoadTime: Date?
    private var rewardedLoadTime: Date?
    
    private var autoShowInterstitial = false
    private var autoShowRewarded = false
    
    // Reward tracking for re-emission after foreground
    private var localRewardEarned = false
    private var localRewardType = ""
    private var localRewardAmount = 0
    
    // Retry configuration
    private var interstitialRetryDelay: TimeInterval = initialRetryDelay
    private var rewardedRetryDelay: TimeInterval = initialRetryDelay
    private var interstitialRetryTimer: Timer?
    private var rewardedRetryTimer: Timer?
    
    // Consent tracking
    private var hasConsent = false
    private var consentChecked = false
    
    // Impression counters
    private var totalImpressions = 0
    private var interstitialImpressions = 0
    private var rewardedImpressions = 0
    
    // MARK: - Initialization
    
    init(viewController: UIViewController, webView: WKWebView) {
        self.viewController = viewController
        self.webView = webView
        super.init()
        
        Logger.log("═" + String(repeating: "═", count: 49))
        Logger.log("ADMOB BRIDGE INITIALIZED")
        Logger.log("═" + String(repeating: "═", count: 49))
        
        setupLifecycleObservers()
    }
    
    deinit {
        interstitialRetryTimer?.invalidate()
        rewardedRetryTimer?.invalidate()
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - Lifecycle
    
    private func setupLifecycleObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(appDidEnterBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
    }
    
    @objc private func appWillEnterForeground() {
        isAppInForeground = true
        Logger.log("App entered foreground")
        refreshStaleAds()
    }
    
    @objc private func appDidEnterBackground() {
        isAppInForeground = false
        Logger.log("App entered background")
    }
    
    func onAppForegroundChange(hasFocus: Bool) {
        isAppInForeground = hasFocus
    }
    
    private func refreshStaleAds() {
        if !isInterstitialFresh() {
            loadInterstitialAd()
        }
        if !isRewardedFresh() {
            loadRewardedAd()
        }
    }
    
    // MARK: - WKScriptMessageHandler
    
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any],
              let action = body["action"] as? String else {
            Logger.log("Invalid message format", level: "E")
            return
        }
        
        Logger.log("JS → \(action)")
        
        switch action {
        case "loadInterstitial":
            let placementId = body["placementId"] as? String ?? ""
            autoShowInterstitial = true
            checkConsentThenLoad { [weak self] in
                self?.loadInterstitialAd()
            }
            
        case "showInterstitial":
            showInterstitialAd()
            
        case "preloadInterstitial":
            autoShowInterstitial = true
            checkConsentThenLoad { [weak self] in
                if self?.isInterstitialReady == true && self?.interstitialAd != nil {
                    self?.showInterstitialAd()
                } else {
                    self?.loadInterstitialAd()
                }
            }
            
        case "loadRewarded":
            let placementId = body["placementId"] as? String ?? ""
            autoShowRewarded = true
            checkConsentThenLoad { [weak self] in
                self?.loadRewardedAd()
            }
            
        case "showRewarded":
            showRewardedAd()
            
        case "preloadRewarded":
            autoShowRewarded = true
            checkConsentThenLoad { [weak self] in
                if self?.isRewardedReady == true && self?.rewardedAd != nil {
                    self?.showRewardedAd()
                } else {
                    self?.loadRewardedAd()
                }
            }
            
        case "isInterstitialReady":
            let ready = isInterstitialReady && interstitialAd != nil
            emitCallback("window.onInterstitialReadyStatus && window.onInterstitialReadyStatus(\(ready));")
            
        case "isRewardedReady":
            let ready = isRewardedReady && rewardedAd != nil
            emitCallback("window.onRewardedReadyStatus && window.onRewardedReadyStatus(\(ready));")
            
        default:
            Logger.log("Unknown action: \(action)", level: "W")
        }
    }
    
    // MARK: - Ad Freshness
    
    private func isInterstitialFresh() -> Bool {
        guard interstitialAd != nil, let loadTime = interstitialLoadTime else {
            return false
        }
        return Date().timeIntervalSince(loadTime) < Self.adExpiryMS
    }
    
    private func isRewardedFresh() -> Bool {
        guard rewardedAd != nil, let loadTime = rewardedLoadTime else {
            return false
        }
        return Date().timeIntervalSince(loadTime) < Self.adExpiryMS
    }
    
    // MARK: - Consent
    
    private func checkConsentThenLoad(completion: @escaping () -> Void) {
        let consentInfo = UMPConsentInformation.sharedInstance
        
        switch consentInfo.consentStatus {
        case .obtained:
            Logger.log("✅ Consent already obtained")
            hasConsent = true
            consentChecked = true
            completion()
            
        case .notRequired:
            Logger.log("ℹ️ Consent not required (non-EEA)")
            hasConsent = true
            consentChecked = true
            completion()
            
        case .required:
            Logger.log("📋 Consent required - loading with limited ads")
            consentChecked = true
            completion()
            
        case .unknown:
            Logger.log("❓ Consent UNKNOWN - loading with non-personalized ads")
            consentChecked = true
            completion()
            
        @unknown default:
            completion()
        }
    }
    
    func onConsentObtained(gdprConsent: Bool) {
        Logger.log("═" + String(repeating: "═", count: 49))
        Logger.log("CONSENT OBTAINED: gdprConsent=\(gdprConsent)")
        Logger.log("═" + String(repeating: "═", count: 49))
        hasConsent = gdprConsent
        consentChecked = true
    }
    
    // MARK: - Interstitial Loading
    
    private func loadInterstitialAd() {
        Logger.log("Loading AdMob Interstitial...")
        
        guard !isInterstitialLoading else {
            Logger.log("Interstitial already loading - skipping")
            return
        }
        
        // Check preload manager first
        if AdPreloadManager.shared.isInterstitialReady(),
           let preloaded = AdPreloadManager.shared.getCachedInterstitial() {
            Logger.log("Using preloaded interstitial")
            isInterstitialReady = true
            interstitialAd = preloaded
            notifyAdLoaded(type: "interstitial")
            if autoShowInterstitial {
                autoShowInterstitial = false
                showInterstitialAd()
            }
            return
        }
        
        if isInterstitialFresh() {
            Logger.log("Fresh interstitial already exists")
            return
        }
        
        isInterstitialLoading = true
        
        let request = GADRequest()
        if !hasConsent && consentChecked {
            let extras = GADExtras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        
        GADInterstitialAd.load(
            withAdUnitID: Self.interstitialAdUnitID,
            request: request
        ) { [weak self] ad, error in
            guard let self = self else { return }
            
            self.isInterstitialLoading = false
            
            if let error = error {
                Logger.log("✗ Interstitial load FAILED: \(error.localizedDescription)", level: "E")
                self.notifyAdFailed(type: "interstitial", error: error.localizedDescription)
                self.scheduleRetry(isInterstitial: true)
                return
            }
            
            guard let ad = ad else { return }
            
            Logger.log("✓ INTERSTITIAL LOADED")
            self.interstitialAd = ad
            self.isInterstitialReady = true
            self.interstitialLoadTime = Date()
            self.interstitialRetryDelay = Self.initialRetryDelay
            self.interstitialRetryTimer?.invalidate()
            
            ad.fullScreenContentDelegate = self
            
            self.notifyAdLoaded(type: "interstitial")
            
            if self.autoShowInterstitial {
                Logger.log("  ► Auto-showing interstitial...")
                self.autoShowInterstitial = false
                self.showInterstitialAd()
            }
        }
    }
    
    // MARK: - Rewarded Loading
    
    private func loadRewardedAd() {
        Logger.log("Loading AdMob Rewarded...")
        
        guard !isRewardedLoading else {
            Logger.log("Rewarded already loading - skipping")
            return
        }
        
        // Check preload manager first
        if AdPreloadManager.shared.isRewardedReady(),
           let preloaded = AdPreloadManager.shared.getCachedRewarded() {
            Logger.log("Using preloaded rewarded")
            isRewardedReady = true
            rewardedAd = preloaded
            notifyAdLoaded(type: "rewarded")
            if autoShowRewarded {
                autoShowRewarded = false
                showRewardedAd()
            }
            return
        }
        
        if isRewardedFresh() {
            Logger.log("Fresh rewarded already exists")
            return
        }
        
        isRewardedLoading = true
        
        let request = GADRequest()
        if !hasConsent && consentChecked {
            let extras = GADExtras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        
        GADRewardedAd.load(
            withAdUnitID: Self.rewardedAdUnitID,
            request: request
        ) { [weak self] ad, error in
            guard let self = self else { return }
            
            self.isRewardedLoading = false
            
            if let error = error {
                Logger.log("✗ Rewarded load FAILED: \(error.localizedDescription)", level: "E")
                self.notifyAdFailed(type: "rewarded", error: error.localizedDescription)
                self.scheduleRetry(isInterstitial: false)
                return
            }
            
            guard let ad = ad else { return }
            
            Logger.log("✓ REWARDED LOADED")
            self.rewardedAd = ad
            self.isRewardedReady = true
            self.rewardedLoadTime = Date()
            self.rewardedRetryDelay = Self.initialRetryDelay
            self.rewardedRetryTimer?.invalidate()
            
            ad.fullScreenContentDelegate = self
            
            self.notifyAdLoaded(type: "rewarded")
            
            if self.autoShowRewarded {
                Logger.log("  ► Auto-showing rewarded...")
                self.autoShowRewarded = false
                self.showRewardedAd()
            }
        }
    }
    
    // MARK: - Show Ads
    
    private func showInterstitialAd() {
        Logger.log("→ Attempting to show interstitial...")
        
        guard !isShowingAd else {
            Logger.log("Already showing an ad", level: "W")
            return
        }
        
        guard let vc = viewController else {
            Logger.log("✗ ViewController lost!", level: "E")
            notifyAdFailed(type: "interstitial", error: "ViewController lost")
            return
        }
        
        // Try preloaded ad first
        if let preloaded = AdPreloadManager.shared.getCachedInterstitial() {
            Logger.log("  ► Using PRELOADED interstitial (fast path)")
            isShowingAd = true
            preloaded.fullScreenContentDelegate = self
            preloaded.present(fromRootViewController: vc)
            AdPreloadManager.shared.clearCachedInterstitial()
            return
        }
        
        // Fallback to local ad
        guard isInterstitialReady, let ad = interstitialAd else {
            Logger.log("⏳ No interstitial cached - loading with auto-show")
            isShowingAd = false
            autoShowInterstitial = true
            emitCallback("window.onInterstitialLoading && window.onInterstitialLoading();")
            checkConsentThenLoad { [weak self] in
                self?.loadInterstitialAd()
            }
            return
        }
        
        if !isInterstitialFresh() {
            Logger.log("Ad is stale - reloading with auto-show", level: "W")
            isInterstitialReady = false
            interstitialAd = nil
            autoShowInterstitial = true
            emitCallback("window.onInterstitialLoading && window.onInterstitialLoading();")
            checkConsentThenLoad { [weak self] in
                self?.loadInterstitialAd()
            }
            return
        }
        
        isShowingAd = true
        ad.present(fromRootViewController: vc)
    }
    
    private func showRewardedAd() {
        Logger.log("→ Attempting to show rewarded...")
        
        guard !isShowingAd else {
            Logger.log("Already showing an ad", level: "W")
            return
        }
        
        guard let vc = viewController else {
            Logger.log("✗ ViewController lost!", level: "E")
            notifyAdFailed(type: "rewarded", error: "ViewController lost")
            return
        }
        
        // Try preloaded ad first
        if let preloaded = AdPreloadManager.shared.getCachedRewarded() {
            Logger.log("  ► Using PRELOADED rewarded (fast path)")
            isShowingAd = true
            localRewardEarned = false
            preloaded.fullScreenContentDelegate = self
            preloaded.present(fromRootViewController: vc) { [weak self] in
                guard let self = self else { return }
                let reward = preloaded.adReward
                Logger.log("★★★ USER EARNED REWARD (PRELOADED) ★★★")
                Logger.log("  Type: \(reward.type)")
                Logger.log("  Amount: \(reward.amount)")
                
                self.localRewardEarned = true
                self.localRewardType = reward.type
                self.localRewardAmount = reward.amount.intValue
                
                self.emitRewardCallback(type: reward.type, amount: reward.amount.intValue)
            }
            AdPreloadManager.shared.clearCachedRewarded()
            return
        }
        
        // Fallback to local ad
        guard isRewardedReady, let ad = rewardedAd else {
            Logger.log("⏳ No rewarded cached - loading with auto-show")
            isShowingAd = false
            autoShowRewarded = true
            emitCallback("window.onRewardedLoading && window.onRewardedLoading();")
            checkConsentThenLoad { [weak self] in
                self?.loadRewardedAd()
            }
            return
        }
        
        if !isRewardedFresh() {
            Logger.log("Ad is stale - reloading with auto-show", level: "W")
            isRewardedReady = false
            rewardedAd = nil
            autoShowRewarded = true
            emitCallback("window.onRewardedLoading && window.onRewardedLoading();")
            checkConsentThenLoad { [weak self] in
                self?.loadRewardedAd()
            }
            return
        }
        
        isShowingAd = true
        localRewardEarned = false
        
        ad.present(fromRootViewController: vc) { [weak self] in
            guard let self = self else { return }
            let reward = ad.adReward
            Logger.log("★★★ USER EARNED REWARD ★★★")
            Logger.log("  Type: \(reward.type)")
            Logger.log("  Amount: \(reward.amount)")
            
            self.localRewardEarned = true
            self.localRewardType = reward.type
            self.localRewardAmount = reward.amount.intValue
            
            self.emitRewardCallback(type: reward.type, amount: reward.amount.intValue)
        }
    }
    
    // MARK: - Retry Logic
    
    private func scheduleRetry(isInterstitial: Bool) {
        let delay = isInterstitial ? interstitialRetryDelay : rewardedRetryDelay
        
        Logger.log("Scheduling retry in \(delay)s")
        
        if isInterstitial {
            interstitialRetryTimer?.invalidate()
            interstitialRetryTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                self?.interstitialRetryDelay = min((self?.interstitialRetryDelay ?? 5) * 2, Self.maxRetryDelay)
                self?.loadInterstitialAd()
            }
        } else {
            rewardedRetryTimer?.invalidate()
            rewardedRetryTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                self?.rewardedRetryDelay = min((self?.rewardedRetryDelay ?? 5) * 2, Self.maxRetryDelay)
                self?.loadRewardedAd()
            }
        }
    }
    
    // MARK: - JavaScript Callbacks
    
    private func notifyAdLoaded(type: String) {
        emitCallback("window.on\(type.capitalized)Loaded && window.on\(type.capitalized)Loaded();")
        emitCallback("window.on\(type.capitalized)Ready && window.on\(type.capitalized)Ready();")
        emitCallback("window.onNativeAdReady && window.onNativeAdReady('\(type)');")
    }
    
    private func notifyAdFailed(type: String, error: String) {
        let escapedError = error.replacingOccurrences(of: "'", with: "\\'")
        emitCallback("window.on\(type.capitalized)LoadFailed && window.on\(type.capitalized)LoadFailed('\(escapedError)');")
    }
    
    private func emitRewardCallback(type: String, amount: Int) {
        emitCallback("""
            if (window.onRewardEarned) window.onRewardEarned('\(type)', \(amount));
            if (window.onRewardedComplete) window.onRewardedComplete('\(type)', \(amount));
        """)
    }
    
    private func emitCallback(_ js: String) {
        DispatchQueue.main.async { [weak self] in
            self?.webView?.evaluateJavaScript(js) { _, error in
                if let error = error {
                    Logger.log("JS callback error: \(error.localizedDescription)", level: "E")
                }
            }
        }
    }
}

// MARK: - GADFullScreenContentDelegate

extension AdMobBridge: GADFullScreenContentDelegate {
    
    func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
        totalImpressions += 1
        if ad is GADInterstitialAd {
            interstitialImpressions += 1
            Logger.log("★★★ INTERSTITIAL IMPRESSION #\(interstitialImpressions) ★★★")
        } else if ad is GADRewardedAd {
            rewardedImpressions += 1
            Logger.log("★★★ REWARDED IMPRESSION #\(rewardedImpressions) ★★★")
        }
    }
    
    func adDidRecordClick(_ ad: GADFullScreenPresentingAd) {
        Logger.log("Ad clicked - user may leave app")
    }
    
    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        isShowingAd = false
        Logger.log("✗ Ad failed to show: \(error.localizedDescription)", level: "E")
        
        if ad is GADInterstitialAd {
            isInterstitialReady = false
            interstitialAd = nil
            notifyAdFailed(type: "interstitial", error: error.localizedDescription)
        } else if ad is GADRewardedAd {
            isRewardedReady = false
            rewardedAd = nil
            notifyAdFailed(type: "rewarded", error: error.localizedDescription)
        }
    }
    
    func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        Logger.log("Ad will present full screen content")
    }
    
    func adWillDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        Logger.log("Ad will dismiss")
    }
    
    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        isShowingAd = false
        Logger.log("Ad dismissed")
        
        if ad is GADInterstitialAd {
            isInterstitialReady = false
            interstitialAd = nil
            autoShowInterstitial = false
            emitCallback("if(window.onInterstitialClosed) window.onInterstitialClosed();")
            loadInterstitialAd() // Preload next
        } else if ad is GADRewardedAd {
            isRewardedReady = false
            rewardedAd = nil
            autoShowRewarded = false
            
            // Re-emit reward callback after dismiss for reliable delivery
            if localRewardEarned {
                Logger.log("Re-emitting reward callback after dismiss")
                emitRewardCallback(type: localRewardType, amount: localRewardAmount)
            } else {
                emitCallback("if(window.onRewardedClosed) window.onRewardedClosed();")
            }
            loadRewardedAd() // Preload next
        }
    }
}
