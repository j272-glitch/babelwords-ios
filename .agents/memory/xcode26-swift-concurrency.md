---
name: Xcode 26 Swift concurrency and delegate APIs
description: Xcode 26 exposes stricter actor isolation for SDK callbacks and UIKit/WebKit delegate closures.
---

Xcode 26 requires SDK callback values such as Google Mobile Ads objects to stay on the callback's execution context rather than being captured into a new `Task` across an actor boundary. Framework delegate closures may also require explicit `@MainActor @Sendable` annotations on conforming methods.

**Why:** Swift 6 diagnostics turn previously compiling SDK integrations into hard errors or warnings that can prevent delegate dispatch at runtime.

**How to apply:** When upgrading Xcode, inspect the SDK's protocol requirement diagnostics directly; match closure actor/sendable annotations exactly, avoid deprecated compatibility properties, and use `DEVELOPER_DIR` rather than password-protected `sudo xcode-select` on non-interactive self-hosted runners.

For self-hosted macOS CI, pin Bundler 2.5.23 and invoke it explicitly after selecting Homebrew Ruby 3.3; an inherited Bundler 1.17.2 fails on modern Ruby with `untaint` errors.

**Why:** The runner can retain an old tool-cache Bundler even after Ruby is upgraded, causing fastlane to fail before the build begins.

**How to apply:** Install the pinned Bundler in the selected Ruby environment, use `bundle _2.5.23_ ...` for dependency and fastlane commands, and verify `ruby -v`, `which ruby`, and `bundle -v` in CI logs.

Google Mobile Ads load callbacks require a split isolation strategy under Swift 6: consume the ad object directly in the callback's main-thread path, and use a real main-queue hop only for off-main recovery without carrying the non-Sendable ad object.

**Why:** Moving `GADInterstitialAd`/`GADAppOpenAd` into a new actor task or nested `MainActor.assumeIsolated` closure triggers data-race diagnostics; calling `assumeIsolated` off-main is unsafe.

**How to apply:** Keep SDK callback closures nonisolated, consume payloads synchronously on the callback's main-thread path, keep SDK-bound managers main-thread confined, and make off-main fallbacks discard the ad and retry rather than crossing the actor boundary with it. Route timeout/retry continuations back to the main queue before touching manager state.

User Messaging Platform consent-info, form-load, and form-dismiss callbacks should remain nonisolated at the SDK boundary; consume the non-Sendable form synchronously when the callback is on main, and use payload-free main-queue recovery otherwise.

**Why:** Consent callbacks may arrive off the main thread, while the form object must not be transferred across an actor boundary under Swift 6.

**How to apply:** Normalize each UMP callback at its closure boundary, transfer only plain error text or booleans when recovery must hop queues, and keep retry/timeout sleeps cancellation-safe.

GitHub Actions Xcode 26 simulator jobs should include an explicit `arch=arm64` destination when selecting a device by UDID, and repository workflows should use Node 24-native action major versions.

**Why:** Xcode reports ambiguous destination warnings when multiple architecture variants match, while hosted runners now warn when older action majors target deprecated Node 20.

**How to apply:** Add `,arch=arm64` to simulator destinations and keep checkout/artifact actions on current Node 24-native majors; do not edit dependency-generated Pod warnings in vendored sources.

StoreKit 2 signed transaction payloads must be read from the `VerificationResult<Transaction>` wrapper, not from `Transaction` itself.

**Why:** Xcode 26 no longer exposes `jwsRepresentation` on `Transaction`; using the transaction object directly causes a simulator compile failure.

**How to apply:** Preserve the verified `Transaction` for its ID and `finish()` call, but pass the verification wrapper's signed JWS to server-side receipt validation.