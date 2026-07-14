# Firebase Setup Guide — BabelWords Android (com.babelwords.com)

Complete step-by-step instructions to register a **new** Android app in Firebase (or an existing project) so the GitHub Actions CI build succeeds with Firebase Crashlytics and Analytics enabled.

> **Time required:** ~10 minutes  
> **Prerequisites:** Google account, access to GitHub repository secrets, the Firebase Console

---

## Step 1: Create or Open a Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Sign in with the same Google account that owns the Play Console app
3. Either:
   - **Create new project:** Click **Create a project** → name it "BabelWords" (or "LinguaWonder") → Continue → enable/disable Google Analytics as you wish → **Create project**
   - **Open existing project:** Click the project name on the dashboard

> **Note:** A Firebase project is separate from your Play Console app. One Firebase project can contain multiple apps (Android, iOS, Web). Play Console and Firebase are linked later via "App linking" in Firebase settings.

---

## Step 2: Add the Android App to Firebase

1. In Firebase Console, click the **gear icon (⚙️) next to "Project Overview"** → **Project settings**
2. Scroll to **Your apps** → Click **��** (Add app) → Select **Android (⚙️)**
3. Fill in the registration form:

| Field | Value |
|-------|-------|
| **Android package name** | `com.babelwords.com` |
| **App nickname** | `BabelWords` |
| **Debug signing certificate SHA-1** | *(optional — skip for now)* |

4. Click **Register app**

> **Critical:** The package name must be exactly `com.babelwords.com` — not `.app`, not `.linguawonder.app`. If you mistype it, the build will fail with the same error again.

---

## Step 3: Download `google-services.json`

1. After registration, Firebase shows a button: **Download google-services.json**
2. Click it → save the file to your computer
3. **Do not rename it** — keep the exact filename `google-services.json`

The file contains:
- `project_number` and `project_id` (Firebase project identity)
- `package_name`: must be `com.babelwords.com`
- API keys (safe to embed in the app — they are not secret)

---

## Step 4: Verify the Package Name Inside the File

Open `google-services.json` in any text editor and confirm:

```json
{
  "client": [
    {
      "client_info": {
        "android_client_info": {
          "package_name": "com.babelwords.com"
        }
      }
    }
  ]
}
```

If it says `com.babelwords.app` or anything else, go back to Step 2 and register again with the correct name.

---

## Step 5: Base64-Encode the File for GitHub

GitHub secrets cannot store raw JSON files — you must encode it as base64 text.

### On macOS / Linux (Terminal):

```bash
# Navigate to the folder containing google-services.json
cd ~/Downloads

# Encode and copy to clipboard
base64 -i google-services.json | pbcopy
```

### On Windows (PowerShell):

```powershell
# Encode to base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\Downloads\google-services.json")) | Set-Clipboard
```

### On Windows (Command Prompt / Git Bash):

```bash
cd ~/Downloads
base64 -w 0 google-services.json > google-services.json.b64.txt
```

Then open `google-services.json.b64.txt` and copy the entire contents.

> The base64 string is long (≈4,000–6,000 characters). That's normal.

---

## Step 6: Set the GitHub Secret

1. Go to your GitHub repository: `github.com/j272-glitch/babelwords-android`
2. Click **Settings** tab → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Fill in:

| Field | Value |
|-------|-------|
| **Name** | `GOOGLE_SERVICES_JSON_BASE64` |
| **Secret** | *(Paste the entire base64 string from Step 5)* |

5. Click **Add secret**

> **If this secret already exists:** click the pencil icon to edit it, delete the old value, and paste the new one.

---

## Step 7: Trigger a Rebuild

1. In your GitHub repository, go to **Actions** tab
2. Click **BabelWords Android Build** on the left
3. Click **Run workflow** → leave defaults → **Run workflow**

The build will:
1. Download the secret → decode → write `android/app/google-services.json`
2. Check that `package_name` matches `com.babelwords.com` → ✅ passes
3. Enable Firebase Crashlytics + Analytics in the build
4. Produce the signed AAB

---

## Step 8: Verify the Build Output

In the GitHub Actions log, look for these lines:

```
✅ Wrote android/app/google-services.json (4,892 bytes) — Firebase will be ENABLED
✅ google-services.json references com.babelwords.com
✅ Firebase ENABLED (google-services.json found)
```

If you see these, the build succeeded with Firebase active. If you see:

```
⚠️ google-services.json package name does NOT match com.babelwords.com.
Disabling Firebase for this build...
```

Then the secret still has the wrong package name — go back to Step 2.

---

## Step 9: Link Firebase to Google Play (optional but recommended)

This enables Crashlytics to show crash-free stats in Play Console and allows Google Analytics to track Play Store conversions.

1. In Firebase Console → **Project settings** → **Integrations** tab
2. Click **Google Play** → **Link**
3. Select your BabelWords app from the Play Console list
4. Confirm → Done

---

## Quick Reference: What Happens Without Firebase

If `GOOGLE_SERVICES_JSON_BASE64` is missing, wrong, or mismatched, the CI workflow **automatically falls back** to a build without Firebase:

| Feature | With Firebase | Without Firebase |
|---------|--------------|------------------|
| Crashlytics crash reporting | ✅ Active | ❌ Disabled (crashes won't be logged) |
| Google Analytics | ✅ Active | ❌ Disabled (no usage stats) |
| Firebase Performance | ✅ Active | ❌ Disabled |
| Build succeeds? | ✅ Yes | ✅ Yes — the app works fine |

The app functions identically to users. Firebase is analytics/crash-logging only — not core functionality.

---

## Troubleshooting

### "No matching client found for package name"
The `google-services.json` in your secret lists a different package name than `com.babelwords.com`. Follow Steps 2–6 again.

### "Failed to decode GOOGLE_SERVICES_JSON_BASE64"
The base64 string is corrupted. Re-encode the file carefully — do not add extra spaces or line breaks.

### Build succeeds but Firebase is disabled
The CI printed the warning and removed the JSON. You need to update the secret with a correctly-matched `google-services.json`.

---

## Related: Web-Side `assetlinks.json`

Don't forget to also update the Digital Asset Links file on `linguagt.com` so Android App Links work:

```json
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
```

See `docs/WEB_APP_UPDATE_INSTRUCTIONS.md` for the full web-side checklist.
