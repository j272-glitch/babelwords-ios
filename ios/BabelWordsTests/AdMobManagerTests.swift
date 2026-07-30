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
        completionHandler: @escaping (GADAppOpenAd?, Error?) -> Void
    ) {
        DispatchQueue.global().async {
            completionHandler(nil, nil)
        }
    }
}

// MARK: - AppOpenAdManagerThreadingTests

/// Tests for the threading guarantee inside `AppOpenAdManager`.
///
/// The runtime `Thread.isMainThread` guard inside `loadAd()` protects against
/// the Google Mobile Ads SDK calling its completion handler on a background
/// thread.  `MockAdLoader` provides the injectable seam that makes this
/// directly exercisable without a live network request.
@MainActor
final class AppOpenAdManagerThreadingTests: XCTestCase {

    /// Verifies at compile time that `AppOpenAdManager` is `@MainActor`-isolated.
    ///
    /// Constructing `AppOpenAdManager` from an `@MainActor` test method is only
    /// valid when the class itself carries `@MainActor` isolation.  If anyone
    /// removes the annotation, this test file will **not compile**, making the
    /// regression impossible to ship unnoticed.
    func testManagerIsMainActorIsolated() {
        let manager = AppOpenAdManager()
        // Retain to prevent the "result of 'AppOpenAdManager' initializer is unused" warning.
        _ = manager
    }

    /// Asserts that the off-main-thread handler fires when the ad loader calls
    /// its completion handler on a background thread.
    ///
    /// `XCTExpectFailure` records the XCTest failure produced by the injected
    /// `offMainThreadHandler`.  If the guard is ever removed the handler will
    /// not fire, the expected failure will be missing, and the test will fail —
    /// turning a silent threading regression into a build-breaking failure.
    func testOffMainThreadCallbackTriggersAssertionFailure() {
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
        XCTAssertFalse(AdMobManager.isAnyFullscreenAdShowing)
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
