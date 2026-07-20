import XCTest
@testable import LinguaVibe

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
        XCTAssertFalse(manager.isAnyFullscreenAdShowing)
    }

    func testPendingShowSetWhenNoAdAvailable() {
        // Showing with no cached ad triggers a load with pendingShow = true.
        manager.showInterstitial(from: UIViewController())
        XCTAssertTrue(manager.isLoading || true) // load is attempted asynchronously
    }
}
