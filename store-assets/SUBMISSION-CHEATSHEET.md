# Codetalker Companion v0.1.0 — Store Submission Cheatsheet

**Single-page paste-and-go reference for Google Play Console + XREAL Store. Use in tandem with the full listings in `google-play/listing.md` and `xreal-store/listing.md`.**

---

## ⚠️ BEFORE YOU SUBMIT — keystore rotation

The local rehearsal AAB at `app/build/outputs/bundle/release/app-release.aab` was built with a **rehearsal-grade keystore** (password `abc123`, file `C:/Users/brand/codetalker-release.keystore`). This keystore must NOT be uploaded to either store.

**Required action before first submission:**

1. Generate a strong production keystore:
   ```powershell
   $strongPass = python -c "import secrets; print(secrets.token_urlsafe(32))"
   Write-Host "SAVE THIS PASSWORD: $strongPass" # Copy to your password manager NOW
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkey -v `
     -keystore "$HOME\codetalker-release-PROD.keystore" `
     -alias codetalker -keyalg RSA -keysize 2048 -validity 25000 -storetype PKCS12 `
     -storepass $strongPass -keypass $strongPass `
     -dname "CN=codetalker companion, OU=Open Circuit Dev, O=Open Circuit Dev"
   ```

2. **Back up the keystore + password to a password manager IMMEDIATELY.** Loss = locked out of Play Store updates forever.

3. Update `~/.gradle/gradle.properties` to point at the production keystore (replace the `abc123` lines):
   ```
   CCT_KEYSTORE_FILE=C:/Users/brand/codetalker-release-PROD.keystore
   CCT_KEYSTORE_PASSWORD=<the-strong-password>
   CCT_KEY_ALIAS=codetalker
   CCT_KEY_PASSWORD=<the-strong-password>
   ```

4. Rebuild: `cd companion-android && ./gradlew bundleRelease assembleRelease`. The new AAB at `app/build/outputs/bundle/release/app-release.aab` is what you upload.

5. Configure GitHub Secrets in `OpenCircuitDev/codetalker-pro` (Settings → Secrets and variables → Actions):
   ```powershell
   $b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\codetalker-release-PROD.keystore"))
   gh secret set CCT_KEYSTORE_BASE64 -b $b64 -R OpenCircuitDev/codetalker-pro
   gh secret set CCT_KEYSTORE_PASSWORD -b $strongPass -R OpenCircuitDev/codetalker-pro
   gh secret set CCT_KEY_ALIAS -b "codetalker" -R OpenCircuitDev/codetalker-pro
   gh secret set CCT_KEY_PASSWORD -b $strongPass -R OpenCircuitDev/codetalker-pro
   ```

6. Tag the release:
   ```bash
   cd companion-android
   bash scripts/release.sh 0.1.0    # or git tag v0.1.0 && git push origin v0.1.0
   ```

7. Watch the Action: `gh run watch -R OpenCircuitDev/codetalker-pro`. The signed AAB + APK will land at `https://github.com/OpenCircuitDev/codetalker-pro/releases/tag/v0.1.0`.

Then proceed to the store-submission steps below.

---

## Google Play Console — submission order

### Step 1. Create app

| Field | Value |
|---|---|
| App name | `codetalker companion` |
| Default language | English (United States) — `en-US` |
| App or game | Application |
| Free or paid | Free |
| Declarations | ☑ App is not Designed for Families. ☑ Complies with Play policies. ☑ Complies with US export laws. |

### Step 2. Set up your app — required tasks (Console left rail)

#### App content

- **Privacy policy URL:** `https://opencircuitdev.github.io/codetalker-pro/PRIVACY-POLICY.html` (after enabling Pages on the codetalker-pro repo, source: `main`/`/companion-android/docs/`).
  - Until Pages is live, paste: `https://github.com/OpenCircuitDev/codetalker-pro/blob/main/companion-android/docs/PRIVACY-POLICY.md`.
- **App access:** "All functionality is available without restrictions." (No login.)
- **Ads:** No.
- **Content rating questionnaire:** answer per `google-play/listing.md` § Content rating questionnaire. Expected: **Everyone / IARC Everyone**.
- **Target audience:** 18+. Not designed for children. Source: `google-play/listing.md` § Target audience.
- **News app:** No.
- **COVID-19 contact tracing and status apps:** No.
- **Data safety:** copy answers from `google-play/data-safety-form.md` line by line. Key item: **only "Crash logs" + "Diagnostics" collected, opt-in, shared with Sentry.**
- **Government apps:** No.
- **Financial features:** None.
- **Health apps:** No.
- **Stories:** No.
- **AI-generated content:** No (the app does not generate AI content; it surfaces output from the user's own daemon).

#### Main store listing

| Field | Value | Source |
|---|---|---|
| App name | `codetalker companion` | `google-play/listing.md` § App name |
| Short description (80 char) | `Hands-free voice + AR companion for your local-first codetalker assistant.` | § Short description |
| Full description (4000 char) | paste the entire fenced block | § Long description |
| Promotional text (170 char) | `Hands-free voice + AR for your local codetalker assistant. Pair, tap to talk, hear replies through earbuds while you work. No cloud, no account, your data.` | § Promotional text |
| App icon (512×512) | `store-assets/google-play/icon-512.png` *(generate from `app/src/main/res/drawable/ic_launcher_foreground.xml` per `feature-graphic-spec.md`)* | § High-res icon |
| Feature graphic (1024×500) | `store-assets/google-play/feature-graphic.png` *(render from `feature-graphic-spec.md`)* | § Feature graphic |
| Phone screenshots (≥2, ≤8) | 7 PNGs from `docs/mockups/screenshots/` per § Screenshots table | each row maps slot→file→caption |
| Tablet screenshots | none required for v0.1.0 | n/a |
| App category | Productivity | § Categorization |
| Tags | Developer, Voice, Accessibility, AR | § Categorization |
| Contact email | `becky@nativeteachingaids.com` | § Contact information |
| Contact website | `https://github.com/OpenCircuitDev/codetalker-pro` | § Contact information |

#### Store settings

- **App category:** Productivity
- **Tags:** Developer, Voice, Accessibility, AR
- **App access:** No login required.
- **Email:** `becky@nativeteachingaids.com`

#### Pricing & distribution

- Free.
- Available in: All countries supported by Google Play. (No regional restrictions.)
- Contains ads: No.

### Step 3. Upload the AAB — Internal testing track (FIRST)

1. Production → **Internal testing** track.
2. Create a new release. Name: `v0.1.0 internal test`.
3. Upload the **production-keystore-signed** AAB from `app/build/outputs/bundle/release/app-release.aab`.
4. Release notes: paste the v0.1.0 entry from `companion-android/CHANGELOG.md`.
5. Add an internal testers email list (your own + 1-2 trusted testers).
6. Submit for review. Internal testing typically reviews within 24h.

### Step 4. Promote to Closed testing → Open testing → Production

After Internal testing passes a real-device smoke test:

1. **Closed testing** (alpha): invite OSS contributors. Same AAB; new track.
2. **Open testing** (beta): public opt-in via Play Store URL. Watch crash-free rate via Console → Quality → Android vitals. **Gate to Production: ≥99% crash-free rate over a continuous 7-day window.**
3. **Production**: full rollout to 100% of users (start at 10% staged rollout if you want safety).

---

## XREAL Store — submission order

### Step 1. XREAL Developer account

1. Apply at `https://developer.xreal.com` if you don't already have an account.
2. Submit business / developer info. Approval typically takes 3–5 business days.

### Step 2. Create app listing

| Field | Value | Source |
|---|---|---|
| App name | `codetalker companion` | `xreal-store/listing.md` § App name |
| Bundle ID | `dev.opencircuit.codetalker` | § Bundle ID |
| Tagline (60) | `Hands-free voice + AR for your local codetalker daemon.` | § Tagline |
| Short description (200) | paste § Short description |
| Long description (3000) | paste § Long description |
| Category | Productivity / Developer tools | § Category |
| Pricing | Free | § Pricing |
| App icon | reuse `store-assets/google-play/icon-512.png` | § Promotional graphics |
| Banner (1024×500) | reuse `store-assets/google-play/feature-graphic.png` | § Promotional graphics |
| Beam Pro screenshots (≥4) | 5 PNGs per § Screenshots table |
| Permissions disclosure | mirror `xreal-store/listing.md` § Permissions disclosure verbatim |

### Step 3. Upload the same AAB

XREAL Store accepts the same signed AAB as Google Play. Upload, fill metadata, submit for review.

---

## GitHub Releases — auto-handled

When `v0.1.0` is tagged and pushed to `OpenCircuitDev/codetalker-pro`, the workflow at `.github/workflows/release.yml` runs:

1. Restores keystore from `CCT_KEYSTORE_BASE64`.
2. Runs the asset gate.
3. Builds signed AAB + APK.
4. Attaches both to the release at `https://github.com/OpenCircuitDev/codetalker-pro/releases/tag/v0.1.0`.

The CHANGELOG section for v0.1.0 is auto-rendered into the release body.

---

## Post-submission checklist

- [ ] Production keystore generated with strong password and **backed up** (1Password / Bitwarden / etc.).
- [ ] Old rehearsal keystore at `C:/Users/brand/codetalker-release.keystore` deleted (`Remove-Item $HOME/codetalker-release.keystore`).
- [ ] `~/.gradle/gradle.properties` updated to point at production keystore.
- [ ] GitHub Secrets in `OpenCircuitDev/codetalker-pro` set: `CCT_KEYSTORE_BASE64`, `CCT_KEYSTORE_PASSWORD`, `CCT_KEY_ALIAS`, `CCT_KEY_PASSWORD`.
- [ ] GitHub Pages enabled on `codetalker-pro` (Settings → Pages → Source: `main` / `/companion-android/docs/`).
- [ ] Tagged `v0.1.0` and watched the GitHub Action complete green.
- [ ] AAB uploaded to Google Play Internal testing.
- [ ] Smoke-tested the install on Beam Pro from Play Internal testing.
- [ ] AAB uploaded to XREAL Store dev portal.
- [ ] Promotion path Internal → Closed → Open → Production planned with crash-free gate.
- [ ] Sentry project created (optional) and DSN wired into `CCT_SENTRY_DSN` Secret.

---

## Live device verification (gate item #3 from CCT-32 plan)

Before any release tag, the Beam Pro must pass the full A.2 / A.3 / B.5 / B.6 behavioral E2Es from `scripts/e2e/`. Today's status:

- ✅ Asset gate (Phase C–H): green
- ✅ A.1 daemon endpoints: PASS
- ⏸ A.2 session detail: SKIP — app not paired (re-run after QR pair)
- ⏸ A.3 mode/voice/cadence pickers: SKIP — app not paired
- ⏸ B.5 lifecycle: not run
- ⏸ B.6 diagnostics: not run

**Steps to complete behavioral verification:**

1. On the Beam Pro, open the codetalker companion app.
2. Open the dashboard at `http://192.168.1.86:17832` (or wherever your daemon is) → AR Companion → "Pair AR Companion" → scan QR.
3. Once paired, run from `companion-android/`:
   ```bash
   bash scripts/e2e/run_release_check.sh
   ```
   Behavioral E2Es should now pass instead of skipping.

---

## Quick command reference

| What | Command |
|---|---|
| Build signed AAB + APK | `cd companion-android; export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"; export PATH="$JAVA_HOME/bin:$PATH"; ./gradlew bundleRelease assembleRelease` |
| Verify APK signature | `"C:/Users/brand/AppData/Local/Android/Sdk/build-tools/37.0.0/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk` |
| Sideload signed APK to Beam Pro | `"$ADB" -s 192.168.1.132:39315 install -r app/build/outputs/apk/release/app-release.apk` (uninstall first if old build was differently signed) |
| Tag release | `git tag v0.1.0 && git push origin v0.1.0` |
| Watch Action | `gh run watch -R OpenCircuitDev/codetalker-pro` |
| View release | `gh release view v0.1.0 -R OpenCircuitDev/codetalker-pro` |
