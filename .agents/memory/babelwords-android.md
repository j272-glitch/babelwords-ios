---
name: BabelWords Android toolchain & git/ads gotchas
description: Non-obvious constraints for the BabelWords (com.babelwords.app) Android WebView wrapper — toolchain version coupling, where versions are pinned, git push flow, and ad-config layout.
---

# Toolchain version coupling (play-services-ads ↔ Kotlin ↔ AGP)
- `play-services-ads` major version dictates the **minimum** Kotlin compiler version. 24.x carries Kotlin metadata 2.2.0, so the project Kotlin compiler must be >= 2.2.0 or `:app:compileReleaseKotlin` fails with "incompatible metadata" PLUS cascading fake errors in your own .kt files (e.g. `Unresolved reference: mapOf`). Those cascading errors are symptoms, not real bugs.
- **Why:** a recent build broke exactly this way. Keep Kotlin >= the ads SDK's build Kotlin.
- Stay on the **24.x** ads line while `compileSdk = 35`. The 25.x line targets API 36 and can force compileSdk 36.

# Versions are pinned in TWO places — keep them in sync
- AGP and Kotlin versions live in BOTH `android/build.gradle` (plugins block + `ext`) AND `android/settings.gradle` (`pluginManagement.resolutionStrategy.eachPlugin { useModule(...) }`). If only one is changed, `useModule` in settings.gradle silently overrides the plugins block.
- TestRigor is fully removed from the repo (build/config/docs scrubbed; the `verifyTestRigorCompatibility` + `buildForTestRigor` Gradle tasks deleted). Remaining testrigor strings live only in dead code / pasted `attached_assets/` reference dumps, which `cleanup.sh` (user-run) deletes.

# gradle.properties must stay clean for AGP 8.x
- AGP 8.x hard-fails on removed legacy flags (android.enableAapt2, android.enableR8, android.r8.failOnMissingClasses, android.enableNewResourceShrinker, android.enableIncrementalDesugaring, android.bundle.enableUncompressedNativeLibs, etc.). Do not re-add them.

# Android builds ONLY in GitHub Actions
- No local Android SDK/Gradle here — cannot compile-test. First real signal is the CI build. Verify the merged manifest and AAB after each build.

# Git push flow (destructive git blocked for the agent)
- `git commit`/`git push`/`git remote set-url`/`git init` are BLOCKED for the main agent. The USER must run them in Replit Shell.
- Use `./push.sh "msg"` — pushes to `j272-glitch/babelwords-android` `main` using the `GITHUB_TOKEN` secret (token redacted from output, never written to .git/config). Replit checkpoints do NOT reach CI; only a push does.
- **Security:** the `origin` remote points at the OLD repo `lingualink-android2` and historically had a PAT embedded in plaintext in `.git/config`. Recommend the user rotate that token and clean the remote URL.

# ripgrep ANSI display artifact
- Captured `rg` output can render ad-unit / manifest strings mangled (e.g. `ca-app-pub-...` collapsed to `n`, `AD_ID` as `.n`). The file bytes are fine. Verify with plain `grep`/`od -c` or the read tool, not colored `rg`.

# Ad config layout — TWO stacks, only one is live
- **ACTIVE runtime path** = `com.babelwords.app` package: `ads/AdMobManager.kt` (load/show) + `bridge/AdBridge.kt` (JS interface), wired in `MainActivity`. Ad UNIT ids come from `res/values/admob_ids.xml` (`admob_interstitial_id`, `admob_rewarded_id`, `admob_rewarded_interstitial_id`) via `getString`. This is what actually runs.
- **DORMANT path** = `com.lingualink.linguagt.ads.*` (`AdBridge.kt`, `AdMobBridge.kt`, `AdPreloadManager.kt`) with hardcoded Kotlin constants — NOT wired into MainActivity. Update for consistency only; changing it alone does nothing at runtime. A past task set production IDs only here and missed the live path.
- AdMob App ID lives in `res/values/strings.xml` as `admob_app_id` (referenced by manifest `@string/admob_app_id`).
- Interstitial + Rewarded + Rewarded-interstitial are wired in the active stack (Banner disabled by policy).
- ANR fix: two `<meta-data>` flags in AndroidManifest `<application>` — `OPTIMIZE_INITIALIZATION` and `OPTIMIZE_AD_LOADING`.

# JS bridge contract (active stack)
- The native bridge is registered as **`window.AdBridge`** (see `WebViewConfig.kt addJavascriptInterface(adBridge, "AdBridge")`), NOT `window.NativeAdBridge`. The repo's `index.html` landing page calls `window.NativeAdBridge` (with one spot mixing `window.AdBridge`) — it is out of sync with the real registration; the production web app must call `window.AdBridge.*`.
- Native→web events flow ONLY through `window.onAdBridgeEvent(eventType, data)`. Adding a new ad type needs NO MainActivity change — events route generically via the `eventCallback` lambda. Event names: `interstitial{Loaded,Shown,Closed,Failed}`, `rewarded{Loaded,Shown,Closed,Failed}`, `rewardedInterstitial{Loaded,Shown,Closed,Failed}`, shared `rewardEarned` (data = amount string), `adMobInitialized`.
- **Init-race contract:** `MainActivity` builds `AdMobManager` inside `lifecycleScope.launch` (async, after `MobileAds.initialize`) but creates `AdBridge` synchronously. So `adMobManager` MUST stay nullable (`var ... = null`) and the provider lambda `{ adMobManager }` must return `AdMobManager?` — every `AdBridge` method null-checks the provider. Making it `lateinit` reintroduces an `UninitializedPropertyAccessException` crash on early JS calls.
- JS event payloads are string-interpolated into single-quoted JS in BOTH `MainActivity` evalJs callback and `AdBridge.fireEvent` — escape `\`, `'`, `\n`, `\r` in `data` (error messages contain apostrophes) or the callback JS breaks.
