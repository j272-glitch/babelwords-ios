import XCTest
import WebKit
@testable import LinguaVibe

@MainActor
final class WebViewCoordinatorTests: XCTestCase {

    private var coordinator: WebViewCoordinator!

    override func setUp() {
        super.setUp()
        coordinator = WebViewCoordinator()
    }

    override func tearDown() {
        coordinator = nil
        super.tearDown()
    }

    func testWebViewExists() {
        XCTAssertNotNil(coordinator.webView)
    }

    func testMicStateDefaultsToFalse() {
        XCTAssertFalse(coordinator.isMicActive)
    }

    func testSetMicStateUpdatesValue() {
        coordinator.setMicState(true)
        XCTAssertTrue(coordinator.isMicActive)
        coordinator.setMicState(false)
        XCTAssertFalse(coordinator.isMicActive)
    }

    func testLoadingCallbacksFire() {
        let expectation = self.expectation(description: "loading change")
        coordinator.onLoadingChange = { isLoading in
            if isLoading { expectation.fulfill() }
        }
        coordinator.load(url: URL(string: "https://linguagt.com")!)
        wait(for: [expectation], timeout: 1.0)
    }
}
