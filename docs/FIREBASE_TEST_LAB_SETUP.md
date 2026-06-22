# Firebase Test Lab — Live Cloud-Device Ad Test (CI)

This is the **complete, start-to-finish** guide to running the BabelWords release APK on a
**real Google cloud phone** straight from GitHub Actions — recording **video + Logcat** so
you can actually watch whether ads load and show. Builds are CI-only (no local device), so
this is the repeatable way to see what ads really do on a device.

The CI job is already wired into `.github/workflows/android-sdk-update-v1.yml`
(job: **Firebase Test Lab (Robo + Logcat + Video)**). Everything below is the one-time
account/console setup you do yourself, then how to run it.

> **App identity used below**
> - Package name: `com.babelwords.app`
> - Builds: GitHub Actions only (repo `j272-glitch/babelwords-android`)
> - Test type: **Robo** (auto-explores the app; no test code to write)

---

## Overview of what you'll do (once)

1. Create or pick a **Firebase project** (a Firebase project *is* a Google Cloud project).
2. *(Optional)* Register the **BabelWords Android app** in that project.
3. Turn on **Blaze billing**.
4. Enable the **Cloud Testing API** and **Cloud Tool Results API**.
5. Create a **service account**, give it **Editor**, download its **JSON key**.
6. Add two **GitHub secrets**: `GCP_SA_KEY` and `GCP_PROJECT_ID`.
7. Run the workflow with **`run_firebase_test = true`** and download the result.

Steps 1–6 are one-time. Step 7 is what you do whenever you want a fresh device test.

---

## Step 1 — Create (or choose) the Firebase project

1. Go to **https://console.firebase.google.com**.
2. Click **Add project** (or select an existing one — e.g. you can reuse `linguavibe-1`).
3. Give it a name, e.g. **BabelWords**. Click **Continue**.
4. Google Analytics is **optional** for Test Lab — you can turn it off. Click
   **Create project** and wait for it to finish.

> A Firebase project and a Google Cloud project are the **same thing** with two consoles.
> Anything you do in the Cloud console (Steps 3–5) applies to this Firebase project.

**Find your project ID now (you'll need it for Step 6):**
In the Firebase console click the **gear icon → Project settings**. The **Project ID**
(e.g. `babelwords-xxxxx` or `linguavibe-1`) is shown at the top. It is **not** the same as
the friendly name — copy the exact ID.

---

## Step 2 — (Optional) Register the BabelWords Android app

For a **Robo test you do NOT need to register the app or add `google-services.json`** —
Test Lab just installs and runs the APK you give it, and BabelWords doesn't use the
Firebase SDKs. Do this step only if you also want the app to appear in the Firebase
console for other Firebase features.

1. In **Project settings → General → Your apps**, click the **Android** icon.
2. **Android package name:** `com.babelwords.app`
3. **App nickname:** `BabelWords` (anything you like).
4. *(Optional)* **SHA-1**: not needed for Test Lab.
5. Click **Register app**. You can **skip** downloading `google-services.json` and skip
   the SDK/Gradle steps — they aren't required for the device test.

---

## Step 3 — Turn on Blaze billing

1. In the Firebase console, bottom-left, click **Upgrade** (or **gear → Usage and
   billing → Details & settings → Modify plan**).
2. Choose the **Blaze (pay-as-you-go)** plan and attach a billing account.

**Why:** Test Lab's free quota only works from the console by hand. To run it from CI
(via `gcloud`) you need Blaze. A single Robo run costs only a few cents.

---

## Step 4 — Enable the two required APIs

1. Go to **https://console.cloud.google.com** and make sure the project selector at the
   top shows the **same project** from Step 1.
2. Open **APIs & Services → Enabled APIs & services → + Enable APIs and services**.
3. Search for and **Enable** each of these:
   - **Cloud Testing API**
   - **Cloud Tool Results API**

---

## Step 5 — Create the service account (the "robot" CI signs in as)

1. Cloud console → **IAM & Admin → Service Accounts → + Create service account**.
2. **Name:** e.g. `github-testlab`. Click **Create and continue**.
3. **Grant this service account access:** add the role **Editor**
   (it needs to write the test results to storage). Click **Continue → Done**.
4. Open the new service account → **Keys** tab → **Add key → Create new key → JSON →
   Create**. A `.json` file downloads. **Treat it like a password** — don't commit it.

---

## Step 6 — Add the two GitHub secrets

In the repo **`j272-glitch/babelwords-android` → Settings → Secrets and variables →
Actions → New repository secret**, add:

| Secret name | Value |
|-------------|-------|
| `GCP_SA_KEY` | The **entire contents** of the service-account JSON file from Step 5 |
| `GCP_PROJECT_ID` | The **Project ID** you copied in Step 1 (e.g. `linguavibe-1`) |

The workflow reads both automatically. Don't paste them anywhere else.

---

## Step 7 — Run the test

1. Repo → **Actions** tab.
2. Open the **"BabelWords Android Build - AAB with Full Diagnostics (Linux)"** workflow.
3. Click **Run workflow**.
4. Set **`run_firebase_test`** to **`true`** (leave the other inputs as-is). Run it.

The normal build runs first, then the Firebase job installs the APK on a Google cloud
phone, auto-explores it (triggering your ad flow), and records everything. When it
finishes, scroll to the bottom of the run and download the
**`firebase-testlab-v<version>-<code>`** artifact for the video + logcat.

> The job is **gated**: it only runs on a manual run with `run_firebase_test = true`.
> Normal pushes/PRs and normal builds never trigger it, so it never costs you anything
> unless you ask for it. If the build only produced an AAB (no APK), the job skips itself
> instead of failing.

---

## Reading the results (the ad funnel)

The job prints a funnel in its log. Look for these markers in order:

| Marker | Means |
|--------|-------|
| `LOADED` | Ad cached and ready ✓ |
| `Attempting to show` / `Auto-show` | A show was triggered |
| Guard rejection (`not resumed`, `background`, `Already showing`) | Show blocked before it reached the screen |
| `FailedToShow` + code | The failure was reported back to the web app |
| `IMPRESSION` | **The ad actually rendered** ✓ (the win) |

A healthy run shows `LOADED` → `Attempting to show` → `IMPRESSION` with no guard
rejections. For deep triage, open the full `logcat.txt` in the downloaded artifact.

> **Tip for the BabelWords bridge debate:** the logcat from a real run is also the
> fastest way to confirm whether the live site calls `window.AdBridge` or
> `window.AndroidAdBridge` — search the log for those names and for the `AdBridge`
> `fireEvent` lines.

---

## No-Blaze / iPhone-only alternative (free, by hand)

If you'd rather not enable Blaze billing:

1. Run a normal build and download the **release APK** artifact (works on an iPhone in
   Safari).
2. Open the **Firebase console → (your project) → Test Lab**.
3. Click **Run a test → Robo test**, upload the APK, enable video, and run on the free
   **console** quota.
4. Watch the video and read the Logcat right in the browser.

This needs only Steps 1–2 above (no service account, no GitHub secrets).

---

## Gotchas (already handled in the workflow — don't undo them)

- **No custom results bucket.** The job lets Test Lab auto-create its own `test-lab-*`
  bucket and parses the path from gcloud's output. Creating your own bucket needs extra
  permissions and will fail the job.
- **Colon-free artifact.** Test Lab folders are timestamped with colons (e.g.
  `21:12:16`), and the GitHub upload step rejects any path containing `:`. The job copies
  just `logcat.txt`, `video.mp4`, and `firebase-run.log` into a flat, colon-free folder
  before uploading.
- **APK-gated.** If the build only produced an AAB and no APK, the job skips itself
  gracefully instead of failing.
- **Project ID, not project name.** `GCP_PROJECT_ID` must be the exact **Project ID**
  from Firebase **Project settings**, not the display name.
