---
name: Android project is in this repo (CI-only build)
description: Orientation — the real BabelWords Android source lives here despite docs claiming a "separate project"; build only via GitHub Actions.
---

The real BabelWords Android project (`com.babelwords.app`) lives in THIS Replit repo under `android/app/src/main/...`. Attached instruction docs sometimes claim the Android source is in a "separate Android Studio project, not this Replit web repo" — that is wrong for this repo. Make Android changes here.

**Why:** A package-rename launch crash (`ClassNotFoundException` for a manifest `android:name` class that no longer existed in sources) had to be fixed directly in this repo's `AndroidManifest.xml`.

**How to apply:**
- Build is GitHub-Actions-only; you cannot build/run the APK locally. Verify by reading sources, not by building.
- After any package rename, check ALL references stay consistent: manifest `android:name` (Application/Activities/Services must point to classes that actually exist), `build.gradle` namespace/applicationId, every `package` decl, `google-services.json` package_name, and `proguard-rules.pro` keeps.
- `minifyEnabled false` and the google-services plugin is disabled, so proguard rules and `google-services.json` are currently inert — mismatches there won't crash today but will bite if either is re-enabled.
