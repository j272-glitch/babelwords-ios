import Foundation
import GoogleMobileAds
@preconcurrency import UserMessagingPlatform

/// UMP (User Messaging Platform) consent manager for GDPR/EEA compliance.
/// Mirrors the Android `ConsentManager` behavior.
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

        consentInformation.requestConsentInfoUpdate(with: parameters) { [weak self] error in
            guard let self = self else { return }

            if let error = error {
                print("[\(self.TAG)] Consent info update failed: \(error.localizedDescription)")
                self.completeConsent(self.consentInformation.canRequestAds)
                return
            }

            let canRequestAds = self.consentInformation.canRequestAds
            print("[\(self.TAG)] Consent info updated. canRequestAds=\(canRequestAds), formAvailable=\(self.consentInformation.formStatus == .available)")

            if self.consentInformation.formStatus == .available {
                self.loadAndShowConsentForm(from: viewController)
            } else {
                self.completeConsent(canRequestAds)
            }
        }
    }

    private func loadAndShowConsentForm(from viewController: UIViewController) {
        UMPConsentForm.load { [weak self] form, error in
            guard let self = self else { return }
            if let error = error {
                print("[\(self.TAG)] Consent form load failed: \(error.localizedDescription)")
                self.completeConsent(self.consentInformation.canRequestAds)
                return
            }

            guard let form = form else {
                self.completeConsent(self.consentInformation.canRequestAds)
                return
            }

            if self.consentInformation.consentStatus == .required {
                form.present(from: viewController) { [weak self] error in
                    guard let self = self else { return }
                    if let error = error {
                        print("[\(self.TAG)] Consent form show error: \(error.localizedDescription)")
                    }
                    print("[\(self.TAG)] Consent form dismissed. canRequestAds=\(self.consentInformation.canRequestAds)")
                    self.completeConsent(self.consentInformation.canRequestAds)
                }
            } else {
                self.completeConsent(self.consentInformation.canRequestAds)
            }
        }
    }

    private func completeConsent(_ canRequestAds: Bool) {
        isProcessing = false
        let callbacks = pendingCallbacks
        pendingCallbacks.removeAll()
        callbacks.forEach { $0(canRequestAds) }
    }

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
