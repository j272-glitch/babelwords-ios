# LinguaVibe iOS App

iOS WKWebView-based real-time speech translation app wrapping linguagt.com.

## Features

- **WKWebView wrapper** for linguagt.com
- **AdMob integration** (Interstitial + Rewarded ads)
- **Native ad preloading** - eliminates WebView bridge latency
- **JavaScript bridges** - Android-compatible interface (`window.AndroidAdBridge`, `window.AndroidBridge`)
- **Microphone permission** handling for speech recognition
- **Deep linking** - Universal Links for linguagt.com domain
- **Foreground recovery** after ads
- **GDPR consent** via Google UMP
- **Exponential backoff** retry for failed ad loads
- **45-minute ad expiration** tracking with 40-minute refresh

## Requirements

- iOS 14.0+
- Xcode 15.0+
- CocoaPods

## Setup

1. **Open Terminal** and navigate to the project directory:
   ```bash
   cd LinguaVibe
   ```

2. **Install CocoaPods dependencies**:
   ```bash
   pod install
   ```

3. **Open the workspace** (not the project):
   ```bash
   open LinguaVibe.xcworkspace
   ```

4. **Configure signing**:
   - Select the LinguaVibe target
   - Go to Signing & Capabilities
   - Select your development team
   - Update the bundle identifier if needed

5. **Universal Links** (optional):
   - Add `apple-app-site-association` file to linguagt.com:
   ```json
   {
     "applinks": {
       "apps": [],
       "details": [
         {
           "appID": "TEAM_ID.com.lingualink.linguagt",
           "paths": ["*"]
         }
       ]
     }
   }
   ```

## Project Structure

```
LinguaVibe/
├── AppDelegate.swift          # App lifecycle, AdMob SDK init
├── SceneDelegate.swift        # Scene lifecycle, deep links
├── MainViewController.swift   # WKWebView, permission handling
├── Ads/
│   ├── AdMobBridge.swift      # JS-native ad interface
│   └── AdPreloadManager.swift # Native ad preloading
├── Bridge/
│   └── WebAppBridge.swift     # General JS-native bridge
├── Utils/
│   └── Logger.swift           # Unified logging
├── Resources/
│   ├── LaunchScreen.storyboard
│   └── Assets.xcassets
├── Info.plist                 # Permissions, AdMob ID
└── LinguaVibe.entitlements    # Universal Links
```

## JavaScript Interface

The app injects Android-compatible JavaScript interfaces for the web app:

### AndroidAdBridge (→ iOSAdBridge)
```javascript
window.AndroidAdBridge.loadInterstitial(placementId)
window.AndroidAdBridge.showInterstitial(placementId)
window.AndroidAdBridge.preloadInterstitial()
window.AndroidAdBridge.loadRewarded(placementId)
window.AndroidAdBridge.showRewarded(placementId)
window.AndroidAdBridge.preloadRewarded()
```

### AndroidBridge (→ iOSBridge)
```javascript
window.AndroidBridge.showInterstitialAd()
window.AndroidBridge.showRewardedAd()
window.AndroidBridge.isRewardedAdReady()
window.AndroidBridge.logEvent(eventName)
window.AndroidBridge.trackTranslation(count)
window.AndroidBridge.grantPremiumAccess(minutes)
```

### Callbacks from Native
```javascript
window.onInterstitialReady()
window.onInterstitialLoaded()
window.onInterstitialClosed()
window.onInterstitialLoadFailed(error)

window.onRewardedReady()
window.onRewardedLoaded()
window.onRewardedClosed()
window.onRewardedLoadFailed(error)
window.onRewardEarned(type, amount)
window.onRewardedComplete(type, amount)

window.onNativeAdReady(type) // 'interstitial' or 'rewarded'
```

## WebView Detection

The app injects detection flags for the web app:
```javascript
window.iOSWebView = true
window.__iOSWKWebView = true
window.AndroidAdBridge // Also exists for compatibility
window.AndroidBridge   // Also exists for compatibility
```

## Ad Unit IDs

| Ad Type | Unit ID |
|---------|---------|
| Interstitial | ca-app-pub-9991891515643313/5076005693 |
| Rewarded | ca-app-pub-9991891515643313/6313049833 |
| App ID | ca-app-pub-9991891515643313~7514450861 |

## Key Differences from Android

| Feature | Android | iOS |
|---------|---------|-----|
| WebView | Android WebView | WKWebView |
| JS Bridge | `@JavascriptInterface` | `WKScriptMessageHandler` |
| Ad SDK | Google Mobile Ads (Kotlin) | Google Mobile Ads (Swift) |
| Consent | UMP Android | UMP iOS |
| Permissions | Runtime permissions | Info.plist + AVAudioSession |
| Deep Links | Intent filters | Universal Links + URL schemes |

## Troubleshooting

### Ads not loading
1. Check network connection
2. Verify AdMob App ID in Info.plist
3. Check console logs for error messages
4. Ensure ATT permission is requested (iOS 14+)

### WebView not loading
1. Check App Transport Security settings
2. Verify URL is correct
3. Check network connectivity

### Microphone not working
1. Check NSMicrophoneUsageDescription in Info.plist
2. Verify AVAudioSession recordPermission status
3. Request permission before web content needs it

## License

Proprietary - LinguaLink
