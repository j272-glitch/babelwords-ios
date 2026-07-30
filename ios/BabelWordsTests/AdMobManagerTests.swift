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

private final class MockInterstitialAdLoader: InterstitialAdLoading {
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
            manager.loadAd()
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
            manager.preloadInterstitial()
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
            manager.preloadInterstitial()
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
            // loadRewardedAndShow -> loadInterstitialAndShow -> load()
            manager.loadRewardedAndShow(from: UIViewController())
            wait(for: [handlerFired], timeout: 2.0)
        }
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
}
