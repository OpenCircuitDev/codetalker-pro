#!/usr/bin/env bash
# CCT-32 Task B.2 E2E — already-onboarded user skips straight to pairing/list.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.2 Onboarding skipped on relaunch (already paired)"
fails=0

cct_install || ((fails++))
sleep 2

# We do NOT clear app data — relaunching with the existing pairing token in
# EncryptedSharedPreferences must skip onboarding (the QR scan acted as the
# de-facto first launch).
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 2

UI="$TMPDIR_WIN/cct_ui_b2_skip.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI" > /dev/null

if grep -qE "Sessions|Pair with codetalker daemon|Allow Camera|Allow Microphone|Allow Notifications" "$UI"; then
  echo "  PASS: relaunch skipped onboarding (landed past welcome screen)"
elif grep -q "Welcome to Codetalker" "$UI"; then
  echo "  FAIL: relaunch did NOT skip onboarding — Welcome page still showing"
  ((fails++))
else
  echo "  FAIL: unexpected screen on relaunch"
  grep -oE 'text="[^"]*"' "$UI" | head -5
  ((fails++))
fi

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-2-skip.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
