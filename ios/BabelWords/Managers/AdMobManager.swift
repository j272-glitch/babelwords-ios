import Foundation
import GoogleMobileAds
import Network
import UIKit
import AVFoundation

/// Unified interstitial ad manager for iOS. Replaces the Android `AdMobManager`.
@MainActor
final class AdMobManager: NSObject {
    private let TAG = "AdMobManager"

    private static let testInterstitialID = "ca-app-pub-3940256099942544/4411468910"
    private static let maxAutoShowAttempts = 3
    private static let foregroundAdCooldown: TimeInterval = 5 * 60
    private static let adExpiry: TimeInterval = 45 * 60
    private static let adRefreshThreshold: TimeInterval = 40 * 60
    private static let loadThrottle: TimeInterval = 30
    private static let loadTimeout: TimeInterval = 15
    private static let retryInitial: TimeInterval = 5
    private static let retryMax: TimeInterval = 60
    private static let retryMaxCount = 10

    /// Cross-manager flag so `AppOpenAdManager` can avoid colliding with a showing interstitial.
    static var isAnyFullscreenAdShowing = false

    var eventCallback: ((String, String?) -> Void)?
    private var getConsentManager: () -> ConsentManager?

    private var interstitial: GADInterstitialAd?
    private var loadTime: Date?
    private var lastLoadTime: Date?
    private var lastShowTime: Date? {
        get { UserDefaults.standard.object(forKey: "interstitial_last_show_time") as? Date }
        set { UserDefaults.standard.set(newValue, forKey: "interstitial_last_show_time") }
    }

    private var isLoading = false
    private var isShowingAd = false
    private var pendingShow = false
    private var isActivityResumed = false

    private var retryCount = 0
    private var retryTask: Task<Void, Never>?
    private var loadTimeoutTask: Task<Void, Never>?

    private var lastForegroundAdTime: Date = .distantPast
    private var wasBackgrounded = false
    private var backgroundTimestamp: Date?

    private var hasAutoShownInterstitial = false
    private var interstitialAutoShowAttempts = 0

    private var networkMonitor: NWPathMonitor?

    private var isTestLab: Bool {
        ProcessInfo.processInfo.environment["FIREBASE_TEST_LAB"] == "true"
    }

    private var interstitialAdUnitID: String {
        isTestLab ? AdMobManager.testInterstitialID : "ca-app-pub-9991891515643313/7320741331"
    }

    init(getConsentManager: @escaping () -> ConsentManager? = { nil }) {
        self.getConsentManager = getConsentManager
        super.init()
    }

    deinit {
        destroy()
    }

    // MARK: - Freshness

    private func isFresh() -> Bool {
        guard let interstitial = interstitial, let loadTime = loadTime else { return false }
        return Date().timeIntervalSince(loadTime) < AdMobManager.adExpiry
    }

    private func shouldRefresh() -> Bool {
        guard let loadTime = loadTime else { return false }
        return Date().timeIntervalSince(loadTime) >= AdMobManager.adRefreshThreshold
    }

    // MARK: - Preload / Show / Load-and-show

    func preloadInterstitial() {
        load(presentingViewController: nil, isPreload: true)
    }

    func showInterstitial(from viewController: UIViewController) {
        if isShowingAd || AdMobManager.isAnyFullscreenAdShowing {
            print("[\(TAG)] showInterstitial: already showing")
            eventCallback?("interstitialFailed", "already_showing")
            return
        }

        if let lastShow = lastShowTime, Date().timeIntervalSince(lastShow) < 30 {
            print("[\(TAG)] showInterstitial: frequency cap")
            eventCallback?("interstitialFailed", "frequency_capped")
            return
        }

        if let ad = interstitial, !isFresh() {
            print("[\(TAG)] Ad stale — reloading with auto-show")
            eventCallback?("interstitialFailed", "ad_stale_reloading")
            pendingShow = true
            load(presentingViewController: viewController)
            return
        }

        guard let ad = interstitial else {
            print("[\(TAG)] No cached ad — loading with auto-show")
            eventCallback?("interstitialFailed", "no_cached_ad")
            pendingShow = true
            load(presentingViewController: viewController)
            return
        }

        isShowingAd = true
        AdMobManager.isAnyFullscreenAdShowing = true
        lastShowTime = Date()
        setAudioModeForAd()

        ad.fullScreenContentDelegate = self
        ad.present(fromRootViewController: viewController)
    }

    func loadInterstitialAndShow(from viewController: UIViewController) {
        if let ad = interstitial, isFresh() {
            showInterstitial(from: viewController)
            load(presentingViewController: nil, isPreload: true)
            return
        }
        pendingShow = true
        load(presentingViewController: viewController)
    }

    func loadRewardedAndShow(from viewController: UIViewController) {
        // Backward-compat alias: rewarded video removed, now uses interstitial.
        loadInterstitialAndShow(from: viewController)
    }

    // MARK: - Core load

    private func load(presentingViewController: UIViewController?, isPreload: Bool = false) {
        guard !isLoading else {
            print("[\(TAG)] Already loading — skipping duplicate request")
            return
        }

        let now = Date()
        if isActivityResumed, let last = lastLoadTime, now.timeIntervalSince(last) < AdMobManager.loadThrottle {
            print("[\(TAG)] Load throttled")
            isLoading = false
            let delay = AdMobManager.loadThrottle - now.timeIntervalSince(last)
            retryTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                guard let self = self, !self.isLoading else { return }
                self.load(presentingViewController: presentingViewController, isPreload: isPreload)
            }
            return
        }

        if isActivityResumed {
            lastLoadTime = now
        }

        if isPreload && isSlowOrMeteredNetwork() {
            print("[\(TAG)] Skipping preload — slow or metered network")
            isLoading = false
            return
        }

        print("[\(TAG)] Loading interstitial…")
        isLoading = true

        let request = getConsentManager()?.buildAdRequest() ?? GADRequest()

        loadTimeoutTask?.cancel()
        loadTimeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(AdMobManager.loadTimeout * 1_000_000_000))
            guard let self = self, self.isLoading else { return }
            print("[\(self.TAG)] Interstitial load timed out")
            self.isLoading = false
            self.interstitial = nil
            self.scheduleRetry(delay: AdMobManager.retryInitial)
        }

        GADInterstitialAd.load(
            withAdUnitID: interstitialAdUnitID,
            request: request
        ) { [weak self] ad, error in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                self.loadTimeoutTask?.cancel()
                self.isLoading = false

                if let error = error {
                    print("[\(self.TAG)] Interstitial load failed: \(error.localizedDescription)")
                    self.interstitial = nil
                    self.eventCallback?("interstitialFailed", error.localizedDescription)
                    let code = (error as NSError).code
                    let delay: TimeInterval
                    switch code {
                    case -2 where self.retryCount == 0:
                        delay = 0
                    case -2:
                        delay = min(AdMobManager.retryInitial * pow(2.0, Double(self.retryCount)), AdMobManager.retryMax)
                    case 3:
                        delay = 30
                    default:
                        delay = AdMobManager.retryInitial
                    }
                    self.scheduleRetry(delay: delay)
                    return
                }

                guard let ad = ad else { return }
                print("[\(self.TAG)] Interstitial loaded")
                self.interstitial = ad
                self.loadTime = Date()
                self.retryCount = 0
                ad.fullScreenContentDelegate = self
                self.eventCallback?("interstitialLoaded", nil)

                if self.pendingShow {
                    self.pendingShow = false
                    if let vc = presentingViewController {
                        self.showInterstitial(from: vc)
                    }
                } else {
                    self.maybeAutoShowInterstitial()
                }
            }
        }
    }

    // MARK: - Retry

    private func scheduleRetry(delay: TimeInterval) {
        retryCount += 1
        if retryCount > AdMobManager.retryMaxCount {
            print("[\(TAG)] Retry limit reached")
            eventCallback?("interstitialFailed", "retry_limit_reached")
            return
        }
        retryTask?.cancel()
        retryTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard let self = self, !self.isLoading else { return }
            self.load(presentingViewController: nil, isPreload: true)
        }
        print("[\(TAG)] Retrying interstitial load in \(delay)s (attempt \(retryCount)/\(AdMobManager.retryMaxCount))")
    }

    private func cancelRetries() {
        retryTask?.cancel()
        retryTask = nil
        loadTimeoutTask?.cancel()
        loadTimeoutTask = nil
    }

    // MARK: - Foreground ad

    func onActivityResumed(_ viewController: UIViewController) {
        isActivityResumed = true

        if pendingShow {
            pendingShow = false
            showInterstitial(from: viewController)
            return
        }

        if wasBackgrounded, let backgroundTimestamp = backgroundTimestamp {
            let inBackground = Date().timeIntervalSince(backgroundTimestamp)
            wasBackgrounded = false
            if inBackground >= 3 && Date().timeIntervalSince(lastForegroundAdTime) >= AdMobManager.foregroundAdCooldown {
                lastForegroundAdTime = Date()
                print("[\(TAG)] Showing foreground interstitial")
                loadInterstitialAndShow(from: viewController)
            }
        }
    }

    func onActivityPaused() {
        isActivityResumed = false
        wasBackgrounded = true
        backgroundTimestamp = Date()
    }

    // MARK: - Network monitoring

    func registerNetworkCallback() {
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if path.status == .satisfied, self.interstitial == nil || !self.isFresh() {
                    print("[\(self.TAG)] Network available — auto-reloading")
                    self.load(presentingViewController: nil, isPreload: true)
                }
            }
        }
        monitor.start(queue: DispatchQueue.global(qos: .utility))
        networkMonitor = monitor
    }

    func unregisterNetworkCallback() {
        networkMonitor?.cancel()
        networkMonitor = nil
    }

    private func isSlowOrMeteredNetwork() -> Bool {
        guard let monitor = networkMonitor else { return false }
        let path = monitor.currentPath
        let isWifi = path.usesInterfaceType(.wifi)
        let isExpensive = path.isExpensive
        return path.status == .satisfied && !isWifi && isExpensive
    }

    // MARK: - Test Lab auto-show

    private func maybeAutoShowInterstitial() {
        guard isTestLab else { return }
        guard AppDelegate.isTestDeviceRegistrationActive else {
            print("[\(TAG)] Test Lab: auto-show suppressed — test-device registration not active")
            return
        }
        guard !hasAutoShownInterstitial else { return }
        guard interstitialAutoShowAttempts < AdMobManager.maxAutoShowAttempts else { return }

        interstitialAutoShowAttempts += 1
        print("[\(TAG)] Test Lab: auto-showing interstitial (attempt \(interstitialAutoShowAttempts))")
        // We need the topmost view controller; use a helper to find it.
        if let vc = topViewController() {
            showInterstitial(from: vc)
        }
    }

    private func topViewController() -> UIViewController? {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = windowScene.windows.first?.rootViewController else { return nil }
        var vc = root
        while let presented = vc.presentedViewController { vc = presented }
        return vc
    }

    // MARK: - Audio

    private func setAudioModeForAd() {
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func restoreAudioMode() {
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker])
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    // MARK: - Destroy

    func destroy() {
        cancelRetries()
        unregisterNetworkCallback()
        interstitial?.fullScreenContentDelegate = nil
        interstitial = nil
        isLoading = false
        pendingShow = false
        AdMobManager.isAnyFullscreenAdShowing = false
        isShowingAd = false
    }

    // MARK: - Queries

    func isInterstitialReady() -> Bool { interstitial != nil && isFresh() }
    func isRewardedReady() -> Bool { isInterstitialReady() }
    func isInitialized() -> Bool { true }
}

// MARK: - GADFullScreenContentDelegate

@preconcurrency
extension AdMobManager: GADFullScreenContentDelegate {
    func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
        AdMobManager.isAnyFullscreenAdShowing = true
    }

    func adDidRecordClick(_ ad: GADFullScreenPresentingAd) {
        AnalyticsManager.logAdClicked(adUnit: interstitialAdUnitID, adFormat: "interstitial")
    }

    func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        print("[\(TAG)] Interstitial shown")
        AdMobManager.isAnyFullscreenAdShowing = true
        hasAutoShownInterstitial = true
        interstitial = nil
        eventCallback?("interstitialShown", nil)
        AnalyticsManager.logAdImpression(adUnit: interstitialAdUnitID, adFormat: "interstitial")
    }

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        print("[\(TAG)] Interstitial dismissed")
        isShowingAd = false
        AdMobManager.isAnyFullscreenAdShowing = false
        restoreAudioMode()
        eventCallback?("interstitialClosed", nil)
        load(presentingViewController: nil, isPreload: true)
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("[\(TAG)] Interstitial show failed: \(error.localizedDescription)")
        isShowingAd = false
        AdMobManager.isAnyFullscreenAdShowing = false
        restoreAudioMode()
        interstitial = nil
        eventCallback?("interstitialFailed", error.localizedDescription)
        AnalyticsManager.logAdFailed(adUnit: interstitialAdUnitID, error: error.localizedDescription)
        load(presentingViewController: nil, isPreload: true)
    }
}
