#!/usr/bin/env bash
# CCT-32 Task A.8 E2E — STT round-trip wiring smoke.
# Unit tests already cover the coordinator state machine; this script
# verifies that on a live device:
#   - the side-button HEADSETHOOK keyevent does not crash the app
#   - the daemon's /api/companion/inject endpoint accepts a synthetic call
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
source "$SCRIPT_DIR/lib/daemon_helpers.sh"

cct_section "A.8 STT roundtrip wiring"
fails=0

cd "$REPO_ROOT" && \
  ./gradlew testDebugUnitTest --tests "dev.opencircuit.codetalker.CompanionViewModelTest" --quiet 2>&1 | tail -2
if [[ $? -eq 0 ]]; then
  echo "  PASS: CompanionViewModelTest 5/5"
else
  echo "  FAIL"; ((fails++))
fi

# On-device: ensure the app survives a HEADSETHOOK keyevent (the side
# button maps to that on Beam Pro). If the app crashes we'd see it
# leave the foreground.
TMPDIR_WIN="C:/tmp/cct_e2e"
mkdir -p /c/tmp/cct_e2e
# Clear crash buffer so we only see crashes from THIS run.
adb_dev logcat -c -b crash 2>&1 > /dev/null
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 2
adb_dev shell input keyevent KEYCODE_HEADSETHOOK
sleep 2
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/post_btn.xml" > /dev/null
crash=$(adb_dev logcat -d -t 200 -b crash 2>&1 | grep -E "FATAL.*opencircuit|opencircuit.*FATAL" | wc -l)
crash="${crash//[$'\t\r\n ']}"
if [[ -z "$crash" || "$crash" == "0" ]]; then
  echo "  PASS: no fresh crashes after HEADSETHOOK keyevent"
else
  echo "  FAIL: $crash fresh crashes after button"; ((fails++))
fi
# Re-launch + verify the app comes back fine.
adb_dev shell am start -n dev.opencircuit.codetalker/.MainActivity > /dev/null
sleep 2
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/post_btn.xml" > /dev/null
if grep -qE "(Sessions|Pair)" "$TMPDIR_WIN/post_btn.xml"; then
  echo "  PASS: app re-launches cleanly"
else
  echo "  FAIL: app does not re-launch"; ((fails++))
fi

# Inject endpoint reachable (won't actually start a buddy without anthropic key, but should respond).
inject=$(curl -sS -m 5 -X POST "$DAEMON_BASE/api/companion/inject" \
  -H "X-CCT-Pairing-Token: bogus" \
  -H "Content-Type: application/json" \
  -d '{"buddy_id":"unknown","text":"hi"}')
cct_assert_contains "$inject" "unauthorized" "inject endpoint enforces token (security check)" || ((fails++))

if [[ $fails -gt 0 ]]; then echo "FAILED"; exit 1; else echo "OK"; fi
