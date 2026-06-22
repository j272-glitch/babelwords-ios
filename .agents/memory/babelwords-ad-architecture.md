---
name: BabelWords ad architecture vs. the user's guides
description: Why most user-supplied "ad fix" guides don't apply to this repo, and what the real ad architecture is.
---

# BabelWords ad architecture vs. the legacy guides

The user repeatedly supplies "ad fix" / "preload" guides that describe a **legacy
LinguaGT architecture** that **does not exist** in this repo (`com.babelwords.app`).

**What the guides describe (legacy — NOT present here):** `window.AndroidAdBridge`,
`AdMobBridge.kt` + `AdPreloadManager.kt`, per-function callbacks
(`onInterstitialClosed`, `onRewardEarned(type,amount)`...), string returns
(`"true"/"not_ready"`), an `isActivityResumed` flag (the "-2 / activity not resumed"
0-impression bug), an `isShowingAd` stuck-flag + show watchdog, UMP consent, package
`com.lingualink.linguagt`, App ID `~7514450861`.

**What's actually in this repo:** bridge registered as `window.AdBridge` (in
`WebViewConfig.kt`), just `AdBridge.kt` (JS interface) + `AdMobManager.kt` (loader),
a single combined dispatcher `window.onAdBridgeEvent(type, data)`, methods returning
real booleans, package `com.babelwords.app`, App ID `~9480266747`. There is **no**
`isActivityResumed`/`isShowingAd`/watchdog/UMP/AdPreloadManager code at all.

**Why it matters:** Most named guide "fixes" (the -2 activity-resumed bug, isShowingAd
watchdog, the -2..-13 guard codes) target code that doesn't exist here, so they are
**not applicable**. Already-satisfied here: manifest `OPTIMIZE_INITIALIZATION` +
`OPTIMIZE_AD_LOADING` flags, `AD_ID` permission, Kotlin 2.2.0 / play-services-ads 24.x.
The only genuinely-applicable reliability gaps in *this* code are: ad expiry/freshness,
retry-with-backoff on load failure, and show-on-load (auto-show when a show is requested
before an ad is ready). AdMobManager auto-preloads on init, so ads are usually ready.

**How to apply:** Before "applying" any future ad guide, grep this repo for the named
symbols first; if they don't exist, the guide is legacy — evaluate against the real
files, don't blindly port. Ad-unit IDs in the repo are placeholders (real BabelWords IDs
added after publishing) — do NOT change them.

**Domain:** the app points at `linguagt.com` everywhere (MainActivity, manifest App
Links, build.gradle BASE_URL, network_security_config, BillingManager) — no
`gtlingua.com` refs in code. Bash grep output sometimes visually mangles long strings
(e.g. shows "linguagt.com" as "ln"); trust the `read` tool over raw grep display.
