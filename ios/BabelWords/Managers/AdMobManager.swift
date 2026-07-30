import Foundation
@preconcurrency import GoogleMobileAds
import Network
import UIKit
import AVFoundation

// MARK: - Ad loading

protocol InterstitialAdLoading {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADInterstitialAd?, Error?) -> Void
    )
}

struct GADInterstitialAdLoader: InterstitialAdLoading {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADInterstitialAd?, Error?) -> Void
    ) {
        GADInterstitialAd.load(
            withAdUnitID: adUnitID,
            request: request,
            completionHandler: completionHandler
        )
    }
}

// MARK: - Shared fullscreen state

final class FullscreenAdState: @unchecked Sendable {
    private let lock = NSLock()
    private var showing = false

    var isShowing: Bool {
        lock.lock()
        defer { lock.unlock() }
        return showing
    }

    func setShowing(_ showing: Bool) {
        lock.lock()
        defer { lock.unlock() }
        self.showing = showing
    }
}

/// Unified interstitial ad manager for iOS. Replaces the Android `AdMobManager`.
final class AdMobManager: NSObject, @unchecked Sendable {
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

    /// Cross-manager state so App Open ads cannot collide with interstitials.
    static let fullscreenAdState = FullscreenAdState()

    var eventCallback: ((String, String?) -> Void)?
    private var getConsentManager: () -> ConsentManager?
    private var adLoader: InterstitialAdLoading

    /// Called when the SDK's completion handler arrives on a background thread.
    /// Debug builds assert for early detection; Release builds recover safely.
    var offMainThreadHandler: (String) -> Void = {
        #if DEBUG
        assertionFailure($0)
        #else
        print("[AdMobManager] \($0)")
        #endif
    }

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

    init(
        getConsentManager: @escaping () -> ConsentManager? = { nil },
        adLoader: InterstitialAdLoading = GADInterstitialAdLoader()
    ) {
        self.getConsentManager = getConsentManager
        self.adLoader = adLoader
        super.init()
    }

    // MARK: - Freshness

    private func isFresh() -> Bool {
        guard interstitial != nil, let loadTime = loadTime else { return false }
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
        if isShowingAd || AdMobManager.fullscreenAdState.isShowing {
            print("[\(TAG)] showInterstitial: already showing")
            eventCallback?("interstitialFailed", "already_showing")
            return
        }

        if let lastShow = lastShowTime, Date().timeIntervalSince(lastShow) < 30 {
            print("[\(TAG)] showInterstitial: frequency cap")
            eventCallback?("interstitialFailed", "frequency_capped")
            return
        }

        if interstitial != nil, !isFresh() {
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
        AdMobManager.fullscreenAdState.setShowing(true)
        lastShowTime = Date()
        setAudioModeForAd()

        ad.fullScreenContentDelegate = self
        ad.present(fromRootViewController: viewController)
    }

    func loadInterstitialAndShow(from viewController: UIViewController) {
        if interstitial != nil, isFresh() {
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
                do {
                    try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                } catch {
                    return
                }
                DispatchQueue.main.async { [weak self] in
                    guard let self = self, !self.isLoading else { return }
                    self.load(presentingViewController: presentingViewController, isPreload: isPreload)
                }
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
            do {
                try await Task.sleep(nanoseconds: UInt64(AdMobManager.loadTimeout * 1_000_000_000))
            } catch {
                return
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self, self.isLoading else { return }
                print("[\(self.TAG)] Interstitial load timed out")
                self.isLoading = false
                self.interstitial = nil
                self.scheduleRetry(delay: AdMobManager.retryInitial)
            }
        }

        adLoader.load(
            withAdUnitID: interstitialAdUnitID,
            request: request
        ) { [weak self] ad, error in
            let errorDescription = error?.localizedDescription
            let errorCode = (error as NSError?)?.code
            guard Thread.isMainThread else {
                self?.offMainThreadHandler(
                    "[\(self?.TAG ?? "AdMobManager")] GADInterstitialAd completion called off main thread"
                )
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.handleOffMainInterstitialCallback(
                        errorDescription: errorDescription,
                        errorCode: errorCode
                    )
                }
                return
            }
            self?.handleInterstitialLoad(
                ad: ad,
                errorDescription: errorDescription,
                errorCode: errorCode
            )
        }
    }

    private func handleOffMainInterstitialCallback(errorDescription: String?, errorCode: Int?) {
        print("[\(TAG)] Ignoring off-main interstitial callback\(errorDescription.map { ": \($0)" } ?? "")")
        loadTimeoutTask?.cancel()
        isLoading = false
        interstitial = nil
        scheduleRetry(delay: retryDelay(for: errorCode))
    }

    private func handleInterstitialLoad(
        ad: GADInterstitialAd?,
        errorDescription: String?,
        errorCode: Int?
    ) {
        loadTimeoutTask?.cancel()
        isLoading = false

        if let errorDescription = errorDescription {
            print("[\(TAG)] Interstitial load failed: \(errorDescription)")
            interstitial = nil
            eventCallback?("interstitialFailed", errorDescription)
            scheduleRetry(delay: retryDelay(for: errorCode))
        } else if let ad = ad {
            print("[\(TAG)] Interstitial loaded")
            interstitial = ad
            loadTime = Date()
            retryCount = 0
            ad.fullScreenContentDelegate = self
            eventCallback?("interstitialLoaded", nil)

            if pendingShow {
                pendingShow = false
                if let vc = topViewController() {
                    showInterstitial(from: vc)
                }
            } else {
                maybeAutoShowInterstitial()
            }
        }
    }

    private func retryDelay(for code: Int?) -> TimeInterval {
        switch code {
        case -2 where retryCount == 0:
            return 0
        case -2:
            return min(AdMobManager.retryInitial * pow(2.0, Double(retryCount)), AdMobManager.retryMax)
        case 3:
            return 30
        default:
            return AdMobManager.retryInitial
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
            do {
                try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            } catch {
                return
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self, !self.isLoading else { return }
                self.load(presentingViewController: nil, isPreload: true)
            }
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
                if path.status == .satisfied,
                   self.interstitial == nil || !self.isFresh() {
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
        AdMobManager.fullscreenAdState.setShowing(false)
        isShowingAd = false
    }

    // MARK: - Queries

    func isInterstitialReady() -> Bool { interstitial != nil && isFresh() }
    func isRewardedReady() -> Bool { isInterstitialReady() }
    func isInitialized() -> Bool { true }
}

// MARK: - GADFullScreenContentDelegate

extension AdMobManager: GADFullScreenContentDelegate {
    func adDidRecordImpression(_ ad: GADFullScreenPresentingAd) {
        guardMainThread { [weak self] in
            self?.adDidRecordImpressionOnMain()
        }
    }

    private func adDidRecordImpressionOnMain() {
        AdMobManager.fullscreenAdState.setShowing(true)
    }

    func adDidRecordClick(_ ad: GADFullScreenPresentingAd) {
        let adUnitID = interstitialAdUnitID
        guardMainThread {
            AnalyticsManager.logAdClicked(adUnit: adUnitID, adFormat: "interstitial")
        }
    }

    func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        guardMainThread { [weak self] in
            self?.adWillPresentFullScreenContentOnMain()
        }
    }

    private func adWillPresentFullScreenContentOnMain() {
        print("[\(TAG)] Interstitial shown")
        AdMobManager.fullscreenAdState.setShowing(true)
        hasAutoShownInterstitial = true
        interstitial = nil
        eventCallback?("interstitialShown", nil)
        AnalyticsManager.logAdImpression(adUnit: interstitialAdUnitID, adFormat: "interstitial")
    }

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        guardMainThread { [weak self] in
            self?.adDidDismissFullScreenContentOnMain()
        }
    }

    private func adDidDismissFullScreenContentOnMain() {
        print("[\(TAG)] Interstitial dismissed")
        isShowingAd = false
        AdMobManager.fullscreenAdState.setShowing(false)
        restoreAudioMode()
        eventCallback?("interstitialClosed", nil)
        load(presentingViewController: nil, isPreload: true)
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        let description = error.localizedDescription
        let adUnitID = interstitialAdUnitID
        guardMainThread { [weak self] in
            self?.adDidFailToPresentOnMain(description: description, adUnitID: adUnitID)
        }
    }

    private func adDidFailToPresentOnMain(description: String, adUnitID: String) {
        print("[\(TAG)] Interstitial show failed: \(description)")
        isShowingAd = false
        AdMobManager.fullscreenAdState.setShowing(false)
        restoreAudioMode()
        interstitial = nil
        eventCallback?("interstitialFailed", description)
        AnalyticsManager.logAdFailed(adUnit: adUnitID, error: description)
        load(presentingViewController: nil, isPreload: true)
    }

    private func guardMainThread(_ operation: @escaping @Sendable () -> Void) {
        if Thread.isMainThread {
            operation()
        } else {
            DispatchQueue.main.async(execute: operation)
        }
    }
}
