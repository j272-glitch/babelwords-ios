---
name: Xcode 26 Swift concurrency and delegate APIs
description: Xcode 26 exposes stricter actor isolation for SDK callbacks and UIKit/WebKit delegate closures.
---

Xcode 26 requires SDK callback values such as Google Mobile Ads objects to stay on the callback's execution context rather than being captured into a new `Task` across an actor boundary. Framework delegate closures may also require explicit `@MainActor @Sendable` annotations on conforming methods.

**Why:** Swift 6 diagnostics turn previously compiling SDK integrations into hard errors or warnings that can prevent delegate dispatch at runtime.

**How to apply:** When upgrading Xcode, inspect the SDK's protocol requirement diagnostics directly; match closure actor/sendable annotations exactly, avoid deprecated compatibility properties, and use `DEVELOPER_DIR` rather than password-protected `sudo xcode-select` on non-interactive self-hosted runners.