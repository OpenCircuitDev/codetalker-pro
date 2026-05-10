#!/usr/bin/env bash
# CCT-32 Task B.1 E2E — camera permission granted -> gate advances past camera.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.1 Permission rationale (camera grant)"
fails=0

cct_install || ((fails++))
sleep 2
cct_clear_app
# Pre-grant camera so the gate skips it. Mic remains denied so we land on
# the mic rationale page next.
adb_dev shell pm grant dev.opencircuit.codetalker android.permission.CAMERA 2>/dev/null || true
adb_dev shell pm revoke dev.opencircuit.codetalker android.permission.RECORD_AUDIO 2>/dev/null || true
cct_launch
sleep 2

UI="$TMPDIR_WIN/cct_ui_b1_grant.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI" > /dev/null

# Camera was pre-granted, so the gate should advance to mic.
if grep -qE "Allow Microphone" "$UI"; then
  echo "  PASS: gate advanced past camera, mic rationale visible"
elif grep -qE "Pair with codetalker|Sessions" "$UI"; then
  echo "  PASS: all permissions already granted, on pairing/list screen"
else
  echo "  FAIL: unexpected screen after camera grant — top text:"
  grep -oE 'text="[^"]*"' "$UI" | head -8
  ((fails++))
fi

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-1-camera-grant.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
