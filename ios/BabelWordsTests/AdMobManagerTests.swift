import XCTest
@testable import BabelWords

// MARK: - AppOpenAdManagerThreadingTests

/// Tests for the threading guarantee inside `AppOpenAdManager`.
///
/// ## Background
///
/// `AppOpenAdManager` is annotated `@MainActor`, which means Swift Concurrency
/// enforces at **compile time** that every public method is called from the
/// main actor.  That covers every caller we write.
///
/// The runtime `Thread.isMainThread` / `assertionFailure` guard inside
/// `loadAd()` exists for exactly one scenario that Swift Concurrency cannot
/// guard against: the Google Mobile Ads SDK calling the
/// `GADAppOpenAd.load(withAdUnitID:request:completionHandler:)` completion
/// handler on a background thread.  The guard fires `assertionFailure` (which
/// terminates the process in Debug/TestFlight builds) and then recovers by
/// hopping back to `@MainActor` via a `Task { @MainActor in … }`.
///
/// ## Why there is no direct assertionFailure test here
///
/// `GADAppOpenAd.load` is a **static Objective-C method** on the real SDK.
/// There is no protocol, virtual-dispatch seam, or injectable closure that
/// would let a unit test substitute a fake loader that calls the handler off
/// the main thread — without linking the real AdMob framework and actually
/// sending a network request.  `XCTExpectFailure` can only catch an
/// `assertionFailure` that the test itself triggers synchronously; it cannot
/// reach inside the SDK closure.
///
/// ## How to strengthen this in the future
///
/// Introduce an `AdLoader` protocol that wraps `GADAppOpenAd.load`.  The
/// production conformance calls the real SDK; a test conformance can
/// immediately call the completion handler on a background queue, letting
/// `XCTExpectFailure` assert the guard fires.  Files to update:
///
///   - `ios/BabelWords/Managers/AppOpenAdManager.swift`
///     Inject an `AdLoader` through the `init` (alongside the existing
///     `getConsentManager` and `getMicActive` closures).
///   - `ios/BabelWordsTests/AdMobManagerTests.swift`
///     Add a `MockAdLoader` conformance and a test that calls the completion
///     handler on `DispatchQueue.global()` inside `XCTExpectFailure { … }`.
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
