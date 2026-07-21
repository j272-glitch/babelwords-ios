import Foundation

enum AppConfig {
    static let webAppURL = URL(string: "https://linguagt.com")!
    static let offlinePage = "offline"
    static let accessToken = Bundle.main.infoDictionary?["BABELWORDS_ACCESS_TOKEN"] as? String ?? ""

    /// Hosts that are allowed to drive the native app (bridges, media capture, navigation).
    static let trustedHosts: Set<String> = ["linguagt.com"]

    static var initialURL: URL {
        if accessToken.isEmpty {
            return webAppURL
        }
        var components = URLComponents(url: webAppURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "access", value: accessToken)]
        return components.url ?? webAppURL
    }

    static func isTrusted(url: URL?) -> Bool {
        guard let host = url?.host?.lowercased() else { return false }
        return trustedHosts.contains(host)
    }

    static func isNavigationAllowed(url: URL?) -> Bool {
        guard let url = url else { return false }
        if url.scheme?.lowercased() == "file" { return true }
        guard let host = url.host?.lowercased() else { return false }
        return trustedHosts.contains(host)
    }
}
