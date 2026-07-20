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
