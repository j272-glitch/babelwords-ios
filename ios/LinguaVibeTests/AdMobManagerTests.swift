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
