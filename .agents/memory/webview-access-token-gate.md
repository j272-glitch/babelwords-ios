---
name: WebView production access gate (query param + cookie)
description: How the app passes the site access code to the externally-hosted web app's production gate, and why it uses ?access= not a User-Agent marker.
---

The externally-hosted web app (linguagt.com) has a server-side access gate (`server/accessGate.ts` on the web side). It accepts the code three ways: `?access=<code>` / `?access_token=<code>` query param (sets a 30-day `site_access` cookie then redirects to clean URL), an `x-access-token` header, or the `site_access` cookie. Public/no-code paths: `/health`, `/.well-known/*`, `/privacy-policy`, `/manifest.json`, robots/sitemap/app-ads/security.txt. The gate is OFF on dev/preview, ON only in production.

The Android app passes the code via the **query-param method**: on first load it does `loadUrl("$WEB_APP_URL/?access=$token")` and enables cookies (`CookieManager` accept + third-party), so subsequent requests ride the `site_access` cookie. Token = `BuildConfig.ACCESS_TOKEN`, sourced from env `BABELWORDS_ACCESS_TOKEN` (CI) or `local.properties`, default empty (so empty token → loads plain URL for dev). CI passes it via a `BABELWORDS_ACCESS_TOKEN` GitHub secret wired into the workflow's top-level `env:` block.

**Why query-param+cookie, not a User-Agent marker:** an earlier attempt appended `BabelWordsApp/<token>` to the WebView User-Agent, but that did NOT match the server's actual gate, which checks query/header/cookie. Match the server's real contract, not an invented one.

**Critical:** the app's token must EXACTLY equal the server's `SITE_ACCESS_TOKEN` secret. Rotating means updating both. Soft gate only (token is extractable from the APK) — not real auth.
