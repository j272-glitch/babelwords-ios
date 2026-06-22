# Web App Update Guide — Match the BabelWords Android Container

**Audience:** whoever maintains the production web app at **linguagt.com**.

**Why:** the Android container (`com.babelwords.app`) was updated. The web app
talks to the container through a JavaScript bridge, so the web side has to be
kept in sync or ad calls will silently fail. This guide lists exactly what to
change on the web.

> The web app lives in a **separate repo** from this Android project. None of
> the changes below are made here — this file is the spec to hand to the web
> team.

---

## TL;DR — what changed on Android

1. **Rewarded-interstitial ads were removed.** The container now supports only
   **Interstitial** and **Rewarded** ads. Any web call to a rewarded-interstitial
   method now hits a method that no longer exists (`undefined`).
2. **Mediation adapters were added** (Unity + Liftoff/Vungle). This needs matching
   lines in the web-hosted **`app-ads.txt`**.
3. **Package/domain are settled** as `com.babelwords.app` / `linguagt.com`. The
   web-hosted **`assetlinks.json`** must list the Play app-signing fingerprint.

---

## 1. Bridge basics (confirm the web uses these names)

The native bridge is injected as **`window.AdBridge`**. Events come back through a
**single dispatcher**, `window.onAdBridgeEvent(eventType, data)` — not one global
function per event.

```javascript
// Detect the Android container
function inAndroidApp() {
  return typeof window.AdBridge !== 'undefined';
}

// Single entry point for ALL native ad events
window.onAdBridgeEvent = function (eventType, data) {
  switch (eventType) {
    case 'rewardEarned': {
      // data is the reward amount as a STRING, e.g. "30"
      const minutes = parseInt(data, 10) || 30;
      grantUnlimitedTranslations(minutes * 60 * 1000);
      break;
    }
    case 'interstitialClosed':
      // resume app flow
      break;
    case 'rewardedClosed':
      // user dismissed (may or may not have earned — rewardEarned fires separately)
      break;
    // ...handle the rest from the table below
    default:
      break;
  }
};
```

> ⚠️ Older docs in this repo (`WEB_APP_INTEGRATION_GUIDE.md`) and the legacy
> `index.html` reference `window.AndroidAdBridge` / `window.NativeAdBridge` and
> per-event callbacks like `window.onRewardEarned(type, amount)`. **That naming is
> stale — do not use it.** The real container only provides `window.AdBridge` and
> `window.onAdBridgeEvent`.

---

## 2. REMOVE all rewarded-interstitial usage

Search the web app for any of these and delete them — the native side no longer
implements them:

**Methods that no longer exist:**

```javascript
window.AdBridge.loadRewardedInterstitial();    // ❌ gone
window.AdBridge.showRewardedInterstitial();    // ❌ gone
window.AdBridge.isRewardedInterstitialReady(); // ❌ gone
```

**Events that will never fire again** — remove these cases from
`onAdBridgeEvent`:

```
rewardedInterstitialLoaded   ❌
rewardedInterstitialShown    ❌
rewardedInterstitialClosed   ❌
rewardedInterstitialFailed   ❌
```

**Diagnostics:** `window.AdBridge.getDiagnostics()` no longer includes the
`rewardedInterstitialReady` field — remove any code that reads it.

If the web app used a rewarded-interstitial somewhere, replace it with a regular
**rewarded** ad (same reward outcome) or an **interstitial** (no reward).

---

## 3. Current bridge contract (the full, correct surface)

### Methods on `window.AdBridge`

| Method | Returns | Purpose |
| --- | --- | --- |
| `initialize()` | void | Preloads interstitial + rewarded; fires `adMobInitialized` |
| `loadInterstitial()` | void | Warm up an interstitial |
| `showInterstitial()` | void | Show it (loads first if none cached) |
| `isInterstitialReady()` | boolean | True if an interstitial is cached |
| `showRewarded()` | void | Show a rewarded ad (loads first if none cached) |
| `isRewardedReady()` | boolean | True if a rewarded ad is cached |
| `isInitialized()` | boolean | True once the ad manager is up |
| `getDiagnostics()` | JSON string | `{ adMobInitialized, interstitialReady, rewardedReady, timestamp }` |
| `testShowInterstitial()` | void | QA helper — force-shows an interstitial |
| `logEvent(eventName)` | void | QA helper — writes a tagged line to the native log |

> Note: these `is*` methods return real **booleans** in the current container, not
> the `'true'`/`'false'` strings the old guide described. Compare with `===
> true` (or just use truthiness) and don't rely on string comparison.

### Events delivered via `window.onAdBridgeEvent(eventType, data)`

| eventType | data | Meaning |
| --- | --- | --- |
| `adMobInitialized` | `"true"` | Bridge ready |
| `interstitialLoaded` | — | Interstitial cached and ready |
| `interstitialShown` | — | Interstitial impression registered |
| `interstitialClosed` | — | Interstitial dismissed |
| `interstitialFailed` | error string | Load/show error (e.g. `not_loaded`, `manager_not_ready`) |
| `rewardedLoaded` | — | Rewarded ad cached and ready |
| `rewardedShown` | — | Rewarded impression registered |
| `rewardEarned` | amount string (e.g. `"30"`) | **User earned the reward — grant it here** |
| `rewardedClosed` | — | Rewarded dismissed |
| `rewardedFailed` | error string | Rewarded load/show error |

### Minimal required handling

```javascript
window.onAdBridgeEvent = function (eventType, data) {
  if (eventType === 'rewardEarned') {
    const minutes = parseInt(data, 10) || 30;
    grantUnlimitedTranslations(minutes * 60 * 1000);
  }
  // interstitialClosed / rewardedClosed: resume normal flow
};
```

---

## 4. Update `app-ads.txt` for mediation

The container added these mediation adapters. The publisher's **`app-ads.txt`**
(served at `https://linguagt.com/app-ads.txt`) must include the corresponding
authorized-seller lines so the new demand can bid.

- Keep the existing Google AdMob line:
  ```
  google.com, pub-9991891515643313, DIRECT, f08c47fec0942fa0
  ```
- Add the lines published by each mediation partner you enable (copy the exact,
  current entries from each network's documentation — IDs change over time):
  - **Unity Ads**
  - **Liftoff / Vungle**

After editing, confirm `https://linguagt.com/app-ads.txt` is reachable and that
the Google AdMob console "app-ads.txt" check shows **verified**. Crawls can take
up to ~24 hours to refresh.

> The actual networks only serve once you also enable Unity and Liftoff/Vungle as
> **bidding** sources in the AdMob console — that's an operational step done after
> the app is approved, not a web code change.

---

## 5. Verify `assetlinks.json` (App Links)

The container verifies Android App Links for `linguagt.com`. The web-hosted
`https://linguagt.com/.well-known/assetlinks.json` must list the **Play
app-signing** SHA-256 fingerprint (the cert Google re-signs installs with):

```
15:5D:00:27:77:20:0B:EC:09:0A:8B:65:46:6C:D5:44:1D:ED:96:6A:4B:96:D8:E3:F4:FD:67:49:FE:24:5D:1B
```

Package name must be `com.babelwords.app`. (Listing the upload key
`D4:1D:…:AB:8B` as a second fingerprint is fine for sideloaded test builds, but
the `15:5D:…` one is the one that matters for Play installs.)

---

## 6. Runtime self-check — confirm the web matches native

Drop this into the web app to confirm, at runtime, that the loaded
`window.AdBridge` exposes exactly the surface this guide expects. It logs a
clear PASS/FAIL so you catch drift between web and native (for example, a
leftover rewarded-interstitial call, or an old container that still has it).

The expected method list mirrors the native `AdBridge.kt` `@JavascriptInterface`
methods one-to-one.

```javascript
function confirmAdBridgeMatchesNative() {
  if (typeof window.AdBridge === 'undefined') {
    console.warn('[AdBridge] Not in Android container — skipping native check.');
    return { inApp: false };
  }

  // Must EXIST on window.AdBridge (matches AdBridge.kt)
  const expected = [
    'initialize',
    'loadInterstitial',
    'showInterstitial',
    'isInterstitialReady',
    'showRewarded',
    'isRewardedReady',
    'isInitialized',
    'getDiagnostics',
    'testShowInterstitial',
    'logEvent',
  ];

  // Must NOT exist (rewarded-interstitial was removed)
  const removed = [
    'loadRewardedInterstitial',
    'showRewardedInterstitial',
    'isRewardedInterstitialReady',
  ];

  const missing = expected.filter((m) => typeof window.AdBridge[m] !== 'function');
  const stale = removed.filter((m) => typeof window.AdBridge[m] === 'function');

  // Event dispatcher must be wired
  const dispatcherOk = typeof window.onAdBridgeEvent === 'function';

  // getDiagnostics must NOT report rewardedInterstitialReady anymore
  let diagOk = true;
  try {
    const diag = JSON.parse(window.AdBridge.getDiagnostics());
    diagOk = !('rewardedInterstitialReady' in diag);
  } catch (e) {
    diagOk = false;
  }

  const pass = missing.length === 0 && stale.length === 0 && dispatcherOk && diagOk;

  if (pass) {
    console.log('[AdBridge] ✅ Web matches native bridge contract.');
  } else {
    console.error('[AdBridge] ❌ Mismatch with native bridge:', {
      missingMethods: missing,             // native expects these but web/container lacks them
      staleRewardedInterstitial: stale,    // container still exposes removed methods (old build)
      onAdBridgeEventMissing: !dispatcherOk,
      diagnosticsStillHasRewardedInterstitial: !diagOk,
    });
  }

  return { inApp: true, pass, missing, stale, dispatcherOk, diagOk };
}

// Run after the container has injected the bridge (e.g. on load, or after
// the first `adMobInitialized` event).
window.addEventListener('load', confirmAdBridgeMatchesNative);
```

What the results mean:
- **`missingMethods` non-empty** → the web app (or an older container) is missing a
  method this guide promises. If running in a current container this should be empty.
- **`staleRewardedInterstitial` non-empty** → the device is running an **old**
  container that still has rewarded-interstitial. The web app should not call those
  methods; treat them as unavailable.
- **`onAdBridgeEventMissing`** → the web app never defined `window.onAdBridgeEvent`,
  so it will receive no ad callbacks. Define it (see Section 1).
- **`diagnosticsStillHasRewardedInterstitial`** → old container; ignore that field.

Keep this check in a debug/QA build at minimum; it's the fastest way to confirm the
web app and the native AdBridge agree before shipping.

---

## 7. Done checklist

- [ ] No remaining `*RewardedInterstitial*` method calls in the web app
- [ ] No `rewardedInterstitial*` cases left in `onAdBridgeEvent`
- [ ] Web app uses `window.AdBridge` + `window.onAdBridgeEvent` (not the old names)
- [ ] `rewardEarned` grants the reward; `data` parsed as a number
- [ ] `app-ads.txt` updated with Unity + Liftoff/Vungle lines and verified
- [ ] `assetlinks.json` lists the `15:5D:…` fingerprint for `com.babelwords.app`
- [ ] `confirmAdBridgeMatchesNative()` logs ✅ PASS inside the container (Section 6)
