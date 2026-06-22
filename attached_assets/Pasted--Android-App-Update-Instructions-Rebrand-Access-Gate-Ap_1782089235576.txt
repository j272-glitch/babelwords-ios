# Android App — Update Instructions (Rebrand + Access Gate + App Links)

**Audience:** Whoever maintains the BabelWords Android project (the WebView wrapper around
`linguagt.com`).

**What this covers:** the three Android-side changes that must line up with the web app after the
BabelWords rebrand:

1. **Package rename** → `com.babelwords.app`
2. **Access gate token** → compiled-in code must equal the server's `SITE_ACCESS_TOKEN`
3. **App Links** → manifest `autoVerify` + signing with the key whose SHA-256 is in
   `assetlinks.json`

> The web side is already done: `assetlinks.json` lists `com.babelwords.app` with the Play App
> Signing SHA-256 `15:5D:00:27:…:24:5D:1B`, the gate whitelists `/.well-known/*`, and the token is
> read from `SITE_ACCESS_TOKEN`. This doc is **only** the Android-side work.

Deep-dive companions (read these for full detail):
- Token mechanism & sample code → `docs/ANDROID_ACCESS_TOKEN_GUIDE.md`
- App Links verification → `docs/GOOGLE_APP_LINKS_MIGRATION.md`
- Signing key & fingerprint → `docs/PLAY_APP_SIGNING_GUIDE.md`

---

## 1. Rename the package to `com.babelwords.app`

This is a **new app identity** (it cannot be changed after first publish, and it's a new Play
listing). Update **every** place the old package appears (`com.linguawonder.app` →
`com.babelwords.app`):

- **`app/build.gradle`**
  ```groovy
  android {
      namespace 'com.babelwords.app'           // was com.linguawonder.app
      defaultConfig {
          applicationId "com.babelwords.app"   // was com.linguawonder.app
      }
  }
  ```
- **All `.kt` / `.java` sources** — every `package com.linguawonder.app...` declaration and matching
  `import com.linguawonder.app...`. Move source folders to `…/java/com/babelwords/app/…` so the
  directory structure matches the new package.
- **`AndroidManifest.xml`** — any fully-qualified component names (e.g. `.MainActivity` is fine, but
  replace any explicit `com.linguawonder.app.*`).
- **ProGuard / R8 `-keep` rules** that reference the old package (e.g. `Application` subclass).
- **Firebase `google-services.json`** — the `package_name` must be `com.babelwords.app`. Register
  the new package in the Firebase console and download a fresh `google-services.json`, or the app
  will crash at startup / lose Firebase services.
- **AdMob / any SDK** consoles that key off the package name (re-register the app there too).

> Tip: do a project-wide search for `com.linguawonder.app` and confirm **zero** matches remain
> (other than historical docs).

---

## 2. Access gate token (must match the server)

The published site (linguagt.com) is gated in production. The app gets through by passing the code
once on first load; the server then sets a 30-day `site_access` cookie that the WebView reuses.

- Store the code as a build secret named **`BABELWORDS_ACCESS_TOKEN`** (in `local.properties` or a
  CI secret) — **do not hardcode it in source.**
- Its value must be **exactly equal** to the server's `SITE_ACCESS_TOKEN`. If you rotate one, rotate
  both, or the app lands on the "Private Access" page.
- On first launch, load:
  ```
  https://linguagt.com/?access=<BABELWORDS_ACCESS_TOKEN>
  ```
  The server validates it, sets the cookie, and 302-redirects to the clean URL. Enable cookies in
  the WebView (`CookieManager.setAcceptCookie(true)` + flush) so later requests pass automatically.

Full Kotlin sample (WebView setup, query-param method, and the header alternative) is in
`docs/ANDROID_ACCESS_TOKEN_GUIDE.md`.

> Dev/preview is never gated, so leave the token empty for non-production builds and just load
> `https://linguagt.com` (or your preview URL) directly.

---

## 3. App Links (deep-link verification)

For `https://linguagt.com` links to open the app (and for verified domain association):

1. In `AndroidManifest.xml`, the deep-link `intent-filter` must use **`android:autoVerify="true"`**
   for host `linguagt.com`:
   ```xml
   <intent-filter android:autoVerify="true">
       <action android:name="android.intent.action.VIEW" />
       <category android:name="android.intent.category.DEFAULT" />
       <category android:name="android.intent.category.BROWSABLE" />
       <data android:scheme="https" android:host="linguagt.com" />
   </intent-filter>
   ```
2. The installed app must be signed with the key whose SHA-256 is published in
   `https://linguagt.com/.well-known/assetlinks.json`. That is currently the **Play App Signing**
   key:
   ```
   15:5D:00:27:77:20:0B:EC:09:0A:8B:65:46:6C:D5:44:1D:ED:96:6A:4B:96:D8:E3:F4:FD:67:49:FE:24:5D:1B
   ```
   Because you publish via **Play App Signing**, Play-installed builds use this key automatically —
   no action needed beyond keeping Play App Signing enabled.
   - If you **sideload** test APKs signed with a separate **upload key**, App Links won't verify for
     those installs until that upload key's SHA-256 is **also** added to `assetlinks.json`. Send me
     that fingerprint and I'll add it.
   - If the signing key ever changes, the published SHA-256 must be updated too (see
     `docs/PLAY_APP_SIGNING_GUIDE.md`).

---

## 4. Build, sign, and upload

1. Build a **signed `.aab`** (Android App Bundle) with your upload key.
2. Upload to a Play track (start with **internal testing**) for the **`com.babelwords.app`** app,
   with **Play App Signing** enabled.
3. Publish the **web app** too (if not already) so the updated `assetlinks.json` is live in
   production.

---

## 5. Verify

1. **assetlinks is live and public (no code needed):**
   ```bash
   curl -s https://linguagt.com/.well-known/assetlinks.json
   # expect package com.babelwords.app + the 15:5D:… fingerprint
   ```
2. **Google's Digital Asset Links API resolves it:**
   ```
   https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://linguagt.com&relation=delegate_permission/common.handle_all_urls
   ```
3. **On a device with the Play build installed:**
   ```bash
   adb shell pm get-app-links com.babelwords.app
   # the linguagt.com domain should report: verified
   ```
4. **Gate works:** the app opens the game in production (cookie set), while a fresh browser with no
   code shows the "Private Access" page.
5. **No leftover old package:** `adb shell pm list packages | grep babelwords` shows the new id, and
   a project search for `com.linguawonder.app` returns nothing in source.

---

## 6. Quick checklist

- [ ] `applicationId` + `namespace` → `com.babelwords.app`
- [ ] All `.kt`/`.java` `package`/`import` declarations + source folders → `com.babelwords.app`
- [ ] Fresh `google-services.json` with `package_name = com.babelwords.app`
- [ ] AdMob / other SDK consoles re-registered for the new package
- [ ] `BABELWORDS_ACCESS_TOKEN` build secret == server `SITE_ACCESS_TOKEN` (not hardcoded)
- [ ] First load uses `https://linguagt.com/?access=<token>`; cookies enabled
- [ ] Manifest deep-link `intent-filter` has `android:autoVerify="true"` for `linguagt.com`
- [ ] App published via Play App Signing (key SHA-256 matches `assetlinks.json`)
- [ ] (If sideloading) upload-key SHA-256 added to `assetlinks.json`
- [ ] Web app republished so production serves the new `assetlinks.json`
- [ ] `adb shell pm get-app-links com.babelwords.app` → verified
