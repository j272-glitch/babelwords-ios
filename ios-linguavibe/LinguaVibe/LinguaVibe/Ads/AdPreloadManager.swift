import UIKit
import GoogleMobileAds
import UserMessagingPlatform

class AdPreloadManager: NSObject {
    
    // MARK: - Singleton
    
    static let shared = AdPreloadManager()
    
    // MARK: - Constants
    
    private static let interstitialAdUnitID = "ca-app-pub-9991891515643313/5076005693"
    private static let rewardedAdUnitID = "ca-app-pub-9991891515643313/6313049833"
    
    private static let adExpiryMS: TimeInterval = 45 * 60 // 45 minutes
    private static let refreshBeforeExpiry: TimeInterval = 40 * 60 // 40 minutes
    private static let initialRetryDelay: TimeInterval = 5
    private static let maxRetryDelay: TimeInterval = 60
    
    // MARK: - Properties
    
    private weak var viewController: UIViewController?
    
    private var interstitialAd: GADInterstitialAd?
    private var rewardedAd: GADRewardedAd?
    
    private var interstitialLoadTime: Date?
    private var rewardedLoadTime: Date?
    
    private var isInterstitialLoading = false
    private var isRewardedLoading = false
    private var isSdkInitialized = false
    private var isAppInForeground = true
    
    private var interstitialRetryDelay: TimeInterval = initialRetryDelay
    private var rewardedRetryDelay: TimeInterval = initialRetryDelay
    
    private var interstitialRetryTimer: Timer?
    private var rewardedRetryTimer: Timer?
    private var interstitialRefreshTimer: Timer?
    private var rewardedRefreshTimer: Timer?
    
    // Consent tracking
    private var hasConsent = false
    private var consentChecked = false
    
    // Callbacks
    var onInterstitialReady: (() -> Void)?
    var onRewardedReady: (() -> Void)?
    var onAdDismissed: (() -> Void)?
    var onRewardEarned: ((String, Int) -> Void)?
    
    // MARK: - Initialization
    
    private override init() {
        super.init()
        setupLifecycleObservers()
    }
    
    deinit {
        interstitialRetryTimer?.invalidate()
        rewardedRetryTimer?.invalidate()
        interstitialRefreshTimer?.invalidate()
        rewardedRefreshTimer?.invalidate()
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
        Logger.log("AdPreloadManager: App entered foreground")
        refreshStaleAds(viewController: viewController)
    }
    
    @objc private func appDidEnterBackground() {
        isAppInForeground = false
        Logger.log("AdPreloadManager: App entered background")
    }
    
    // MARK: - Public Interface
    
    func initialize(viewController: UIViewController) {
        self.viewController = viewController
        
        if isSdkInitialized {
            Logger.log("AdPreloadManager: SDK already initialized, checking consent")
            checkConsentAndPreload()
            return
        }
        
        Logger.log("═" + String(repeating: "═", count: 49))
        Logger.log("NATIVE AD PRELOAD MANAGER INITIALIZING")
        Logger.log("═" + String(repeating: "═", count: 49))
        
        isSdkInitialized = true
        checkConsentAndPreload()
    }
    
    func refreshStaleAds(viewController: UIViewController?) {
        self.viewController = viewController
        if !isInterstitialFresh() {
            preloadInterstitial()
        }
        if !isRewardedFresh() {
            preloadRewarded()
        }
    }
    
    func isInterstitialReady() -> Bool {
        return interstitialAd != nil && isInterstitialFresh()
    }
    
    func isRewardedReady() -> Bool {
        return rewardedAd != nil && isRewardedFresh()
    }
    
    func getCachedInterstitial() -> GADInterstitialAd? {
        guard isInterstitialFresh() else { return nil }
        return interstitialAd
    }
    
    func getCachedRewarded() -> GADRewardedAd? {
        guard isRewardedFresh() else { return nil }
        return rewardedAd
    }
    
    func clearCachedInterstitial() {
        interstitialAd = nil
        preloadInterstitial() // Start loading next
    }
    
    func clearCachedRewarded() {
        rewardedAd = nil
        preloadRewarded() // Start loading next
    }
    
    func onConsentObtained(gdprConsent: Bool) {
        Logger.log("═" + String(repeating: "═", count: 49))
        Logger.log("CONSENT OBTAINED: gdprConsent=\(gdprConsent)")
        Logger.log("═" + String(repeating: "═", count: 49))
        
        hasConsent = gdprConsent
        consentChecked = true
        
        if gdprConsent {
            preloadAllAds()
        }
    }
    
    // MARK: - Private Methods
    
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
    
    private func checkConsentAndPreload() {
        let consentInfo = UMPConsentInformation.sharedInstance
        
        switch consentInfo.consentStatus {
        case .obtained:
            Logger.log("✅ Consent already OBTAINED")
            hasConsent = true
            consentChecked = true
            preloadAllAds()
            
        case .notRequired:
            Logger.log("ℹ️ Consent NOT_REQUIRED (non-EEA)")
            hasConsent = true
            consentChecked = true
            preloadAllAds()
            
        case .required:
            Logger.log("📋 Consent REQUIRED - preloading with limited ads")
            preloadAllAds()
            
        case .unknown:
            Logger.log("❓ Consent UNKNOWN - preloading with non-personalized ads IMMEDIATELY")
            preloadAllAds()
            
        @unknown default:
            preloadAllAds()
        }
    }
    
    private func preloadAllAds() {
        Logger.log("Preloading all ads...")
        Logger.logAdEvent("AdPreloadManager: Preloading all ads")
        preloadInterstitial()
        preloadRewarded()
    }
    
    private func preloadInterstitial() {
        guard !isInterstitialFresh() else {
            Logger.log("Fresh interstitial already cached")
            return
        }
        
        // Clear stale ad
        if interstitialAd != nil && !isInterstitialFresh() {
            Logger.log("Clearing stale interstitial")
            interstitialAd = nil
        }
        
        guard !isInterstitialLoading else {
            Logger.log("Interstitial already loading")
            return
        }
        
        isInterstitialLoading = true
        Logger.log("Loading interstitial ad: \(Self.interstitialAdUnitID)")
        Logger.logAdEvent("AdPreloadManager: Loading interstitial")
        
        let request = buildAdRequest()
        
        GADInterstitialAd.load(
            withAdUnitID: Self.interstitialAdUnitID,
            request: request
        ) { [weak self] ad, error in
            guard let self = self else { return }
            
            self.isInterstitialLoading = false
            
            if let error = error {
                Logger.log("✗ Interstitial failed: \(error.localizedDescription)", level: "E")
                Logger.logAdEvent("AdPreloadManager: Interstitial failed - \(error.localizedDescription)")
                self.scheduleRetry(isInterstitial: true)
                return
            }
            
            guard let ad = ad else { return }
            
            Logger.log("✓ Interstitial LOADED and cached")
            Logger.logAdEvent("AdPreloadManager: Interstitial loaded")
            
            self.interstitialRetryTimer?.invalidate()
            self.interstitialAd = ad
            self.interstitialLoadTime = Date()
            self.interstitialRetryDelay = Self.initialRetryDelay
            
            self.scheduleRefreshBeforeExpiry(isInterstitial: true)
            self.onInterstitialReady?()
        }
    }
    
    private func preloadRewarded() {
        guard !isRewardedFresh() else {
            Logger.log("Fresh rewarded already cached")
            return
        }
        
        // Clear stale ad
        if rewardedAd != nil && !isRewardedFresh() {
            Logger.log("Clearing stale rewarded")
            rewardedAd = nil
        }
        
        guard !isRewardedLoading else {
            Logger.log("Rewarded already loading")
            return
        }
        
        isRewardedLoading = true
        Logger.log("Loading rewarded ad: \(Self.rewardedAdUnitID)")
        Logger.logAdEvent("AdPreloadManager: Loading rewarded")
        
        let request = buildAdRequest()
        
        GADRewardedAd.load(
            withAdUnitID: Self.rewardedAdUnitID,
            request: request
        ) { [weak self] ad, error in
            guard let self = self else { return }
            
            self.isRewardedLoading = false
            
            if let error = error {
                Logger.log("✗ Rewarded failed: \(error.localizedDescription)", level: "E")
                Logger.logAdEvent("AdPreloadManager: Rewarded failed - \(error.localizedDescription)")
                self.scheduleRetry(isInterstitial: false)
                return
            }
            
            guard let ad = ad else { return }
            
            Logger.log("✓ Rewarded LOADED and cached")
            Logger.logAdEvent("AdPreloadManager: Rewarded loaded")
            
            self.rewardedRetryTimer?.invalidate()
            self.rewardedAd = ad
            self.rewardedLoadTime = Date()
            self.rewardedRetryDelay = Self.initialRetryDelay
            
            self.scheduleRefreshBeforeExpiry(isInterstitial: false)
            self.onRewardedReady?()
        }
    }
    
    private func buildAdRequest() -> GADRequest {
        let request = GADRequest()
        
        if !hasConsent && consentChecked {
            Logger.log("Building non-personalized ad request")
            let extras = GADExtras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        
        return request
    }
    
    private func scheduleRetry(isInterstitial: Bool) {
        let delay = isInterstitial ? interstitialRetryDelay : rewardedRetryDelay
        
        Logger.log("Scheduling retry in \(delay)s")
        
        if isInterstitial {
            interstitialRetryTimer?.invalidate()
            interstitialRetryTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                self?.interstitialRetryDelay = min((self?.interstitialRetryDelay ?? 5) * 2, Self.maxRetryDelay)
                self?.preloadInterstitial()
            }
        } else {
            rewardedRetryTimer?.invalidate()
            rewardedRetryTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                self?.rewardedRetryDelay = min((self?.rewardedRetryDelay ?? 5) * 2, Self.maxRetryDelay)
                self?.preloadRewarded()
            }
        }
    }
    
    private func scheduleRefreshBeforeExpiry(isInterstitial: Bool) {
        let delay = Self.refreshBeforeExpiry
        Logger.log("Scheduling \(isInterstitial ? "interstitial" : "rewarded") refresh in \(Int(delay / 60)) minutes")
        
        if isInterstitial {
            interstitialRefreshTimer?.invalidate()
            interstitialRefreshTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                if self?.isAppInForeground == true && self?.isInterstitialLoading == false {
                    Logger.log("⏰ Refresh-before-expiry triggered for interstitial")
                    self?.interstitialAd = nil
                    self?.preloadInterstitial()
                }
            }
        } else {
            rewardedRefreshTimer?.invalidate()
            rewardedRefreshTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] _ in
                if self?.isAppInForeground == true && self?.isRewardedLoading == false {
                    Logger.log("⏰ Refresh-before-expiry triggered for rewarded")
                    self?.rewardedAd = nil
                    self?.preloadRewarded()
                }
            }
        }
    }
}
