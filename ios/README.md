# BabelWords iOS

UIKit iOS replacement for the former Android wrapper. It wraps the web app at `https://linguagt.com` in a `WKWebView` and provides native AdMob, StoreKit subscriptions, Firebase analytics, and UMP consent.

## Requirements

- Xcode 26+
- iOS 26.0+
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
   open BabelWords.xcworkspace
   ```
4. Set your `DEVELOPMENT_TEAM` in `project.yml` or in Xcode build settings.
5. Place `GoogleService-Info.plist` in `ios/BabelWords/Resources/` if you want Firebase Analytics/Crashlytics. The build gracefully degrades if it is absent.

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
- `window.BabelWordsSubscriptionBridge` (and `window.AndroidSubscriptionBridge` for backward compatibility) exposes `purchaseProduct`, `subscribe`, and `restorePurchases`, dispatching `subscription_event` CustomEvents.

## CI/CD

The `.github/workflows/ios-build.yml` workflow runs on every push and PR:

- **Build and test on simulator** — runs on GitHub’s `macos-latest` runner, no signing required.
- **Archive and export IPA** — runs on GitHub’s `macos-latest` runner and uses `fastlane match` to download signing certificates from the encrypted match repo.
- **Firebase Test Lab iOS** — runs the `BabelWordsUITests` UI test bundle on a physical iOS device in Firebase Test Lab, matching the Android CI’s Firebase Test Lab coverage.

A separate manual workflow, `.github/workflows/ios-test-lab-v1.yml`, mirrors the Android CI structure more closely:

- **Build iOS App + XCUITest Runner** — creates a signed ad-hoc `.ipa` and a zipped XCUITest runner on `macos-latest`.
- **XCUITest on iOS Simulator** — runs the UI tests on a simulator.
- **Firebase Test Lab iOS Device Test** — uploads the `.ipa` and XCUITest runner to Firebase Test Lab on a physical device, then downloads logs and video and scans for ad markers.

Trigger the manual workflow from the GitHub Actions tab with the desired boolean inputs.

### GitHub Secrets

Add these secrets under `Settings → Secrets and variables → Actions`:

| Secret | Required for | Description |
|--------|--------------|-------------|
| `APP_STORE_CONNECT_API_KEY_KEY_ID` | archive, test lab | App Store Connect API key ID |
| `APP_STORE_CONNECT_API_KEY_ISSUER_ID` | archive, test lab | App Store Connect issuer ID |
| `APP_STORE_CONNECT_API_KEY_BASE64` | archive, test lab | base64 of the `.p8` key file |
| `MATCH_PASSWORD` | archive, test lab | `fastlane match` repo encryption passphrase |
| `MATCH_GIT_BASIC_AUTHORIZATION` | archive, test lab | base64 of `username:personal-access-token` for the match repo |
| `GCP_PROJECT_ID` | test lab | Google Cloud project with Firebase Test Lab enabled |
| `GCP_SERVICE_ACCOUNT_KEY_BASE64` | test lab | base64 of a service account JSON key with Firebase Test Lab Admin role |
| `FIREBASE_TEST_LAB_BUCKET` | test lab | Optional GCS bucket for results (defaults to `gs://test-lab-${GCP_PROJECT_ID}`) |
| `GOOGLE_SERVICES_INFO_PLIST_BASE64` | all jobs | Optional base64 of `GoogleService-Info.plist` for Firebase Analytics/Crashlytics |

### Setup steps

1. Create an Apple Developer account and App ID `com.babelwords.com`.
2. Generate an App Store Connect API key, download the `.p8` file, and add the three API key secrets above.
3. Create a private GitHub repo for `fastlane match` (e.g., `j272-glitch/babelwords-match`).
4. Add the match secrets (`MATCH_PASSWORD` and `MATCH_GIT_BASIC_AUTHORIZATION`).
5. The first time the archive or Firebase Test Lab job runs, `fastlane match` generates the distribution certificate and provisioning profile and stores them encrypted in the match repo.

See `fastlane/Fastfile` and `fastlane/Appfile` for the lane configuration. Remember to replace `YOUR_ORG`, `YOUR_TEAM_ID`, and `your-apple-id@example.com` in those files if they still contain placeholders.

## Notes

- AdMob on iOS uses `GADInterstitialAd` for both interstitial and rewarded aliases (the Android build had already unified rewarded onto the interstitial pipeline).
- App Open ads (`GADAppOpenAd`) are shown only after a warm resume of 5 seconds or more, with a 4-hour frequency cap persisted to `UserDefaults`.
- The microphone watchdog resets the mic state after 45 seconds if the JS heartbeat (`notifyMicActive`) stops.
- Universal Links are configured for `https://linguagt.com` via `BabelWords.entitlements`.

