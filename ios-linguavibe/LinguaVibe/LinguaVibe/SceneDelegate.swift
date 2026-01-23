import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    
    var window: UIWindow?
    
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = (scene as? UIWindowScene) else { return }
        
        window = UIWindow(windowScene: windowScene)
        window?.rootViewController = MainViewController()
        window?.makeKeyAndVisible()
        
        // Handle deep links from scene connection
        if let urlContext = connectionOptions.urlContexts.first {
            handleDeepLink(url: urlContext.url)
        }
        
        // Handle universal links from scene connection
        if let userActivity = connectionOptions.userActivities.first,
           userActivity.activityType == NSUserActivityTypeBrowsingWeb,
           let url = userActivity.webpageURL {
            handleDeepLink(url: url)
        }
    }
    
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        if let url = URLContexts.first?.url {
            handleDeepLink(url: url)
        }
    }
    
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb,
           let url = userActivity.webpageURL {
            handleDeepLink(url: url)
        }
    }
    
    func sceneDidDisconnect(_ scene: UIScene) {
    }
    
    func sceneDidBecomeActive(_ scene: UIScene) {
        Logger.log("App became active (foreground)")
        NotificationCenter.default.post(name: .appDidBecomeActive, object: nil)
    }
    
    func sceneWillResignActive(_ scene: UIScene) {
        Logger.log("App will resign active")
    }
    
    func sceneWillEnterForeground(_ scene: UIScene) {
        Logger.log("App entering foreground")
        NotificationCenter.default.post(name: .appWillEnterForeground, object: nil)
    }
    
    func sceneDidEnterBackground(_ scene: UIScene) {
        Logger.log("App entered background")
        NotificationCenter.default.post(name: .appDidEnterBackground, object: nil)
    }
    
    private func handleDeepLink(url: URL) {
        Logger.log("Scene deep link: \(url.absoluteString)")
        NotificationCenter.default.post(
            name: .handleDeepLink,
            object: nil,
            userInfo: ["url": url]
        )
    }
}

extension Notification.Name {
    static let appDidBecomeActive = Notification.Name("appDidBecomeActive")
    static let appWillEnterForeground = Notification.Name("appWillEnterForeground")
    static let appDidEnterBackground = Notification.Name("appDidEnterBackground")
}
