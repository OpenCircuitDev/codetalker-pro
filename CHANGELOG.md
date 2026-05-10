# Changelog

All notable changes to **codetalker companion** are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Active development against `main`. Released v0.1.0 entry below describes
the initial public release.

---

## [0.1.0] — 2026-05-09

First public release of the codetalker companion for XREAL Beam Pro
and Air 2 Pro glasses. This is the v1.0 store-ready cut tracked under
**CCT-32**.

### Added — Phase A (feature completeness)

- **Daemon client (CCT-32 A.1):** full session, voice, character, and
  markup endpoints (`getSession`, `putOverlay`, `listVoices`,
  `listCharacters`, `attachCharacter`, `detachCharacter`).
- **SessionDetailScreen (CCT-32 A.2):** per-session control surface
  with header, active toggle, and back-nav back to the session list.
- **Pickers (CCT-32 A.3):** segmented `ModePicker` (brief/direct/live/
  trigger), `VoicePicker` dropdown over the daemon's voice library,
  segmented `CadencePicker`, and `MutedToggle` Switch.
- **MarkupQuickPanel (CCT-32 A.4):** six markup forms split into three
  categories (listen density, inline detail, structural pauses) with
  spec-driven kind dropdowns wired to the overlay PUT endpoint.
- **Character bottom sheet (CCT-32 A.5):** modal sheet over the
  daemon's character library; tap to attach/detach with persona
  colour, cloned-voice support, mesh path passthrough.
- **List → detail nav polish (CCT-32 A.6):** session list cards are
  fully clickable; selected card shows ripple feedback while detail
  loads; back gesture returns without reload.
- **AudioFocusManager (CCT-32 A.7):** wraps Android `AudioManager`
  focus events; TTSPlayer requests focus on play, abandons on stop.
- **STT round-trip (CCT-32 A.8):** Beam Pro side-button push-to-talk;
  speech captured, injected into a buddy session, replies stream over
  SSE, audio plays through phone speakers, caption renders on HUD.

### Added — Phase B (production hardening)

- **Permission rationale screens (CCT-32 B.1)** for camera, mic, and
  notifications, with deep-link to system settings.
- **OnboardingScreen (CCT-32 B.2):** three-page first-launch tour
  (welcome, daemon hint, permission asks); persisted in DataStore.
- **Error UX catalog (CCT-32 B.3):** every recoverable failure is one
  of nine `AppError` cases (DaemonUnreachable, TokenExpired, MicDenied,
  CameraDenied, NetworkDown, AudioFocusLost, InvalidPairingPayload,
  SessionOffline, DaemonVersionMismatch). `ErrorBanner` renders with
  user-meaningful copy + recovery action.
- **BootReceiver (CCT-32 B.4):** opt-in boot autostart toggle in
  PreferencesScreen; only re-launches the foreground service when the
  user explicitly enabled it AND has paired.
- **Lifecycle hardening (CCT-32 B.5):** screen-off audio pause via
  Lifecycle observer, SSE auto-reconnect on network change, active
  session id persisted across process death.
- **DiagnosticsScreen (CCT-32 B.6):** live status cards for pairing
  token expiry, daemon last-success, audio buffer state, buddy session
  id, glasses connectivity, network RTT, and battery.

### Added — Phase C (branding)

- **App icon (CCT-32 C.1):** adaptive launcher with cyan→violet
  waveform-orb glyph, monochrome variant for Android 13+ themed icons.
- **Splash screen (CCT-32 C.2):** Android 12+ SplashScreen API with
  branded background and logo via `installSplashScreen()`.
- **Store assets (CCT-32 C.3):** Google Play and XREAL Store listing
  copy, feature graphic spec, screenshot manifest, GitHub Releases
  template under `store-assets/`.
- **Strings polish (CCT-32 C.4):** end-user-final copy for every
  visible label; `<plurals>` for sessions count and four time-ago
  helpers (seconds/minutes/hours/days).
- **AboutScreen (CCT-32 C.5):** version (BuildConfig), license summary,
  third-party libraries list, GitHub source link, privacy + ToS deep
  links, Check-for-updates link.
- **SessionOffline error wrap (CCT-32 C.5):** the long-standing
  "Broken pipe" technical message is now wrapped in
  `AppError.SessionOffline` with the user-meaningful body
  "This session isn't currently live in Claude Code" and a
  "Refresh sessions" recovery action.

### Added — Phase D (release pipeline)

- **Release keystore procedure (CCT-32 D.1):** documented in
  `docs/DEVELOPER-GUIDE.md` §3 with rotation playbook for compromise.
- **Signed release bundle (CCT-32 D.2):** `bundleRelease` reads four
  CCT_KEYSTORE_* properties; degrades gracefully to unsigned for CI
  smoke / fresh-checkout dev flow.
- **ProGuard / R8 rules (CCT-32 D.3):** keep rules for OkHttp,
  Media3 / ExoPlayer, ZXing, Compose, Sentry, org.json, coroutines,
  and the project's own wire types.
- **Versioning (CCT-32 D.4):** versionCode 1, versionName "0.1.0";
  semver bump documented in DEVELOPER-GUIDE §4.
- **Release script + changelog (CCT-32 D.5):** `scripts/release.sh`
  bumps versions, runs unit + E2E gate, builds the bundle, tags, and
  pushes; `CHANGELOG.md` (this file) is rendered into each tag's
  GitHub release body.

### Added — Phase E (store + updates)

- **Google Play listing (CCT-32 E.1):** full Console-ready copy in
  `store-assets/google-play/listing.md` including content rating
  answers, target audience, Data Safety summary.
- **XREAL Store listing (CCT-32 E.2):** parallel listing with
  XREAL-specific phrasing about the Air 2 Pro display + Beam Pro side
  button.
- **GitHub Releases automation (CCT-32 E.3):** `.github/workflows/release.yml`
  triggers on tag push, restores the keystore from
  `CCT_KEYSTORE_BASE64`, builds AAB + APK, attaches as assets.
- **In-app update check (CCT-32 E.4):** AboutScreen "Check for updates"
  link opens the codetalker-pro releases page.

### Added — Phase F (privacy + legal)

- **Privacy policy (CCT-32 F.1):** `docs/PRIVACY-POLICY.md` covers
  pairing token storage, opt-in crash reports, no PII / audio /
  transcript collection, deletion via clear-data.
- **Terms of service (CCT-32 F.2):** `docs/TERMS.md` — usage terms,
  license disclaimer, liability disclaimers.
- **Manifest disclosures (CCT-32 F.3):** every dangerous permission
  has a rationale meta-data tag; `usesPermissionFlags="neverForLocation"`
  where applicable.
- **Data Safety form responses (CCT-32 F.4):**
  `store-assets/google-play/data-safety-form.md` — every Google Play
  Data Safety question has a documented answer.

### Added — Phase G (telemetry, opt-in)

- **Sentry SDK (CCT-32 G.1):** `io.sentry:sentry-android` integrated
  behind `AppPreferences.crashReportingEnabled`. Init guard prevents
  the SDK from starting unless the user has explicitly opted in. DSN
  flows through `BuildConfig.SENTRY_DSN` from a Gradle property.
- **First-launch consent (CCT-32 G.2):** `ConsentFlow` shows the dialog
  exactly once after onboarding completes; `crashReportingConsentAsked`
  flag gates re-asks. Setting can be flipped any time in Preferences.
- **Privacy policy crash-reporting addendum (CCT-32 G.3):** updated
  PRIVACY-POLICY.md spells out exactly what's sent (stack traces, app
  version, device model) and what's not (PII, audio, text, transcripts).

### Added — Phase H (documentation)

- **User Guide (CCT-32 H.1):** `docs/USER-GUIDE.md` — install, pair,
  day-to-day, glasses connection, troubleshooting, FAQ.
- **Developer Guide (CCT-32 H.2):** `docs/DEVELOPER-GUIDE.md` —
  prerequisites, build, test, release, architecture, contribute.
- **API reference (CCT-32 H.3):** `docs/API.md` — typed endpoint
  reference for DaemonClient + integration examples.
- **Mockups → screenshots (CCT-32 H.4):** real Beam Pro screenshots
  drop into `docs/mockups/index.html` per the Section 7 swap recipe;
  status badges flip from "mockup" to "screenshot".
- **Final store copy polish (CCT-32 H.5):** Google Play + XREAL Store
  listings finalized with locked tagline + key-features bullets.

### Tests

- 91 → 96+ unit tests (Phase G adds ConsentFlow + CrashReporter
  coverage).
- 21 → 22+ E2E shell scripts (Phase C–H verifications).
- `scripts/e2e/run_release_check.sh` is the pre-tag gate.

### Known gaps

- The codetalker-pro public OSS repository (CCT-30) is not yet live;
  GitHub deep-links in AboutScreen 404 until that lands.
- Keystore for the production app must be generated by the maintainer
  before submitting to Google Play / XREAL Store; procedure in
  DEVELOPER-GUIDE §3.

[Unreleased]: https://github.com/OpenCircuitDev/codetalker-pro/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/OpenCircuitDev/codetalker-pro/releases/tag/v0.1.0
