# codetalker companion — Developer Guide

This guide covers everything a developer needs to build, sign, and ship
the codetalker companion app. It is written for the local-only
development workflow that ships v0.1.0; once the open-core split
(CCT-30) lands, this document is mirrored on the public repo's site.

---

## 1. Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog or newer (Iguana / Koala recommended) |
| JDK | 17 (bundled with Android Studio JBR) |
| Android SDK | platform-33 + platform-35 + build-tools 35.0.0 |
| Gradle wrapper | 8.7+ (provided in repo) |
| Git | 2.40+ |

`local.properties` must point at your SDK:

```properties
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

The repo gitignores this file by design — every contributor has their
own SDK path.

---

## 2. Building from source

### Debug

```bash
cd companion-android
./gradlew installDebug
```

The first run pulls Gradle wrapper jars, AGP 8.5.2, and AndroidX
artefacts (~1 GB). Subsequent builds are seconds.

### Run unit tests

```bash
./gradlew testDebugUnitTest
```

Expected: ~96 passing tests covering the daemon client, audio focus,
button router, error catalog, markup catalog, lifecycle observers, and
the new Phase G consent flow + crash reporter init guard.

### End-to-end shell tests

```bash
bash scripts/e2e/run_release_check.sh
```

This is the pre-tag gate: every Phase C–H asset / config / shell test
runs in sequence. Non-zero on any failure.

---

## 3. Release signing — keystore procedure

> **The release keystore is NEVER committed.** It lives outside the
> repo, and its passwords live in `~/.gradle/gradle.properties` (which
> is gitignored on every developer's home directory).

### 3.1 Generating a fresh keystore

Run this once per project lifetime. The keystore validity is set to
~25 000 days so a single key signs the entire lifespan of the app.

```bash
keytool -genkey -v \
  -keystore ~/codetalker-release.keystore \
  -alias codetalker \
  -keyalg RSA \
  -keysize 2048 \
  -validity 25000 \
  -storetype PKCS12
```

Answer the prompts:

- First/last name: **codetalker companion**
- Organizational unit: **Open Circuit Dev**
- Organization: **Open Circuit Dev**
- Locality / state / country: pick a real one
- Set the storepass and keypass to the same long random secret.

### 3.2 Recording the signing config

Add to `~/.gradle/gradle.properties` (NOT the repo's `gradle.properties`):

```properties
CCT_KEYSTORE_FILE=/Users/you/codetalker-release.keystore
CCT_KEYSTORE_PASSWORD=...long-random-secret...
CCT_KEY_ALIAS=codetalker
CCT_KEY_PASSWORD=...long-random-secret...
```

`app/build.gradle.kts`'s release `signingConfigs` block reads these
properties via `project.findProperty(...)`. Missing properties degrade
gracefully — `bundleRelease` still produces an unsigned bundle, useful
for non-developer CI.

### 3.3 Verifying the signed AAB

```bash
./gradlew bundleRelease
ls -lh app/build/outputs/bundle/release/

# Expected: app-release.aab  ~ 8-12 MB

# Confirm signing certificate fingerprint:
keytool -list -v -keystore ~/codetalker-release.keystore -alias codetalker
```

Record the SHA-256 of the certificate in the public README so users can
verify any APK download.

### 3.4 Key rotation

If the production keystore is ever compromised:

1. Generate a new keystore (§3.1).
2. Use Google Play's [App Signing key reset](https://support.google.com/googleplay/android-developer/answer/9842756)
   flow to re-bind the new upload certificate to the existing app
   listing.
3. Update GitHub Secrets per §6 below.
4. Force-bump `versionCode` and tag a hot-fix release. Push the AAB +
   APK with the new signature; users on Play and the XREAL Store will
   receive the rotated signing through the normal store update path.
5. Publish the new SHA-256 fingerprint in `README.md`.

---

## 4. Versioning

The companion follows semver:

- `versionName` is `MAJOR.MINOR.PATCH`. Default at `0.1.0`.
- `versionCode` is a monotonically-incrementing integer. v0.1.0 = 1.

`scripts/release.sh bump <patch|minor|major>` does the bump in
`app/build.gradle.kts` for you and creates the matching git tag.

---

## 5. ProGuard / R8

`app/proguard-rules.pro` keeps the rules required by:

- OkHttp + okhttp-sse internals (reflection on stream parsers).
- ExoPlayer / Media3 (MediaSource factories).
- ZXing (encoding/decoding QR symbols via reflection).
- Compose runtime (already covered by AndroidX consumer rules; we
  document the pattern explicitly here for forward-compat).
- Sentry SDK (anti-shrink markers for crash payload classes).
- `org.json` usage by `DaemonClient` (JSONObject reflection on Android).

The release build runs minification (`isMinifyEnabled = true`); debug
builds skip it for fast iteration.

---

## 6. CI release workflow (GitHub Actions)

`.github/workflows/release.yml` triggers on a `vX.Y.Z` tag push:

1. Fetches the source.
2. Restores the keystore from a base64-encoded GitHub Secret.
3. Runs `./gradlew bundleRelease` and `./gradlew assembleRelease`.
4. Uploads the AAB + APK as release assets.

Required GitHub repository secrets:

| Secret | Description |
|---|---|
| `CCT_KEYSTORE_BASE64` | Base64 of `release.keystore` (`base64 -i release.keystore`) |
| `CCT_KEYSTORE_PASSWORD` | Keystore password |
| `CCT_KEY_ALIAS` | Key alias (default `codetalker`) |
| `CCT_KEY_PASSWORD` | Key password |
| `CCT_SENTRY_DSN` | Sentry DSN for crash reporting (optional) |

Set them via the repo's `Settings → Secrets and variables → Actions`
page, NOT in the workflow YAML.

---

## 7. Architecture overview

```
┌─────────────────────────────────────────────────────────────────┐
│ MainActivity                                                    │
│  ├── installSplashScreen()                                       │
│  ├── PairingFlow (Encrypted prefs + QR scanner)                  │
│  ├── ButtonRouter ←── HardwareKeys ←── KeyEvent dispatch         │
│  ├── ScreenStateObserver / NetworkStateObserver                  │
│  └── CompanionRoot (Compose tree)                                │
│       ├── OnboardingScreen → PermissionGate                      │
│       ├── PairingScreen                                          │
│       ├── SessionListScreen ←── DaemonClient.listSessions()      │
│       ├── SessionDetailScreen ←── DaemonClient.getSession()      │
│       ├── PreferencesScreen ←── AppPreferences (DataStore)       │
│       ├── DiagnosticsScreen                                      │
│       └── AboutScreen                                            │
│                                                                  │
│ CompanionForegroundService (mediaPlayback)                       │
│  ├── ExoPlayer (TTS audio stream)                                │
│  └── SSE listener (Buddy session events)                         │
│                                                                  │
│ Telemetry                                                        │
│  ├── ConsentFlow (gate first-launch dialog)                      │
│  └── CrashReporter (Sentry, opt-in only)                         │
└─────────────────────────────────────────────────────────────────┘
```

Read order for new contributors:

1. `DaemonClient.kt` — wire layer.
2. `MainActivity.kt` — root composition + state ownership.
3. `SessionDetailScreen.kt` — the hottest UI path.
4. `CompanionForegroundService.kt` — background lifecycle.
5. `telemetry/` — opt-in Sentry hookup.

---

## 8. Contributing

The repo is local-only until CCT-30 splits to `codetalker-pro`. Until
then, contributions go through internal review only.

After the split:

- Branch off `main`. PRs target `main`.
- Every PR runs `./gradlew testDebugUnitTest` and the E2E sequencer
  via the GitHub Actions CI workflow.
- Sign every commit with the bot or a personal GPG key.
- Use Conventional Commits style (`feat:`, `fix:`, `docs:`, ...).

---

## 9. Cutting a release

Streamlined flow:

```bash
cd companion-android
scripts/release.sh bump patch         # 0.1.0 -> 0.1.1
./gradlew testDebugUnitTest
bash scripts/e2e/run_release_check.sh
git push origin main --tags           # CI workflow attaches AAB + APK
```

Then upload the AAB to the Play Console internal track and the XREAL
Store dev portal.
