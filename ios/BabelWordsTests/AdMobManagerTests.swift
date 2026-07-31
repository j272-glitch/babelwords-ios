import XCTest
@testable import BabelWords
@preconcurrency import GoogleMobileAds

// MARK: - MockAdLoader

/// Test double that calls the completion handler synchronously on a global
/// (background) queue, simulating the threading bug the guard is designed to
/// catch.
private final class MockAdLoader: AdLoader {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADAppOpenAd?, Error?) -> Void
    ) {
        DispatchQueue.global().async {
            completionHandler(nil, nil)
        }
    }
}

// MARK: - AppOpenAdManagerThreadingTests

/// Tests the runtime thread-confinement recovery inside `AppOpenAdManager`.
///
/// `AppOpenAdManager` is intentionally main-thread confined at runtime rather
/// than globally `@MainActor`-isolated because the Google Mobile Ads callback
/// API is nonisolated and returns non-Sendable ad objects. `MockAdLoader`
/// makes the background-callback path directly exercisable without a network
/// request.
@MainActor
final class AppOpenAdManagerThreadingTests: XCTestCase {

    func testManagerCanBeConstructedWithInjectedLoader() {
        let manager = AppOpenAdManager()
        _ = manager
    }

    /// Asserts that the off-main-thread handler fires when the ad loader calls
    /// its completion handler on a background thread.
    ///
    /// `XCTExpectFailure` records the XCTest failure produced by the injected
    /// `offMainThreadHandler`.  If the guard is ever removed the handler will
    /// not fire, the expected failure will be missing, and the test will fail —
    /// turning a silent threading regression into a build-breaking failure.
    func testOffMainThreadCallbackTriggersThreadingGuard() {
        let manager = AppOpenAdManager(adLoader: MockAdLoader())
        let handlerFired = expectation(description: "offMainThreadHandler called")

        // Replace the default assertionFailure with an XCTFail so the test
        // process is not terminated and XCTExpectFailure can intercept it.
        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        XCTExpectFailure("off-main-thread GAD callback must trigger the threading guard") {
            manager.onConsentChanged(canRequestAds: true)
            wait(for: [handlerFired], timeout: 2.0)
        }
    }

    func testInterstitialOffMainThreadCallbackTriggersThreadingGuard() {
        let manager = AdMobManager(adLoader: MockInterstitialAdLoader())
        let handlerFired = expectation(description: "interstitial off-main handler called")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        XCTExpectFailure("off-main-thread interstitial callback must trigger the threading guard") {
            manager.onConsentChanged(canRequestAds: true)
            wait(for: [handlerFired], timeout: 2.0)
        }
    }
}

// MARK: - MockInterstitialAdLoader

/// Test double that calls the completion handler synchronously on a global
/// (background) queue, simulating the threading bug the guard is designed to
/// catch.
private final class MockInterstitialAdLoader: InterstitialAdLoader {
    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADInterstitialAd?, Error?) -> Void
    ) {
        DispatchQueue.global().async {
            completionHandler(nil, nil)
        }
    }
}

// MARK: - CountingInterstitialAdLoader

/// Test double that records every `load` call but never invokes the completion
/// handler, keeping the manager in the `isLoading` state so tests can inspect
/// the side-effects of cache-invalidation without needing a real `GADInterstitialAd`.
private final class CountingInterstitialAdLoader: InterstitialAdLoader {
    var loadCallCount = 0

    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADInterstitialAd?, Error?) -> Void
    ) {
        loadCallCount += 1
        // intentionally never calls completionHandler
    }
}

// MARK: - DelayedInterstitialAdLoader

/// Test double that captures the most-recent completion handler without calling
/// it, allowing tests to fire it at a chosen point in time (e.g. after
/// `onConsentChanged()`) to exercise the consent-epoch race-condition guard.
private final class DelayedInterstitialAdLoader: InterstitialAdLoader {
    /// Holds the completion block from the most-recent `load` call.
    var capturedCompletion: (@Sendable (GADInterstitialAd?, Error?) -> Void)?

    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADInterstitialAd?, Error?) -> Void
    ) {
        capturedCompletion = completionHandler
    }
}

// MARK: - InterstitialAdManagerThreadingTests

/// Tests for the threading guarantee inside `AdMobManager` (interstitial path).
///
/// `MockInterstitialAdLoader` provides the injectable seam that exercises the
/// `Thread.isMainThread` guard in `AdMobManager.load()` without a live network
/// request.
@MainActor
final class InterstitialAdManagerThreadingTests: XCTestCase {

    /// Asserts that the off-main-thread handler fires when the interstitial ad
    /// loader calls its completion handler on a background thread.
    ///
    /// `XCTExpectFailure` records the XCTest failure produced by the injected
    /// `offMainThreadHandler`.  If the guard is ever removed the handler will
    /// not fire, the expected failure will be missing, and the test will fail —
    /// turning a silent threading regression into a build-breaking failure.
    func testInterstitialOffMainThreadCallbackTriggersHandler() {
        let manager = AdMobManager(adLoader: MockInterstitialAdLoader())
        let handlerFired = expectation(description: "offMainThreadHandler called for interstitial")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        XCTExpectFailure("off-main-thread GAD interstitial callback must trigger the threading guard") {
            manager.onConsentChanged(canRequestAds: true)
            wait(for: [handlerFired], timeout: 2.0)
        }
    }
}

// MARK: - RewardedAdManagerThreadingTests

/// Tests for the threading guarantee inside `AdMobManager` (rewarded path).
///
/// Rewarded is routed through the same `load()` internals as interstitial, so
/// the same `Thread.isMainThread` guard applies.
@MainActor
final class RewardedAdManagerThreadingTests: XCTestCase {

    /// Asserts that the off-main-thread handler fires on the rewarded code path
    /// (which delegates to the interstitial loader).
    func testRewardedOffMainThreadCallbackTriggersHandler() {
        let manager = AdMobManager(adLoader: MockInterstitialAdLoader())
        let handlerFired = expectation(description: "offMainThreadHandler called for rewarded")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        XCTExpectFailure("off-main-thread GAD rewarded callback must trigger the threading guard") {
            // Granting consent starts the preload through the same load()
            // internals used by loadRewardedAndShow.
            manager.onConsentChanged(canRequestAds: true)
            wait(for: [handlerFired], timeout: 2.0)
        }
    }
}

// MARK: - MockFullScreenPresentingAd

/// Minimal conformer so tests can call delegate methods without a real ad object.
private final class MockFullScreenPresentingAd: NSObject, GADFullScreenPresentingAd {}

// MARK: - AdMobManagerDelegateThreadingTests

/// Verifies that `guardMainThread` in `AdMobManager`'s `GADFullScreenContentDelegate`
/// extension fires `offMainThreadHandler` when a delegate callback arrives off the
/// main thread.
///
/// Each test dispatches the relevant delegate method onto `DispatchQueue.global()`
/// and wraps the wait inside `XCTExpectFailure` so that the injected `XCTFail`
/// is recorded rather than failing the suite — turning a future threading
/// regression into a build-breaking failure.
@MainActor
final class AdMobManagerDelegateThreadingTests: XCTestCase {

    private func makeManager() -> AdMobManager {
        AdMobManager(adLoader: MockInterstitialAdLoader())
    }

    func testAdWillPresentDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adWillPresentFullScreenContent")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adWillPresentFullScreenContent must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adWillPresentFullScreenContent(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
    }

    func testAdDidDismissDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adDidDismissFullScreenContent")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adDidDismissFullScreenContent must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adDidDismissFullScreenContent(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
    }

    func testAdDidFailToPresentDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for didFailToPresentFullScreenContentWithError")
        let stateReset = expectation(description: "fullscreen state reset after failed app-open presentation")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        AdMobManager.fullscreenAdState.setShowing(true)
        let mockAd = MockFullScreenPresentingAd()
        let error = NSError(domain: "com.test", code: -1, userInfo: nil)
        XCTExpectFailure("off-main delegate callback didFailToPresentFullScreenContentWithError must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.ad(mockAd, didFailToPresentFullScreenContentWithError: error)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
        DispatchQueue.main.async {
            XCTAssertFalse(AdMobManager.fullscreenAdState.isShowing)
            stateReset.fulfill()
        }
        wait(for: [stateReset], timeout: 2.0)
    }

    func testAdDidRecordImpressionDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adDidRecordImpression")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adDidRecordImpression must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adDidRecordImpression(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
    }

    func testAdDidRecordClickDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adDidRecordClick")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adDidRecordClick must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adDidRecordClick(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
    }
}

// MARK: - CountingAdLoader

/// Test double that records every `load` call but never invokes the completion
/// handler, keeping the manager in the `isLoading` state so tests can inspect
/// the side-effects of cache-invalidation without needing a real `GADAppOpenAd`.
private final class CountingAdLoader: AdLoader {
    var loadCallCount = 0

    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADAppOpenAd?, Error?) -> Void
    ) {
        loadCallCount += 1
        // intentionally never calls completionHandler
    }
}

// MARK: - DelayedAppOpenAdLoader

/// Retains each completion so a pre-consent callback can be delivered after
/// the manager has started its replacement load.
private final class DelayedAppOpenAdLoader: AdLoader {
    var completions: [@Sendable (GADAppOpenAd?, Error?) -> Void] = []

    func load(
        withAdUnitID adUnitID: String,
        request: GADRequest,
        completionHandler: @escaping @Sendable (GADAppOpenAd?, Error?) -> Void
    ) {
        completions.append(completionHandler)
    }
}

// MARK: - AppOpenAdManagerConsentTests

/// Verifies that `AppOpenAdManager.onConsentChanged()` immediately invalidates
/// any cached app-open ad and kicks off a fresh preload.
@MainActor
final class AppOpenAdManagerConsentTests: XCTestCase {

    /// Verifies that `onConsentChanged()` immediately makes `isAdAvailable`
    /// return `false` and triggers a fresh load with the updated consent signal.
    func testOnConsentChangedInvalidatesCacheAndTriggersReload() {
        let countingLoader = CountingAdLoader()
        let manager = AppOpenAdManager(adLoader: countingLoader)

        // Baseline — no cached ad yet.
        XCTAssertFalse(manager.isAdAvailable, "should start with no available app-open ad")

        // Trigger a preload so the manager enters the loading state (loadCallCount == 1).
        manager.onConsentChanged(canRequestAds: true)
        manager.loadAd()
        let loadsBefore = countingLoader.loadCallCount

        // Simulate consent changing mid-session.
        manager.onConsentChanged(canRequestAds: true)

        // The cache must be empty immediately.
        XCTAssertFalse(manager.isAdAvailable,
                       "isAdAvailable must be false immediately after onConsentChanged()")

        // A fresh load must have been kicked off (loadCallCount incremented).
        XCTAssertGreaterThan(countingLoader.loadCallCount, loadsBefore,
                             "onConsentChanged() must trigger a fresh load")
    }

    func testRevokedConsentInvalidatesCacheWithoutReloading() {
        let countingLoader = CountingAdLoader()
        let manager = AppOpenAdManager(adLoader: countingLoader)

        manager.onConsentChanged(canRequestAds: true)
        let loadsBeforeRevoke = countingLoader.loadCallCount
        manager.onConsentChanged(canRequestAds: false)

        XCTAssertFalse(manager.isAdAvailable)
        XCTAssertEqual(
            countingLoader.loadCallCount,
            loadsBeforeRevoke,
            "revoked consent must not start an App Open ad request"
        )
    }

    func testPreConsentInFlightCallbackCannotInterruptReplacementLoad() {
        let delayedLoader = DelayedAppOpenAdLoader()
        let manager = AppOpenAdManager(adLoader: delayedLoader)

        manager.onConsentChanged(canRequestAds: true)
        manager.loadAd()
        XCTAssertEqual(delayedLoader.completions.count, 1)

        manager.onConsentChanged(canRequestAds: true)
        XCTAssertEqual(delayedLoader.completions.count, 2)

        // The old request completes after the replacement request is active.
        // It must not cancel the replacement load or change its state.
        delayedLoader.completions[0](
            nil,
            NSError(domain: "com.test.stale-app-open", code: -1, userInfo: nil)
        )

        manager.loadAd()
        XCTAssertEqual(
            delayedLoader.completions.count,
            2,
            "A stale app-open callback must not clear the replacement load state"
        )
    }
}

// MARK: - AppOpenAdManagerDelegateThreadingTests

/// Verifies that `guardMainThread` in `AppOpenAdManager`'s `GADFullScreenContentDelegate`
/// extension fires `offMainThreadHandler` when a delegate callback arrives off the
/// main thread.
@MainActor
final class AppOpenAdManagerDelegateThreadingTests: XCTestCase {

    private func makeManager() -> AppOpenAdManager {
        AppOpenAdManager(adLoader: MockAdLoader())
    }

    func testAdWillPresentDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adWillPresentFullScreenContent")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adWillPresentFullScreenContent must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adWillPresentFullScreenContent(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
    }

    func testAdDidDismissDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for adDidDismissFullScreenContent")
        let stateReset = expectation(description: "fullscreen state reset after dismiss")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        AdMobManager.fullscreenAdState.setShowing(true)
        let mockAd = MockFullScreenPresentingAd()
        XCTExpectFailure("off-main delegate callback adDidDismissFullScreenContent must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.adDidDismissFullScreenContent(mockAd)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
        DispatchQueue.main.async {
            XCTAssertFalse(AdMobManager.fullscreenAdState.isShowing)
            stateReset.fulfill()
        }
        wait(for: [stateReset], timeout: 2.0)
    }

    func testAdDidFailToPresentDelegateOffMainFiresHandler() {
        let manager = makeManager()
        let handlerFired = expectation(description: "offMainThreadHandler fired for didFailToPresentFullScreenContentWithError")
        let stateReset = expectation(description: "fullscreen state reset after failed app-open presentation")

        manager.offMainThreadHandler = { message in
            XCTFail(message)
            handlerFired.fulfill()
        }

        AdMobManager.fullscreenAdState.setShowing(true)
        let mockAd = MockFullScreenPresentingAd()
        let error = NSError(domain: "com.test", code: -1, userInfo: nil)
        XCTExpectFailure("off-main delegate callback didFailToPresentFullScreenContentWithError must trigger the threading guard") {
            DispatchQueue.global().async {
                manager.ad(mockAd, didFailToPresentFullScreenContentWithError: error)
            }
            wait(for: [handlerFired], timeout: 2.0)
        }
        DispatchQueue.main.async {
            XCTAssertFalse(AdMobManager.fullscreenAdState.isShowing)
            stateReset.fulfill()
        }
        wait(for: [stateReset], timeout: 2.0)
    }
}

// MARK: - AdMobManagerTests

@MainActor
final class AdMobManagerTests: XCTestCase {

    private var manager: AdMobManager!

    override func setUp() {
        super.setUp()
        manager = AdMobManager()
    }

    override func tearDown() {
        manager.destroy()
        manager = nil
        super.tearDown()
    }

    func testInitialState() {
        XCTAssertFalse(manager.isInterstitialReady())
        XCTAssertFalse(manager.isRewardedReady())
        XCTAssertTrue(manager.isInitialized())
    }

    func testDestroyResetsState() {
        manager.destroy()
        XCTAssertFalse(AdMobManager.fullscreenAdState.isShowing)
    }

    func testPendingShowFiresNoCachedAdEvent() {
        let expectation = self.expectation(description: "no cached ad event")
        manager.eventCallback = { event, _ in
            if event == "interstitialFailed" {
                expectation.fulfill()
            }
        }
        manager.showInterstitial(from: UIViewController())
        wait(for: [expectation], timeout: 1.0)
    }

    // MARK: - Consent-change cache invalidation

    /// Verifies that `onConsentChanged()` immediately invalidates any cached
    /// interstitial so that `isInterstitialReady()` returns `false` and a fresh
    /// load is triggered with the updated consent signal.
    func testOnConsentChangedInvalidatesCacheAndTriggersReload() {
        let countingLoader = CountingInterstitialAdLoader()
        let localManager = AdMobManager(adLoader: countingLoader)
        defer { localManager.destroy() }

        // Baseline — no cached ad yet.
        XCTAssertFalse(localManager.isInterstitialReady(), "should start with no ready interstitial")

        // Trigger a preload so the manager enters the loading state (loadCallCount == 1).
        localManager.onConsentChanged(canRequestAds: true)
        localManager.preloadInterstitial()
        let loadsBefore = countingLoader.loadCallCount

        // Simulate consent changing mid-session.
        localManager.onConsentChanged(canRequestAds: true)

        // The cache must be empty immediately.
        XCTAssertFalse(localManager.isInterstitialReady(),
                       "isInterstitialReady() must be false immediately after onConsentChanged()")

        // A fresh load must have been kicked off (loadCallCount incremented).
        XCTAssertGreaterThan(countingLoader.loadCallCount, loadsBefore,
                             "onConsentChanged() must trigger a fresh load")
    }

    /// Verifies the consent-epoch race-condition guard: a `GADInterstitialAd.load`
    /// callback that was dispatched *before* `onConsentChanged()` but arrives
    /// *after* it must be silently dropped so the stale ad never enters the cache.
    ///
    /// `DelayedInterstitialAdLoader` captures the completion handler without
    /// calling it, letting the test fire it at a controlled point in time.
    func testPreConsentInFlightCallbackDroppedAfterConsentChanged() {
        let delayedLoader = DelayedInterstitialAdLoader()
        let localManager = AdMobManager(adLoader: delayedLoader)
        defer { localManager.destroy() }

        // 1. Start a load under the *original* consent context.
        localManager.onConsentChanged(canRequestAds: true)
        localManager.preloadInterstitial()
        guard let staleCompletion = delayedLoader.capturedCompletion else {
            XCTFail("AdMobManager must have called adLoader.load() during preloadInterstitial()")
            return
        }

        // 2. Consent changes mid-session — epoch increments, cache cleared, new load starts.
        localManager.onConsentChanged(canRequestAds: true)

        // 3. Arm the event callback *after* the consent change so any spurious event
        //    that leaks from the stale callback is detectable.
        var staleEventFired = false
        localManager.eventCallback = { event, _ in
            // Both "interstitialFailed" and "interstitialLoaded" would indicate the
            // stale callback was not dropped.
            staleEventFired = true
        }

        // 4. Fire the old (pre-consent-change) completion handler on the main thread,
        //    simulating the real SDK delivering its response late.  Passing a non-nil
        //    error exercises the error branch; with the epoch guard the handler must
        //    return before reaching any eventCallback or state-mutation code.
        staleCompletion(nil, NSError(domain: "com.test.stale", code: -1, userInfo: nil))

        // 5. Assert the stale callback was silently dropped.
        XCTAssertFalse(staleEventFired,
                       "A pre-consent-change load callback must be silently dropped after onConsentChanged()")
        XCTAssertFalse(localManager.isInterstitialReady(),
                       "isInterstitialReady() must remain false after a dropped stale callback")
    }

    func testRevokedConsentInvalidatesCacheWithoutReloading() {
        let countingLoader = CountingInterstitialAdLoader()
        let localManager = AdMobManager(adLoader: countingLoader)
        defer { localManager.destroy() }

        localManager.onConsentChanged(canRequestAds: true)
        let loadsBeforeRevoke = countingLoader.loadCallCount
        localManager.onConsentChanged(canRequestAds: false)

        XCTAssertFalse(localManager.isInterstitialReady())
        XCTAssertEqual(
            countingLoader.loadCallCount,
            loadsBeforeRevoke,
            "revoked consent must not start an interstitial request"
        )
    }
}
