import XCTest
import WebKit
@testable import BabelWords

@MainActor
final class AdBridgeTests: XCTestCase {

    private var coordinator: WebViewCoordinator!
    private var bridge: AdBridge!

    override func setUp() {
        super.setUp()
        coordinator = WebViewCoordinator()
        bridge = coordinator.adBridge
        bridge.coordinator = coordinator
    }

    override func tearDown() {
        coordinator = nil
        bridge = nil
        super.tearDown()
    }

    func testSetupAddsScriptHandler() {
        let handlers = coordinator.webView.configuration.userContentController.userScripts
        XCTAssertFalse(handlers.isEmpty)
    }

    func testFireEventEscapesStrings() {
        let expectation = self.expectation(description: "JS evaluated")
        coordinator.evaluateJavaScript("window.onAdBridgeEvent = function(e, d) { window.__lastEvent = e; window.__lastData = d; }") { _, _ in
            self.bridge.fireEvent("testEvent", data: "a'b\\c")
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 200_000_000)
                self.coordinator.evaluateJavaScript("window.__lastData") { result, _ in
                    XCTAssertEqual(result as? String, "a\\'b\\\\c")
                    expectation.fulfill()
                }
            }
        }
        wait(for: [expectation], timeout: 2.0)
    }
}
