# BabelWords

Real-time speech translation, now as a native iOS app.

This repository contains the **native iOS wrapper** for the BabelWords web app, hosted at `https://linguagt.com`. It embeds the web app in a `WKWebView` and provides native iOS integrations for ads, subscriptions, analytics, and consent.

## Quick start

1. `cd ios`
2. `xcodegen generate` (if you changed `project.yml`)
3. `pod install`
4. `open BabelWords.xcworkspace`
5. Build and run on an iOS 26+ simulator or device.

See [`ios/README.md`](ios/README.md) and [`replit.md`](replit.md) for full setup and architecture details.

## What's here

- `ios/` — the native iOS project (UIKit, Swift, WKWebView, AdMob, StoreKit, Firebase)
- `docs/` — web-app integration guides and best practices
- `.github/workflows/ios-build.yml` — GitHub Actions CI/CD for build, test, and archive

## Migration from Android

The former Android Gradle project has been removed and replaced with the iOS project above. The web app's JavaScript bridge contract remains the same, so the web layer requires no changes.
