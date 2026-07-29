import XCTest
import WebKit
@testable import BabelWords

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
        coordinator.loadHTMLString("<html><body>fixture</body></html>")
        wait(for: [expectation], timeout: 1.0)
    }

    /// Resetting the watchdog while the mic is still active must NOT deactivate it:
    /// the cancelled prior timer should never set isMicActive to false.
    func testWatchdogResetDoesNotDeactivateActiveMic() async throws {
        coordinator.setMicState(true)
        XCTAssertTrue(coordinator.isMicActive, "Mic should be active after setMicState(true)")

        // Immediately reset again (cancels first watchdog, schedules a new one).
        coordinator.setMicState(true)
        XCTAssertTrue(coordinator.isMicActive, "Mic should remain active after rapid re-set")

        // Yield briefly — a cancelled task that falls through would have mutated state by now.
        try await Task.sleep(nanoseconds: 50_000_000)  // 50 ms
        XCTAssertTrue(coordinator.isMicActive, "Cancelled watchdog must not have deactivated mic")
    }

    /// Turning the mic off cancels the watchdog and must leave isMicActive false.
    func testWatchdogCancelledWhenMicDeactivated() async throws {
        coordinator.setMicState(true)
        coordinator.setMicState(false)
        XCTAssertFalse(coordinator.isMicActive)

        try await Task.sleep(nanoseconds: 50_000_000)  // 50 ms
        XCTAssertFalse(coordinator.isMicActive, "Mic should stay false after explicit deactivation")
    }
}
