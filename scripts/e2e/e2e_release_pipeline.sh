#!/usr/bin/env bash
# CCT-32 Phase D — release pipeline asset/config verification.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." &> /dev/null && pwd)"

cct_section "D — Release pipeline shape checks"
fails=0

# D.1 — keystore docs
cct_assert_file_exists "$REPO_ROOT/docs/DEVELOPER-GUIDE.md" "D.1 DEVELOPER-GUIDE present" || ((fails++))
guide="$REPO_ROOT/docs/DEVELOPER-GUIDE.md"
for section in 'Generating a fresh keystore' 'Recording the signing config' 'Key rotation' 'CCT_KEYSTORE_FILE' 'CCT_KEYSTORE_PASSWORD'; do
  if ! grep -q "$section" "$guide"; then
    echo "  FAIL: DEVELOPER-GUIDE missing '$section'"; ((fails++))
  fi
done

# .gitignore must block the keystore.
gi="$REPO_ROOT/.gitignore"
for pat in '*.keystore' '*.jks' 'keystore.properties'; do
  if ! grep -qF "$pat" "$gi"; then
    echo "  FAIL: .gitignore missing $pat"; ((fails++))
  fi
done

# D.2 — release signing config wired in build.gradle.kts.
build="$REPO_ROOT/app/build.gradle.kts"
for marker in 'signingConfigs' 'CCT_KEYSTORE_FILE' 'CCT_KEY_ALIAS' 'isMinifyEnabled = true' 'proguardFiles'; do
  if ! grep -q "$marker" "$build"; then
    echo "  FAIL: build.gradle.kts missing $marker"; ((fails++))
  fi
done

# D.3 — proguard-rules.pro present + has the expected library blocks.
pg="$REPO_ROOT/app/proguard-rules.pro"
cct_assert_file_exists "$pg" "D.3 proguard-rules.pro present" || ((fails++))
for lib in 'okhttp3' 'androidx.media3' 'com.google.zxing' 'androidx.compose' 'io.sentry' 'org.json' 'kotlinx.coroutines' 'dev.opencircuit.codetalker.net'; do
  if ! grep -q "$lib" "$pg"; then
    echo "  FAIL: proguard-rules.pro missing keep block for $lib"; ((fails++))
  fi
done

# D.4 — versionCode 1 / versionName 0.1.0 (or higher after bumps).
vname=$(grep -E '^\s*versionName\s*=\s*"' "$build" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
vcode=$(grep -E '^\s*versionCode\s*=\s*' "$build" | head -1 | sed -E 's/.*=\s*([0-9]+).*/\1/')
if [[ -z "$vname" || -z "$vcode" ]]; then
  echo "  FAIL: versionName / versionCode missing in build.gradle.kts"; ((fails++))
else
  echo "  PASS: D.4 versionName=$vname  versionCode=$vcode"
fi

# D.5 — CHANGELOG + release.sh present + executable.
cct_assert_file_exists "$REPO_ROOT/CHANGELOG.md" "D.5 CHANGELOG.md present" || ((fails++))
cct_assert_file_exists "$REPO_ROOT/scripts/release.sh" "D.5 release.sh present" || ((fails++))

# CHANGELOG must include the v0.1.0 entry.
if ! grep -q '\[0.1.0\]' "$REPO_ROOT/CHANGELOG.md"; then
  echo "  FAIL: CHANGELOG missing [0.1.0] entry"; ((fails++))
fi

# release.sh subcommands.
rs="$REPO_ROOT/scripts/release.sh"
for cmd in 'check)' 'build)' 'bump)' 'tag)' 'dry-run)' 'full)'; do
  if ! grep -qF "$cmd" "$rs"; then
    echo "  FAIL: release.sh missing subcommand $cmd"; ((fails++))
  fi
done

# release.sh dry-help should succeed.
if bash "$rs" help > /dev/null 2>&1; then
  echo "  PASS: release.sh help runs"
else
  echo "  FAIL: release.sh help exits non-zero"; ((fails++))
fi

# AAB output should be reachable when bundleRelease has run.
aab="$REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
if [[ -f "$aab" ]]; then
  size=$(stat -c%s "$aab" 2>/dev/null || stat -f%z "$aab" 2>/dev/null || echo 0)
  if [[ "$size" -gt 1000000 ]]; then
    echo "  PASS: app-release.aab present (size=$size bytes)"
  else
    echo "  FAIL: app-release.aab is suspiciously small ($size bytes)"; ((fails++))
  fi
else
  echo "  SKIP: app-release.aab not built yet (run ./gradlew bundleRelease)"
fi

cct_section_summary "$fails" "D — Release pipeline"
[[ $fails -eq 0 ]]
