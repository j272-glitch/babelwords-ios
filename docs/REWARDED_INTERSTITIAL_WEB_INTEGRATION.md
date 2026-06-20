# Rewarded Interstitial — Web App Integration

Suggested code for the production web app (linguagt.com) to use the new
**rewarded-interstitial** ad type now wired into the Android container.

## Important: bridge object name

The native bridge is registered as **`window.AdBridge`** (see
`WebViewConfig.kt` → `addJavascriptInterface(adBridge, "AdBridge")`).

Call `window.AdBridge.*`, **not** `window.NativeAdBridge`. The repo's
`index.html` landing page uses the old `NativeAdBridge` name (with one spot
mixing `AdBridge`) and is out of sync with the real registration — do not copy
that naming into the production app.

## Native methods now available on `window.AdBridge`

| Method | Returns | Purpose |
| --- | --- | --- |
| `initialize()` | void | Preloads interstitial, rewarded, **and rewarded-interstitial** |
| `loadRewardedInterstitial()` | void | Manually warm up a rewarded-interstitial |
| `showRewardedInterstitial()` | void | Show it (auto-loads + retries if none cached) |
| `isRewardedInterstitialReady()` | boolean | True if one is cached and ready |
| `getDiagnostics()` | JSON string | Now includes `rewardedInterstitialReady` |

## 1. Show / preload helpers

```javascript
// Show a rewarded-interstitial (full-screen ad that grants a reward).
// Falls back gracefully in a plain browser.
function showRewardedInterstitial() {
    if (window.AdBridge && window.AdBridge.isRewardedInterstitialReady) {
        if (window.AdBridge.isRewardedInterstitialReady()) {
            window.AdBridge.showRewardedInterstitial();
        } else {
            // Not cached yet — kick off a load; it will auto-fire
            // rewardedInterstitialLoaded when ready.
            window.AdBridge.loadRewardedInterstitial();
            console.log('Rewarded interstitial loading — try again shortly.');
        }
    } else {
        console.log('Rewarded interstitial only available in the Android app.');
    }
}

// Optional: warm one up ahead of time (it also preloads automatically on init).
function preloadRewardedInterstitial() {
    if (window.AdBridge && window.AdBridge.loadRewardedInterstitial) {
        window.AdBridge.loadRewardedInterstitial();
    }
}
```

## 2. Event handling

Add these cases to your existing `window.onAdBridgeEvent` switch. All
native → web events flow through this single callback:

```javascript
window.onAdBridgeEvent = function (eventType, data) {
    switch (eventType) {
        // ... your existing cases ...

        case 'rewardedInterstitialLoaded':
            console.log('✅ Rewarded interstitial ready');
            break;

        case 'rewardedInterstitialShown':
            console.log('🎉 Rewarded interstitial shown — impression counted');
            break;

        case 'rewardedInterstitialClosed':
            console.log('✅ Rewarded interstitial closed');
            // The next one auto-preloads natively; nothing required here.
            break;

        case 'rewardedInterstitialFailed':
            console.error('❌ Rewarded interstitial failed:', data);
            break;

        // NOTE: the reward itself still arrives via the shared 'rewardEarned'
        // event (data = amount string, e.g. "30"). Your existing rewardEarned
        // handler already covers both rewarded and rewarded-interstitial —
        // no change needed there.
    }
};
```

## Full event contract (active stack)

| Event | Data | Meaning |
| --- | --- | --- |
| `adMobInitialized` | `"true"` | SDK + bridge ready |
| `interstitialLoaded` / `Shown` / `Closed` / `Failed` | — / — / — / error | Interstitial lifecycle |
| `rewardedLoaded` / `Shown` / `Closed` / `Failed` | — / — / — / error | Rewarded lifecycle |
| `rewardedInterstitialLoaded` / `Shown` / `Closed` / `Failed` | — / — / — / error | Rewarded-interstitial lifecycle |
| `rewardEarned` | amount string, e.g. `"30"` | Reward granted (shared by rewarded **and** rewarded-interstitial) |

## Notes

- `initialize()` already preloads all three ad types, so explicit
  `loadRewardedInterstitial()` calls are optional.
- After an ad is shown/dismissed, the native side auto-preloads the next one.
- In a plain browser (no `window.AdBridge`), all helpers no-op safely so the
  same code runs everywhere.
