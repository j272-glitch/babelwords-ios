import UIKit

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?
    private var mainViewController: MainViewController?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        let mainVC = MainViewController()
        self.mainViewController = mainVC
        window.rootViewController = mainVC
        window.makeKeyAndVisible()
        self.window = window

        // Handle a universal link that launched the app.
        if let userActivity = connectionOptions.userActivities.first,
           userActivity.activityType == NSUserActivityTypeBrowsingWeb,
           let url = userActivity.webpageURL,
           AppConfig.isTrusted(url: url) {
            mainVC.route(url: url)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url,
              AppConfig.isTrusted(url: url) else { return }
        mainViewController?.route(url: url)
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard userActivity.activityType == NSUserActivityTypeBrowsingWeb,
              let url = userActivity.webpageURL,
              AppConfig.isTrusted(url: url) else { return }
        mainViewController?.route(url: url)
    }

    func handleDeepLink(url: URL) {
        guard AppConfig.isTrusted(url: url) else { return }
        mainViewController?.route(url: url)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        mainViewController?.onSceneDidBecomeActive()
    }

    func sceneWillResignActive(_ scene: UIScene) {
        mainViewController?.onSceneWillResignActive()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        mainViewController?.onSceneDidEnterBackground()
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        mainViewController?.onSceneWillEnterForeground()
    }
}
