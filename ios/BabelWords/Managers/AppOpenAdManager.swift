import Foundation
@preconcurrency import GoogleMobileAds
import UIKit
import AVFoundation

// MARK: - AdLoader

/// Seam that wraps `GADAppOpenAd.load` so tests can inject a double.
protocol AdLoader {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADAppOpenAd?, Error?) -> Void
    )
}

/// Production conformance — delegates straight to the real SDK.
struct GADAppOpenAdLoader: AdLoader {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADAppOpenAd?, Error?) -> Void
    ) {
        GADAppOpenAd.load(
            withAdUnitID: adUnitID,
            request: request,
            completionHandler: completionHandler
        )
    }
}

// MARK: - AppOpenAdManager

/// App Open ad manager for iOS. Replaces the Android `AppOpenAdManager`.
final class AppOpenAdManager: NSObject, @unchecked Sendable {
    private let TAG = "AppOpenAdManager"

    private static let loadTimeout: TimeInterval = 15
    private static let frequencyCap: TimeInterval = 4 * 60 * 60
    private static let backgroundThreshold: TimeInterval = 5
    private static let retryNoFill: TimeInterval = 60
    private static let retryTimeout: TimeInterval = 20
    private static let retryNetwork: TimeInterval = 15
    private static let retryMaxCount = 10
    private static let prefsLastShow = "app_open_last_show_ms"

    private var appOpenAd: GADAppOpenAd?
    private var isLoading = false
    private var isShowingAd = false
    private var retryCount = 0
    private var canRequestAds = false
    private var loadStartTime: Date?
    private var lastBackgroundTime: Date?
    private var hasEnteredBackground = false

    private var loadTimeoutTask: Task<Void, Never>?
    private var retryTask: Task<Void, Never>?

    private var getConsentManager: () -> ConsentManager?
    private var getMicActive: () -> Bool
    private var adLoader: AdLoader

    /// Incremented each time consent changes so that any in-flight
    /// `GADAppOpenAd.load` callback dispatched before the change is dropped.
    private var consentEpoch: Int = 0

    /// Called when the SDK's completion handler arrives on a background thread.
    /// Debug builds assert for early detection; Release builds recover safely.
    var offMainThreadHandler: (String) -> Void = {
        #if DEBUG
        assertionFailure($0)
        #else
        print("[AppOpenAdManager] \($0)")
        #endif
    }

    private var isTestLab: Bool {
        ProcessInfo.processInfo.environment["FIREBASE_TEST_LAB"] == "true"
    }

    private var adUnitID: String {
        isTestLab ? "ca-app-pub-3940256099942544/9251695926" : "ca-app-pub-9991891515643313/9157268089"
    }

    init(
        getConsentManager: @escaping () -> ConsentManager? = { nil },
        getMicActive: @escaping () -> Bool = { false },
        adLoader: AdLoader = GADAppOpenAdLoader()
    ) {
        self.getConsentManager = getConsentManager
        self.getMicActive = getMicActive
        self.adLoader = adLoader
        super.init()
    }

    // MARK: - Lifecycle

    func onDidEnterBackground() {
        lastBackgroundTime = Date()
        hasEnteredBackground = true
    }

    func onWillEnterForeground(from viewController: UIViewController) {
        guard hasEnteredBackground else {
            print("[\(TAG)] Cold start — skipping App Open ad")
            return
        }
        if let lastBackground = lastBackgroundTime, Date().timeIntervalSince(lastBackground) >= AppOpenAdManager.backgroundThreshold {
            print("[\(TAG)] Warm-resume detected")
            showAdIfAvailable(from: viewController)
        }
    }

    // MARK: - Queries

    /// `true` when a loaded ad is cached and ready to present.
    var isAdAvailable: Bool { appOpenAd != nil }

    // MARK: - Load

    func loadAd() {
        guard canRequestAds else {
            print("[\(TAG)] Load skipped — ad consent unavailable")
            return
        }
        guard !isLoading, appOpenAd == nil else { return }

        let lastShow = UserDefaults.standard.object(forKey: AppOpenAdManager.prefsLastShow) as? Date ?? .distantPast
        if Date().timeIntervalSince(lastShow) < AppOpenAdManager.frequencyCap {
            print("[\(TAG)] Frequency cap active")
            return
        }

        isLoading = true
        loadStartTime = Date()
        print("[\(TAG)] Loading App Open ad…")

        // Capture the epoch at load-time so any completion callback that arrives
        // after a consent change (which increments consentEpoch) is dropped.
        let capturedEpoch = consentEpoch

        loadTimeoutTask?.cancel()
        loadTimeoutTask = Task { [weak self] in
            do {
                try await Task.sleep(nanoseconds: UInt64(AppOpenAdManager.loadTimeout * 1_000_000_000))
            } catch {
                return
            }
            DispatchQueue.main.async { [weak self] in
                guard let self = self,
                      self.isLoading,
                      self.consentEpoch == capturedEpoch else { return }
                print("[\(self.TAG)] App Open load timed out")
                self.isLoading = false
                self.appOpenAd = nil
                self.scheduleRetry(delay: AppOpenAdManager.retryTimeout)
            }
        }

        let request = getConsentManager()?.buildAdRequest() ?? GADRequest()
        adLoader.load(withAdUnitID: adUnitID, request: request) { [weak self] ad, error in
            let errorDescription = error?.localizedDescription
            let errorCode = (error as NSError?)?.code
            guard Thread.isMainThread else {
                self?.offMainThreadHandler(
                    "[\(self?.TAG ?? "AppOpenAdManager")] GADAppOpenAd completion called off main thread"
                )
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.handleOffMainLoadCallback(
                        epoch: capturedEpoch,
                        errorDescription: errorDescription,
                        errorCode: errorCode
                    )
                }
                return
            }
            self?.handleLoad(
                epoch: capturedEpoch,
                ad: ad,
                errorDescription: errorDescription,
                errorCode: errorCode
            )
        }
    }

    private func handleOffMainLoadCallback(epoch: Int, errorDescription: String?, errorCode: Int?) {
        guard epoch == consentEpoch else {
            print("[\(TAG)] Dropping stale off-main App Open callback (epoch \(epoch), current \(consentEpoch))")
            return
        }
        print("[\(TAG)] Ignoring off-main App Open callback\(errorDescription.map { ": \($0)" } ?? "")")
        loadTimeoutTask?.cancel()
        isLoading = false
        appOpenAd = nil
        scheduleRetry(delay: retryDelay(for: errorCode))
    }

    private func handleLoad(
        epoch: Int,
        ad: GADAppOpenAd?,
        errorDescription: String?,
        errorCode: Int?
    ) {
        guard epoch == consentEpoch else {
            print("[\(TAG)] Dropping stale App Open load callback (epoch \(epoch), current \(consentEpoch))")
            return
        }
        loadTimeoutTask?.cancel()
        isLoading = false

        if let errorDescription = errorDescription {
            print("[\(TAG)] App Open load failed: \(errorDescription)")
            appOpenAd = nil
            scheduleRetry(delay: retryDelay(for: errorCode))
        } else if let ad = ad {
            print("[\(TAG)] App Open ad loaded")
            appOpenAd = ad
            retryCount = 0
            ad.fullScreenContentDelegate = self
        }
    }

    // MARK: - Consent

    /// Called by `ConsentManager` whenever the user's consent state changes.
    /// Drops the cached app-open ad (which was built with the old consent signal)
    /// and immediately kicks off a fresh preload so the next show uses the
    /// updated UMP consent string.
    func onConsentChanged(canRequestAds: Bool) {
        print("[\(TAG)] Consent changed — invalidating cached App Open ad")
        self.canRequestAds = canRequestAds
        // Increment the epoch FIRST so any in-flight GADAppOpenAd.load callback
        // dispatched before the consent change is dropped by the epoch guard.
        consentEpoch += 1
        loadTimeoutTask?.cancel()
        retryTask?.cancel()
        appOpenAd?.fullScreenContentDelegate = nil
        appOpenAd = nil
        isLoading = false
        retryCount = 0
        guard canRequestAds else { return }
        loadAd()
    }

    private func retryDelay(for code: Int?) -> TimeInterval {
        switch code {
        case 3: return AppOpenAdManager.retryNoFill
        case -2: return AppOpenAdManager.retryTimeout
        default: return AppOpenAdManager.retryNetwork
        }
    }

    // MARK: - Show

    func showAdIfAvailable(from viewController: UIViewController) {
        guard canRequestAds else {
            print("[\(TAG)] Show skipped — ad consent unavailable")
            return
        }
        guard !isShowingAd, let ad = appOpenAd else { return }
        guard !AdMobManager.fullscreenAdState.isShowing else {
            print("[\(TAG)] Blocked — interstitial is showing")
            return
        }
        guard !getMicActive() else {
            print("[\(TAG)] Blocked — mic active")
            return
        }
        let lastShow = UserDefaults.standard.object(forKey: AppOpenAdManager.prefsLastShow) as? Date ?? .distantPast
        guard Date().timeIntervalSince(lastShow) >= AppOpenAdManager.frequencyCap else {
            print("[\(TAG)] Blocked by frequency cap")
            return
        }

        isShowingAd = true
        AdMobManager.fullscreenAdState.setShowing(true)
        setAudioModeForAd()
        ad.present(fromRootViewController: viewController)
    }

    // MARK: - Retry

    private func scheduleRetry(delay: TimeInterval) {
        retryCount += 1
        if retryCount > AppOpenAdManager.retryMaxCount {
            print("[\(TAG)] Retry limit reached")
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
                self?.loadAd()
            }
        }
        print("[\(TAG)] Retrying App Open load in \(delay)s (attempt \(retryCount)/\(AppOpenAdManager.retryMaxCount))")
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

    // MARK: - Cleanup

    func cleanup() {
        loadTimeoutTask?.cancel()
        retryTask?.cancel()
        appOpenAd?.fullScreenContentDelegate = nil
        appOpenAd = nil
        AdMobManager.fullscreenAdState.setShowing(false)
    }
}

// MARK: - GADFullScreenContentDelegate

extension AppOpenAdManager: GADFullScreenContentDelegate {
    func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        guardMainThread { [weak self] in
            self?.adWillPresentOnMain()
        }
    }

    private func adWillPresentOnMain() {
        print("[\(TAG)] App Open shown")
        AdMobManager.fullscreenAdState.setShowing(true)
        UserDefaults.standard.set(Date(), forKey: AppOpenAdManager.prefsLastShow)
        appOpenAd = nil
    }

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        guardMainThread { [weak self] in
            self?.adDidDismissOnMain()
        }
    }

    private func adDidDismissOnMain() {
        print("[\(TAG)] App Open dismissed")
        isShowingAd = false
        AdMobManager.fullscreenAdState.setShowing(false)
        restoreAudioMode()
        loadAd()
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        let description = error.localizedDescription
        guardMainThread { [weak self] in
            self?.adDidFailToPresentOnMain(description: description)
        }
    }

    private func adDidFailToPresentOnMain(description: String) {
        print("[\(TAG)] App Open show failed: \(description)")
        isShowingAd = false
        AdMobManager.fullscreenAdState.setShowing(false)
        restoreAudioMode()
        appOpenAd = nil
        loadAd()
    }

    private func guardMainThread(_ operation: @escaping @Sendable () -> Void) {
        if Thread.isMainThread {
            operation()
        } else {
            offMainThreadHandler("[\(TAG)] GADFullScreenContentDelegate callback called off main thread")
            DispatchQueue.main.async(execute: operation)
        }
    }
}
