# Web App — Update Instructions for the BabelWords Android App

**Audience:** Whoever manages the web app served at `linguagt.com` (the site the Android app wraps).

**Why this is needed:** The Android app was rebranded and now uses a new package name and a
production access gate. Two things on the **web side** must be in sync for the app to work:

1. The Digital Asset Links file (`assetlinks.json`) must list the **new package name**, so Android
   App Links verification passes.
2. The server access gate's token must match the token compiled into the app.

Nothing in this document requires changing the Android app — these are **web-app-side** changes.

---

## 1. Update `assetlinks.json` (App Links verification)

The website serves a Digital Asset Links file at:

```
https://linguagt.com/.well-known/assetlinks.json
```

It must list the app's **new package name** and the SHA-256 fingerprint of the signing
certificate the installed app uses.

### What changed
- **Old package name:** `com.linguawonder.app`
- **New package name:** `com.babelwords.app`

### What to publish

Update the served `assetlinks.json` (and any second copy your web repo keeps in sync, e.g.
`public/.well-known/assetlinks.json` and `client/public/.well-known/assetlinks.json`):

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.babelwords.app",
      "sha256_cert_fingerprints": [
        "D4:1D:60:84:0C:13:6A:3B:95:9E:A7:11:6F:84:00:70:06:42:9B:11:8C:7F:96:31:14:7E:0D:05:D4:7A:AB:8B"
      ]
    }
  }
]
```

> **Important about the fingerprint:** the value above is the BabelWords upload/keystore
> fingerprint. If the app is published with **Google Play App Signing**, Google re-signs the app
> with a *different* key, and that key's SHA-256 is the one installed devices actually use. Get it
> from **Play Console → Test and release → Setup → App integrity → App signing**, and list it too.
> You may include **multiple** fingerprints (e.g. Play App Signing key + upload key) for a smooth
> rollout:

```json
"sha256_cert_fingerprints": [
  "PLAY_APP_SIGNING_SHA256_FINGERPRINT_HERE",
  "UPLOAD_KEY_SHA256_FINGERPRINT_HERE"
]
```

Keep it valid JSON — no trailing commas, no placeholder text left behind.

### Keep it publicly reachable
The access gate must continue to **whitelist `/.well-known/*`** so this file loads without an
access code. App Links verification and the access gate are independent; verification must work
even when the rest of the site is restricted.

---

## 2. Keep the access-gate token in sync

The Android app passes the site access code on first load using the query parameter the gate
already supports:

```
https://linguagt.com/?access=<code>
```

The server then sets the `site_access` cookie (30 days) and the app rides that cookie afterward.

**Requirement:** the code compiled into the app (GitHub secret `BABELWORDS_ACCESS_TOKEN`) must be
**exactly equal** to the server's `SITE_ACCESS_TOKEN`. If you rotate one, rotate both, or the app
will land on the "access required" page.

No web code change is needed here as long as the gate already accepts `?access=`,
`x-access-token`, or the `site_access` cookie. This is a soft gate (the code is extractable from an
installed app), not real authentication.

---

## 3. Deploy and verify

1. **Publish the web app** so the updated `assetlinks.json` is live.
2. Confirm it loads publicly (no access code required):
   ```bash
   curl -s https://linguagt.com/.well-known/assetlinks.json
   ```
3. Check Google's Digital Asset Links API returns your statement with the new package + fingerprint:
   ```
   https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://linguagt.com&relation=delegate_permission/common.handle_all_urls
   ```
4. On a device with the new build installed:
   ```bash
   adb shell pm get-app-links com.babelwords.app
   # the linguagt.com domain should report: verified
   ```
5. Sanity-check the gate: opening `https://linguagt.com/?access=<the token>` should set the cookie
   and load the site; opening the site fresh without a code (in production) should show the access
   page.

---

## 4. Notes

- Re-run section 1 whenever the **signing key** changes (resetting Play App Signing changes the
  fingerprint again).
- If Play Console ever shows a different package name than `com.babelwords.app`, update
  `package_name` to match — the app and `assetlinks.json` must always agree.
