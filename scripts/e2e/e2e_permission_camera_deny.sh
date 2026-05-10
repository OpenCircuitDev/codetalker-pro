#!/usr/bin/env bash
# CCT-32 Task B.1 E2E — camera permission denied -> rationale screen.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.1 Permission rationale (camera deny)"
fails=0

cct_install || ((fails++))
sleep 2
cct_clear_app
# Explicitly revoke camera + mic + notifications so we can observe the gate.
adb_dev shell pm revoke dev.opencircuit.codetalker android.permission.CAMERA 2>/dev/null || true
adb_dev shell pm revoke dev.opencircuit.codetalker android.permission.RECORD_AUDIO 2>/dev/null || true
adb_dev shell pm revoke dev.opencircuit.codetalker android.permission.POST_NOTIFICATIONS 2>/dev/null || true
cct_launch
sleep 2

UI="$TMPDIR_WIN/cct_ui_b1_deny.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI" > /dev/null

if grep -qE "Allow Camera|Allow Microphone|Allow Notifications" "$UI"; then
  echo "  PASS: rationale screen visible"
else
  echo "  FAIL: rationale screen missing — got:"
  grep -oE 'text="[^"]*"' "$UI" | head -5
  ((fails++))
fi

if grep -qE "Step 1 of [23]" "$UI"; then
  echo "  PASS: step counter rendered"
else
  echo "  FAIL: step counter missing"
  ((fails++))
fi

if grep -q "Allow" "$UI"; then
  echo "  PASS: Allow button present"
else
  echo "  FAIL: Allow button missing"
  ((fails++))
fi

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-1-camera-deny.png" > /dev/null
echo "  screenshot saved"

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
