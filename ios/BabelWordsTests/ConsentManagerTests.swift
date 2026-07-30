import XCTest
@preconcurrency import UserMessagingPlatform
@testable import BabelWords

// MARK: - Mocks

/// Synchronously calls its completion handler on `requestConsentInfoUpdate`,
/// simulating the UMP SDK calling back immediately on the main thread.
@MainActor
private final class MockConsentInformation: ConsentInformationProviding {

    // Configure before each test
    var stubbedError: Error? = nil
    var canRequestAds: Bool = false
    var formStatus: UMPFormStatus = .unknown
    var consentStatus: UMPConsentStatus = .notRequired
    var didReset = false

    func requestConsentInfoUpdate(
        with parameters: UMPRequestParameters,
        completionHandler: @escaping (Error?) -> Void
    ) {
        // Call synchronously so tests can use XCTestExpectation with timeout 0.
        completionHandler(stubbedError)
    }

    func reset() {
        didReset = true
    }
}

/// A mock consent form that immediately calls its completion handler when
/// `present` is invoked.
@MainActor
private final class MockConsentForm: ConsentFormPresenting {
    var stubbedPresentError: Error? = nil
    var presentCallCount = 0

    func present(
        from viewController: UIViewController,
        completionHandler: ((Error?) -> Void)?
    ) {
        presentCallCount += 1
        completionHandler?(stubbedPresentError)
    }
}

// MARK: - Tests

/// Exercises the full `requestConsent` → load → present → `completeConsent`
/// path for `ConsentManager` using synchronous mocks, so every branch can be
/// driven without touching the live UMP SDK.
///
/// ### Off-main-thread guard
/// The `@MainActor` type annotation on `ConsentManager` enforces thread safety
/// at the Swift type-system level rather than via a runtime `assertionFailure`.
/// Swift concurrency rejects calls from non-isolated contexts at compile time,
/// so there is no executable background-thread branch to test at runtime; this
/// is noted here as a documented known gap rather than a missing test.
@MainActor
final class ConsentManagerTests: XCTestCase {

    // MARK: - Helpers

    /// Returns a `ConsentManager` wired to the given `MockConsentInformation`.
    /// If `form` is non-nil the loader calls back with that mock form; otherwise
    /// it calls back with `(nil, formLoadError)`.
    private func makeManager(
        info: MockConsentInformation,
        form: MockConsentForm? = nil,
        formLoadError: Error? = nil
    ) -> ConsentManager {
        let loader: ConsentFormLoader = { completion in
            completion(form, formLoadError)
        }
        return ConsentManager(consentInformation: info, formLoader: loader)
    }

    // MARK: - Existing public-API tests (retained)

    func testIsConsentAvailableReturnsFalseWhenFormStatusUnknown() {
        let info = MockConsentInformation()
        info.formStatus = .unknown
        let manager = makeManager(info: info)
        XCTAssertFalse(manager.isConsentAvailable())
    }

    func testIsConsentAvailableReturnsTrueWhenFormStatusAvailable() {
        let info = MockConsentInformation()
        info.formStatus = .available
        let manager = makeManager(info: info)
        XCTAssertTrue(manager.isConsentAvailable())
    }

    func testBuildAdRequestReturnsNonNil() {
        let info = MockConsentInformation()
        let manager = makeManager(info: info)
        XCTAssertNotNil(manager.buildAdRequest())
    }

    func testResetConsentDelegatesToProvider() {
        let info = MockConsentInformation()
        let manager = makeManager(info: info)
        manager.resetConsent()
        XCTAssertTrue(info.didReset)
    }

    // MARK: - Form-not-required branch

    /// When `formStatus` is not `.available`, consent should complete immediately
    /// and `onConsentReady` should receive the current `canRequestAds` value.
    func testFormNotRequired_canRequestAds_true() {
        let info = MockConsentInformation()
        info.formStatus = .unknown          // not .available → skip form
        info.canRequestAds = true

        let manager = makeManager(info: info)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { canAds in
            received = canAds
        }

        XCTAssertNotNil(received, "onConsentReady should have been called synchronously")
        XCTAssertTrue(received == true, "Expected canRequestAds == true")
    }

    func testFormNotRequired_canRequestAds_false() {
        let info = MockConsentInformation()
        info.formStatus = .unknown
        info.canRequestAds = false

        let manager = makeManager(info: info)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { canAds in
            received = canAds
        }

        XCTAssertNotNil(received)
        XCTAssertFalse(received == true, "Expected canRequestAds == false")
    }

    // MARK: - Consent info update error branch

    /// When `requestConsentInfoUpdate` returns an error, `onConsentReady` should
    /// still be called (with the current `canRequestAds` value) rather than
    /// swallowing the callback.
    func testConsentInfoUpdateError_callsOnConsentReady() {
        let info = MockConsentInformation()
        info.stubbedError = NSError(domain: "test", code: 1, userInfo: nil)
        info.canRequestAds = false

        let manager = makeManager(info: info)
        var callCount = 0

        manager.requestConsent(from: UIViewController()) { _ in
            callCount += 1
        }

        XCTAssertEqual(callCount, 1, "onConsentReady must be called even when update fails")
    }

    func testConsentInfoUpdateError_propagatesCanRequestAds() {
        let info = MockConsentInformation()
        info.stubbedError = NSError(domain: "test", code: 1, userInfo: nil)
        info.canRequestAds = true   // true even though update errored

        let manager = makeManager(info: info)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { received = $0 }

        XCTAssertEqual(received, true)
    }

    // MARK: - Form-required branch (consent status == .required)

    /// When a form is available AND `consentStatus == .required`, the form must
    /// be presented and `onConsentReady` must fire after dismissal.
    func testFormRequired_formPresented_onConsentReadyCalled() {
        let info = MockConsentInformation()
        info.formStatus = .available
        info.consentStatus = .required
        info.canRequestAds = true

        let form = MockConsentForm()
        let manager = makeManager(info: info, form: form)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { received = $0 }

        XCTAssertEqual(form.presentCallCount, 1, "Form must be presented once")
        XCTAssertNotNil(received, "onConsentReady must be called after form dismissal")
        XCTAssertEqual(received, true)
    }

    func testFormRequired_formPresentError_stillCallsOnConsentReady() {
        let info = MockConsentInformation()
        info.formStatus = .available
        info.consentStatus = .required
        info.canRequestAds = false

        let form = MockConsentForm()
        form.stubbedPresentError = NSError(domain: "present", code: 2, userInfo: nil)
        let manager = makeManager(info: info, form: form)
        var callCount = 0

        manager.requestConsent(from: UIViewController()) { _ in callCount += 1 }

        XCTAssertEqual(callCount, 1, "onConsentReady must fire even when form present returns an error")
    }

    // MARK: - Form-available but consent NOT required

    /// When a form is available but `consentStatus` is NOT `.required`, the form
    /// must NOT be presented and consent should complete immediately.
    func testFormAvailable_consentNotRequired_formNotPresented() {
        let info = MockConsentInformation()
        info.formStatus = .available
        info.consentStatus = .notRequired   // form available but not required
        info.canRequestAds = true

        let form = MockConsentForm()
        let manager = makeManager(info: info, form: form)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { received = $0 }

        XCTAssertEqual(form.presentCallCount, 0, "Form must NOT be presented when consent is not required")
        XCTAssertEqual(received, true)
    }

    // MARK: - Form load error branch

    /// When `UMPConsentForm.load` returns an error, consent should fall through
    /// and still call `onConsentReady`.
    func testFormLoadError_callsOnConsentReady() {
        let info = MockConsentInformation()
        info.formStatus = .available
        info.consentStatus = .required
        info.canRequestAds = false

        let loadError = NSError(domain: "load", code: 3, userInfo: nil)
        let manager = makeManager(info: info, form: nil, formLoadError: loadError)
        var callCount = 0

        manager.requestConsent(from: UIViewController()) { _ in callCount += 1 }

        XCTAssertEqual(callCount, 1, "onConsentReady must be called even when form load fails")
    }

    func testFormLoadNilNoError_callsOnConsentReady() {
        // Edge case: loader calls back with (nil, nil).
        let info = MockConsentInformation()
        info.formStatus = .available
        info.canRequestAds = true

        let manager = makeManager(info: info, form: nil, formLoadError: nil)
        var received: Bool? = nil

        manager.requestConsent(from: UIViewController()) { received = $0 }

        XCTAssertNotNil(received, "onConsentReady must be called when form is nil with no error")
        XCTAssertEqual(received, true)
    }

    // MARK: - Pending-callback coalescing

    /// Multiple calls to `requestConsent` while a request is already in flight
    /// should result in every callback being called exactly once when the first
    /// request completes.
    func testMultipleConcurrentRequests_allCallbacksFired() {
        let info = MockConsentInformation()
        info.formStatus = .unknown
        info.canRequestAds = true

        let manager = makeManager(info: info)
        var results: [Bool] = []
        let vc = UIViewController()

        manager.requestConsent(from: vc) { results.append($0) }
        manager.requestConsent(from: vc) { results.append($0) }
        manager.requestConsent(from: vc) { results.append($0) }

        XCTAssertEqual(results.count, 3, "All three callbacks must fire")
        XCTAssertTrue(results.allSatisfy { $0 == true })
    }

    /// After a completed request, a second independent call should also go
    /// through the full flow and call back correctly.
    func testSecondRequestAfterFirstCompletes_callbackFired() {
        let info = MockConsentInformation()
        info.formStatus = .unknown
        info.canRequestAds = true

        let manager = makeManager(info: info)
        var firstResult: Bool? = nil
        var secondResult: Bool? = nil
        let vc = UIViewController()

        manager.requestConsent(from: vc) { firstResult = $0 }
        XCTAssertNotNil(firstResult)

        info.canRequestAds = false
        manager.requestConsent(from: vc) { secondResult = $0 }
        XCTAssertEqual(secondResult, false, "Second independent request must also complete")
    }
}
