# Google Play Console — codetalker companion v0.1.0

**Package name:** `dev.opencircuit.codetalker`
**Default language:** en-US
**Category:** Productivity → Developer tools
**Content rating:** Everyone (PEGI 3 / IARC equivalent)
**Target audience age range:** 18+
**Pricing:** Free
**Contains ads:** No
**In-app purchases:** No

## App name

```
codetalker companion
```

## Short description (max 80 chars)

```
Hands-free voice + AR companion for your local-first codetalker assistant.
```

(Char count: 79.)

## Long description (max 4000 chars)

```
codetalker companion turns your XREAL Beam Pro and Air 2 Pro glasses into a
hands-free conduit for your local codetalker desktop assistant. Pair once
with a QR code, then speak to your machine and hear its replies through
earbuds while you work — no cloud, no account, no microphone leaving your
network.

KEY FEATURES

• Hands-free voice loop. Tap the Beam Pro side button to talk, release to
  send. Replies stream back through your audio output of choice while a
  live caption renders on the AR HUD.

• Local-first. Pairing flows over your LAN or Tailnet — there is no
  account, no cloud sync, and no telemetry on by default. Your codetalker
  daemon stays on the machine you ran it from.

• Per-session controls. Pick a Claude Code session in the dashboard, set
  a speaking mode (brief / direct / live / trigger), choose a voice or
  cloned character, dial cadence, and tune markup verbosity from a single
  detail screen on your phone.

• Character-aware narration. If you've attached a character with a cloned
  voice in the desktop dashboard, the companion mirrors that voice and
  surfaces the character's persona color on the HUD.

• AR overlay. Render the live caption + active session over the world
  via the XREAL Air 2 Pro display when paired with a Beam Pro.

• Survives lock screen, network changes, and reboots. The foreground
  service keeps audio alive in the background; SSE auto-reconnects when
  Wi-Fi flips. Optional opt-in for boot autostart.

• Crash reports are off by default. If you turn them on, only stack
  traces and device model are sent — never your speech, transcripts, or
  the contents of any session.

PRIVACY AT A GLANCE

• No account required.
• No audio recordings, transcripts, or session text are stored or shared.
• The pairing token and daemon URL live in the device's encrypted
  preferences and never leave the device.
• Crash reports are opt-in and contain no personally identifiable data.

REQUIREMENTS

• A machine running the codetalker desktop daemon. The daemon is open
  source — see the GitHub link below.
• An Android 12+ device. Recommended: XREAL Beam Pro (Android 14, hardware
  side button, 4630946175150030210 secondary display for the glasses).
• Optional: XREAL Air 2 Pro glasses for the AR HUD.

LINKS

• Source: https://github.com/OpenCircuitDev/codetalker-pro
• Privacy policy: see the in-app About screen
• Terms of service: see the in-app About screen
• Issues: file at the GitHub repo above

This app is open source under the MIT license for the OSS components and
proprietary copyright for the companion application code (see About →
Licenses for the full breakdown).
```

(Char count: ~2090 — well under the 4000 limit.)

## Promotional text (max 170 chars)

Final, locked v0.1.0 wording:

```
Hands-free voice + AR for your local codetalker assistant. Pair, tap to talk, hear replies through earbuds while you work. No cloud, no account, your data.
```

(Char count: 165.)

## Key features bullets (locked for v0.1.0 store rotation)

These ride beneath the screenshots in the Play listing:

1. **Hands-free voice loop.** Side button → talk → listen.
2. **Local-first.** Pairs over LAN or Tailnet. No account.
3. **Per-session controls.** Mode, voice, cadence, character, markup
   on a single detail screen.
4. **Character-aware narration.** Cloned voices come along for the
   ride.
5. **AR HUD overlay.** Live captions render on Air 2 Pro glasses.
6. **Lifecycle hardened.** Survives lock screen, network changes, and
   reboots.
7. **Crash reports off by default.** Opt-in only; never sends audio
   or transcripts.

## Screenshots — phone (required: at least 2, max 8)

| Slot | File | Caption |
|---|---|---|
| 1 | `../../../docs/mockups/screenshots/cct-phaseB-after-pair.png` | Pair once with a QR — your daemon, your network. |
| 2 | `../../../docs/mockups/screenshots/cct-phaseB-list.png` | Every Claude Code session, live status at a glance. |
| 3 | `../../../docs/mockups/screenshots/cct-task-a-2-detail.png` | One screen for mode, voice, cadence, character, markup. |
| 4 | `../../../docs/mockups/screenshots/cct-task-a-3-pickers.png` | Brief / direct / live / trigger — pick the verbosity that fits. |
| 5 | `../../../docs/mockups/screenshots/cct-task-a-5-character.png` | Cloned voices come along for the ride. |
| 6 | `../../../docs/mockups/screenshots/cct-task-b-1-camera-grant.png` | Permission rationale every dangerous ask. |
| 7 | `../../../docs/mockups/screenshots/cct-task-b-2-onboarding.png` | Three-step onboarding before any pairing happens. |

(All screenshots captured on real Beam Pro X4200 hardware, Android 14.)

## Feature graphic (1024×500)

`feature-graphic.png` — generated from `feature-graphic-spec.md`. Cyan→
violet orb on midnight surface with the wordmark "codetalker companion"
in white, the tagline "speak to your code, listen to your code" beneath.

## High-res icon (512×512)

`icon-512.png` — rendered from `app/src/main/res/drawable/ic_launcher_foreground.xml`
at 512×512 with the launcher background fill applied.

## Categorization

- **Application or game:** Application
- **Type:** Productivity / Developer tools
- **Tags:** Developer, Voice, Accessibility, AR

## Content rating questionnaire — answers

| Question | Answer |
|---|---|
| Violence | None |
| Sexual content | None |
| Profanity | None (the app does not synthesise any pre-recorded text) |
| Drug references | None |
| Gambling | None |
| User-generated content | No (the app does not host or display content from other users) |
| Real-money transactions | None |
| Location sharing | No |
| Personal information sharing | No |

Result expected: **Everyone / IARC Everyone**.

## Target audience

| Question | Answer |
|---|---|
| Target age group | 18+ (developer tooling) |
| Children's content? | No |
| Designed for Families? | No |
| Family-friendly ads? | N/A — no ads |

## Privacy policy URL

```
https://opencircuitdev.github.io/codetalker-pro/PRIVACY-POLICY.html
```

(Hosted from `docs/PRIVACY-POLICY.md` via GitHub Pages once the
codetalker-pro public repo lands per CCT-30.)

## Contact information

- **Email:** becky@nativeteachingaids.com
- **Website:** https://github.com/OpenCircuitDev/codetalker-pro
- **Phone:** Optional — leave blank for v0.1.0

## Submission tracks

1. **Internal testing** — initial. Brand smoke-tests on a clean Beam Pro.
2. **Closed alpha** — invite OSS contributors and early adopters.
3. **Open beta** — once telemetry shows stable crash-free rate >99% over a
   week.
4. **Production** — after open beta clears one full release cycle without
   regressions.
