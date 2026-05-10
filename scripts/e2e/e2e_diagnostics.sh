#!/usr/bin/env bash
# CCT-32 Task B.6 E2E — long-press header -> Preferences -> Diagnostics.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.6 Diagnostics screen reachable via long-press menu"
fails=0

cct_install || ((fails++))
sleep 2
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 3

UI="$TMPDIR_WIN/cct_ui_b6_list.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI" > /dev/null

if ! grep -q "Sessions" "$UI"; then
  cct_skip "Sessions screen not visible — onboarding/permissions/pairing path not yet completed."
  exit 0
fi

# Long-press the Sessions title. Find its bounds.
BOUNDS=$(grep -oE 'text="Sessions"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UI" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
if [[ -z "$BOUNDS" ]]; then
  echo "  FAIL: Sessions title bounds not found"
  ((fails++))
else
  X=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f1)
  Y=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f2)
  X2=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f1)
  Y2=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f2)
  CX=$(( (X + X2) / 2 ))
  CY=$(( (Y + Y2) / 2 ))
  # Synthesize a long-press: down, swipe in place for 800ms, up.
  adb_dev shell input swipe "$CX" "$CY" "$CX" "$CY" 800
  sleep 2
fi

UI2="$TMPDIR_WIN/cct_ui_b6_prefs.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI2" > /dev/null

if grep -q "Preferences" "$UI2"; then
  echo "  PASS: long-press opened Preferences"
else
  echo "  FAIL: Preferences not reached — top text:"
  grep -oE 'text="[^"]*"' "$UI2" | head -8
  ((fails++))
fi

if grep -q "Start on device boot" "$UI2"; then
  echo "  PASS: Start-on-boot toggle visible"
else
  echo "  FAIL: Start-on-boot toggle missing"
  ((fails++))
fi

# Tap Diagnostics button.
BOUNDS=$(grep -oE 'text="Diagnostics"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UI2" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
if [[ -n "$BOUNDS" ]]; then
  X=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f1)
  Y=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f2)
  X2=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f1)
  Y2=$(echo "$BOUNDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f2)
  cct_tap $(( (X + X2) / 2 )) $(( (Y + Y2) / 2 ))
  sleep 3
fi

UI3="$TMPDIR_WIN/cct_ui_b6_diag.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI3" > /dev/null

if grep -q "Diagnostics" "$UI3"; then
  echo "  PASS: Diagnostics screen reached"
else
  echo "  FAIL: Diagnostics screen not reached"
  grep -oE 'text="[^"]*"' "$UI3" | head -8
  ((fails++))
fi

# Verify the live status cards are present.
for label in "Daemon health" "Round-trip latency" "Pairing token" "Active session id" "Battery"; do
  if grep -q "$label" "$UI3"; then
    echo "  PASS: status card '$label' visible"
  else
    echo "  FAIL: status card '$label' missing"
    ((fails++))
  fi
done

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-6-diagnostics.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
