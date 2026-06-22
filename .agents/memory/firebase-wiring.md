---
name: Firebase Crashlytics + Analytics wiring (BabelWords Android)
description: Why Firebase is gated on google-services.json existence, fed via a CI secret, and which BoM/plugin versions are required.
---

# Firebase wiring decisions

Firebase Crashlytics + Analytics are applied in `android/app/build.gradle` ONLY when
`google-services.json` exists (`def firebaseEnabled = file('google-services.json').exists()`),
guarding both the plugin `apply` and the BoM/deps block.

**Why:** the google-services Gradle plugin hard-fails a build if the config file is missing.
Builds without Firebase config (local, or CI without the secret) must still succeed, so the
whole Firebase block is conditional rather than unconditional.

**How to apply:** never make Firebase deps/plugins unconditional. If you add more Firebase
libs, put them inside the same `if (firebaseEnabled)` block.

## Config delivery
`google-services.json` is NEVER committed — it carries the project's client API key (the
three copies in attached_assets were deleted for this reason). It lives only in the GitHub
secret `GOOGLE_SERVICES_JSON_BASE64`, decoded by a CI step into `android/app/google-services.json`
before the Gradle build (same pattern as `ANDROID_KEYSTORE_BASE64`). `.gitignore` blocks it.

## Version constraint
Firebase BoM must be **33.x** (used 33.7.0), google-services plugin **4.4.2**, crashlytics
plugin **3.0.2** — the old commented 32.8.0 BoM was for Kotlin 1.8/1.9 and is incompatible
with this project's Kotlin 2.2.0 / AGP 8.5.1.
**Why:** 32.x predates Kotlin 2.x metadata support.

## Init & collection
No Application subclass exists; Firebase auto-inits via FirebaseInitProvider. The manifest
flags were flipped from disabling collection to **enabling** it
(`firebase_analytics_collection_enabled=true`, `firebase_crashlytics_collection_enabled=true`)
so crash/usage data reports out of the box. This reverses an earlier "defer init for startup
perf" choice — intentional, because deferred collection with no runtime enable code meant
Firebase reported nothing.
