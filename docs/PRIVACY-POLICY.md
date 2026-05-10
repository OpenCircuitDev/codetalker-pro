# Privacy policy — codetalker companion

**Effective date:** 2026-05-09
**Version:** 1.0 (covers companion app v0.1.0)
**Operator:** Open Circuit Dev (becky@nativeteachingaids.com)

This privacy policy describes what data the **codetalker companion**
Android application ("the app", "we") collects, how it stores that
data, who it shares the data with, and how a user can delete it.

The app is **local-first**. There is no operator-side server, no
account system, and no cloud sync. Every interaction flows between
the app and the codetalker desktop daemon running on the user's own
machine.

---

## 1. Summary

**No PII.** No audio recordings. No transcripts. The app is local-first
and runs without an account.

| Category | Status |
|---|---|
| Account required | **No** |
| User identifiers collected | **None** |
| Audio recordings stored | **No** |
| Speech transcripts stored | **No** |
| Session contents (Claude Code messages) stored | **No** |
| Pairing token stored on device | **Yes** (encrypted) |
| Daemon URL stored on device | **Yes** (encrypted) |
| Crash reports sent to a third party | **Only if you opt in** |

If you never opt into crash reports, **no data ever leaves your
device** — every byte the app produces is delivered exclusively to
the codetalker desktop you paired with.

---

## 2. What data the app handles

The app handles three pieces of data:

### 2.1 Pairing token + daemon URL

When you scan the QR code from your codetalker desktop dashboard,
the app stores:

- **Daemon URL:** the LAN or Tailnet address of your desktop's
  codetalker daemon (e.g. `http://192.168.1.86:17832`).
- **Pairing token:** a random secret the daemon issues to authenticate
  the app's API requests.

These values are stored in `EncryptedSharedPreferences` (AES-GCM
encryption, key in the Android Keystore). They never leave the
device. They are sent only to the daemon URL itself, in the
`X-CCT-Pairing-Token` header.

### 2.2 Voice input

When you press the Beam Pro side button, the app captures the audio
stream from the microphone, packetizes it, and sends it directly to
the codetalker daemon you paired with. The audio is **never**:

- written to disk on the device,
- sent to any third party,
- inspected, logged, or otherwise retained by the app.

The codetalker desktop daemon is your own server — its data-handling
practices are documented in the desktop app's privacy policy.

### 2.3 Voice + caption output

The daemon streams synthesised speech and live captions back to the
app. The app:

- plays the audio through your selected output device.
- renders the caption on the AR HUD (when paired with XREAL Air 2 Pro).

Output is held only in memory; it is not stored, logged, or copied
anywhere else. Caption text scrolls off the HUD without being
retained.

---

## 3. Permissions

| Permission | Why we ask | Data collected |
|---|---|---|
| `RECORD_AUDIO` | Push-to-talk speech capture during the side-button gesture. | Live audio stream. **Not stored.** Sent only to the paired codetalker daemon. |
| `CAMERA` | One-time QR pairing only. | Single QR image, decoded in memory, discarded after pairing. |
| `INTERNET` + `ACCESS_NETWORK_STATE` | Reach the codetalker desktop over LAN / Tailnet. | None — these are network capability flags, not data. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep audio alive when the screen is off (Android requires the foreground notification). | None. |
| `POST_NOTIFICATIONS` | Show the foreground service status notification. | None. |
| `RECEIVE_BOOT_COMPLETED` | Optional opt-in: re-launch the foreground service after device boot. | Boot timestamp (not transmitted). |

Every dangerous permission ships with a rationale screen the user
must dismiss before the system permission prompt opens, so the user
sees the "why" before accepting.

---

## 4. Crash reporting (opt-in only)

The app integrates the [Sentry Android SDK](https://github.com/getsentry/sentry-java).
**Sentry is not initialized** until you set "Send anonymous crash
reports" to ON in Preferences. Default: OFF.

When you opt in, and the app encounters an unhandled exception, the
following is sent to the Sentry project owned by Open Circuit Dev:

- **Stack trace** of the crashing thread.
- **App version** (e.g. `0.1.0`, build `1`).
- **Device model + Android version** (e.g. "XREAL Beam Pro X4200,
  Android 14").
- **Sentry SDK auto-included context:** session id (random per launch,
  not tied to any account), free memory, screen size, locale.

The following is **never** sent:

- Audio recordings or transcripts.
- Caption text or session contents.
- Daemon URL, pairing token, or any other secret.
- Personally identifying information.
- IP address (Sentry's `sendDefaultPii` is set to `false`).
- Any breadcrumb captured before the crash unless that breadcrumb
  was emitted by the app's own code (we never instrument the
  daemon-bound HTTP traffic).

You can disable crash reporting at any time in Preferences. Disabling
takes effect immediately; the SDK is shut down and no further events
are dispatched.

If you want crash reports to be sent to your own Sentry project
instead of Open Circuit Dev's, set the `CCT_SENTRY_DSN` Gradle
property (or the GitHub Action secret) to your own DSN at build time.
The default DSN points at Open Circuit Dev's project; bundles built
without a DSN do not initialize Sentry at all.

---

## 5. Storage

| Data | Storage | Encryption |
|---|---|---|
| Pairing token | `EncryptedSharedPreferences` | AES-GCM, master key in Android Keystore |
| Daemon URL | `EncryptedSharedPreferences` | AES-GCM, master key in Android Keystore |
| Onboarding flag, boot opt-in, active session id, crash-reporting toggle | `DataStore` (preferences) | Disk encryption only (Android default) |
| Audio | RAM only — never written | n/a |
| Captions | RAM only — never written | n/a |

There is no SQLite database, no cache directory of session content,
and no log file written to disk.

---

## 6. Sharing

We do **not** share data with anyone. Specifically:

- We do not sell, license, or rent any data.
- We do not share data with advertisers — there are no ads.
- We do not share data with analytics providers.
- We do not share data with affiliates.
- The only third party that ever receives data is the Sentry SDK,
  and only if you opt in (see §4 above).

Your codetalker desktop daemon is **your own server**; it is not a
"third party" and it is not operated by us.

---

## 7. Deletion

You can delete every piece of data the app stores by:

1. Opening Android Settings → Apps → codetalker companion.
2. Tapping **Storage & cache → Clear storage**.

This wipes the encrypted preferences (token + URL), the DataStore
preferences (settings), and any in-memory state. After clearing
storage, the app behaves as if freshly installed.

To stop the app from running, you can uninstall it; uninstall
removes every byte the app ever wrote to the device.

If you opted into crash reports and want past crash data deleted, email
becky@nativeteachingaids.com with the message "Please delete my crash
reports" — we will purge any matching events from Sentry. Crash events
are retained for 90 days by default.

---

## 8. Children

The app is targeted at developers and is not designed for use by
children under 13 (or the equivalent age in your jurisdiction). We do
not knowingly collect data from children. If you believe a child has
used the app, contact us and we will purge any associated crash report
data.

---

## 9. Changes to this policy

If we change anything material, we will:

1. Update this document and bump the version number at the top.
2. Bump the app's `versionCode` so existing users see an update prompt.
3. Surface a one-time notice on next launch summarizing the change.

The change history of this document lives in the codetalker-pro Git
repository (`docs/PRIVACY-POLICY.md`); every revision is publicly
auditable.

---

## 10. Contact

For privacy questions, deletion requests, or anything else covered by
this policy:

- **Email:** becky@nativeteachingaids.com
- **GitHub issues:** https://github.com/OpenCircuitDev/codetalker-pro/issues

We aim to respond within 14 days.
