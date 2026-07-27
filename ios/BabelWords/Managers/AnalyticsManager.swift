import Foundation
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics

/// Firebase Analytics wrapper. Gracefully degrades when Firebase is not configured.
final class AnalyticsManager {
    private static let TAG = "AnalyticsManager"
    private static let lock = NSLock()
    nonisolated(unsafe) private static var isInitialized = false
    nonisolated(unsafe) private static var isCollectionEnabled = false

    static func configure() {
        lock.lock()
        defer { lock.unlock() }
        guard !isInitialized else { return }
        isInitialized = true
        // FirebaseApp.configure() is called from AppDelegate; this marks the wrapper ready.
        isCollectionEnabled = FirebaseApp.app() != nil
    }

    static func setAnalyticsCollectionEnabled(_ enabled: Bool) {
        lock.lock()
        defer { lock.unlock() }
        isCollectionEnabled = enabled
        Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    static func logEvent(_ name: String, parameters: [String: String] = [:]) {
        guard isCollectionEnabled else { return }
        let converted = parameters.reduce(into: [String: Any]()) { $0[$1.key] = $1.value }
        Analytics.logEvent(name, parameters: converted)
    }

    static func logScreenView(screenName: String, screenClass: String) {
        logEvent(AnalyticsEventScreenView, parameters: [
            AnalyticsParameterScreenName: screenName,
            AnalyticsParameterScreenClass: screenClass
        ])
    }

    static func setUserProperty(_ name: String, value: String?) {
        guard isCollectionEnabled else { return }
        Analytics.setUserProperty(value, forName: name)
    }

    static func logException(_ error: Error, contextMessage: String? = nil) {
        contextMessage.map { Crashlytics.crashlytics().log($0) }
        Crashlytics.crashlytics().record(error: error)
    }

    static func logAppOpen() { logEvent(AnalyticsEventAppOpen) }
    static func logTranslationStarted(sourceLang: String, targetLang: String) {
        logEvent("translation_started", parameters: ["source_lang": sourceLang, "target_lang": targetLang])
    }
    static func logMicActivated() { logEvent("mic_activated") }
    static func logAdImpression(adUnit: String, adFormat: String) {
        logEvent("ad_impression", parameters: ["ad_unit": adUnit, "ad_format": adFormat])
    }
    static func logAdClicked(adUnit: String, adFormat: String) {
        logEvent("ad_clicked", parameters: ["ad_unit": adUnit, "ad_format": adFormat])
    }
    static func logAdFailed(adUnit: String, error: String) {
        logEvent("ad_failed", parameters: ["ad_unit": adUnit, "error": error])
    }
    static func logBillingEvent(event: String, productId: String) {
        logEvent(event, parameters: ["product_id": productId])
    }
}
