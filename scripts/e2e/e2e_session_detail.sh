#!/usr/bin/env bash
# CCT-32 Task A.2 E2E — session list -> detail navigation.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
# Use a Windows-friendly path so adb pull doesn't fail on MSYS path mapping.
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "A.2 Session detail navigation"
fails=0

# Note: do NOT cct_clear_app — that wipes pairing token. Reinstall keeps it.
cct_install || ((fails++))
sleep 2
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 2

UI1="$TMPDIR_WIN/cct_ui_a2_list.xml"
UI2="$TMPDIR_WIN/cct_ui_a2_detail.xml"
UI3="$TMPDIR_WIN/cct_ui_a2_back.xml"

# First verify we're on the list (paired) — if unpaired, we'd see PairingScreen.
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI1" > /dev/null
if grep -q "Sessions" "$UI1"; then
  echo "  PASS: Sessions list visible (paired)"
else
  cct_skip "App not paired — pair via dashboard QR first"
  exit 0
fi

# Tap into the first list row (high in the list, below status bar/header).
cct_tap 540 400
sleep 2

adb_dev shell "screencap -p -d 4630946175150030210 /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-a-2-detail.png" > /dev/null

adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI2" > /dev/null
if grep -q "Back" "$UI2"; then
  echo "  PASS: Back button rendered"
else
  echo "  FAIL: Back button missing"; ((fails++))
fi
if grep -qE "(Make active|Active session)" "$UI2"; then
  echo "  PASS: Make active toggle rendered"
else
  echo "  FAIL: Make active toggle missing"; ((fails++))
fi

# Tap Back to return.
cct_tap 170 250
sleep 1
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI3" > /dev/null
if grep -q "Unpair" "$UI3"; then
  echo "  PASS: back nav returned to Sessions list (Unpair button visible)"
else
  echo "  FAIL: back nav did not return to Sessions list"; ((fails++))
fi

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
