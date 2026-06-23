---
name: AdMob auto-show in Firebase Test Lab
description: How/why ads auto-display only inside Test Lab so the recorded video proves ads SHOW (not just load), and the safety rules that keep it out of production.
---

# AdMob Test Lab auto-show

Robo (the automated crawler in Firebase Test Lab) taps native buttons but cannot reliably
click the ad-trigger buttons that live inside the WebView (the web app at linguagt.com), so
on its own a Test Lab run only proves ads **load**, never that they **show**.

To capture an ad actually rendering on the test video, the app self-detects Test Lab via the
system setting `firebase.test.lab == "true"` (set only on Test Lab devices, never on a real
user's phone). When in Test Lab it: switches to Google's official **test** ad units, then
auto-shows the interstitial once, then the rewarded ad once (after the interstitial closes).

**Why test ad units:** showing **real** ad units under automated tests can be flagged by
AdMob as invalid traffic and risk the account. Never auto-show live units.

**Why the "already shown" guard must be set on render, not on the show() call:**
if you set the one-shot guard before `ad.show()` actually renders, a transient lifecycle
failure (activity not resumed, etc.) consumes the guard and the capture is lost with no
retry. Set `hasAutoShown*` only inside `onAdShowedFullScreenContent`, and bound retries with
an attempt counter so a permanently-failing show can never loop
(show -> fail -> preload -> onAdLoaded -> show -> ...).

**How to apply:** any future "do X automatically in Test Lab" hook should gate on
`firebase.test.lab`, prefer test resources, mark success on the real success callback (not
the trigger), and bound retries.
