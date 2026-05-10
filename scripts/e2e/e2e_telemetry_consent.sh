#!/usr/bin/env bash
# CCT-32 Phase G — telemetry / consent verification.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." &> /dev/null && pwd)"

cct_section "G — Telemetry / consent shape checks"
fails=0

# G.1 — CrashReporter wrapper.
cr="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/telemetry/CrashReporter.kt"
cct_assert_file_exists "$cr" "G.1 CrashReporter.kt present" || ((fails++))
for marker in 'object CrashReporter' 'fun init' 'BuildConfig.SENTRY_DSN' 'isSendDefaultPii = false' 'SentryAndroid.init'; do
  if ! grep -q "$marker" "$cr"; then
    echo "  FAIL: CrashReporter missing $marker"; ((fails++))
  fi
done
# Init must short-circuit when disabled or DSN blank.
if ! grep -q 'if (!enabled) return' "$cr"; then
  echo "  FAIL: CrashReporter must short-circuit when not enabled"; ((fails++))
fi
if ! grep -q 'if (dsn.isBlank()) return' "$cr"; then
  echo "  FAIL: CrashReporter must short-circuit on blank DSN"; ((fails++))
fi

# G.2 — ConsentFlow + ConsentDialog.
cf="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/telemetry/ConsentFlow.kt"
cct_assert_file_exists "$cf" "G.2 ConsentFlow.kt present" || ((fails++))
for marker in 'shouldShowConsent' 'recordConsent' 'recordDecline' 'crashReportingConsentAsked'; do
  if ! grep -q "$marker" "$cf"; then
    echo "  FAIL: ConsentFlow missing $marker"; ((fails++))
  fi
done
cd_="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/telemetry/ConsentDialog.kt"
cct_assert_file_exists "$cd_" "G.2 ConsentDialog.kt present" || ((fails++))

# AppPreferences extension for crashReportingEnabled / consentAsked.
prefs="$REPO_ROOT/app/src/main/kotlin/dev/opencircuit/codetalker/prefs/AppPreferences.kt"
for k in 'crashReportingEnabled' 'crashReportingConsentAsked' 'setCrashReportingEnabled' 'setCrashReportingConsentAsked'; do
  if ! grep -q "$k" "$prefs"; then
    echo "  FAIL: AppPreferences missing $k"; ((fails++))
  fi
done

# Tests for ConsentFlow + CrashReporter must exist.
cct_assert_file_exists "$REPO_ROOT/app/src/test/kotlin/dev/opencircuit/codetalker/telemetry/ConsentFlowTest.kt" "G.2 ConsentFlowTest.kt present" || ((fails++))
cct_assert_file_exists "$REPO_ROOT/app/src/test/kotlin/dev/opencircuit/codetalker/telemetry/CrashReporterTest.kt" "G.1 CrashReporterTest.kt present" || ((fails++))

# G.3 — Privacy policy must contain the crash-reporting addendum.
priv="$REPO_ROOT/docs/PRIVACY-POLICY.md"
for claim in 'Crash reporting' 'opt-in' 'sendDefaultPii' 'Stack trace' 'never'; do
  if ! grep -qi "$claim" "$priv"; then
    echo "  FAIL: PRIVACY-POLICY missing crash-reporting claim '$claim'"; ((fails++))
  fi
done

# Sentry dependency wired in build.gradle.kts.
build="$REPO_ROOT/app/build.gradle.kts"
if ! grep -q 'libs.sentry.android' "$build"; then
  echo "  FAIL: build.gradle.kts missing Sentry dependency"; ((fails++))
fi
# BuildConfig SENTRY_DSN field present.
if ! grep -q 'SENTRY_DSN' "$build"; then
  echo "  FAIL: build.gradle.kts missing SENTRY_DSN buildConfigField"; ((fails++))
fi

cct_section_summary "$fails" "G — Telemetry"
[[ $fails -eq 0 ]]
