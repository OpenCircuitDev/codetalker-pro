#!/usr/bin/env bash
# CCT-32 Phase C — verification script for branding deliverables.
# Asset-only / strings-only changes don't have a runtime API to hit; this
# script asserts the files exist with the expected shape.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." &> /dev/null && pwd)"
RES="$REPO_ROOT/app/src/main/res"

cct_section "C — Branding asset shape checks"
fails=0

# C.1 — Adaptive launcher icon set.
cct_assert_file_exists "$RES/mipmap-anydpi-v26/ic_launcher.xml" "C.1 adaptive ic_launcher.xml" || ((fails++))
cct_assert_file_exists "$RES/mipmap-anydpi-v26/ic_launcher_round.xml" "C.1 adaptive ic_launcher_round.xml" || ((fails++))
cct_assert_file_exists "$RES/drawable/ic_launcher_foreground.xml" "C.1 foreground vector drawable" || ((fails++))
cct_assert_file_exists "$RES/drawable/ic_launcher_monochrome.xml" "C.1 monochrome variant for Android 13+ themed icons" || ((fails++))
cct_assert_file_exists "$RES/values/ic_launcher_background.xml" "C.1 background colour resource" || ((fails++))

# Manifest must reference the icons.
manifest="$REPO_ROOT/app/src/main/AndroidManifest.xml"
if ! grep -q 'android:icon="@mipmap/ic_launcher"' "$manifest"; then
  echo "  FAIL: manifest is missing android:icon=@mipmap/ic_launcher"; ((fails++))
fi
if ! grep -q 'android:roundIcon="@mipmap/ic_launcher_round"' "$manifest"; then
  echo "  FAIL: manifest is missing android:roundIcon=@mipmap/ic_launcher_round"; ((fails++))
fi

# Icon foreground must contain the cyan + violet brand colours.
fg="$RES/drawable/ic_launcher_foreground.xml"
if ! grep -qi '22D3EE' "$fg"; then
  echo "  FAIL: ic_launcher_foreground.xml missing brand cyan #22D3EE"; ((fails++))
fi
if ! grep -qi 'A855F7' "$fg"; then
  echo "  FAIL: ic_launcher_foreground.xml missing brand violet #A855F7"; ((fails++))
fi

# C.2 — Splash theme attributes set.
themes="$RES/values/themes.xml"
for attr in windowSplashScreenBackground windowSplashScreenAnimatedIcon windowSplashScreenIconBackgroundColor; do
  if ! grep -q "$attr" "$themes"; then
    echo "  FAIL: themes.xml missing $attr"; ((fails++))
  fi
done
# MainActivity must call installSplashScreen()
if ! grep -q 'installSplashScreen' "$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/MainActivity.kt"; then
  echo "  FAIL: MainActivity does not call installSplashScreen()"; ((fails++))
fi

# C.3 — Store-asset listings exist with required sections.
store="$REPO_ROOT/store-assets"
cct_assert_file_exists "$store/google-play/listing.md" "C.3 google-play listing" || ((fails++))
cct_assert_file_exists "$store/xreal-store/listing.md" "C.3 xreal-store listing" || ((fails++))
cct_assert_file_exists "$store/google-play/feature-graphic-spec.md" "C.3 feature graphic spec" || ((fails++))

# Listing must have a Short description and Long description section.
gp="$store/google-play/listing.md"
for section in 'Short description' 'Long description' 'Promotional text' 'Screenshots' 'Privacy policy URL' 'Content rating'; do
  if ! grep -q "$section" "$gp"; then
    echo "  FAIL: google-play/listing.md missing section '$section'"; ((fails++))
  fi
done
# Long description must reference the brand name + tagline.
if ! grep -q 'codetalker companion' "$gp"; then
  echo "  FAIL: google-play/listing.md does not mention 'codetalker companion'"; ((fails++))
fi

# C.4 — Strings polish: <plurals> for sessions_count + minutes_ago.
strings="$RES/values/strings.xml"
for tag in 'sessions_count' 'minutes_ago' 'seconds_ago' 'hours_ago' 'days_ago'; do
  if ! grep -q "<plurals name=\"$tag\">" "$strings"; then
    echo "  FAIL: strings.xml missing <plurals> $tag"; ((fails++))
  fi
done
# C.5 — AboutScreen file present + references BuildConfig.
about="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/ui/AboutScreen.kt"
cct_assert_file_exists "$about" "C.5 AboutScreen.kt" || ((fails++))
if ! grep -q 'BuildConfig.VERSION_NAME' "$about"; then
  echo "  FAIL: AboutScreen does not read BuildConfig.VERSION_NAME"; ((fails++))
fi
# AboutScreen must surface license info + GitHub link + privacy policy + ToS.
for section in 'License' 'Third-party' 'Privacy policy' 'Terms of service' 'Source on GitHub' 'Check for updates'; do
  if ! grep -q "$section" "$about"; then
    echo "  FAIL: AboutScreen missing reference to '$section'"; ((fails++))
  fi
done

# C.5 SessionOffline error wrap.
err="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/ui/errors/AppError.kt"
if ! grep -q 'data object SessionOffline' "$err"; then
  echo "  FAIL: AppError missing SessionOffline catalog entry"; ((fails++))
fi
if ! grep -qi "Claude Code" "$err"; then
  echo "  FAIL: AppError SessionOffline body should mention Claude Code"; ((fails++))
fi
if ! grep -qi 'broken pipe' "$err"; then
  echo "  FAIL: AppErrors mapping should match broken-pipe IOException"; ((fails++))
fi

cct_section_summary "$fails" "C — Branding"
[[ $fails -eq 0 ]]
