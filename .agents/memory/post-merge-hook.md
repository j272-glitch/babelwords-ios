---
name: Post-merge hook configuration
description: Replit post-merge setup requires a configured executable script and protected .replit updates must use the platform configuration flow.
---

Post-merge setup must point `.replit` at an executable, fast, non-interactive script. For this iOS project, the hook validates repository inputs only; native Xcode and CocoaPods work belongs on macOS CI.

**Why:** Replit runs post-merge setup in the Linux workspace, while native iOS compilation is unavailable there. A missing hook fails every task merge before workflow reconciliation.

**How to apply:** Use the supported post-merge configuration API for the script path and timeout, then run the post-merge setup flow to verify both setup and reconciliation.