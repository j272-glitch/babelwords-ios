---
name: BabelWords signing key
description: Which keystore signs the BabelWords app and why the old ones were retired
---

# BabelWords release signing key

The app uses a **fresh keystore generated June 2026** specifically for BabelWords:
alias `babelwords`, RSA 2048, valid to 2053, SHA-256
`D4:1D:60:84:0C:13:6A:3B:95:9E:A7:11:6F:84:00:70:06:42:9B:11:8C:7F:96:31:14:7E:0D:05:D4:7A:AB:8B`.

**Why a fresh key:** BabelWords had never been published to Google Play, so there was
no lock-in. The inherited LinguaGT-era keystores (`release.keystore` /
`linguagt-release-key`, and the dev `my-release-key.jks` / `my-alias`) had their
passwords committed in plaintext (`keystore-info.txt`, `android-secrets-setup.md`),
so they were considered exposed and retired.

# Two keys under Play App Signing (upload key vs app signing key)
The `D4:1D:…:AB:8B` keystore is the **upload key** (signs the AAB we upload). Google
**Play App Signing** re-signs installed builds with a different, Play-managed **app signing
key**: SHA-256 `15:5D:00:27:77:20:0B:EC:09:0A:8B:65:46:6C:D5:44:1D:ED:96:6A:4B:96:D8:E3:F4:FD:67:49:FE:24:5D:1B`.

**Why it matters:** App Links verification is tied to the cert installed devices actually run.
For Play-installed builds that is the `15:5D:…` app signing key — so the **served**
`assetlinks.json` on linguagt.com must list `15:5D:…`. The repo's reference `assetlinks.json`
is NOT served (server.js doesn't serve it; the live copy is in the separate web app repo); it
lists BOTH `15:5D:…` (Play installs) and `D4:1D:…` (sideloaded test APKs signed with only the
upload key, which otherwise won't App-Link-verify).

**How to apply:**
- The keystore lives ONLY in GitHub Actions secrets: `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (=`babelwords`), `ANDROID_KEY_PASSWORD`.
  Store password == key password (single value).
- `.gitignore` blocks `*.jks`, `*.keystore`, and the base64/info files from ever
  being committed. The base64 value is the only backup — also keep it in a password manager.
- **Never regenerate this key once BabelWords is published** — Play ties updates to it.
  Before publish, regenerating is free.
- CI signing reads these env vars in `android/app/build.gradle`; an empty
  `ANDROID_KEYSTORE_BASE64` makes the build's signing step exit 1 (Gradle still says
  BUILD SUCCESSFUL just before).
