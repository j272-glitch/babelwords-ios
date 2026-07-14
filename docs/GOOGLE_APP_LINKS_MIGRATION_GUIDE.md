# Google App Links — Migrating from the Legacy App to the New Google Play / Cloud App

**Audience:** Whoever manages the Android app in Google Play Console and the signing keys.

**Why this is needed:** Android App Links verification is tied to the app's **signing
certificate**. The current `assetlinks.json` was created for the previous app setup. When the app
is (re)published under the **new Google Play app / Play App Signing key**, that key's SHA-256
fingerprint is different, so verification will fail until `assetlinks.json` lists the new
fingerprint.

---

## 1. How verification works today

The website serves a Digital Asset Links file at:

```
https://linguagt.com/.well-known/assetlinks.json
```

Current contents (served by this repo from `public/.well-known/assetlinks.json`):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.babelwords.com",
      "sha256_cert_fingerprints": [
        "D4:1D:60:84:0C:13:6A:3B:95:9E:A7:11:6F:84:00:70:06:42:9B:11:8C:7F:96:31:14:7E:0D:05:D4:7A:AB:8B"
      ]
    }
  }
]
```

Android verifies an app by fetching this file and confirming it lists the app's `package_name`
plus the SHA-256 fingerprint of the certificate the installed app was signed with. The web access
gate **whitelists `/.well-known/*`**, so this file stays publicly reachable even when the rest of
the site is restricted.

> The fingerprint above belongs to the legacy/previous signing setup. It must be replaced with
> the fingerprint from the new Google Play app (see below).

---

## 2. Get the correct SHA-256 fingerprint from the NEW app

In **Google Play Console** for the new app:

1. Go to **Test and release → Setup → App integrity → App signing**.
2. Copy the **SHA-256 certificate fingerprint** under **"App signing key certificate"**.
   - With **Play App Signing**, Google re-signs your app, so this is the fingerprint that matters
     for installed builds.
3. Also copy the **"Upload key certificate"** SHA-256 if you want links to verify for builds you
   install directly (e.g., internal testing via the upload key).

If you build/sign locally instead, you can read a keystore's fingerprint with:

```bash
keytool -list -v -keystore /path/to/your.keystore -alias your-alias
# look for: SHA256: D4:1D:...
```

---

## 3. Update assetlinks.json

Edit **both** copies in this repo so they stay in sync, then redeploy:

- `public/.well-known/assetlinks.json`  (the one actually served)
- `client/public/.well-known/assetlinks.json`

Confirm `package_name` is correct (currently `com.babelwords.com`) and replace the
fingerprint(s). You may list **multiple** fingerprints to support a transition window (for
example, the new Play signing key + the upload key, or old + new during rollout):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.babelwords.com",
      "sha256_cert_fingerprints": [
        "PLAY_APP_SIGNING_SHA256_FINGERPRINT_HERE",
        "UPLOAD_KEY_SHA256_FINGERPRINT_HERE"
      ]
    }
  }
]
```

Keep it valid JSON (no trailing commas, no placeholder text left behind).

---

## 4. Deploy and verify

1. **Publish the web app** so the new file is live.
2. Confirm it loads publicly (no access code required):
   ```bash
   curl -s https://linguagt.com/.well-known/assetlinks.json
   ```
3. **Google Statement List Tester / Digital Asset Links API:**
   ```
   https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://linguagt.com&relation=delegate_permission/common.handle_all_urls
   ```
   It should return your statement with the new fingerprint.
4. In the **Android app**, ensure the deep-link `intent-filter` uses `android:autoVerify="true"`
   for host `linguagt.com`:
   ```xml
   <intent-filter android:autoVerify="true">
       <action android:name="android.intent.action.VIEW" />
       <category android:name="android.intent.category.DEFAULT" />
       <category android:name="android.intent.category.BROWSABLE" />
       <data android:scheme="https" android:host="linguagt.com" />
   </intent-filter>
   ```
5. On a device with the new build installed:
   ```bash
   adb shell pm get-app-links com.babelwords.com
   # the linguagt.com domain should report: verified
   ```

---

## 5. Notes

- App Links verification and the site **access gate** are independent. The gate never blocks
  `/.well-known/*`, so verification works whether or not the site is restricted.
- If Play Console shows a **different** package name for the new app, update `package_name` in
  `assetlinks.json` to match, and align the Android project (`applicationId`/`namespace`).
- Changing the signing key (e.g., resetting Play App Signing) will change the fingerprint again —
  re-run steps 2–4 whenever the signing key changes.
