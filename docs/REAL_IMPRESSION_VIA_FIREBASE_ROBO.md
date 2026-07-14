# How a Real Ad Impression Was Served Through Firebase Robo

A detailed, step-by-step account of how the BabelWords app served a **genuinely
rendered ad impression** — both an interstitial and a rewarded ad — during an
automated **Firebase Test Lab Robo test**, captured on video and proven in the
logs.

---

## First, what "real impression" means here

There are two different meanings of "real," and it's important not to confuse them:

| Term | What happened in this test |
|---|---|
| **Real *rendered* impression** ✅ | The ad **actually appeared on screen**, fullscreen, and AdMob's SDK fired `onAdShowedFullScreenContent` — the official "impression registered" signal. This is real and is what the video shows. |
| **Production / revenue impression** ❌ | This was **not** a billable impression against your real ad units. Under Test Lab the app deliberately uses **Google's sample ad units**, so no real money/inventory was involved. |

So: a **real ad genuinely rendered and registered an impression** — using test ad
units, on purpose. Serving your **production** ad units under an automated test
would risk AdMob flagging it as **invalid traffic**, which can suspend the account.
That's why the code swaps to sample units inside Test Lab.

---

## The cast: who did what

| Component | Role in serving the impression |
|---|---|
| **Firebase Test Lab** | Cloud device farm. Installed the APK on a real Android 13 phone, ran the test, recorded video + logcat. |
| **Robo test** | Google's automated UI crawler. Its only essential job here was to **launch the app and keep it in the foreground** while it explored. It did **not** tap a "watch ad" button. |
| **AdMobManager (app code)** | Detected the test environment, preloaded ads from sample units, and **auto-showed** them. This is what actually served the impression. |
| **AdMob SDK** | Fetched the test ad creative and rendered it fullscreen, firing the impression callback. |

> **Key insight:** Robo did **not** "find" the ad by crawling. The web UI lives
> inside a WebView, which Robo can't meaningfully crawl, and there's no native
> button for it to tap. The impression happened because the app's own
> **Test-Lab-only auto-show logic** fired the moment the ad loaded. Robo's
> contribution was simply *launching the app and keeping it onscreen*.

---

## The end-to-end chain (what made the impression happen)

```
 Robo launches the app
        │
        ▼
 MainActivity.onCreate() → constructs AdMobManager
        │
        ▼
 AdMobManager detects Firebase Test Lab
   (Settings.System "firebase.test.lab" == "true")
        │
        ▼
 Because it's Test Lab → use Google SAMPLE ad units
   (interstitial 3940256099942544/1033173712,
    rewarded   3940256099942544/5224354917)
        │
        ▼
 init { preloadInterstitial(); preloadRewarded() }   ← load starts immediately
        │
        ▼
 AdMob SDK fetches the test creative → onAdLoaded fires
        │
        ▼
 maybeAutoShowInterstitial()  ← gated by isTestLab, bounded retries
        │
        ▼
 ad.show(activity)  on the main thread, into a "showable" Activity
        │
        ▼
 onAdShowedFullScreenContent()  ← *** REAL RENDERED IMPRESSION ***
        │
        ▼
 onAdDismissed → preload next + auto-show the rewarded ad
        │
        ▼
 Rewarded shows → reward callback fires → reward earned → dismissed → reload
```

---

## Why each safeguard mattered for serving the impression

1. **Test Lab detection** (`firebase.test.lab` system setting): the single switch
   that turns on auto-show and selects sample ad units. `true` only on Test Lab
   devices, never on a real phone — so this path can never run in production.
2. **Preload on startup:** the ad was already loading before anything else, so it
   was ready within seconds of launch — Robo didn't need to do anything.
3. **Auto-show gated by `isTestLab`:** a real user is never auto-shown an ad; the
   crawler is, so the video can capture it.
4. **Guard set only on real render:** `hasAutoShown*` flips inside
   `onAdShowedFullScreenContent` (true render), not when `show()` is called. A
   transient show failure could therefore retry instead of being permanently
   blocked.
5. **Bounded retries (`MAX_AUTO_SHOW_ATTEMPTS = 3`):** prevents any infinite
   show loop if rendering kept failing.
6. **`showableActivity()` check:** only shows into an Activity that isn't
   finishing/destroyed — avoids a crash that would have aborted the impression.
7. **Reload after dismiss:** the next ad was preloaded the instant the first
   closed, which is why a second `✅ Rewarded loaded` appears at the end.

---

## The proof: actual log timeline from the artifact

From the Test Lab artifact `logcat.txt` (device-local timestamps; the run's wall
clock in `firebase-run.log` was 00:44–00:47 UTC). The matrix outcome was
**Passed** on `MediumPhone.arm-33-en_US-portrait` (Android 13), test time 101s.

```
17:44:59.783  FirebaseCrashlytics  Initializing Firebase Crashlytics 19.3.0 for com.babelwords.com
17:45:00.115  AdMobManager  Loading interstitial…
17:45:00.116  AdMobManager  Loading rewarded ad…
17:45:05.799  AdMobManager  ✅ Interstitial loaded
17:45:05.800  AdMobManager  🧪 Test Lab: auto-showing interstitial (attempt 1)
17:45:05.861  AdMobManager  ✅ Interstitial shown          ← REAL impression #1 (interstitial)
17:45:07.010  AdMobManager  ✅ Rewarded loaded
17:45:11.336  AdMobManager  Interstitial dismissed
17:45:11.337  AdMobManager  🧪 Test Lab: auto-showing rewarded (attempt 1)
17:45:11.371  AdMobManager  ✅ Rewarded shown              ← REAL impression #2 (rewarded)
17:45:19.325  AdMobManager  💰 Reward earned: 10           ← reward callback fired
17:45:35.540  AdMobManager  Rewarded dismissed
17:45:37.636  AdMobManager  ✅ Rewarded loaded             ← next ad auto-preloaded
```

### Reading the timeline

- **~5.7s from launch to first impression** (`00.115` load start → `05.861`
  shown). That's the preload + network fetch of the test creative.
- **`✅ Interstitial shown`** is the moment of the real render — this is exactly
  what the recorded `video.mp4` displays.
- The interstitial **auto-shows on load**; the rewarded **auto-shows after the
  interstitial is dismissed** (sequenced, not stacked simultaneously).
- **`💰 Reward earned: 10`** proves the rewarded ad ran to completion and the
  reward callback fired — the same signal that would grant hints to a real user.
- The final **`✅ Rewarded loaded`** proves the reload-after-dismiss safeguard
  worked: the app is immediately ready to serve again.

---

## What the video.mp4 shows

The artifact's `video.mp4` is the visual counterpart of the log above: the app
launches, the interstitial test ad fills the screen, it dismisses, the rewarded
test ad plays, and it dismisses. That footage + the `✅ … shown` log lines
together are the proof that a **real, rendered impression** was served — not just
an ad that quietly "loaded" in the background.

---

## How to reproduce / re-verify

1. Run the GitHub Actions workflow with **Run Firebase Test Lab** enabled (needs
   Blaze billing + `GCP_SA_KEY` / `GCP_PROJECT_ID` secrets).
2. Download the `firebase-testlab-*` artifact.
3. Open `firebase-run.log` → confirm `OUTCOME: Passed`.
4. `grep` `logcat.txt` for the impression markers:
   ```
   rg -n "✅ Interstitial shown|✅ Rewarded shown|💰 Reward earned" logcat.txt
   ```
5. Watch `video.mp4` to see the ads render.

If you see both `✅ … shown` lines plus `💰 Reward earned`, a real rendered
impression (and reward) was served end-to-end.

---

## Honest limitations

- **Test ad units only.** No production impression, no revenue, by design (§ "what
  real impression means").
- **Auto-show is Test-Lab-only.** It exists purely so an automated test can prove
  rendering. A real user never gets an ad shoved at them this way.
- **Reward grant is native-side only.** The log proves the reward *callback* fired
  (`💰 Reward earned: 10`); whether the **web app** then credits hints is verified
  separately (see `WEB_APP_AD_REWARDS.md`), because Robo doesn't log into a real
  account.

---

## Related docs

- `FIREBASE_TEST_LAB_SETUP.md` — how the Test Lab job + auto-show are wired in CI.
- `AD_SERVING_BEST_PRACTICES.md` — the general patterns behind this behavior.
- `WEB_APP_AD_REWARDS.md` — how the web app turns `rewardEarned` into hints.
