# Memory Index

- [BabelWords ad architecture](babelwords-ad-architecture.md) — user's ad-fix guides describe a legacy LinguaGT design (AdMobBridge/AndroidAdBridge/isActivityResumed) that does NOT exist in this repo; grep before porting any guide.
- [Firebase wiring](firebase-wiring.md) — Firebase gated on google-services.json existence; config via GOOGLE_SERVICES_JSON_BASE64 secret (never committed); BoM 33.x required for Kotlin 2.2.
- [Firebase Test Lab CI parsing](firebase-test-lab-ci.md) — gcloud wraps the results-bucket URL in '[ ]'; the path parser MUST strip the trailing ']' or logcat/video never copy.
