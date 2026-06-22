# Firebase Test Lab — Live Cloud-Device Ad Test (CI)

This explains how to run the BabelWords release APK on a **real Google cloud phone**
straight from GitHub Actions, record **video + Logcat**, and download them — so you can
actually watch whether ads load and show, even though all builds are CI-only (no local
device).

The CI job is already wired into `.github/workflows/android-sdk-update-v1.yml`
(job: **Firebase Test Lab (Robo + Logcat + Video)**). You only have to do the one-time
account setup below and then trigger the workflow with the test turned on.

---

## What the test does

1. Takes the **release APK** that the normal build job produced.
2. Installs it on a Google cloud phone and **auto-launches it** (a Robo test taps around
   the app on its own), which triggers your startup ad flow.
3. Records the screen to **video** and captures the full **Logcat**.
4. Scans the Logcat for the ad funnel (`LOADED` → `Attempting to show` → `IMPRESSION`)
   and prints it in the job log.
5. Uploads `video.mp4`, `logcat.txt`, and `firebase-run.log` as a downloadable artifact
   named `firebase-testlab-v<version>-<code>`.

---

## One-time account setup (you must do this once — it can't be done from code)

These steps happen in the Google Cloud / Firebase console and in your GitHub repo
settings. They only need to be done once.

### 1. Turn on billing (Blaze)
- Open the Google Cloud project you want to use (e.g. `linguavibe-1`).
- Enable the **Blaze (pay-as-you-go)** billing plan.
- **Why:** Test Lab's free quota only works from the Firebase console by hand. To run it
  from CI (via `gcloud`) you need Blaze. A single Robo run is a few cents.

### 2. Enable the two required APIs
In the Cloud console → **APIs & Services → Enable APIs**, enable:
- **Cloud Testing API**
- **Cloud Tool Results API**

### 3. Create a service account (the "robot user" CI logs in as)
- Cloud console → **IAM & Admin → Service Accounts → Create service account**.
- Give it the **Editor** role (it needs permission to write test results to storage).
- After creating it, open it → **Keys → Add key → Create new key → JSON**, and download
  the JSON file. Keep it safe — treat it like a password.

### 4. Add two GitHub repo secrets
In the `j272-glitch/babelwords-android` repo → **Settings → Secrets and variables →
Actions → New repository secret**, add:

| Secret name | Value |
|-------------|-------|
| `GCP_SA_KEY` | The **entire contents** of the service-account JSON file you downloaded |
| `GCP_PROJECT_ID` | Your Cloud project id (e.g. `linguavibe-1`) |

> The workflow reads these two secrets automatically. Do not paste them anywhere else.

---

## How to run the test

1. Go to the repo → **Actions** tab.
2. Pick the **"BabelWords Android Build - AAB with Full Diagnostics (Linux)"** workflow.
3. Click **Run workflow**.
4. Set **`run_firebase_test`** to **`true`** (leave the other inputs as they are).
5. Run it.

The build job runs first as usual. Then the Firebase job runs the APK on the cloud phone.
When it finishes, scroll to the bottom of that workflow run and download the
**`firebase-testlab-v<version>-<code>`** artifact to watch the video and read the logcat.

> The job is **gated**: it only runs on a manual run with `run_firebase_test = true`.
> Normal pushes/PRs and normal builds never trigger it, so it never costs you anything
> unless you ask for it.

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

---

## No-Blaze / iPhone-only alternative

If you'd rather not enable Blaze billing, you can do it by hand for free:

1. Run a normal build and download the **release APK** artifact (works fine on an iPhone
   in Safari).
2. Open the **Firebase console → Test Lab**.
3. Upload the APK and start a **Robo test** with video, on the free **console** quota.
4. Watch the video and read the Logcat right in the browser.

---

## Notes / gotchas (already handled in the workflow — don't undo them)

- **No custom results bucket.** The job lets Test Lab auto-create its own `test-lab-*`
  bucket and parses the path from gcloud's output. Creating your own bucket needs extra
  permissions and will fail the job.
- **Colon-free artifact.** Test Lab folders are timestamped with colons (e.g.
  `21:12:16`), and the GitHub upload step rejects any path containing `:`. The job copies
  just `logcat.txt`, `video.mp4`, and `firebase-run.log` into a flat, colon-free folder
  before uploading.
- **APK-gated.** If the build only produced an AAB and no APK, the job skips itself
  gracefully instead of failing.
