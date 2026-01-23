import UIKit
import GoogleMobileAds
import AppTrackingTransparency
import AdSupport
import UserMessagingPlatform

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var window: UIWindow?
    
    // AdMob App ID
    static let adMobAppID = "ca-app-pub-9991891515643313~7514450861"
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        
        Logger.log("═" + String(repeating: "═", count: 49))
        Logger.log("LINGUAVIBE iOS APP STARTING")
        Logger.log("═" + String(repeating: "═", count: 49))
        
        // Initialize Google Mobile Ads SDK on background queue
        DispatchQueue.global(qos: .userInitiated).async {
            GADMobileAds.sharedInstance().start { status in
                Logger.log("✓ AdMob SDK initialized")
                status.adapterStatusesByClassName.forEach { (adapter, status) in
                    Logger.log("  Adapter: \(adapter) - \(status.state.rawValue)")
                }
                
                // Request ATT permission after SDK init (iOS 14+)
                if #available(iOS 14, *) {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        ATTrackingManager.requestTrackingAuthorization { status in
                            Logger.log("ATT status: \(status.rawValue)")
                        }
                    }
                }
            }
        }
        
        return true
    }
    
    // MARK: - UISceneSession Lifecycle
    
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
    
    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
    }
    
    // MARK: - Deep Link Handling (for older iOS)
    
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
        return handleDeepLink(url: url)
    }
    
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb,
           let url = userActivity.webpageURL {
            return handleDeepLink(url: url)
        }
        return false
    }
    
    private func handleDeepLink(url: URL) -> Bool {
        Logger.log("Deep link received: \(url.absoluteString)")
        
        guard let host = url.host,
              host == "linguagt.com" || host == "www.linguagt.com" else {
            return false
        }
        
        // Post notification for ViewController to handle
        NotificationCenter.default.post(
            name: .handleDeepLink,
            object: nil,
            userInfo: ["url": url]
        )
        
        return true
    }
}

// MARK: - Notification Names
extension Notification.Name {
    static let handleDeepLink = Notification.Name("handleDeepLink")
}
