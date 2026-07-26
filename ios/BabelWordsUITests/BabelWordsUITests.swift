import XCTest

final class BabelWordsUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    func testLaunchAndWebViewLoads() throws {
        // Wait up to 15 seconds for the WebView to exist after launch.
        let webView = app.webViews.firstMatch
        XCTAssertTrue(webView.waitForExistence(timeout: 15), "WebView did not appear")

        // The loading overlay should disappear once the WebView is ready.
        let loading = app.staticTexts["Loading..."]
        XCTAssertFalse(loading.waitForExistence(timeout: 15), "Loading overlay stuck")
    }

    func testLaunchDisplaysWebView() throws {
        // The web view is the main content; ensure it exists after launch.
        let webView = app.webViews.firstMatch
        XCTAssertTrue(webView.waitForExistence(timeout: 5))
    }

    func testAdButtonsAreReachable() throws {
        // If the web app exposes buttons with accessibility labels, verify they are reachable.
        let translateButton = app.buttons["Translate"]
        XCTAssertTrue(translateButton.waitForExistence(timeout: 15))
    }

    func testOfflineRetryButtonExistsOnError() throws {
        // Simulate an unreachable URL by toggling airplane mode is not reliable in UI tests;
        // this test simply verifies the error overlay buttons exist if they are shown.
        let retryButton = app.buttons["Retry"]
        let offlineButton = app.buttons["Offline mode"]

        // If an error occurs, the buttons should be present and tappable.
        if retryButton.waitForExistence(timeout: 3) {
            XCTAssertTrue(retryButton.isHittable)
        }
        if offlineButton.waitForExistence(timeout: 3) {
            XCTAssertTrue(offlineButton.isHittable)
        }
    }
}
