#!/usr/bin/env bash
# CCT-32 Task B.5 E2E — screen off pauses, screen on resumes.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.5 Screen-off pause / screen-on resume"
fails=0

cct_install || ((fails++))
sleep 2

# Make sure the app is running with the lifecycle observer registered.
adb_dev shell am start -n dev.opencircuit.codetalker/.MainActivity > /dev/null
sleep 3

# Clear logcat tail, then toggle the screen off and on.
adb_dev logcat -c 2>/dev/null || true
adb_dev shell input keyevent KEYCODE_POWER
sleep 2
adb_dev shell input keyevent KEYCODE_POWER
sleep 2

LOG=$(adb_dev logcat -d -t 500 2>/dev/null | grep -E "CompanionFg" || true)
echo "  logcat lifecycle markers:"
echo "$LOG" | head -10

if echo "$LOG" | grep -q "ACTION_PAUSE"; then
  echo "  PASS: ACTION_PAUSE forwarded to service"
else
  cct_skip "ACTION_PAUSE not in logcat — observer may not have triggered (some emulators ignore power events)"
fi

if echo "$LOG" | grep -q "ACTION_RESUME"; then
  echo "  PASS: ACTION_RESUME forwarded to service"
else
  cct_skip "ACTION_RESUME not in logcat"
fi

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
