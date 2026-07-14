# BabelWords Android — Update Guide

A practical playbook for updating the BabelWords Android app (com.babelwords.com, LinguaWonder). It captures hard-won lessons from recent releases so future updates go smoothly.

> **Read this first:** the Android app builds **only in GitHub Actions** — there is no local Android SDK/Gradle in this environment. The first real signal is the CI build. Plan changes carefully and verify the produced AAB after every build.

---

## 1. Toolchain (current, known-good)

| Component | Version | Notes |
|---|---|---|
| Android Gradle Plugin (AGP) | **8.5.1** | Pinned in `android/build.gradle` plugins + `resolutionStrategy` in `settings.gradle`. Both must agree. |
| Gradle | **8.9** | `android/gradle/wrapper/gradle-wrapper.properties` (`gradle-8.9-all.zip`). |
| Kotlin | **2.2.0** | Required by play-services-ads 24.x (K2 compiler). |
| JDK | **17** | Required for Gradle 8.x. |
| compileSdk / targetSdk | **35** | |
| minSdk | **24** | |
| build-tools | **35.0.0** | |

All live in `android/build.gradle` under `plugins {}` and `ext {}`. `android/app/build.gradle` inherits via `rootProject.ext.*`.

---

## 2. How to bump the app version

Change in **two places** — Gradle files and CI workflow. CI overrides with `-Pandroid.injected.version.code/name`, so workflow inputs/env take precedence.

1. **Gradle files** (fallbacks when CI inputs absent):
   - `android/app/build.gradle` → `versionCode` and `versionName`
   - `android/build.gradle` → `versionCode` / `versionName` in `subprojects { ... defaultConfig { ... } }`
2. **CI workflow** (`.github/workflows/android-sdk-update-v1.yml`) → three spots:
   - `version_name` input **description**
   - `version_name` input **default**
   - `env:` fallback `VERSION_NAME`

**Precedence:** workflow input → env fallback → Gradle default.

**Rules**
- `versionCode` must be strictly higher than any artifact uploaded to Play (all tracks). Play rejects re-used or lower codes.
- A version, once used, is "burned" — bump again even if the build failed and produced nothing.
- After editing, verify no stragglers:
  ```bash
  rg --color never -n "<OLD_VERSION>" android/ .github/workflows/android-sdk-update-v1.yml
  ```

---

## 3. AdMob / ANR — the real fixes

### 3a. ANR mitigation
ANRs in Play Vitals (`MessageQueue.nativePollOnce`, `AdMobBridge.loadInterstitialAd`) are caused by the Google Mobile Ads SDK doing sync work on the **main thread** during init and ad loading.

**Fix — add these `<meta-data>` flags inside `<application>`** in `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION"
    android:value="true"/>
<meta-data
    android:name="com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING"
    android:value="true"/>
```
These move SDK init + ad loading off the main thread. No web-app / JS-bridge changes needed.

### 3b. No web-app changes for native ad work
The JS bridge contract (`loadInterstitial()`, `showInterstitial()`, `isInterstitialReady()`, `window.on*`) is stable. Native SDK bumps and manifest changes never require touching the web app.

---

## 4. ⚠️ play-services-ads ↔ Kotlin version coupling

- **play-services-ads 24.x is compiled with Kotlin 2.x.** The 24.9.0 jars carry Kotlin metadata 2.2.0.
- A Kotlin 1.9.x compiler can only read metadata up to 2.0.0, so the build fails at `:app:compileReleaseKotlin`.

**Rule:** Kotlin version must be ≥ the ads SDK's Kotlin version. We use **Kotlin 2.2.0** to match play-services-ads 24.9.0.

**compileSdk ceiling:** stay on the 24.x ads line while `compileSdk = 35`. The 25.x line targets API 36.

---

## 5. Google Play "AD_ID permission missing"

When Play warns that AD_ID is missing:
- The source manifest **does** declare it (`com.google.android.gms.permission.AD_ID`).
- The warning persists because Play validates **every active artifact across all tracks**. Old AABs built before AD_ID existed stay active and trigger it.

**What actually fixes it:**
1. Verify the AAB has it: `unzip -p app-release.aab base/manifest/AndroidManifest.xml | grep -a AD_ID`
2. In Play Console, **supersede/deactivate old releases** in every track.
3. Complete **App content → Advertising ID** declaration.
4. **NEVER click "Release without permission"** — that zeroes the advertising ID and kills ad revenue.

A **CI gate** in the workflow fails the build if AD_ID is absent from the compiled merged manifest (checked after the OPTIMIZE flags step).

---

## 6. Standard release flow

1. Make code/config changes.
2. Bump version in both places (§2) — Gradle files + workflow defaults.
3. Verify signing secrets in GitHub (keystore base64 + passwords).
4. Push to GitHub `main`. Replit checkpoints alone do **not** reach CI.
5. Trigger the workflow (`android-sdk-update-v1.yml`, via `workflow_dispatch`).
   Confirm version inputs match what you bumped.
6. After green build, verify the AAB:
   - AD_ID present (§5)
   - both OPTIMIZE meta-data entries present
   - `versionName` matches intent (`aapt dump badging`)
7. Upload to Play, then **supersede old artifacts** in all tracks.
8. Monitor Play Vitals ANR trend for 7–14 days.

---

## 7. Firebase Test Lab (Robo test)

The CI runs a **Robo test** on a real cloud device when manually triggered with `run_firebase_test = true`.

**What happens:**
- Test Lab auto-launches the app on a `MediumPhone.arm` device
- Records video + captures logcat
- CI scans logcat for ad funnel markers (Test Lab detection, IMPRESSION)
- **CI fails** if ad impression is missing or test-device registration failed

**Required secrets:** `GCP_SA_KEY` (base64 service account JSON) and `GCP_PROJECT_ID` in GitHub Secrets.

**CI checks:**
- `Firebase Test Lab detected` in logcat → confirms test-device registration
- `test-device registration FAILED` → hard-fail
- `IMPRESSION` in logcat → confirms ad rendered
- `REAL_ADS_ON_TEST_LAB is true` → hard-fail (safety gate against accidental real-fill runs)

**Required GCP setup:**
- Enable **Cloud Testing API** and **Cloud Tool Results API**
- Service account needs **Editor** role (or Cloud Testing Admin + Storage Object Admin)
- Blaze billing required (Test Lab free quota is console-only)

---

## 8. Gotchas / red herrings

- **ripgrep ANSI color glitch:** colored `rg` output can make manifest lines look mangled. Verify with `grep` or `od -c`.
- **A failed build still "uses up" a version number** — bump again next attempt.
- **Primary workflow:** the only active CI build file is `.github/workflows/android-sdk-update-v1.yml`.
- **AdMob IDs** (production):
  - App ID: `ca-app-pub-9991891515643313~9480266747`
  - Interstitial: `ca-app-pub-9991891515643313/7320741331`
  - App Open: `ca-app-pub-9991891515643313/3733157606`
- **Ad formats:** Interstitial + App Open active. Rewarded unit removed — backward-compat in `AdMobManager.loadRewardedAndShow()` delegates to interstitial.

---

## 9. Quick reference — files you'll touch most

| File | What it controls |
|---|---|
| `android/build.gradle` | AGP, Kotlin, SDK versions; global version defaults |
| `android/app/build.gradle` | App version, dependencies (play-services-ads 24.9.0) |
| `android/app/src/main/AndroidManifest.xml` | Permissions (AD_ID), AdMob App ID, OPTIMIZE flags |
| `.github/workflows/android-sdk-update-v1.yml` | CI build, version defaults, AD_ID gate, Test Lab |
