---
name: BabelWords Android toolchain & git/ads gotchas
description: Non-obvious constraints for the BabelWords (com.linguawonder.app) Android WebView wrapper — toolchain version coupling, where versions are pinned, git push flow, and ad-config layout.
---

# Toolchain version coupling (play-services-ads ↔ Kotlin ↔ AGP)
- `play-services-ads` major version dictates the **minimum** Kotlin compiler version. 24.x carries Kotlin metadata 2.2.0, so the project Kotlin compiler must be >= 2.2.0 or `:app:compileReleaseKotlin` fails with "incompatible metadata" PLUS cascading fake errors in your own .kt files (e.g. `Unresolved reference: mapOf`). Those cascading errors are symptoms, not real bugs.
- **Why:** a recent build broke exactly this way. Keep Kotlin >= the ads SDK's build Kotlin.
- Stay on the **24.x** ads line while `compileSdk = 35`. The 25.x line targets API 36 and can force compileSdk 36.

# Versions are pinned in TWO places — keep them in sync
- AGP and Kotlin versions live in BOTH `android/build.gradle` (plugins block + `ext`) AND `android/settings.gradle` (`pluginManagement.resolutionStrategy.eachPlugin { useModule(...) }`). If only one is changed, `useModule` in settings.gradle silently overrides the plugins block.
- `android/build.gradle` has a `verifyTestRigorCompatibility` task with `assert agp_version == '<X>'` — bumping AGP requires updating this assert or the build hard-fails.

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

# Ad config layout
- AdMob App ID lives in `res/values/strings.xml` as `admob_app_id` (referenced by manifest `@string/admob_app_id`).
- Ad UNIT ids are hardcoded Kotlin constants across `ads/AdBridge.kt`, `ads/AdMobBridge.kt`, `ads/AdPreloadManager.kt` — interstitial appears in all three; update them together.
- Only Interstitial + Rewarded are enabled (Banner disabled by policy).
- ANR fix: two `<meta-data>` flags in AndroidManifest `<application>` — `OPTIMIZE_INITIALIZATION` and `OPTIMIZE_AD_LOADING` (move ads SDK init/loading off main thread). No web/JS-bridge changes needed.
