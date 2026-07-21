# Overview

BabelWords (formerly LinguaGT/LinguaLink) is a real-time speech translation application. The project wraps a web application (hosted at linguagt.com) in a native iOS `WKWebView` container, providing speech translation capabilities across 36 languages with microphone access and modern web features.

The application is built using:
- **Native iOS (UIKit / Swift)** - WebView wrapper with permission handling
- **GitHub Actions** - Automated CI/CD for iOS builds, tests, and archives
- **CocoaPods** - Dependency management (Google Mobile Ads, Firebase, UMP)
- **XcodeGen** - Xcode project generation from `project.yml`
- **Capacitor** - Cross-platform mobile framework (web layer)
- **Node.js** - Development tooling and web serving
- **Google IMA SDK** - Video ad monetization

# User Preferences

Preferred communication style: Simple, everyday language.

# System Architecture

## Build System

**Xcode + XcodeGen**: The iOS project is generated from `ios/project.yml`. This keeps the project file out of merge conflicts and makes the layout explicit. After editing `project.yml`, run `xcodegen generate` in `ios/` to regenerate the `.xcodeproj`.

**CocoaPods**: Native dependencies are managed in `ios/Podfile`:
- Google Mobile Ads SDK (`Google-Mobile-Ads-SDK`) for AdMob interstitial / app open ads
- Google User Messaging Platform (`GoogleUserMessagingPlatform`) for GDPR consent
- Firebase Analytics + Crashlytics (`FirebaseAnalytics`, `FirebaseCrashlytics`)
- StoreKit is built-in for subscriptions and in-app purchases

**Build Configurations**: Supports both debug and release builds. Release archives require an Apple Developer Team, signing certificate, and provisioning profile configured via GitHub Actions secrets.

## iOS Application Architecture

**WKWebView Container**: `MainViewController` wraps the web app in a `WKWebView` with:
- JavaScript enabled and modern web APIs
- Microphone / camera permission grants via `WKUIDelegate`
- Deep link support for `linguagt.com` via Universal Links (`BabelWords.entitlements`)
- SSL error handling and an offline fallback page (`offline.html`)
- Redirect-loop detection and a stale-mic watchdog

**Permission Management**: The web layer requests media capture permissions; the native layer grants them for the app's own microphone/camera usage. `NSMicrophoneUsageDescription`, `NSSpeechRecognitionUsageDescription`, and `NSCameraUsageDescription` are declared in `Info.plist`.

**Deep Linking**: Configured with Apple Universal Links for the `linguagt.com` domain. The `apple-app-site-association` file must be served by the web host; `BabelWords.entitlements` declares the associated domains.

**Crash Prevention**: Ported from the Android 91-solution crash-prevention system:
- Mic safety watchdog (45s stale-lock reset)
- WebView lifecycle state checks
- Thread-safe `@MainActor` manager classes
- Nullability guards and safe unwrapping
- Ad state desynchronization guards (frequency cap, freshness checks, session invalidation)
- JavaScript bridge timing via message handlers and page-load tracking
- Activity lifecycle observation via `SceneDelegate`
- Retry cancellation, network callback cleanup, and memory-leak prevention

Key files: `MainViewController.swift`, `WebViewCoordinator.swift`, `AdBridge.swift`, `SubscriptionBridge.swift`, `AdMobManager.swift`, `AppOpenAdManager.swift`, `ConsentManager.swift`, `BillingManager.swift`, `AnalyticsManager.swift`, `AppDelegate.swift`, `SceneDelegate.swift`.

## Third-Party Integrations

**Ad Integration (AdMob)**:

`AdBridge` - Primary ad interface exposed to JavaScript as `window.AdBridge`:
- Supports interstitial and rewarded ad types (rewarded is a backward-compat alias to interstitial)
- Events are dispatched via `window.onAdBridgeEvent`

`AdMobManager`:
- 45-minute ad expiry, 40-minute refresh threshold
- 30-second frequency cap between shows
- 15-second load timeout with exponential backoff retry
- Atomic single-flight loading guard
- Auto-show on stale/no ad with `pendingShow` flag
- Network availability auto-reload
- Test Lab auto-show gating (only when `FIREBASE_TEST_LAB` is true and test-device registration is active)
- Audio session switching for ad playback ( playback → playAndRecord )

`AppOpenAdManager`:
- `GADAppOpenAd` shown only after warm resume (≥ 5s in background)
- 4-hour frequency cap persisted to `UserDefaults`
- Blocks when mic is active or an interstitial is showing
- 15s load timeout and retry limits

`ConsentManager`:
- UMP consent info update
- Loads and shows the consent form if required
- Builds ad requests with the UMP consent context applied automatically by the Google Mobile Ads SDK
- Gracefully falls back to non-personalized ads when consent is not obtained

**Analytics**: Firebase Analytics + Crashlytics wrapper (`AnalyticsManager`). If `GoogleService-Info.plist` is not present, the app degrades gracefully and logs locally are suppressed. Custom events include translation, mic, ad, billing, and screen-view events.

**Billing**: StoreKit 2 (`BillingManager`) for subscriptions and consumables:
- `purchaseProduct(productId)` for one-time products and subscriptions
- `restorePurchases()` restores current entitlements and consumables
- Server validation posts to `https://linguagt.com/api/iap/apple/verify`
- Dispatches `subscription_event` CustomEvents to the web app

**Security**: Conversation mode security remains at the web layer. The native iOS wrapper only persists the access token in memory; cookies are handled by `WKWebView`.

# External Dependencies

## Core iOS Dependencies
- **iOS SDK**: Deployment target 15.0+
- **Swift**: Version 5.9+
- **Xcode**: 15.0+
- **CocoaPods**: Latest stable version

## CocoaPods Dependencies
- `Google-Mobile-Ads-SDK` ~> 11.0
- `GoogleUserMessagingPlatform` ~> 2.2
- `FirebaseAnalytics` ~> 11.0
- `FirebaseCrashlytics` ~> 11.0

## Node.js Dependencies
- **@anthropic-ai/sdk**: Version 0.60.0 (Claude AI integration)
- **Express**: Version 5.1.0 (web server)
- **Puppeteer**: Version 24.17.1 (browser automation)

## Build Tools
- **XcodeGen**: For project generation from `project.yml`
- **CocoaPods**: Native dependency resolution
- **xcodebuild**: CI builds and tests
- **GitHub Actions**: macOS runners for build/test/archive

## Cloud Services
- **GitHub Actions**: macOS runners for CI/CD
- **Web Hosting**: linguagt.com domain
- **Universal Links**: Apple associated-domains verification

## Optional Integrations
- **Twilio**: SMS notifications (referenced in attached assets)
- **Facebook Login**: OAuth authentication (GDPR-compliant implementation documented)
- **Google/X (Twitter) Sign-In**: Social authentication options

## Development Tools
- **Replit Database**: Key-value storage (@replit/database)
- **Semgrep**: Security scanning with Bicep rules
- **Docker**: Claude AI development environment with MCP servers

# CI/CD

The GitHub Actions workflow (`.github/workflows/ios-build.yml`) runs on macOS and:
1. Selects Xcode 15.4
2. Installs CocoaPods
3. Generates the Xcode project with XcodeGen (if installed)
4. Optionally decodes `GoogleService-Info.plist` from a base64 secret
5. Runs `pod install`
6. Builds the app for the iPhone 15 simulator
7. Runs unit and UI tests
8. On `main`/`master`, optionally archives the app for release distribution using signing secrets

Required secrets for release archives:
- `IOS_P12_BASE64` — distribution certificate
- `IOS_P12_PASSWORD` — certificate password
- `IOS_MOBILEPROVISION_BASE64` — App Store / Ad Hoc provisioning profile
- `IOS_DEVELOPMENT_TEAM` — Apple Team ID
- `IOS_PROVISIONING_PROFILE_SPECIFIER` — provisioning profile specifier

Optional secret:
- `GOOGLE_SERVICES_INFO_PLIST_BASE64` — Firebase configuration plist

# Local Development

1. `cd ios`
2. `xcodegen generate` (if you change `project.yml`)
3. `pod install`
4. `open BabelWords.xcworkspace`
5. Build and run on a simulator or device

To enable Firebase locally, place `GoogleService-Info.plist` in `ios/BabelWords/Resources/`.
