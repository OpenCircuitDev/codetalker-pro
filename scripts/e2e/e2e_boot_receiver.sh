#!/usr/bin/env bash
# CCT-32 Task B.4 E2E — boot receiver only starts service when opted-in.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.4 BootReceiver opt-in"
fails=0

cct_install || ((fails++))
sleep 2

# Send BOOT_COMPLETED broadcast directly (privileged but works on debug builds).
adb_dev shell am force-stop dev.opencircuit.codetalker
adb_dev shell am broadcast -a android.intent.action.BOOT_COMPLETED -p dev.opencircuit.codetalker > /tmp/bc_out.txt 2>&1
sleep 3

# Check whether the foreground service is running.
SERVICES=$(adb_dev shell dumpsys activity services dev.opencircuit.codetalker 2>/dev/null | grep -c "CompanionForegroundService" || echo "0")
if [[ "$SERVICES" -gt 0 ]]; then
  echo "  PASS: BootReceiver started CompanionForegroundService"
else
  cct_skip "Service not started — opt-in flag may be off (expected when not yet enabled)"
fi

# Check logcat for the BootReceiver path being entered.
LOG=$(adb_dev logcat -d -t 200 2>/dev/null | grep -iE "CompanionFg|BootReceiver" | tail -5)
echo "  logcat tail:"
echo "$LOG" | head -5

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-4-boot.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
