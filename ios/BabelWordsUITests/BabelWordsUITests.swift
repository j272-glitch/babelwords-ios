import XCTest

final class BabelWordsUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchEnvironment["BABELWORDS_UI_TEST_MODE"] = "true"
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    func testLaunchAndWebViewLoads() throws {
        // Wait up to 15 seconds for the WebView to exist after launch.
        let webView = app.webViews.firstMatch
        XCTAssertTrue(webView.waitForExistence(timeout: 15), "WebView did not appear")

        let fixtureMarker = app.staticTexts["BabelWords Test Fixture"]
        XCTAssertTrue(fixtureMarker.waitForExistence(timeout: 15), "Test fixture did not load")

        // The loading overlay should disappear once the WebView is ready.
        let loading = app.staticTexts["Loading..."]
        XCTAssertFalse(loading.waitForExistence(timeout: 15), "Loading overlay stuck")
    }

    func testLaunchDisplaysWebView() throws {
        // The web view is the main content; ensure it exists after launch.
        let webView = app.webViews.firstMatch
        XCTAssertTrue(webView.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["BabelWords Test Fixture"].waitForExistence(timeout: 5))
    }

    func testAdButtonsAreReachable() throws {
        let translateButton = app.buttons["Translate"]
        XCTAssertTrue(translateButton.waitForExistence(timeout: 15))
    }

    func testOfflineRetryButtonExistsOnError() throws {
        app.terminate()
        app.launchEnvironment["BABELWORDS_UI_TEST_ERROR"] = "true"
        app.launch()

        let retryButton = app.buttons["Retry"]
        let offlineButton = app.buttons["Offline mode"]

        XCTAssertTrue(retryButton.waitForExistence(timeout: 5))
        XCTAssertTrue(offlineButton.waitForExistence(timeout: 5))
        XCTAssertTrue(retryButton.isHittable)
        XCTAssertTrue(offlineButton.isHittable)
    }
}
