# Ad Serving Best Practices

Lessons distilled from the **BabelWords** Android AdMob integration, written to be
applied to the **LinguaVibe** project. Both are WebView-wrapped translation apps
that monetize with AdMob **interstitial + rewarded** ads (no banners) and bridge
events to a web app. These are the patterns that made ad serving fast, reliable,
and crash-free in Firebase Test Lab.

> Scope: this is about **how to serve ads well** — preloading, lifecycle safety,
> reward integrity, consent, and testing. It is framework-agnostic in spirit; the
> examples are Kotlin/AdMob but the principles port directly.

---

## 1. Preload early, show instantly

**Do:** load the next interstitial and rewarded ad **at startup**, before the user
ever asks for one. Showing a full-screen ad should be instant — never make the
user wait on a network load.

```kotlin
init {
    preloadInterstitial()
    preloadRewarded()
}
```

**Why it matters:** the web bridge already adds latency. If you also load the ad
on demand, the user taps "watch ad" and stares at nothing for 1–3 seconds, then
many give up. Preloading removes that delay entirely.

---

## 2. Always reload the next ad after one finishes

AdMob full-screen ad objects are **single-use**. After an ad shows, it's dead —
you must load a fresh one. Reload in **every** terminal callback:

```kotlin
override fun onAdDismissedFullScreenContent() {
    eventCallback("interstitialClosed", null)
    preloadInterstitial()          // reload after dismiss
}
override fun onAdFailedToShowFullScreenContent(error: AdError) {
    interstitialAd = null
    preloadInterstitial()          // reload after a failed show too
}
```

Also null the reference the moment it's shown (`onAdShowedFullScreenContent`) so
you never accidentally show a spent ad.

**Why:** the #1 cause of "ad only works once" bugs is forgetting to reload after
dismiss or after a show failure.

---

## 3. Guard against duplicate loads

A double `preload()` call (e.g. bridge + lifecycle both firing) wastes requests
and can trip AdMob's rate limits. Guard with a "loading" flag **and** a "already
loaded" check:

```kotlin
fun preloadInterstitial() {
    if (interstitialAd != null || loadingInterstitial) return
    loadingInterstitial = true
    // …load…
}
```

Reset the flag in **both** `onAdLoaded` and `onAdFailedToLoad`.

---

## 4. Never show an ad into a dead Activity

Showing a full-screen ad while the Activity is finishing/destroyed is a classic
crash. Always resolve a **safe** Activity first:

```kotlin
private fun showableActivity(): Activity? {
    val activity = context as? Activity ?: return null
    if (activity.isFinishing || activity.isDestroyed) return null
    return activity
}
```

And do all show/load UI work on the main thread (`activity.runOnUiThread { … }`).

---

## 5. Reward integrity — grant only on the reward callback

For rewarded ads, the reward fires through a **dedicated callback**, separate from
"shown" and "closed." Grant the reward **only** there:

```kotlin
ad.show(activity) { rewardItem ->
    val amount = rewardItem.amount.takeIf { it > 0 } ?: DEFAULT_REWARD
    eventCallback("rewardEarned", amount.toString())
}
```

Rules that protect you from giving away free value:
- **Don't** grant on `rewardedShown` or `rewardedClosed` — a user can close early.
- Provide a sensible **default amount** if AdMob reports `0`.
- One `show()` → at most one reward. Don't loop/retry `show()` on success.
- For high-value rewards, consider AdMob **Server-Side Verification (SSV)** so the
  reward is confirmed by AdMob → your backend, not just the client.

See the companion doc `WEB_APP_AD_REWARDS.md` for the web side of this contract.

---

## 6. Decouple native from the web app with an event bridge

Keep the ad manager dumb about business logic. It emits **named events**; the web
app decides what they mean (show a spinner, grant hints, update UI):

```
interstitialLoaded / interstitialShown / interstitialClosed / interstitialFailed
rewardedLoaded / rewardedShown / rewardEarned / rewardedClosed / rewardedFailed
```

**Why:** the same web app runs in a browser (no ads) and in the app (ads). A clean
event contract means the web team integrates once and the native side can evolve
without breaking them. Keep the event names **identical** on both sides and
documented in one place.

---

## 7. Handle consent (GDPR/UMP) before requesting personalized ads

> ⚠️ **Gap to close for production.** The current BabelWords manager requests ads
> with a plain `AdRequest.Builder().build()` and does **not** run Google's UMP
> consent flow. For EU/UK users and Play policy compliance, LinguaVibe should add
> it.

Best practice:
1. On startup, run the **UMP** (`UserMessagingPlatform`) consent flow to gather or
   confirm consent.
2. Only initialize the Mobile Ads SDK / request ads **after** consent is resolved.
3. When consent is not granted, request **non-personalized ads** (`npa=1`).
4. Treat `UNKNOWN` consent as "request non-personalized now" rather than blocking
   ads forever — don't let an unresolved consent state silently kill revenue.
5. Propagate the consent result to your preload logic so the first preloaded ad
   already respects it.

---

## 8. Set guards on *real* render, not on the attempt

When you track "have I already done X" state, flip the flag in the callback that
fires on an **actual render** (`onAdShowedFullScreenContent`), not when you *call*
`show()`. A `show()` can fail; if you set the guard too early, a transient failure
permanently blocks the feature.

```kotlin
override fun onAdShowedFullScreenContent() {
    hasShown = true          // only now is it truly shown
}
```

Pair this with **bounded retries** so a repeatedly-failing show can't loop forever:

```kotlin
if (attempts >= MAX_ATTEMPTS) { Log.w(TAG, "gave up"); return }
attempts++
```

---

## 9. Keep test traffic out of production ad units

Automated tests (Firebase Test Lab, CI device farms, Appium) must **never** hit
your real ad units — AdMob can flag it as **invalid traffic** and suspend your
account. Detect the test environment and swap to Google's official sample units:

```kotlin
private val isTestLab by lazy {
    runCatching {
        "true".equals(
            Settings.System.getString(context.contentResolver, "firebase.test.lab"),
            ignoreCase = true)
    }.getOrDefault(false)
}
// use TEST_INTERSTITIAL_ID / TEST_REWARDED_ID when isTestLab
```

The `firebase.test.lab` system setting is `"true"` only on Test Lab devices and is
never set on a real phone, so this path can't run in production. (Sample unit IDs:
interstitial `ca-app-pub-3940256099942544/1033173712`, rewarded
`ca-app-pub-3940256099942544/5224354917`.)

---

## 10. Make ads provable in automated tests (optional but valuable)

To get **video proof** that ads actually render (not just load) in Test Lab,
auto-show them when running under test — gated strictly behind the `isTestLab`
check from §9, using sample units, with the bounded-retry pattern from §8. This is
how BabelWords produced a Test Lab video showing both the interstitial and the
rewarded ad playing, plus a logged `💰 Reward earned`.

This auto-show logic must be **impossible** to trigger in production — guard every
entry point with `isTestLab`.

---

## 11. Log with searchable markers

Use distinctive, greppable log markers so you can verify behavior from a logcat
dump in seconds:

```
✅ Interstitial loaded      ✅ Interstitial shown
✅ Rewarded loaded          ✅ Rewarded shown
💰 Reward earned: 10        🧪 Test Lab: auto-showing …
```

When debugging "ads don't work," the first question is *load vs. show vs. reward* —
clear markers answer it immediately. (See §13 for the verification sequence.)

---

## 12. UX: respect the user and AdMob's policies

Robust serving is also about **when** you show ads:
- **Rewarded = opt-in only.** Never auto-play a rewarded ad at a real user; it must
  be a deliberate "watch ad for X" tap.
- **Frequency-cap interstitials.** Don't show one on every screen transition. Pick
  natural break points and add a minimum interval between interstitials.
- **Never** show a full-screen ad during active, time-sensitive use (e.g. mid
  live-translation). Wait for a pause.
- **Don't** stack ads back-to-back for real users (the back-to-back pattern in this
  project is Test-Lab-only).
- Disable the trigger button while an ad is in flight to prevent double taps.

---

## 13. How to verify ad serving is healthy

Pull a logcat (real device with `adb logcat`, or the Test Lab `logcat.txt`
artifact) and confirm the full lifecycle:

```
AdMobManager: ✅ Rewarded loaded
AdMobManager: ✅ Rewarded shown
AdMobManager: 💰 Reward earned: 10
AdMobManager: Rewarded dismissed
AdMobManager: Loading rewarded ad…        ← auto-reload kicked in
AdMobManager: ✅ Rewarded loaded          ← next one ready
```

If you see `shown` → `reward earned` → `dismissed` → `loaded again`, serving is
healthy end-to-end. Missing reload after dismiss = §2 bug. No `shown` after
`loaded` = lifecycle/Activity issue (§4). No `reward earned` = reward wired to the
wrong callback (§5).

---

## 14. Quick adoption checklist for LinguaVibe

- [ ] Preload interstitial + rewarded at startup (§1)
- [ ] Reload after every dismiss **and** failed show (§2)
- [ ] Duplicate-load guards reset in both load callbacks (§3)
- [ ] `showableActivity()` check + main-thread show/load (§4)
- [ ] Reward granted only in the reward callback, with default amount (§5)
- [ ] One documented event contract shared with the web app (§6)
- [ ] **UMP consent flow added** before requesting ads (§7) ← not yet in BabelWords
- [ ] State guards flip on real render, retries bounded (§8)
- [ ] Test-environment detection → sample ad units (§9)
- [ ] Greppable log markers (§11)
- [ ] Interstitial frequency cap + rewarded opt-in only (§12)
