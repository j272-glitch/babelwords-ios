import XCTest
@testable import BabelWords

@MainActor
final class ConsentManagerTests: XCTestCase {

    private var consent: ConsentManager!

    override func setUp() {
        super.setUp()
        consent = ConsentManager()
    }

    override func tearDown() {
        consent = nil
        super.tearDown()
    }

    func testIsConsentAvailableReturnsFalseInitially() {
        XCTAssertFalse(consent.isConsentAvailable())
    }

    func testBuildAdRequestReturnsNonNil() {
        let request = consent.buildAdRequest()
        XCTAssertNotNil(request)
    }
}
