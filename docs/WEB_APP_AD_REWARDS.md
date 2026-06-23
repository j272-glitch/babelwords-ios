# Web App Guide: Rewarding Hints from Ads

This guide is for the **web app** (linguagt.com) running inside the BabelWords
Android WebView. It explains exactly how to show ads and how to grant hints when
a user finishes a rewarded ad.

The native Android side is already done and verified in Firebase Test Lab. The
only thing the web app must do is **call the bridge** and **listen for events**.

---

## 1. The bridge in one picture

```
  Web app (linguagt.com JS)                 Native Android (AdBridge.kt)
  ─────────────────────────                 ───────────────────────────
  window.AdBridge.showRewarded()  ───────▶  shows the rewarded ad
                                            user watches the ad
  window.onAdBridgeEvent(          ◀──────  fires "rewardEarned" with amount
    "rewardEarned", "10")
        │
        ▼
  add 10 hints to the user
```

- The native side is exposed to JavaScript as **`window.AdBridge`**.
- The native side talks back to the web app by calling **`window.onAdBridgeEvent(eventType, data)`**.
- **Granting the actual hints is the web app's job.** Native only tells you the
  reward was earned and how much. It does not (and cannot) touch your hint balance.

> ⚠️ This bridge **only exists inside the Android app**. On a normal desktop or
> mobile browser, `window.AdBridge` is `undefined`. Always feature-detect (see §5).

---

## 2. Methods you can call (`window.AdBridge`)

| Method | Returns | What it does |
|---|---|---|
| `initialize()` | — | Preloads interstitial + rewarded ads. Call once on app start. |
| `isInitialized()` | `boolean` | Whether AdMob finished initializing. |
| `showRewarded()` | — | Shows the rewarded ad. Triggers `rewardEarned` if the user completes it. |
| `isRewardedReady()` | `boolean` | `true` if a rewarded ad is loaded and ready to show right now. |
| `showInterstitial()` | — | Shows a full-screen interstitial ad (no reward). |
| `isInterstitialReady()` | `boolean` | `true` if an interstitial is loaded. |
| `loadInterstitial()` | — | Manually preload an interstitial. |
| `getDiagnostics()` | `string` (JSON) | `{adMobInitialized, interstitialReady, rewardedReady, timestamp}`. |
| `logEvent(name)` | — | Writes a line to the native logcat (handy for debugging). |

---

## 3. Events you receive (`window.onAdBridgeEvent`)

Define one global handler. The native side calls it with `(eventType, data)`,
both **strings**.

| `eventType` | `data` | Meaning |
|---|---|---|
| `adMobInitialized` | `"true"` | AdMob ready. |
| `interstitialLoaded` | `""` | Interstitial ready to show. |
| `interstitialShown` | `""` | Interstitial impression registered. |
| `interstitialClosed` | `""` | Interstitial dismissed. |
| `interstitialFailed` | error message | Interstitial load/show error. |
| `rewardedLoaded` | `""` | Rewarded ad ready to show. |
| `rewardedShown` | `""` | Rewarded impression registered. |
| **`rewardEarned`** | **amount, e.g. `"10"`** | **User finished the ad — grant this many hints.** |
| `rewardedClosed` | `""` | Rewarded ad dismissed (fires whether or not a reward was earned). |
| `rewardedFailed` | error message / `"not_loaded"` / `"manager_not_ready"` | Rewarded load/show error. |

### The golden rule for hints

> **Grant hints only on `rewardEarned`.**
> Do **not** grant hints on `rewardedShown` or `rewardedClosed`. A user can
> close the ad early without finishing it — in that case you get
> `rewardedClosed` but **no** `rewardEarned`, and they should get nothing.

`data` for `rewardEarned` is the reward amount **as a string**. Parse it with
`parseInt`. If AdMob reports `0`, native sends a default of `30` (configured on
the AdMob ad unit), so you will always get a positive number.

---

## 4. Minimal working example

```html
<script>
  // 1) Listen for native events. Define this BEFORE you call any ad method.
  window.onAdBridgeEvent = function (eventType, data) {
    switch (eventType) {
      case 'rewardEarned': {
        const hints = parseInt(data, 10) || 0;
        if (hints > 0) {
          grantHints(hints);   // <-- YOUR function: add hints to the user
        }
        break;
      }
      case 'rewardedFailed':
        showToast('Ad could not be shown. Please try again.');
        reEnableHintButton();
        break;
      case 'rewardedClosed':
        // Ad closed. If no rewardEarned arrived, the user skipped it.
        reEnableHintButton();
        break;
      // (optional) handle the other events for UI state
    }
  };

  // 2) When the user taps "Watch ad for hints"
  function onWatchAdForHints() {
    if (!window.AdBridge) {
      showToast('Ads are only available in the app.');
      return;
    }
    if (window.AdBridge.isRewardedReady()) {
      window.AdBridge.showRewarded();
    } else {
      showToast('Ad is still loading, try again in a moment.');
    }
  }

  // 3) Preload ads once when the app is ready
  if (window.AdBridge) {
    window.AdBridge.initialize();
  }
</script>
```

### Why grant hints inside the handler, not after `showRewarded()`

`showRewarded()` returns immediately — it does **not** wait for the ad to finish.
The reward arrives **later**, asynchronously, through `onAdBridgeEvent`. So the
hint-granting logic must live in the event handler, never right after the
`showRewarded()` call.

---

## 5. Safe feature detection (web + app)

The same web code runs both in a normal browser and inside the app. Guard every
call so the website doesn't crash where the bridge is absent.

```js
function adsAvailable() {
  return typeof window.AdBridge !== 'undefined'
      && typeof window.AdBridge.showRewarded === 'function';
}

function canShowRewardedNow() {
  return adsAvailable() && window.AdBridge.isRewardedReady();
}
```

Use these to decide whether to even show the "Watch ad for hints" button.

---

## 6. Recommended UX flow

1. On app start: call `AdBridge.initialize()` (preloads ads).
2. Show the "Watch ad for hints" button only when `canShowRewardedNow()` is `true`
   (otherwise grey it out or hide it).
3. On tap: disable the button, call `AdBridge.showRewarded()`.
4. On `rewardEarned`: add the hints, update the on-screen counter, show a
   "You earned N hints!" confirmation.
5. On `rewardedClosed` **without** a preceding `rewardEarned`: re-enable the
   button, grant nothing.
6. On `rewardedFailed`: re-enable the button, tell the user to try again.

A new ad auto-preloads after each one closes, so the button can become available
again on its own shortly after.

---

## 7. Preventing abuse / double-granting

Because hints have value, protect against duplicate grants:

- **Debounce the button** — ignore taps while an ad is in flight (disable it in
  step 3 above).
- **Server-side ledger (recommended)** — when `rewardEarned` fires, record the
  grant on your backend rather than only in `localStorage`, so a user can't reset
  the page to replay rewards. Optionally enforce a daily cap there.
- **One reward per show** — each `showRewarded()` produces at most one
  `rewardEarned`. Don't loop or auto-retry `showRewarded()` on success.

> For the highest level of fraud protection AdMob also offers
> **Server-Side Verification (SSV)** callbacks (AdMob calls your backend
> directly). That's optional and a larger setup — the in-app `rewardEarned`
> event above is sufficient for most cases.

---

## 8. How to verify it's working

**Quick check (logcat):** while connected to a device with `adb logcat`, look for
this sequence after watching a rewarded ad:

```
AdMobManager: ✅ Rewarded shown
AdMobManager: 💰 Reward earned: 10
AdBridge:     fireEvent: rewardEarned data=10
AdMobManager: Rewarded dismissed
```

If you see `💰 Reward earned` and `fireEvent: rewardEarned`, the native side did
its job — any missing hints after that are a **web-app handler** problem.

**End-to-end check:** sideload the APK on a real phone, note your hint count,
watch a rewarded ad to completion, and confirm the count goes up by the reward
amount.

> Note: Firebase Test Lab confirms the **native** side (`rewardEarned: 10` fired
> correctly), but it can't verify your hint counter because it doesn't log into a
> real account. The web handler must be tested on a real device or in the app.

---

## 9. Common mistakes

| Symptom | Likely cause |
|---|---|
| Hints never increase | `window.onAdBridgeEvent` not defined, or no `case 'rewardEarned'`. |
| Hints increase even when user skips the ad | Granting on `rewardedShown`/`rewardedClosed` instead of `rewardEarned`. |
| Works in app, crashes on website | Missing `window.AdBridge` feature check (see §5). |
| Double hints | Granting both in the handler **and** after `showRewarded()`, or not debouncing the button. |
| Button never enables | Not calling `initialize()`, or not checking `isRewardedReady()` before showing. |
| `rewardEarned` arrives but amount is wrong | Reading `data` as a number directly — it's a **string**, use `parseInt(data, 10)`. |
