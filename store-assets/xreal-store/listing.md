# XREAL Store — codetalker companion v0.1.0

**Bundle ID:** `dev.opencircuit.codetalker`
**Version:** 0.1.0 (versionCode 1)
**Category:** Productivity → Developer tools
**Pricing:** Free

## App name

```
codetalker companion
```

## Tagline (max 60 chars)

Final, locked v0.1.0 wording:

```
Hands-free voice + AR for your local codetalker daemon.
```

(Char count: 56.)

## Key features bullets (locked for v0.1.0)

1. **Side button is push-to-talk.** Native Beam Pro hardware integration.
2. **Air 2 Pro HUD.** Live captions over the world.
3. **Local-first.** No account, no cloud, your data.
4. **Per-session controls.** Mode, voice, cadence, character, markup.
5. **Foreground service.** Audio survives screen-off and lock.
6. **Boot autostart.** Optional opt-in for always-ready glasses.
7. **Crash reports opt-in only.** No PII ever leaves your device.

## Short description (max 200 chars)

```
Pair your XREAL Beam Pro with a codetalker desktop and use the side button
to talk to Claude Code hands-free. Live captions render on Air 2 Pro
glasses; replies stream through earbuds. Local network only.
```

(Char count: 197.)

## Long description (max 3000 chars)

```
codetalker companion is the XREAL-native client for the open-source
codetalker desktop assistant. Once paired, the side button on your Beam
Pro becomes a push-to-talk for Claude Code: hold to speak, release to
send, and listen to replies through earbuds while a live caption renders
on the Air 2 Pro display.

WHY XREAL

• Hardware side button as native push-to-talk. No on-screen tap needed.
• Air 2 Pro display is the AR HUD — caption + active session render
  over the world without grabbing your full attention.
• Beam Pro audio routing keeps replies in your earbuds without breaking
  the system audio focus contract.
• Foreground media-playback service keeps the audio stream alive when
  the screen is off and the user is just listening.

WHAT IT DOES

• Pair: scan a QR from the codetalker desktop dashboard. The pairing
  token + daemon URL are stored in the device's encrypted preferences.
• Pick a session: every active Claude Code session running on your
  desktop appears in the list. Tap one and "Make active" to route audio
  + captions to the AR HUD.
• Speak: hold the side button, talk, release. Replies stream back as
  audio + captions.
• Tune the experience: pick a voice, character, cadence, and markup
  verbosity from the per-session detail screen.

LOCAL-FIRST

• No account, no cloud sync, no remote servers in the loop.
• Pairing happens over your LAN or Tailnet — the daemon controls all
  the data.
• Crash reports are off by default; opt-in via Preferences if you want
  to help with stability.

REQUIRES

• An XREAL Beam Pro running Android 14.
• A machine running the codetalker desktop daemon (open source —
  github.com/OpenCircuitDev/codetalker-pro).
• Optional: XREAL Air 2 Pro glasses for the AR HUD overlay.

This is the v0.1.0 first XREAL Store release. Future updates land via
in-store auto-update.
```

(Char count: ~1810.)

## Screenshots — Beam Pro (required: at least 4)

| Slot | File | Caption |
|---|---|---|
| 1 | `../../../docs/mockups/screenshots/cct-phaseB-after-pair.png` | First-launch pairing flow. |
| 2 | `../../../docs/mockups/screenshots/cct-phaseB-list.png` | Live session list. |
| 3 | `../../../docs/mockups/screenshots/cct-task-a-2-detail.png` | Per-session control surface. |
| 4 | `../../../docs/mockups/screenshots/cct-task-a-3-pickers.png` | Mode / voice / cadence pickers. |
| 5 | `../../../docs/mockups/screenshots/cct-task-a-5-character.png` | Character attach + cloned voices. |

## Promotional graphics

- **App icon:** see `../google-play/icon-512.png` (same asset).
- **Banner:** see `../google-play/feature-graphic.png` (1024×500). XREAL
  Store accepts the same crop.
- **Hero image:** TBD for v0.1.0 — Phase H polish task. Falls back to the
  feature graphic for the initial submission.

## Permissions disclosure

| Permission | Why |
|---|---|
| RECORD_AUDIO | Push-to-talk speech capture; never recorded or stored. |
| CAMERA | One-time QR pairing only. Not used after pairing. |
| INTERNET / ACCESS_NETWORK_STATE | Talks to the user's local codetalker daemon over LAN/Tailnet. |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK | Keeps the audio stream alive while the user is wearing glasses. |
| POST_NOTIFICATIONS | Foreground-service status notification. |
| RECEIVE_BOOT_COMPLETED | Optional opt-in for autostart on Beam Pro boot. |

All disclosures mirror the Google Play Data Safety form responses.

## Contact

- **Developer:** Open Circuit Dev
- **Email:** becky@nativeteachingaids.com
- **Website:** https://github.com/OpenCircuitDev/codetalker-pro
