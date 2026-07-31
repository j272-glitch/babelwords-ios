import Foundation
import GoogleMobileAds
@preconcurrency import UserMessagingPlatform

// MARK: - Testability protocols

/// Abstracts `UMPConsentInformation` so tests can inject a mock without hitting
/// the live UMP SDK.
protocol ConsentInformationProviding: AnyObject {
    func requestConsentInfoUpdate(
        with parameters: UMPRequestParameters?,
        completionHandler: @escaping @Sendable (Error?) -> Void
    )
    var canRequestAds: Bool { get }
    var formStatus: UMPFormStatus { get }
    var consentStatus: UMPConsentStatus { get }
    func reset()
}

/// Abstracts `UMPConsentForm` presentation so tests can inject a mock form.
protocol ConsentFormPresenting: AnyObject {
    func present(
        from viewController: UIViewController?,
        completionHandler: (@Sendable (Error?) -> Void)?
    )
}

/// Closure type that loads a consent form and calls back with a
/// `ConsentFormPresenting` (or an error).  In production this wraps
/// `UMPConsentForm.load`; in tests a stub closure is injected.
typealias ConsentFormLoader = (@escaping @Sendable (ConsentFormPresenting?, Error?) -> Void) -> Void

// Production conformances (no additional implementation needed).
extension UMPConsentInformation: ConsentInformationProviding {}
extension UMPConsentForm: ConsentFormPresenting {}

// MARK: -

/// UMP (User Messaging Platform) consent manager for GDPR/EEA compliance.
/// Mirrors the Android `ConsentManager` behavior.
///
/// ## Thread safety
/// This class is main-thread confined. UMP completion closures remain
/// nonisolated at the SDK boundary because the SDK does not accept actor-isolated
/// function values; their state/UI work is normalized onto the main queue below.
///
/// The `@preconcurrency import UserMessagingPlatform` suppresses Swift 6
/// "Sendable" diagnostics on UMP closure types that the SDK has not yet annotated.
///
/// ## Off-main-thread guard
/// SDK callbacks that arrive off-main complete with payload-free recovery; the
/// non-Sendable form is never transferred into a detached actor task.
final class ConsentManager: NSObject, @unchecked Sendable {
    private let TAG = "ConsentManager"

    // MARK: - Dependencies (injectable for testing)

    private let consentInformation: ConsentInformationProviding
    private let formLoader: ConsentFormLoader

    // MARK: - State

    private var isProcessing = false
    private var pendingCallbacks: [(Bool) -> Void] = []
    private var pendingViewController: UIViewController?

    // MARK: - Consent-change notification

    /// Assigned by the owner (e.g. MainViewController) to receive a notification
    /// whenever consent completes.  The canonical use-case is invalidating
    /// any ad cache that was built with the previous consent signal.
    var onConsentUpdated: ((Bool) -> Void)?

    // MARK: - Initializers

    /// Production initializer — uses the live UMP SDK.
    override init() {
        self.consentInformation = UMPConsentInformation.sharedInstance
        self.formLoader = { completion in
            UMPConsentForm.load { form, error in
                completion(form, error)
            }
        }
        super.init()
    }

    /// Testing initializer — allows injecting mock consent information and a
    /// stub form loader so the full request → load → present → completeConsent
    /// path can be exercised without hitting the live UMP SDK.
    init(
        consentInformation: ConsentInformationProviding,
        formLoader: @escaping ConsentFormLoader
    ) {
        self.consentInformation = consentInformation
        self.formLoader = formLoader
        super.init()
    }

    // MARK: - Public API

    /// Request consent info, show the form if required, and call back when ads can proceed.
    func requestConsent(from viewController: UIViewController, onConsentReady: @escaping (Bool) -> Void) {
        precondition(Thread.isMainThread, "ConsentManager must be used on the main thread")
        pendingCallbacks.append(onConsentReady)
        guard !isProcessing else { return }
        isProcessing = true
        pendingViewController = viewController

        let parameters: UMPRequestParameters? = {
            let parameters = UMPRequestParameters()
            parameters.tagForUnderAgeOfConsent = false
            return parameters
        }()

        consentInformation.requestConsentInfoUpdate(with: parameters) { [weak self] error in
            let errorDescription = error?.localizedDescription
            guard Thread.isMainThread else {
                DispatchQueue.main.async { [weak self] in
                    self?.handleConsentInfoUpdateResult(errorDescription: errorDescription)
                }
                return
            }
            self?.handleConsentInfoUpdateResult(errorDescription: errorDescription)
        }
    }

    // MARK: - Consent-info update result

    private func handleConsentInfoUpdateResult(
        errorDescription: String?
    ) {
        if let errorDescription = errorDescription {
            print("[\(TAG)] Consent info update failed: \(errorDescription)")
            completeConsent(consentInformation.canRequestAds)
            return
        }

        let canRequestAds = consentInformation.canRequestAds
        print("[\(TAG)] Consent info updated. canRequestAds=\(canRequestAds), formAvailable=\(consentInformation.formStatus == .available)")

        if consentInformation.formStatus == .available {
            guard let viewController = pendingViewController else {
                completeConsent(canRequestAds)
                return
            }
            loadAndShowConsentForm(from: viewController)
        } else {
            completeConsent(canRequestAds)
        }
    }

    // MARK: - Form load + present

    private func loadAndShowConsentForm(from viewController: UIViewController) {
        formLoader { [weak self] form, error in
            let errorDescription = error?.localizedDescription
            if Thread.isMainThread {
                self?.handleFormLoadResult(
                    form: form,
                    errorDescription: errorDescription,
                    viewController: viewController
                )
            } else {
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    print("[\(self.TAG)] Consent form loaded off-main; completing without presenting")
                    self.completeConsent(self.consentInformation.canRequestAds)
                }
            }
        }
    }

    private func handleFormLoadResult(
        form: ConsentFormPresenting?,
        errorDescription: String?,
        viewController: UIViewController
    ) {
        if let errorDescription = errorDescription {
            print("[\(TAG)] Consent form load failed: \(errorDescription)")
            completeConsent(consentInformation.canRequestAds)
            return
        }

        guard let form = form else {
            completeConsent(consentInformation.canRequestAds)
            return
        }

        if consentInformation.consentStatus == .required {
            form.present(from: viewController) { [weak self] error in
                let errorDescription = error?.localizedDescription
                guard Thread.isMainThread else {
                    DispatchQueue.main.async { [weak self] in
                        self?.handleFormPresentResult(errorDescription: errorDescription)
                    }
                    return
                }
                self?.handleFormPresentResult(errorDescription: errorDescription)
            }
        } else {
            completeConsent(consentInformation.canRequestAds)
        }
    }

    private func handleFormPresentResult(errorDescription: String?) {
        if let errorDescription = errorDescription {
            print("[\(TAG)] Consent form show error: \(errorDescription)")
        }
        print("[\(TAG)] Consent form dismissed. canRequestAds=\(consentInformation.canRequestAds)")
        completeConsent(consentInformation.canRequestAds)
    }

    // MARK: - Complete

    private func completeConsent(_ canRequestAds: Bool) {
        isProcessing = false
        pendingViewController = nil
        let callbacks = pendingCallbacks
        pendingCallbacks.removeAll()
        callbacks.forEach { $0(canRequestAds) }
        onConsentUpdated?(canRequestAds)
    }

    // MARK: - Helpers

    /// Build a GADRequest with the appropriate consent context.
    func buildAdRequest() -> GADRequest {
        let request = GADRequest()
        // UMP consent string is automatically applied by the Google Mobile Ads SDK.
        return request
    }

    func resetConsent() {
        precondition(Thread.isMainThread, "ConsentManager must be used on the main thread")
        consentInformation.reset()
    }

    func isConsentAvailable() -> Bool {
        consentInformation.formStatus == .available
    }
}
