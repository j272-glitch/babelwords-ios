# BabelWords Android — Consolidated Ad System Guide

**Date:** 2026-07-12
**Scope:** Complete playbook for the BabelWords Android ad stack. Describes what is
*actually implemented* in the codebase, not aspirational features.

> **Architecture:** Native Kotlin WebView wrapper for `linguagt.com`. Package:
> `com.babelwords.com`. App name: LinguaWonder. Builds **only in GitHub Actions**.

---

## Table of Contents

1. [Prerequisites & Toolchain](#1-prerequisites--toolchain)
2. [Version Bumping](#2-version-bumping)
3. [Ad Architecture Overview](#3-ad-architecture-overview)
4. [AdMobManager.kt — Unified Interstitial Pipeline](#4-admobmanagerkt--unified-interstitial-pipeline)
5. [AppOpenAdManager.kt — App Open Ads](#5-appopenadmanagerkt--app-open-ads)
6. [ConsentManager.kt — UMP / GDPR](#6-consentmanagerkt--ump--gdpr)
7. [AdBridge.kt — JS Bridge Contract](#7-adbridgekt--js-bridge-contract)
8. [MainActivity.kt — Lifecycle & Mic Safety](#8-mainactivitykt--lifecycle--mic-safety)
9. [AndroidManifest.xml — Required Entries](#9-androidmanifestxml--required-entries)
10. [Firebase Test Lab (CI)](#10-firebase-test-lab-ci)
11. [Standard Release Flow](#11-standard-release-flow)
12. [Post-Release Checklist](#12-post-release-checklist)

---

## 1. Prerequisites & Toolchain

| Component | Version | Notes |
|---|---|---|
| AGP | **8.5.1** | Pinned in `android/build.gradle` + `settings.gradle` resolutionStrategy |
| Gradle | **8.9** | `gradle-8.9-all.zip` in `gradle-wrapper.properties` |
| Kotlin | **2.2.0** | K2 compiler; required by play-services-ads 24.x |
| JDK | **17** | |
| compileSdk / targetSdk | **35** | |
| minSdk | **24** | |
| build-tools | **35.0.0** | |
| play-services-ads | **24.9.0** | Core AdMob SDK |
| UMP | **3.1.0** | `com.google.android.ump:user-messaging-platform` |
| lifecycle-process | **2.8.7** | For `AppOpenAdManager` DefaultLifecycleObserver |

**Critical:** Kotlin version must be ≥ the Kotlin version the ads SDK was built
with. play-services-ads 24.9.0 carries Kotlin metadata 2.2.0, so Kotlin 2.2.0 is
required.

---

## 2. Version Bumping

Change in **two places** every release:

1. **Gradle files** (fallbacks when CI inputs absent):
   - `android/app/build.gradle` → `versionCode` / `versionName`
   - `android/build.gradle` → `versionCode` / `versionName` in `subprojects { defaultConfig { ... } }`
2. **CI workflow** (`.github/workflows/android-sdk-update-v1.yml`):
   - `version_name` input **description** and **default**
   - `env:` fallback `VERSION_NAME`

**Precedence:** workflow input → env fallback → Gradle default.

**Rules:**
- `versionCode` must be strictly higher than any artifact ever uploaded to Play.
- A version, once used, is "burned" — bump again even if the build failed.
- Verify no stragglers: `rg --color never -n "<OLD_VERSION>" android/ .github/workflows/android-sdk-update-v1.yml`

---

## 3. Ad Architecture Overview

The ad system is intentionally minimal — four Kotlin files, no dual-pipeline
complexity:

| File | Role |
|---|---|
| `AdMobManager.kt` | Unified interstitial pipeline (load, show, preload, retry) |
| `AppOpenAdManager.kt` | App Open ad with warm-resume + 4h cap |
| `ConsentManager.kt` | UMP consent flow + personalized/npa=1 request builder |
| `AdBridge.kt` | JS bridge (`window.AdBridge`) exposed to WebView |

**No separate `AdPreloadManager`, `AdMobBridge`, or `InterstitialAdManager`** —
these do not exist. All ad logic is consolidated in `AdMobManager` + `AdBridge`.

### Ad Formats

| Format | Status | Unit ID |
|---|---|---|
| Interstitial | ✅ Active | `ca-app-pub-9991891515643313/7320741331` |
| App Open | ✅ Active | `ca-app-pub-9991891515643313/3733157606` |
| Rewarded | ❌ Removed | `loadRewardedAndShow()` aliases to interstitial |
| Banner | ❌ Disabled by policy | |

### Production AdMob IDs (strings.xml)

```xml
<string name="admob_app_id">ca-app-pub-9991891515643313~9480266747</string>
<string name="admob_interstitial_id">ca-app-pub-9991891515643313/7320741331</string>
<string name="admob_app_open_id">ca-app-pub-9991891515643313/3733157606</string>
```

---

## 4. AdMobManager.kt — Unified Interstitial Pipeline

**Location:** `android/app/src/main/java/com/babelwords/app/ads/AdMobManager.kt`

### Key Behaviors

| Behavior | Implementation |
|---|---|
| **AtomicBoolean CAS load guard** | `isLoading.compareAndSet(false, true)` prevents concurrent loads |
| **pendingShow auto-show** | If `pendingShow=true` when ad loads, auto-shows immediately |
| **Pre-resume throttle** | `lastLoadTime` only updated when `isActivityResumed=true` |
| **Network callback auto-reload** | `registerNetworkCallback()` reloads on connectivity return |
| **Session-token callback safety** | `AtomicLong callbackToken` — stale callbacks are dropped |
| **15s timeout** | `handler.postDelayed` nulls stale ads, triggers retry |
| **Exponential retry** | 5s → 10s → 20s → 40s → 60s max |
| **Cross-manager fullscreen guard** | `isAnyFullscreenAdShowing` prevents overlapping App Open + Interstitial |
| **Consent-aware requests** | `getConsentManager().buildAdRequest()` with `npa=1` fallback |
| **Test Lab gating** | `maybeAutoShowInterstitial()` checks `isTestLab && isTestDeviceRegistrationActive` |

### Cross-Manager Fullscreen Guard

`AdMobManager.isAnyFullscreenAdShowing` is a `@Volatile` companion variable:
- **Set** when interstitial/App Open shows (`onAdShowedFullScreenContent`)
- **Cleared** when dismissed/failed/destroyed (`onAdDismissedFullScreenContent`, `onAdFailedToShowFullScreenContent`, `cleanup()`)
- **Checked** in `AppOpenAdManager.showAdIfAvailable()` before showing

This prevents the App Open ad from appearing while an interstitial is already on
screen (and vice versa).

### Public Methods

| Method | Purpose |
|---|---|
| `loadInterstitialAndShow(activity)` | Primary trigger — loads then shows |
| `showInterstitial(activity)` | Show cached ad (or load+show if stale/missing) |
| `preloadInterstitial()` | Warm cache for faster show |
| `loadRewardedAndShow(activity)` | Backward-compat alias → `loadInterstitialAndShow()` |
| `isInterstitialReady()` | True if ad loaded AND not currently showing |
| `isRewardedReady()` | Alias to `isInterstitialReady()` |
| `onActivityResumed(activity)` | Call from `MainActivity.onResume()` |
| `onActivityPaused()` | Call from `MainActivity.onPause()` |
| `registerNetworkCallback()` | Auto-reload on connectivity return |
| `unregisterNetworkCallback()` | Call before destroy |
| `destroy()` | Cleanup — cancel runnables, clear callbacks |
| `maybeAutoShowInterstitial(activity)` | Auto-show on launch (gated by Test Lab + mic) |

---

## 5. AppOpenAdManager.kt — App Open Ads

**Location:** `android/app/src/main/java/com/babelwords/app/ads/AppOpenAdManager.kt`

### Key Behaviors

| Behavior | Implementation |
|---|---|
| **Warm-resume detection** | `onStop` records timestamp; `onStart` checks if ≥5s passed → shows ad |
| **4h frequency cap** | `SharedPreferences` key `app_open_last_show_ms` (survives process death) |
| **15s load timeout** | `handler.postDelayed` with `LOAD_TIMEOUT_MS` → nulls stale ad |
| **Error backoff** | No-fill → 60s, timeout → 20s, network → 15s |
| **Destroyed guard** | `isDestroyed` checked in every callback + load/show |
| **Mic block** | `showAdIfAvailable()` skips if `activity.isMicActive` |
| **Cross-manager guard** | Checks `AdMobManager.isAnyFullscreenAdShowing` before show |
| **Consent-aware requests** | `getConsentManager().buildAdRequest()` with `npa=1` fallback |
| **Lifecycle** | `DefaultLifecycleObserver` wired to `ProcessLifecycleOwner` |

### Lifecycle Wiring (MainActivity)

```kotlin
// onCreate:
appOpenAdManager = AppOpenAdManager(this@MainActivity) { consentManager }
ProcessLifecycleOwner.get().lifecycle.addObserver(appOpenAdManager)

// onDestroy:
ProcessLifecycleOwner.get().lifecycle.removeObserver(appOpenAdManager)
appOpenAdManager.cleanup()
```

**Critical:** Always call `removeObserver` in `onDestroy`. The manager holds a
strong `MainActivity` reference — without removal, destroyed activities leak and
duplicate callbacks accumulate after recreation.

### Public Methods

| Method | Purpose |
|---|---|
| `loadAd()` | Load an App Open ad (respects frequency cap) |
| `showAdIfAvailable()` | Show if loaded, warmed-resume, and guards pass |
| `cleanup()` | Cancel runnables, clear callbacks, set `isDestroyed=true` |

---

## 6. ConsentManager.kt — UMP / GDPR

**Location:** `android/app/src/main/java/com/babelwords/app/ads/ConsentManager.kt`

### Flow

1. `requestConsent(activity, onConsentReady)` — updates consent status with Google servers
2. If form available → loads and shows form
3. `onConsentReady(true)` fires when resolved (or not needed for region)
4. **Ads are never blocked** — unknown/declined consent → `npa=1` (non-personalized)

### Request Builder

```kotlin
fun buildAdRequest(): AdRequest {
    return if (canRequestAds && consentStatus != DENIED) {
        AdRequest.Builder().build()              // personalized
    } else {
        val extras = Bundle().apply { putString("npa", "1") }
        AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()                               // non-personalized
    }
}
```

Both `AdMobManager` and `AppOpenAdManager` call `getConsentManager()?.buildAdRequest()`
so all ad requests respect the user's consent choice.

---

## 7. AdBridge.kt — JS Bridge Contract

**Location:** `android/app/src/main/java/com/babelwords/app/bridge/AdBridge.kt`

**Exposed as:** `window.AdBridge` (NOT `window.AndroidAdBridge`)

**Events fired to web app via:** `window.onAdBridgeEvent(eventType, data)` (NOT `window.onAdEvent`)

### Methods Exposed to JavaScript

| Method | Purpose |
|---|---|
| `loadInterstitial()` | Preload interstitial |
| `showInterstitial()` | Show cached interstitial |
| `isInterstitialReady()` | Boolean — ad loaded + not showing |
| `loadRewarded()` | Backward-compat → preload interstitial |
| `showRewarded()` | Backward-compat → show interstitial |
| `isRewardedReady()` | Alias to `isInterstitialReady()` |
| `loadInterstitialAndShow()` | 1-step load+show |
| `loadRewardedAndShow()` | Backward-compat → `loadInterstitialAndShow()` |
| `getDiagnostics()` | JSON with ad state |
| `notifyMicActive(boolean)` | JS heartbeat for mic safety |
| `requestConsent()` | Trigger UMP consent flow |
| `initialize()` | Legacy — preloads interstitial |

### Events Fired to Web App

Events are sent as `window.onAdBridgeEvent(eventType, data)` from `MainActivity`:

| Event | When |
|---|---|
| `interstitialLoaded` | Ad loaded successfully |
| `interstitialFailed` | Ad failed to load |
| `interstitialShown` | Ad displayed |
| `interstitialDismissed` | Ad closed by user |
| `interstitialFailedToShow` | Show attempt failed |
| `consentResolved` | UMP consent resolved |

---

## 8. MainActivity.kt — Lifecycle & Mic Safety

**Location:** `android/app/src/main/java/com/babelwords/app/MainActivity.kt`

### Mic Safety

```kotlin
@Volatile
var isMicActive: Boolean = false
    private set

private val micWatchdogHandler = Handler(Looper.getMainLooper())
private var micWatchdogRunnable: Runnable? = null

fun setMicState(active: Boolean) {
    isMicActive = active
    if (active) {
        // Re-arm 45s watchdog
        micWatchdogRunnable?.let { micWatchdogHandler.removeCallbacks(it) }
        val watchdog = Runnable {
            if (isMicActive) {
                isMicActive = false
                Log.w("MicWatchdog", "Stale lock cleared after 45s")
            }
        }
        micWatchdogRunnable = watchdog
        micWatchdogHandler.postDelayed(watchdog, 45_000L)
    } else {
        micWatchdogRunnable?.let {
            micWatchdogHandler.removeCallbacks(it)
            micWatchdogRunnable = null
        }
    }
}
```

**JS heartbeat:** The web app calls `window.AdBridge.notifyMicActive(true)` every
15 seconds while recording. Each call re-arms the 45s watchdog, preventing stale
locks if the web app crashes.

**App Open blocked during recording:** `AppOpenAdManager.showAdIfAvailable()`
checks `activity.isMicActive` and skips if true.

### WebView Permission Security

```kotlin
webView.webChromeClient = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        val allowed = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> true
                else -> false
            }
        }
        if (allowed.isNotEmpty()) {
            request.grant(allowed.toTypedArray())
        } else {
            request.deny()
        }
    }
}
```

Only **audio/video capture** is granted. All other permission requests are denied
by default — no blanket `request.grant(request.resources)`.

---

## 9. AndroidManifest.xml — Required Entries

Inside `<application>`:

```xml
<!-- AdMob App ID -->
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-9991891515643313~9480266747"/>

<!-- ANR prevention flags (REQUIRED for play-services-ads 24.x) -->
<meta-data
    android:name="com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION"
    android:value="true"/>
<meta-data
    android:name="com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING"
    android:value="true"/>
```

Permissions:

```xml
<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
```

**Custom Application class:**

```xml
<application
    android:name="com.babelwords.com.BabelWordsApplication"
    ...>
```

`BabelWordsApplication.kt` handles `MobileAds.initialize()` and Test Lab test-device
registration.

---

## 10. Firebase Test Lab (CI)

### What Actually Runs

The CI runs a **single Robo test** (Test Lab auto-launches the app), NOT custom
instrumentation tests. There is no `androidTest/` directory or test suite.

### Trigger

Manual only — `workflow_dispatch` with `run_firebase_test = true`.

### Device

`MediumPhone.arm`, API 33, portrait, 180s timeout, video recording enabled.

### CI Verification Steps

1. **APK presence** — hard-fails if APK artifact is missing
2. **Test Lab detection** — `Firebase Test Lab detected` in logcat
3. **Test-device registration** — confirms `BabelWordsApplication` ran correctly
4. **Ad impression** — `IMPRESSION` in logcat confirms ad rendered
5. **Real-ads safety** — hard-fails if `REAL_ADS_ON_TEST_LAB` marker appears

### Required Secrets

| Secret | Value |
|---|---|
| `GCP_SA_KEY` | Base64-encoded service account JSON |
| `GCP_PROJECT_ID` | `babelwords-android` |

### GCP Setup Required

1. Enable **Cloud Testing API**
2. Enable **Cloud Tool Results API**
3. Service account role: **Editor** (or Cloud Testing Admin + Storage Object Admin)
4. Blaze billing on the project

### Test Lab Ad Unit IDs

When `isTestLab=true`, both managers switch to Google sample units:
- Interstitial: `ca-app-pub-3940256099942544/1033173712`
- App Open: `ca-app-pub-3940256099942544/9251695926`

This prevents real ad impressions from Test Lab devices.

---

## 11. Standard Release Flow

1. Make code/config changes.
2. Bump version in Gradle files + CI workflow (§2).
3. Verify signing secrets in GitHub.
4. Push to GitHub `main`.
5. Trigger workflow (`workflow_dispatch`).
   - Set `version_name` and `version_code`
   - Set `run_firebase_test = true` to validate with Test Lab
6. After green build, verify AAB:
   - `unzip -p app-release.aab base/manifest/AndroidManifest.xml | grep -a AD_ID`
   - `aapt dump badging app-release.aab | grep versionName`
7. Upload to Play Console.
8. **Supersede old artifacts** in all tracks (Internal / Closed / Open / Production).
9. Complete **App content → Advertising ID** declaration in Play Console.
10. Monitor Play Vitals ANR trend for 7–14 days.

---

## 12. Post-Release Checklist

- [ ] AD_ID permission present in merged manifest (CI gate verifies)
- [ ] OPTIMIZE_INITIALIZATION + OPTIMIZE_AD_LOADING present in manifest (CI gate verifies)
- [ ] AdMob App ID matches production value in strings.xml
- [ ] Version code strictly higher than any previous Play upload
- [ ] Test Lab run completed with impression confirmed (if triggered)
- [ ] Old Play Console tracks superseded with new release
- [ ] Advertising ID declaration completed in Play Console
- [ ] `setMicState` method signature unchanged (Kotlin generates setter for `var`)
- [ ] ProcessLifecycle observer removed in `onDestroy`
- [ ] WebView permission request uses whitelist (audio/video only)

---

## Appendix: Files That Do NOT Exist

Do not look for these — they are not part of this codebase:

- `AdPreloadManager.kt` — preloading is in `AdMobManager.kt`
- `AdMobBridge.kt` — bridge is `AdBridge.kt`
- `InterstitialAdManager.kt` — consolidated into `AdMobManager.kt`
- `android/app/src/androidTest/` — no instrumentation tests
- Unity/Vungle mediation adapters — only AdMob is configured
