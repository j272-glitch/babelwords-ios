# Google Play Store Listing Guide — BabelWords

Everything you need to fill out the Google Play Console listing, with
ready-to-paste copy. Built from the app's actual features and permissions.

> ⚠️ **Name mismatch to resolve before you publish.**
> The app's display name (in `android/app/src/main/res/values/strings.xml` →
> `app_name`) is currently **`LinguaWonder`**, but the package, signing key,
> repo, and AdMob account are all **BabelWords**. The Play listing name should
> match the name users see under the icon on their phone. **Pick one name** and
> make them consistent:
> - If the product is **BabelWords**, change `app_name` to `BabelWords` and
>   rebuild.
> - If it's **LinguaWonder**, use that everywhere below instead.
> The rest of this guide assumes **BabelWords** — swap the name if you decide
> otherwise.

---

## 0. Before you start — checklist

You'll need:
- A Google Play Console account ($25 one-time, already paid if you've published before).
- The signed **AAB** from your GitHub Actions build (`babelwords-v1.0.0-<code>.aab`).
- App icon **512×512** PNG (32-bit, with alpha).
- Feature graphic **1024×500** PNG/JPG.
- At least **2** phone screenshots (4–8 recommended).
- A privacy policy URL (required — see §8).
- Decision on the final app name (see warning above).

---

## 1. App details

| Field | Value |
|---|---|
| **App name** (max 30 chars) | `BabelWords: Voice Translator` |
| **Default language** | English (United States) – en-US |
| **App or game** | App |
| **Free or paid** | Free (with in-app purchases) |
| **Category** | Tools *(alternative: Education)* |
| **Tags** | Translation, Productivity, Communication |
| **Contact email** | *your support email* |
| **Website** | https://linguagt.com |
| **Phone** (optional) | leave blank unless you have a support line |

> The name field is **30 characters max**. `BabelWords: Voice Translator` is 28.
> Other options: `BabelWords – Translate Speech` (29), `BabelWords Translator` (21).

---

## 2. Short description (max 80 characters)

Shows under the app name in search results. Pick one:

```
Real-time voice translation in 36 languages. Speak, hear, and be understood.
```
(76 chars)

Alternatives:
```
Translate speech instantly across 36 languages — just talk and listen.
```
```
Break language barriers. Real-time speech translation in 36 languages.
```

---

## 3. Full description (max 4000 characters)

Paste-ready. Edit any claim that isn't accurate for your build.

```
BabelWords turns your phone into a real-time voice translator. Just speak, and
hear your words in another language — instantly. Whether you're traveling,
meeting someone new, studying, or working across languages, BabelWords helps you
understand and be understood in 36 languages.

REAL-TIME SPEECH TRANSLATION
• Speak naturally and get instant translation
• Two-way conversation mode — pass the phone back and forth and keep talking
• Clear, natural-sounding voice output
• Works across 36 languages

BUILT FOR REAL CONVERSATIONS
• Fast, accurate translations powered by modern AI
• Simple, distraction-free interface
• Designed for travel, business, learning, and everyday life

EARN HINTS BY WATCHING ADS
• Get extra hints for free by watching a short rewarded video
• No pressure — ads are always optional

GO PREMIUM (OPTIONAL)
• Remove ads and unlock more with a subscription
• Scholar and Premium plans available
• Manage or cancel anytime in Google Play

WHY BABELWORDS?
• 36 languages in your pocket
• No more pointing at menus or fumbling with phrasebooks
• Speak with confidence, anywhere

Download BabelWords and start talking across languages today.

—
Microphone access is used only for speech translation while you're using
conversation mode. See our Privacy Policy for details.
```

> **Don't keyword-stuff.** Play's policy penalizes repetitive keywords. Keep it
> readable. Mention "translate / translation / languages" naturally a few times,
> not 20.

---

## 4. Graphic assets

| Asset | Size / format | Required? | Notes |
|---|---|---|---|
| **App icon** | 512×512 PNG, 32-bit w/ alpha | ✅ | Matches the launcher icon. |
| **Feature graphic** | 1024×500 PNG/JPG, no alpha | ✅ | Banner at top of listing. Put the name + one tagline. Keep text away from edges. |
| **Phone screenshots** | 16:9 or 9:16, min 320px, max 3840px | ✅ (min 2) | Show: conversation mode, language picker, a live translation, the "watch ad for hints" flow. |
| **7-inch tablet** | up to 8 screenshots | optional | Only if you support tablets well. |
| **10-inch tablet** | up to 8 screenshots | optional | Same. |
| **Promo video** | YouTube URL | optional | A 15–30s demo of a live translation converts well. |

**Screenshot tips**
- Add a short caption banner on each (e.g. "Speak. Translate. Understand.").
- First 2 screenshots matter most — they show in search. Lead with the
  conversation/translation screen.
- Use a real device frame or clean mockups; keep them consistent.

---

## 5. Content rating

Fill out the **Content rating questionnaire** in Play Console (required). For a
translation app with ads:

- Category: **Utility / Productivity / Communication**
- Violence, sexual content, profanity, drugs, gambling: **No**
- **Does the app share the user's location?** No
- **Does it contain ads?** **Yes** (you serve AdMob interstitial + rewarded ads)
- Expected result: **Everyone / PEGI 3** (the questionnaire decides the final rating).

---

## 6. Ads declaration

In **Play Console → App content → Ads**:
- **Does your app contain ads?** → **Yes.**

The app shows AdMob interstitial and rewarded video ads, so this must be "Yes."
Failing to declare ads is a common rejection reason.

---

## 7. Data safety (required)

Play Console → App content → **Data safety**. Based on this app's permissions and
SDKs (AdMob, Firebase Analytics/Crashlytics, microphone, billing):

**Data collected / shared**

| Data type | Collected? | Shared? | Why |
|---|---|---|---|
| **Audio (voice)** | Yes (transient) | Depends | Microphone is used for speech translation. If audio is sent to a server/AI for translation, declare it as collected. If processed and not stored, you can mark it as not stored — but be accurate. |
| **App activity / interactions** | Yes | Yes (analytics/ads) | Firebase Analytics + AdMob. |
| **Crash logs / diagnostics** | Yes | No | Firebase Crashlytics. |
| **Advertising ID** | Yes | Yes | AdMob uses the `AD_ID` permission for ads. |
| **Purchase history** | Yes | No | Google Play Billing (subscriptions). |
| **Approximate device / network info** | Yes | Yes | Standard for ads/analytics. |

**Security practices to declare**
- Data encrypted in transit: **Yes** (HTTPS to linguagt.com).
- Users can request data deletion: provide a method (email or in-app) and a URL.

> ⚠️ The Data safety form must match what the app **actually** does. Confirm with
> whoever runs the linguagt.com backend exactly what happens to the microphone
> audio (translated and discarded vs. stored) before you submit. Misdeclaring
> here is a serious policy violation.

---

## 8. Privacy policy (required)

A public privacy policy URL is **mandatory** because the app:
- requests the **microphone**,
- uses the **Advertising ID** / serves ads,
- collects analytics and crash data,
- handles **purchases**.

It must disclose: what data is collected, how it's used, who it's shared with
(Google AdMob, Firebase), and how users can request deletion. Host it on
linguagt.com (e.g. `https://linguagt.com/privacy`) and paste the URL into
Play Console → App content → Privacy policy.

---

## 9. In-app products & subscriptions

The app sells subscriptions through Google Play Billing. Create these in
**Play Console → Monetize → Subscriptions** with product IDs that **exactly match
the code**:

| Product ID (must match code) | Type | Suggested name |
|---|---|---|
| `sub_scholar_monthly` | Subscription (monthly) | Scholar |
| `sub_premium_monthly` | Subscription (monthly) | Premium |

For each: set a base plan (monthly, auto-renewing), price per region, a free
trial/intro offer if desired, and a clear benefits description (e.g. "Remove ads,
unlimited hints"). The product IDs are referenced directly by `BillingManager.kt`
— if they don't match, purchases fail with `product_not_found`.

> In the listing's pricing, mark the app **Free** with **in-app purchases** —
> not Paid.

---

## 10. App access (for review)

If any features sit behind login or a paywall, Play Console → App content →
**App access** lets you give reviewers test credentials or steps. Provide:
- a demo account (if login is required), or
- note that core translation works without an account, and subscriptions can be
  reviewed via Play's license-test accounts.

This prevents rejections where the reviewer "can't access the app's features."

---

## 11. Countries, pricing, and release

1. **Countries/regions:** select all where the languages and ads are appropriate
   (usually worldwide).
2. **Pricing:** Free.
3. **Release track:** start with **Internal testing** → **Closed/Open testing**
   → **Production**. Internal testing surfaces crashes and policy issues before a
   public launch.
4. Upload the signed **AAB** (not APK) for production.

---

## 12. Versioning reminder

Each upload needs a **higher version code** than the last. Your GitHub Actions
workflow now auto-increments the version code from the build number (+100 offset),
so every CI build is safe to upload. Version name (e.g. `1.0.0`) is the
human-facing number shown on the listing — bump it for meaningful releases
(`1.0.1`, `1.1.0`, …).

---

## 13. Quick pre-submit checklist

- [ ] App name consistent everywhere (resolve LinguaWonder vs BabelWords)
- [ ] Short description ≤ 80 chars
- [ ] Full description proofread, no keyword stuffing
- [ ] Icon 512×512, feature graphic 1024×500, ≥2 phone screenshots
- [ ] Content rating questionnaire completed
- [ ] **Ads declaration = Yes**
- [ ] Data safety form filled accurately (microphone audio confirmed)
- [ ] Privacy policy URL live and linked
- [ ] Subscriptions created with IDs `sub_scholar_monthly`, `sub_premium_monthly`
- [ ] App access / test instructions provided
- [ ] Signed AAB uploaded with a higher version code
- [ ] Released to a testing track first
```
