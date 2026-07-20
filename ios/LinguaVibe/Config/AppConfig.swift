import Foundation

enum AppConfig {
    static let webAppURL = URL(string: "https://linguagt.com")!
    static let offlinePage = "offline"
    static let accessToken = Bundle.main.infoDictionary?["BABELWORDS_ACCESS_TOKEN"] as? String ?? ""

    static var initialURL: URL {
        if accessToken.isEmpty {
            return webAppURL
        }
        var components = URLComponents(url: webAppURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "access", value: accessToken)]
        return components.url ?? webAppURL
    }
}
