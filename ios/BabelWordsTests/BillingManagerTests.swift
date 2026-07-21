import XCTest
@testable import BabelWords

@MainActor
final class BillingManagerTests: XCTestCase {

    private var billing: BillingManager!

    override func setUp() {
        super.setUp()
        billing = BillingManager()
    }

    override func tearDown() {
        billing = nil
        super.tearDown()
    }

    func testEventDispatcherReceivesError() {
        let expectation = self.expectation(description: "error dispatched")
        billing.eventDispatcher = { detail in
            if let event = detail["event"] as? String, event == "purchase_error" {
                expectation.fulfill()
            }
        }

        Task {
            await billing.purchaseProduct("nonexistent_product")
        }

        wait(for: [expectation], timeout: 3.0)
    }
}
