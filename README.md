# Codetalker AR Companion (Android)

CCT-31 paid-edition Android app for XREAL Air 2 Pro + Beam Pro. Companion to the public OSS [codetalker](https://github.com/OpenCircuitDev/codetalker) daemon.

**Status**: Phases 5–7 + character integration + MJPEG decoder + Phase 10a foreground service + Phase 8 AR skeleton shipped. The Nebula SDK AAR drop translates the skeleton into real AR rendering — see "XREAL Nebula SDK" below.

**Mockups + docs**: see [`docs/mockups/index.html`](../docs/mockups/index.html) in the public repo.

## Quick start

```bash
# 1. Daemon side (public repo)
pip install --user claude-code-talker
claude-code-talker serve

# 2. Get the dashboard's pairing token
#    → http://127.0.0.1:17832/ui-react/ → Preferences → AR Companion

# 3. Android side (this directory)
./gradlew installDebug                # installs to connected adb device
adb shell am start -n dev.opencircuit.codetalker/.MainActivity

# 4. In the app: Pair (scan QR or paste token), see your sessions
```

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

## Beam Pro deployment — first install

The Beam Pro ships with Android 14. To install this app:

### 1. Enable Developer Options
On the Beam Pro:
1. **Settings → About Beam Pro → Build number** — tap 7 times until "You are now a developer" appears.
2. **Settings → System → Developer options** — toggle **USB debugging** to ON.
3. Connect Beam Pro to your PC via USB-C.
4. On the Beam Pro, accept the "Allow USB debugging?" prompt and check **Always allow from this computer**.

### 2. Verify adb sees the device
On your PC:
```bash
adb devices -l
# Expected: your Beam Pro listed with "device" status
# E.g.: ABCD1234   device product:beam_pro model:Beam_Pro
```

If nothing shows: try a different USB-C cable (data-rated, not power-only), reboot the Beam Pro, or run `adb kill-server && adb start-server`.

### 3. Wireless adb (no cable, optional)
Beam Pro supports wireless debugging:
1. **Settings → System → Developer options → Wireless debugging → ON**
2. Tap **Pair device with pairing code** — note the IP/port + 6-digit code.
3. On PC: `adb pair 192.168.1.42:34567` (use the IP+port shown), enter code.
4. Then `adb connect 192.168.1.42:34567` (the OTHER port shown on the main wireless-debugging screen).
5. `adb devices -l` should now list the Beam Pro over WiFi.

### 4. Install + launch
```bash
./gradlew installDebug
adb shell am start -n dev.opencircuit.codetalker/.MainActivity

# Tail logs while testing
adb logcat -s codetalker:V CompanionForegroundService:V ButtonRouter:V
```

### 5. Test the side button
With the app open, press the Beam Pro's side buttons and watch logcat. The `HardwareKeys` class catches `KEYCODE_HEADSETHOOK / MEDIA_PLAY_PAUSE / CAMERA / VOLUME_UP/DOWN` — adjust [`HardwareKeys.kt:53-56`](app/src/main/kotlin/dev/opencircuit/codetalker/input/HardwareKeys.kt) if the actual keycode reported by your firmware is different.

```bash
# Tighter logcat during button tuning
adb logcat -s ViewRootImpl:* | grep -i "key"
```

## XREAL Nebula SDK (required for Phase 8 AR rendering)
The Nebula SDK is currently distributed as local AARs. Once you've downloaded it from the [XREAL developer portal](https://www.xreal.com/developer/):

1. Drop the `.aar` into `app/libs/`
2. Uncomment the corresponding `implementation(files(...))` line in `app/build.gradle.kts`
3. Replace the `TODO(nebula)` markers in [`AROverlayActivity.kt`](app/src/main/kotlin/dev/opencircuit/codetalker/ar/AROverlayActivity.kt), [`HudLayer.kt`](app/src/main/kotlin/dev/opencircuit/codetalker/ar/HudLayer.kt), and [`MenuLayer.kt`](app/src/main/kotlin/dev/opencircuit/codetalker/ar/MenuLayer.kt) — typically 5–10 lines per file (see comments inline for each anchor type).
4. Re-sync, rebuild, redeploy.

Until the SDK is dropped in, the AR Compose layers render as flat overlays on the Beam Pro's primary screen — useful for layout iteration before glasses are connected.

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
| 5a — Project scaffold | ✅ shipped | Yes — `./gradlew test` cold-build verified |
| 5b — DaemonClient + PairingFlow + ConnectionGuard | ✅ shipped | Yes — 12 MockWebServer tests |
| 5c — Pairing UI: Choose ↔ QR ↔ Manual | ✅ shipped | Yes — emulator |
| 5c+ — CameraX + ZXing QR scanner | ✅ shipped | Yes — emulator with synthetic QR |
| 6 — ButtonRouter state machine + STTRecorder | ✅ shipped | Yes — 11 state-machine tests |
| 6+ — HardwareKeys + dispatchKeyEvent + Beam Pro pipeline | ✅ shipped | **Partial** — code wired; Beam Pro needs adb-logcat tuning to confirm exact keycode |
| 7 — TTSPlayer (ExoPlayer + auth-header HTTP source) | ✅ shipped | Yes — emulator with daemon WAV stream |
| 9-decode — MJPEG parser | ✅ shipped | Yes — 6 pure-bytes tests |
| char — CharacterChip + persona avatars + voice cloning surfaces | ✅ shipped | Yes — daemon resolves attached_character; chip renders |
| 10a — Foreground service (audio + SSE survive backgrounding) | ✅ shipped | Yes — declared in manifest, manageable from MainActivity |
| 8 — AR HUD + Menu layers (Nebula SDK) | ✅ skeleton shipped, 🔜 awaits AAR | **Skeleton**: yes (compiles, renders flat). **Real AR**: needs Nebula AAR + glasses |
| 9-AR — Screen mirror plane in AR composition | pending | Needs Phase 8 |
| 10b — Tailscale recipe + battery profile + branding | partial | This README has the recipe; battery/branding need device |

**Test count:** 32 unit tests passing across 4 suites: ButtonRouter (11) · DaemonClient (12) · RetryPolicy (3) · MjpegStream (6).

## Open user contribution: `RetryPolicy.retryDelayMs`

The reconnection cadence is yours to design — the function is stubbed at `app/src/main/kotlin/dev/opencircuit/codetalker/net/ConnectionGuard.kt`. The file's docblock describes the trade-offs (aggressive vs patient vs WiFi-aware). Replace the placeholder body with whatever cadence matches your taste; the unit tests pin the contract.

## License

This directory is closed-source pending the CCT-30 open-core split. Once `codetalker-pro` exists, the license will land here. **Do not copy this code into the public OSS repo.**
