# Codetalker AR Companion (Android)

CCT-31 paid-edition Android app for XREAL Air 2 Pro + Beam Pro. Companion to the public OSS [codetalker](https://github.com/OpenCircuitDev/codetalker) daemon.

**Status**: Phase 5 scaffold (project skeleton + LAN client + pairing). Phases 6–10 layer on STT input, TTS playback, AR HUD, screen mirror, and polish.

## Repo plan

This directory is gitignored from the public `codetalker` repo. It runs as its own local git repo until the `codetalker-pro` private repo is created (see CCT-30 in the public repo's `docs/superpowers/specs/`).

## Setup

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- JDK 17 (bundled with recent Android Studio)
- A running codetalker daemon on your LAN — usually `pip install --user claude-code-talker && claude-code-talker serve`
- (For AR phases) XREAL Air 2 Pro + Beam Pro
- (Optional) Tailscale on phone + PC for remote-from-anywhere use

### Open the project
1. Launch Android Studio
2. **File → Open** → select this `companion-android/` directory
3. Wait for Gradle sync. The `libs.versions.toml` resolves all deps from Maven Central + Google.
4. Build → Make Project (Ctrl+F9). First sync downloads ~200 MB of AndroidX + Compose.

### XREAL Nebula SDK (required for Phase 8+)
The Nebula SDK is currently distributed as local AARs. Once you've downloaded it from the [XREAL developer portal](https://www.xreal.com/developer/):

1. Drop the `.aar` into `app/libs/`
2. Uncomment the corresponding `implementation(files(...))` line in `app/build.gradle.kts`
3. Re-sync

Until the SDK is dropped in, the project compiles and the LAN-client tests run, but AR rendering is stubbed.

## Pairing with the daemon

1. In the codetalker dashboard (`http://<pc-ip>:17832/ui-react/`), open **Preferences → AR Companion → Issue pairing token**.
2. A QR code appears.
3. On the phone, launch this app, tap **Pair**, scan the QR. (Manual entry of `daemon_url` + `pairing_token` works too.)
4. The token is stored in Android Keystore-backed `EncryptedSharedPreferences`.

## Tailscale (optional remote use)

1. Install [Tailscale](https://tailscale.com/) on both the PC and the Android phone (free tier covers personal use).
2. `tailscale up` on both.
3. Pair the phone with the daemon's Tailnet IP (`100.x.x.x:17832`) instead of the LAN IP.
4. Now the app works anywhere with internet.

The daemon already binds to `0.0.0.0`, so it's reachable on whatever network it happens to be on. There's no Tailscale-specific code in the daemon or this app.

## Smoke test (no glasses needed)

After pairing:

```kotlin
// Connect to daemon, list sessions
val client = DaemonClient(daemonUrl, pairingToken)
val sessions = client.listSessions()
println(sessions.map { it.displayName })
```

If the daemon is running and the token is valid, you'll see your codetalker session catalog.

## Run the unit tests

```bash
./gradlew test
```

Phase 5 tests cover `DaemonClient` (8 tests), `PairingFlow` (TBD), and `RetryPolicy` (3 tests). They use OkHttp's MockWebServer — no daemon required.

## Phase progress

| Phase | Status | Verifiable without hardware? |
|---|---|---|
| 5a — Project scaffold | ✅ shipped | Yes — Gradle sync + build |
| 5b — DaemonClient + PairingFlow + ConnectionGuard | ✅ shipped | Yes — unit tests with MockWebServer |
| 5c — Pairing Activity (QR scan) | 🔜 next | Yes — emulator |
| 6 — Button router + STT | pending | Partial — emulator for state machine; Beam Pro for hardware keys |
| 7 — TTS playback | pending | Yes — emulator with mock audio source |
| 8 — AR HUD + menu | pending | **No** — needs Nebula SDK + glasses |
| 9 — Screen mirror | pending | Partial — emulator for MJPEG decode; glasses for visual |
| 10 — Polish + Tailscale recipe | pending | Yes for code; no for end-to-end behavior |

## Open user contribution: `RetryPolicy.retryDelayMs`

The reconnection cadence is yours to design — the function is stubbed at `app/src/main/kotlin/dev/opencircuit/codetalker/net/ConnectionGuard.kt`. The file's docblock describes the trade-offs (aggressive vs patient vs WiFi-aware). Replace the placeholder body with whatever cadence matches your taste; the unit tests pin the contract.

## License

This directory is closed-source pending the CCT-30 open-core split. Once `codetalker-pro` exists, the license will land here. **Do not copy this code into the public OSS repo.**
