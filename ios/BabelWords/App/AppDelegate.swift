import UIKit
import Foundation
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics
import GoogleMobileAds

final class TestDeviceRegistrationState: @unchecked Sendable {
    private let lock = NSLock()
    private var active = false

    var isActive: Bool {
        lock.lock()
        defer { lock.unlock() }
        return active
    }

    func setActive(_ active: Bool) {
        lock.lock()
        defer { lock.unlock() }
        self.active = active
    }
}

/// Module-level singleton so ad managers can read it from any isolation domain
/// without going through the `@MainActor`-inferred `AppDelegate` type.
/// `TestDeviceRegistrationState` is thread-safe via `NSLock`, so no actor
/// isolation is required at the access site.
let testDeviceRegistrationState = TestDeviceRegistrationState()

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    static var shared: AppDelegate? {
        UIApplication.shared.delegate as? AppDelegate
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        configureFirebase()
        AnalyticsManager.logAppOpen()
        configureAdMobTestDeviceForTestLab()
        GADMobileAds.sharedInstance().start { _ in
            AnalyticsManager.logEvent("admob_initialized")
        }
        return true
    }

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        .portrait
    }

    // MARK: - Firebase

    private func configureFirebase() {
        if let options = loadGoogleServiceOptions() {
            FirebaseApp.configure(options: options)
            AnalyticsManager.setAnalyticsCollectionEnabled(true)
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(true)
        } else {
            AnalyticsManager.setAnalyticsCollectionEnabled(false)
        }
    }

    private func loadGoogleServiceOptions() -> FirebaseOptions? {
        guard let filePath = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
              let options = FirebaseOptions(contentsOfFile: filePath) else {
            return nil
        }
        return options
    }

    // MARK: - Test Lab safeguard

    private func configureAdMobTestDeviceForTestLab() {
        #if DEBUG
        let useRealAds = (Bundle.main.infoDictionary?["REAL_ADS_ON_TEST_LAB"] as? Bool) ?? false
        let isTestLab = ProcessInfo.processInfo.environment["FIREBASE_TEST_LAB"] == "true"
        guard isTestLab else { return }

        if useRealAds {
            testDeviceRegistrationState.setActive(true)
            return
        }

        testDeviceRegistrationState.setActive(true)
        #else
        testDeviceRegistrationState.setActive(false)
        #endif
    }
}
