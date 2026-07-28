import Foundation
@preconcurrency import GoogleMobileAds
import UIKit
import AVFoundation

/// App Open ad manager for iOS. Replaces the Android `AppOpenAdManager`.
@MainActor
final class AppOpenAdManager: NSObject {
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
    private var loadStartTime: Date?
    private var lastBackgroundTime: Date?
    private var hasEnteredBackground = false

    private var loadTimeoutTask: Task<Void, Never>?
    private var retryTask: Task<Void, Never>?

    private var getConsentManager: () -> ConsentManager?
    private var getMicActive: () -> Bool

    private var isTestLab: Bool {
        ProcessInfo.processInfo.environment["FIREBASE_TEST_LAB"] == "true"
    }

    private var adUnitID: String {
        isTestLab ? "ca-app-pub-3940256099942544/9251695926" : "ca-app-pub-9991891515643313/9157268089"
    }

    init(
        getConsentManager: @escaping () -> ConsentManager? = { nil },
        getMicActive: @escaping () -> Bool = { false }
    ) {
        self.getConsentManager = getConsentManager
        self.getMicActive = getMicActive
        super.init()
    }

    deinit {
        cleanup()
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

    // MARK: - Load

    func loadAd() {
        guard !isLoading, appOpenAd == nil else { return }

        let lastShow = UserDefaults.standard.object(forKey: AppOpenAdManager.prefsLastShow) as? Date ?? .distantPast
        if Date().timeIntervalSince(lastShow) < AppOpenAdManager.frequencyCap {
            print("[\(TAG)] Frequency cap active")
            return
        }

        isLoading = true
        loadStartTime = Date()
        print("[\(TAG)] Loading App Open ad…")

        loadTimeoutTask?.cancel()
        loadTimeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(AppOpenAdManager.loadTimeout * 1_000_000_000))
            guard let self = self, self.isLoading else { return }
            print("[\(self.TAG)] App Open load timed out")
            self.isLoading = false
            self.appOpenAd = nil
            self.scheduleRetry(delay: AppOpenAdManager.retryTimeout)
        }

        let request = getConsentManager()?.buildAdRequest() ?? GADRequest()
        GADAppOpenAd.load(withAdUnitID: adUnitID, request: request) { [weak self] ad, error in
            guard let self = self else { return }
            guard Thread.isMainThread else {
                assertionFailure("GADAppOpenAd.load callback not on main thread")
                Task { @MainActor [weak self] in
                    guard let self = self else { return }
                    self.loadTimeoutTask?.cancel()
                    self.isLoading = false
                    self.appOpenAd = nil
                    self.scheduleRetry(delay: AppOpenAdManager.retryNetwork)
                }
                return
            }

            MainActor.assumeIsolated {
                self.handleAppOpenLoadResult(ad, error)
            }
        }
    }

    private func handleAppOpenLoadResult(_ ad: GADAppOpenAd?, _ error: Error?) {
        loadTimeoutTask?.cancel()
        isLoading = false

        if let error = error {
            print("[\(TAG)] App Open load failed: \(error.localizedDescription)")
            appOpenAd = nil
            let code = (error as NSError).code
            let delay: TimeInterval
            switch code {
            case 3: delay = AppOpenAdManager.retryNoFill
            case -2: delay = AppOpenAdManager.retryTimeout
            default: delay = AppOpenAdManager.retryNetwork
            }
            scheduleRetry(delay: delay)
            return
        }

        guard let ad = ad else { return }
        print("[\(TAG)] App Open ad loaded")
        appOpenAd = ad
        retryCount = 0
        ad.fullScreenContentDelegate = self
    }

    // MARK: - Show

    func showAdIfAvailable(from viewController: UIViewController) {
        guard !isShowingAd, let ad = appOpenAd else { return }
        guard !AdMobManager.isAnyFullscreenAdShowing else {
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
        AdMobManager.isAnyFullscreenAdShowing = true
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
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            self?.loadAd()
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
        AdMobManager.isAnyFullscreenAdShowing = false
    }
}

// MARK: - GADFullScreenContentDelegate

@MainActor
extension AppOpenAdManager: @preconcurrency GADFullScreenContentDelegate {
    func adWillPresentFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        print("[\(TAG)] App Open shown")
        AdMobManager.isAnyFullscreenAdShowing = true
        UserDefaults.standard.set(Date(), forKey: AppOpenAdManager.prefsLastShow)
        appOpenAd = nil
    }

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        print("[\(TAG)] App Open dismissed")
        isShowingAd = false
        AdMobManager.isAnyFullscreenAdShowing = false
        restoreAudioMode()
        loadAd()
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        print("[\(TAG)] App Open show failed: \(error.localizedDescription)")
        isShowingAd = false
        AdMobManager.isAnyFullscreenAdShowing = false
        restoreAudioMode()
        appOpenAd = nil
        loadAd()
    }
}
