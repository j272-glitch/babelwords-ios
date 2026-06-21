---
name: WebView testing access gate via User-Agent
description: Why the app tags requests with a UA marker (not a header/query param) to gate the externally-hosted web app during testing.
---

The app gates access to the externally-hosted web app (linguagt.com) during testing by appending a marker to the WebView `userAgentString` (`BabelWordsApp/<token>`), where the token is `BuildConfig.ACCESS_TOKEN` (set in `app/build.gradle`, overridable via `local.properties` `LINGUALINK_ACCESS_TOKEN`). The web side allows requests only when that marker is present.

**Why UA, not a custom loadUrl header or `?token=` query param:** the web app is a SPA — its own `fetch`/XHR calls and subresource requests would NOT inherit a one-time main-frame header or an initial URL param, but they DO inherit the WebView's User-Agent. UA rides on every request automatically.

**Why this is fine here / limits:** test-only gate. The token is extractable from the APK and is sent to every host the WebView contacts (incl. third parties like AdMob), so it is NOT real authorization — do not treat it as production auth. The real web app is hosted outside this repo, so only the app side lives here; the matching check must be added on linguagt.com.
