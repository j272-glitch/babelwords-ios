import UIKit
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics
import GoogleMobileAds

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    static var shared: AppDelegate? {
        UIApplication.shared.delegate as? AppDelegate
    }

    /// True only after AdMob test-device registration has been applied on Firebase Test Lab.
    @MainActor
    static var isTestDeviceRegistrationActive = false

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
            AppDelegate.isTestDeviceRegistrationActive = true
            return
        }

        AppDelegate.isTestDeviceRegistrationActive = true
        #else
        AppDelegate.isTestDeviceRegistrationActive = false
        #endif
    }
}
