# Google Play Data Safety form — codetalker companion v0.1.0

These are the answers to enter into the Play Console's "Data Safety"
section verbatim. They mirror the privacy policy at
`docs/PRIVACY-POLICY.md`. When the privacy policy changes, these
answers must be updated in lockstep.

Last reviewed: **2026-05-09** (covers app v0.1.0).

---

## Section 1 — Data collection and security

### 1.1 Does your app collect or share any of the required user data types?

> **Yes** — but only one optional category (Crashes) and only when the
> user explicitly opts in.

The app collects no data by default. The crash-report data category
is opt-in; users see the consent dialog once after onboarding and can
flip the setting any time in Preferences.

### 1.2 Is all of the user data collected by your app encrypted in transit?

> **Yes.** When the daemon URL uses `https://`, traffic is TLS
> encrypted. Crash reports to Sentry are sent over HTTPS; Sentry's
> SDK enforces TLS.

For LAN-only `http://` daemon URLs, traffic remains within the user's
own network and is authenticated by the pairing token. There is no
plaintext PII transmitted.

### 1.3 Do you provide a way for users to request that their data be deleted?

> **Yes.** The privacy policy at `docs/PRIVACY-POLICY.md` §7 documents
> two paths: (1) clear app data via Android Settings, which wipes
> 100 % of the on-device store; and (2) email
> becky@nativeteachingaids.com to purge any opted-in crash reports
> from Sentry.

---

## Section 2 — Data types

For each Data Safety form question "Does your app collect or share
any of the following user data types?", here is the answer:

### Personal info

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Name | No | No | n/a | n/a |
| Email address | No | No | n/a | n/a |
| User IDs | No | No | n/a | n/a |
| Address | No | No | n/a | n/a |
| Phone number | No | No | n/a | n/a |
| Race and ethnicity | No | No | n/a | n/a |
| Political or religious beliefs | No | No | n/a | n/a |
| Sexual orientation | No | No | n/a | n/a |
| Other info | No | No | n/a | n/a |

### Financial info

All categories: **No**. The app has no payment surface, no IAP, and
no ads.

### Health and fitness

All categories: **No**. The app does not access any health-related
sensor.

### Messages

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Emails | No | No | n/a | n/a |
| SMS or MMS | No | No | n/a | n/a |
| Other in-app messages | No | No | n/a | n/a |

### Photos and videos

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Photos | No | No | n/a | n/a |
| Videos | No | No | n/a | n/a |

The CAMERA permission is used for QR pairing only. The decoded QR
content is held in memory for the duration of the scan and then
discarded; no image is saved.

### Audio files

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Voice or sound recordings | **No** — see explanation below | No | n/a | n/a |
| Music files | No | No | n/a | n/a |
| Other audio files | No | No | n/a | n/a |

> **Voice handling explanation (paste verbatim into the Play Console
> when prompted):** "The app captures the microphone signal during
> push-to-talk and streams it directly to the codetalker desktop
> daemon running on the user's own machine. The audio is never
> written to device storage, never sent to the developer's servers
> (the developer does not operate any servers), and never sent to
> any third party. The codetalker desktop is the user's own server."

### Files and docs

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Files and documents | No | No | n/a | n/a |

### Calendar

All categories: **No**.

### Contacts

All categories: **No**.

### App activity

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| App interactions | No | No | n/a | n/a |
| In-app search history | No | No | n/a | n/a |
| Installed apps | No | No | n/a | n/a |
| Other user-generated content | No | No | n/a | n/a |
| Other actions | No | No | n/a | n/a |

### Web browsing

All categories: **No**.

### App info and performance

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Crash logs | **Yes — opt-in only** | Shared with Sentry | Optional | App functionality / Analytics |
| Diagnostics | **Yes — opt-in only** | Shared with Sentry | Optional | App functionality / Analytics |
| Other app performance data | No | No | n/a | n/a |

> **Crash logs explanation:** "Stack traces, app version, and device
> model only. Sent to Sentry (https://sentry.io). Default OFF; user
> sees an explicit consent dialog after onboarding. Setting can be
> flipped any time in Preferences."
>
> **Diagnostics explanation:** "Sentry's auto-included SDK context:
> session id (random per launch), free memory, screen size, locale.
> No PII, no audio, no transcripts, no IP address (sendDefaultPii =
> false)."

### Device or other IDs

| Type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Device or other IDs | No | No | n/a | n/a |

(Sentry ships a device install ID if and only if `sendDefaultPii =
true`; we set it to `false` so no device IDs are dispatched.)

---

## Section 3 — Data usage and handling

For the **only** data type we collect (Crashes / Diagnostics, opt-in):

| Question | Answer |
|---|---|
| Is this data collection optional? | **Yes** |
| Why do you collect or share this user data? | **App functionality** + **Analytics**. Specifically: identify common crash patterns and prioritise stability work. |
| Is this data processed ephemerally? | No (Sentry retains for 90 days by default) |
| Is this data shared with any third parties? | Yes — Sentry. |
| Are users notified before this data is collected? | Yes — explicit in-app consent dialog with title "Help improve codetalker?" |
| Can users request that this data be deleted? | Yes — email becky@nativeteachingaids.com. |

---

## Section 4 — Privacy policy URL

```
https://github.com/OpenCircuitDev/codetalker-pro/blob/main/companion-android/docs/PRIVACY-POLICY.md
```

Hosted on GitHub once the open-core repo lands per CCT-30. Until
then the policy is shipped in-app via the About screen and on the
GitHub Releases page.

---

## Section 5 — Children

| Question | Answer |
|---|---|
| Is your app's target audience children under 13? | No |
| Do you knowingly collect personal info from children? | No |
| Do you have a separate, COPPA-compliant disclosure? | n/a — we don't target children. |

---

## Section 6 — Sign-off

I have reviewed every entry above against the privacy policy at
`docs/PRIVACY-POLICY.md` and confirm the answers match.

- **Reviewer:** Open Circuit Dev
- **Date:** 2026-05-09
