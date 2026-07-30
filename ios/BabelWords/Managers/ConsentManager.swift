import Foundation
import GoogleMobileAds
@preconcurrency import UserMessagingPlatform

/// UMP (User Messaging Platform) consent manager for GDPR/EEA compliance.
/// Mirrors the Android `ConsentManager` behavior.
///
/// ## Thread safety
/// This class is `@MainActor`-isolated. Every UMP completion closure is annotated
/// `@MainActor`, which instructs Swift's concurrency runtime to always hop to the
/// main actor before executing the body — providing the same guarantee as the
/// explicit `Thread.isMainThread` guard used in `AdMobManager` and
/// `AppOpenAdManager`, but via Swift's type system rather than a runtime assertion.
///
/// The `@preconcurrency import UserMessagingPlatform` suppresses Swift 6
/// "Sendable" diagnostics on UMP closure types that the SDK has not yet annotated;
/// it does **not** weaken the `@MainActor` isolation enforced on each callback
/// below.
@MainActor
final class ConsentManager: NSObject {
    private let TAG = "ConsentManager"

    private var consentInformation: UMPConsentInformation {
        UMPConsentInformation.sharedInstance
    }

    private var isProcessing = false
    private var pendingCallbacks: [(Bool) -> Void] = []

    /// Request consent info, show the form if required, and call back when ads can proceed.
    func requestConsent(from viewController: UIViewController, onConsentReady: @escaping (Bool) -> Void) {
        pendingCallbacks.append(onConsentReady)
        guard !isProcessing else { return }
        isProcessing = true

        let parameters = UMPRequestParameters()
        parameters.tagForUnderAgeOfConsent = false

        // @MainActor annotation ensures this closure is always dispatched on the
        // main actor regardless of the thread the UMP SDK uses internally.
        consentInformation.requestConsentInfoUpdate(with: parameters) { @MainActor [weak self] error in
            guard let self = self else { return }
            self.handleConsentInfoUpdateResult(error: error, viewController: viewController)
        }
    }

    // MARK: - Consent-info update result

    private func handleConsentInfoUpdateResult(error: Error?, viewController: UIViewController) {
        if let error = error {
            print("[\(TAG)] Consent info update failed: \(error.localizedDescription)")
            completeConsent(consentInformation.canRequestAds)
            return
        }

        let canRequestAds = consentInformation.canRequestAds
        print("[\(TAG)] Consent info updated. canRequestAds=\(canRequestAds), formAvailable=\(consentInformation.formStatus == .available)")

        if consentInformation.formStatus == .available {
            loadAndShowConsentForm(from: viewController)
        } else {
            completeConsent(canRequestAds)
        }
    }

    // MARK: - Form load + present

    private func loadAndShowConsentForm(from viewController: UIViewController) {
        // @MainActor annotation ensures this closure is always dispatched on the
        // main actor regardless of the thread the UMP SDK uses internally.
        UMPConsentForm.load { @MainActor [weak self] form, error in
            guard let self = self else { return }
            self.handleFormLoadResult(form: form, error: error, viewController: viewController)
        }
    }

    private func handleFormLoadResult(form: UMPConsentForm?, error: Error?, viewController: UIViewController) {
        if let error = error {
            print("[\(TAG)] Consent form load failed: \(error.localizedDescription)")
            completeConsent(consentInformation.canRequestAds)
            return
        }

        guard let form = form else {
            completeConsent(consentInformation.canRequestAds)
            return
        }

        if consentInformation.consentStatus == .required {
            // @MainActor annotation ensures this closure is always dispatched on
            // the main actor regardless of the thread the UMP SDK uses internally.
            form.present(from: viewController) { @MainActor [weak self] error in
                guard let self = self else { return }
                self.handleFormPresentResult(error: error)
            }
        } else {
            completeConsent(consentInformation.canRequestAds)
        }
    }

    private func handleFormPresentResult(error: Error?) {
        if let error = error {
            print("[\(TAG)] Consent form show error: \(error.localizedDescription)")
        }
        print("[\(TAG)] Consent form dismissed. canRequestAds=\(consentInformation.canRequestAds)")
        completeConsent(consentInformation.canRequestAds)
    }

    // MARK: - Complete

    private func completeConsent(_ canRequestAds: Bool) {
        isProcessing = false
        let callbacks = pendingCallbacks
        pendingCallbacks.removeAll()
        callbacks.forEach { $0(canRequestAds) }
    }

    // MARK: - Helpers

    /// Build a GADRequest with the appropriate consent context.
    func buildAdRequest() -> GADRequest {
        let request = GADRequest()
        // UMP consent string is automatically applied by the Google Mobile Ads SDK.
        return request
    }

    func resetConsent() {
        UMPConsentInformation.sharedInstance.reset()
    }

    func isConsentAvailable() -> Bool {
        consentInformation.formStatus == .available
    }
}
