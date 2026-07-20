# LinguaVibe iOS

UIKit iOS replacement for the former Android wrapper. It wraps the web app at `https://linguagt.com` in a `WKWebView` and provides native AdMob, StoreKit subscriptions, Firebase analytics, and UMP consent.

## Requirements

- Xcode 15+
- iOS 15.0+
- CocoaPods
- Apple Developer Team (for device testing / App Store)

## Setup

1. Generate the Xcode project (if you edit `project.yml`):
   ```bash
   cd ios
   xcodegen generate
   ```
2. Install dependencies:
   ```bash
   pod install
   ```
3. Open the workspace:
   ```bash
   open LinguaVibe.xcworkspace
   ```
4. Set your `DEVELOPMENT_TEAM` in `project.yml` or in Xcode build settings.
5. Place `GoogleService-Info.plist` in `ios/LinguaVibe/Resources/` if you want Firebase Analytics/Crashlytics. The build gracefully degrades if it is absent.

## Architecture

| iOS component | Replaces Android component |
|---------------|----------------------------|
| `AppDelegate` | `BabelWordsApplication` |
| `SceneDelegate` | `ProcessLifecycleOwner` / activity lifecycle |
| `MainViewController` | `MainActivity` + `R.layout.activity_main` |
| `WebViewCoordinator` | `WebViewConfig` |
| `AdBridge` | `AdBridge` |
| `SubscriptionBridge` | `SubscriptionBridge` |
| `AdMobManager` | `AdMobManager` |
| `AppOpenAdManager` | `AppOpenAdManager` |
| `ConsentManager` | `ConsentManager` |
| `BillingManager` | `BillingManager` |
| `AnalyticsManager` | `AnalyticsManager` |

## JavaScript bridge

The web app receives the same events as the Android build:
- `window.AdBridge` exposes `loadInterstitial()`, `showInterstitial()`, `loadInterstitialAndShow()`, `isInterstitialReady()`, etc.
- `window.onAdBridgeEvent(event, data)` receives interstitial/rewarded/consent events.
- `window.LinguaVibeSubscriptionBridge` (and `window.AndroidSubscriptionBridge` for backward compatibility) exposes `purchaseProduct`, `subscribe`, and `restorePurchases`, dispatching `subscription_event` CustomEvents.

## CI/CD

The `.github/workflows/ios-build.yml` workflow runs on every push and PR:

- **Build and test on simulator** — runs on GitHub’s `macos-latest` runner, no signing required.
- **Archive and export IPA** — runs on a self-hosted cloud Mac (MacStadium, AWS EC2 Mac, or Orka) and uses `fastlane match` to manage signing.

### Cloud Mac setup

1. Create an Apple Developer account and App ID `com.babelwords.LinguaVibe`.
2. Generate an App Store Connect API key in a browser, download the `.p8` file, and add these GitHub Secrets:
   - `APP_STORE_CONNECT_API_KEY_KEY_ID`
   - `APP_STORE_CONNECT_API_KEY_ISSUER_ID`
   - `APP_STORE_CONNECT_API_KEY_BASE64` (base64 of the `.p8` file)
3. Create a private GitHub repo for `fastlane match` (e.g., `your-org/linguavibe-match`).
4. Add these GitHub Secrets:
   - `MATCH_PASSWORD` — encryption passphrase for the match repo
   - `MATCH_GIT_BASIC_AUTHORIZATION` — base64 of `username:personal-access-token`
5. Provision a cloud Mac and install the GitHub Actions self-hosted runner on it.
6. The first time the archive job runs, `fastlane match` generates the distribution certificate and provisioning profile on the cloud Mac and stores them encrypted in the match repo.

See `fastlane/Fastfile` and `fastlane/Appfile` for the lane configuration. Remember to replace `YOUR_ORG`, `YOUR_TEAM_ID`, and `your-apple-id@example.com` in those files.

## Notes

- AdMob on iOS uses `GADInterstitialAd` for both interstitial and rewarded aliases (the Android build had already unified rewarded onto the interstitial pipeline).
- App Open ads (`GADAppOpenAd`) are shown only after a warm resume of 5 seconds or more, with a 4-hour frequency cap persisted to `UserDefaults`.
- The microphone watchdog resets the mic state after 45 seconds if the JS heartbeat (`notifyMicActive`) stops.
- Universal Links are configured for `https://linguagt.com` and `https://gtlingua.com` via `LinguaVibe.entitlements`.

