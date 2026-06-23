---
name: Firebase Test Lab CI results parsing
description: gcloud wraps the results-bucket URL in brackets; the path parser must strip the trailing ']' or logcat/video never copy.
---

# Test Lab results-bucket parsing quirk

`gcloud firebase test android run` prints the auto-created results bucket as a
bracket-wrapped URL, e.g.
`...at [https://console.developers.google.com/storage/browser/test-lab-XXXX/2026-06-23_00:01:23.750983_qtNd/]`.

The workflow parses that path (we deliberately do NOT pass `--results-bucket`). The grep
`storage/browser/[^[:space:]"]+` captures the **trailing `]`**, so without stripping it the
gs:// path becomes `gs://.../qtNd/]` and the `gsutil cp` silently copies nothing — the
artifact ends up with only `firebase-run.log`, no logcat.txt / video.mp4.

**Fix:** the sed that builds GS_PATH must strip `]` and anything after it
(`s#].*##`) in addition to the query string and trailing slashes.

**Also note:** Test Lab result folders are timestamped with colons (`00:01:23`), which is
why the upload step flattens files into a colon-free folder (upload-artifact rejects `:`).

**Confirmed working:** a Robo run on MediumPhone.arm / Android 33 returned OUTCOME=Passed,
so the end-to-end CI path (build APK -> upload artifact -> Test Lab -> parse bucket) is sound.
