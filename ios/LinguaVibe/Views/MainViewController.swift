import UIKit
import WebKit

/// Main UIKit view controller that wraps the web app in WKWebView. Replaces Android `MainActivity`.
@MainActor
final class MainViewController: UIViewController {

    private let TAG = "MainViewController"

    private lazy var webCoordinator = WebViewCoordinator()
    private lazy var consentManager = ConsentManager()
    private lazy var adMobManager = AdMobManager { [weak self] in self?.consentManager }
    private lazy var appOpenAdManager = AppOpenAdManager(
        getConsentManager: { [weak self] in self?.consentManager },
        getMicActive: { [weak self] in self?.webCoordinator.isMicActive ?? false }
    )
    private lazy var billingManager = BillingManager()

    private let loadingContainer = UIView()
    private let errorContainer = UIView()
    private let retryButton = UIButton(type: .system)
    private let offlineButton = UIButton(type: .system)
    private let spinner = UIActivityIndicatorView(style: .large)


    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupWebView()
        setupLoadingOverlay()
        setupErrorOverlay()
        wireBridges()
        wireManagers()
        observeNetwork()
        requestConsentAndLoadAds()
        loadInitialURL()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        AnalyticsManager.logScreenView(screenName: "main", screenClass: "MainViewController")
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        adMobManager.onActivityResumed(self)
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        adMobManager.onActivityPaused()
    }

    deinit {
        adMobManager.unregisterNetworkCallback()
        adMobManager.destroy()
        appOpenAdManager.cleanup()
    }

    // MARK: - Setup

    private func setupWebView() {
        let webView = webCoordinator.webView
        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    private func setupLoadingOverlay() {
        loadingContainer.translatesAutoresizingMaskIntoConstraints = false
        loadingContainer.backgroundColor = .systemBackground
        view.addSubview(loadingContainer)
        NSLayoutConstraint.activate([
            loadingContainer.topAnchor.constraint(equalTo: view.topAnchor),
            loadingContainer.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            loadingContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            loadingContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        spinner.translatesAutoresizingMaskIntoConstraints = false
        spinner.startAnimating()
        loadingContainer.addSubview(spinner)
        NSLayoutConstraint.activate([
            spinner.centerXAnchor.constraint(equalTo: loadingContainer.centerXAnchor),
            spinner.centerYAnchor.constraint(equalTo: loadingContainer.centerYAnchor)
        ])
    }

    private func setupErrorOverlay() {
        errorContainer.translatesAutoresizingMaskIntoConstraints = false
        errorContainer.backgroundColor = .systemBackground
        errorContainer.isHidden = true
        view.addSubview(errorContainer)
        NSLayoutConstraint.activate([
            errorContainer.topAnchor.constraint(equalTo: view.topAnchor),
            errorContainer.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            errorContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            errorContainer.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        let stack = UIStackView()
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.axis = .vertical
        stack.spacing = 16
        stack.alignment = .center
        errorContainer.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: errorContainer.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: errorContainer.centerYAnchor),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: errorContainer.leadingAnchor, constant: 32),
            errorContainer.trailingAnchor.constraint(greaterThanOrEqualTo: stack.trailingAnchor, constant: 32)
        ])

        let titleLabel = UILabel()
        titleLabel.text = "Unable to load Babel Words"
        titleLabel.font = UIFont.preferredFont(forTextStyle: .headline)
        titleLabel.textAlignment = .center

        let messageLabel = UILabel()
        messageLabel.text = "Check your connection and try again."
        messageLabel.font = UIFont.preferredFont(forTextStyle: .subheadline)
        messageLabel.textColor = .secondaryLabel
        messageLabel.textAlignment = .center
        messageLabel.numberOfLines = 0

        retryButton.setTitle("Retry", for: .normal)
        retryButton.addTarget(self, action: #selector(onRetry), for: .touchUpInside)
        retryButton.titleLabel?.font = UIFont.preferredFont(forTextStyle: .body)

        offlineButton.setTitle("Offline mode", for: .normal)
        offlineButton.addTarget(self, action: #selector(onOfflineMode), for: .touchUpInside)
        offlineButton.titleLabel?.font = UIFont.preferredFont(forTextStyle: .body)

        stack.addArrangedSubview(titleLabel)
        stack.addArrangedSubview(messageLabel)
        stack.addArrangedSubview(retryButton)
        stack.addArrangedSubview(offlineButton)
    }

    private func wireBridges() {
        webCoordinator.adBridge.adMobManager = adMobManager
        webCoordinator.adBridge.consentManager = consentManager
        webCoordinator.subscriptionBridge.billingManager = billingManager
        webCoordinator.subscriptionBridge.eventDispatcher = { [weak self] detail in
            self?.dispatchSubscriptionEvent(detail)
        }
    }

    private func wireManagers() {
        webCoordinator.onLoadingChange = { [weak self] isLoading in
            self?.loadingContainer.isHidden = !isLoading
            if isLoading { self?.errorContainer.isHidden = true }
        }
        webCoordinator.onError = { [weak self] _ in
            self?.errorContainer.isHidden = false
            self?.loadingContainer.isHidden = true
        }
        webCoordinator.onRetry = { [weak self] in
            self?.errorContainer.isHidden = true
            self?.loadingContainer.isHidden = false
            self?.loadInitialURL()
        }

        adMobManager.eventCallback = { [weak self] event, data in
            self?.webCoordinator.adBridge.fireEvent(event, data: data ?? "")
        }
    }

    private func observeNetwork() {
        adMobManager.registerNetworkCallback()
    }

    private func requestConsentAndLoadAds() {
        consentManager.requestConsent(from: self) { [weak self] canRequestAds in
            print("[\(self?.TAG ?? "MainViewController")] Consent resolved: canRequestAds=\(canRequestAds)")
            guard canRequestAds else { return }
            self?.adMobManager.preloadInterstitial()
            self?.appOpenAdManager.loadAd()
        }
    }

    /// Route a universal link or deep-link URL into the WebView.
    func route(url: URL) {
        webCoordinator.load(url: url)
    }

    private func loadInitialURL() {
        webCoordinator.load(url: AppConfig.initialURL)
    }

    // MARK: - Actions

    @objc private func onRetry() {
        errorContainer.isHidden = true
        loadingContainer.isHidden = false
        loadInitialURL()
    }

    @objc private func onOfflineMode() {
        errorContainer.isHidden = true
        if let url = Bundle.main.url(forResource: "offline", withExtension: "html") {
            webCoordinator.load(url: url)
        }
    }

    private func dispatchSubscriptionEvent(_ detail: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: detail, options: []),
              let json = String(data: data, encoding: .utf8) else { return }
        let script = """
        (function(){
          var e = new CustomEvent('subscription_event', { detail: \(json) });
          window.dispatchEvent(e);
        })();
        """
        webCoordinator.evaluateJavaScript(script)
    }

    // MARK: - Scene lifecycle (forwarded by SceneDelegate)

    func onSceneDidBecomeActive() {
        adMobManager.onActivityResumed(self)
        appOpenAdManager.onWillEnterForeground(from: self)
    }

    func onSceneWillResignActive() {
        adMobManager.onActivityPaused()
    }

    func onSceneDidEnterBackground() {
        appOpenAdManager.onDidEnterBackground()
    }

    func onSceneWillEnterForeground() {
        // App open ad decision is deferred until viewDidAppear / didBecomeActive.
    }

    // MARK: - Back navigation

    override var prefersHomeIndicatorAutoHidden: Bool { true }
}
