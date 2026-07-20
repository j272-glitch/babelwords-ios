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

## Notes

- AdMob on iOS uses `GADInterstitialAd` for both interstitial and rewarded aliases (the Android build had already unified rewarded onto the interstitial pipeline).
- App Open ads (`GADAppOpenAd`) are shown only after a warm resume of 5 seconds or more, with a 4-hour frequency cap persisted to `UserDefaults`.
- The microphone watchdog resets the mic state after 45 seconds if the JS heartbeat (`notifyMicActive`) stops.
- Universal Links are configured for `https://linguagt.com` and `https://gtlingua.com` via `LinguaVibe.entitlements`.
