#!/usr/bin/env bash
# CCT-32 Task B.2 E2E — first-launch onboarding flow.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.2 Onboarding flow"
fails=0

cct_install || ((fails++))
sleep 2
cct_clear_app
cct_launch
sleep 2

UI1="$TMPDIR_WIN/cct_ui_b2_welcome.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI1" > /dev/null

if grep -q "Welcome to Codetalker" "$UI1"; then
  echo "  PASS: Welcome page visible"
else
  echo "  FAIL: Welcome page missing"
  grep -oE 'text="[^"]*"' "$UI1" | head -5
  ((fails++))
fi

if grep -q "1 of 3" "$UI1"; then
  echo "  PASS: step counter '1 of 3' rendered"
else
  echo "  FAIL: step counter missing"; ((fails++))
fi

# Tap "Get started" — coords approximate for a vertically-centered button.
# Use uiautomator coordinates from the dumped XML to find precise location.
COORDS=$(grep -oE 'text="Get started"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UI1" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
if [[ -n "$COORDS" ]]; then
  X=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f1)
  Y=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f2)
  X2=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f1)
  Y2=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f2)
  CX=$(( (X + X2) / 2 ))
  CY=$(( (Y + Y2) / 2 ))
  cct_tap "$CX" "$CY"
else
  cct_tap 320 800  # fallback
fi
sleep 2

UI2="$TMPDIR_WIN/cct_ui_b2_daemon.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI2" > /dev/null
if grep -q "Pair with the daemon" "$UI2"; then
  echo "  PASS: page 2 (daemon setup) reached"
else
  echo "  FAIL: page 2 not reached"
  grep -oE 'text="[^"]*"' "$UI2" | head -5
  ((fails++))
fi
if grep -q "2 of 3" "$UI2"; then
  echo "  PASS: step counter '2 of 3' rendered"
else
  echo "  FAIL: step 2 counter missing"; ((fails++))
fi

# Tap Continue.
COORDS=$(grep -oE 'text="Continue"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' "$UI2" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
if [[ -n "$COORDS" ]]; then
  X=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f1)
  Y=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | head -1 | tr -d '[]' | cut -d',' -f2)
  X2=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f1)
  Y2=$(echo "$COORDS" | grep -oE '\[[0-9]+,[0-9]+\]' | tail -1 | tr -d '[]' | cut -d',' -f2)
  cct_tap $(( (X + X2) / 2 )) $(( (Y + Y2) / 2 ))
fi
sleep 2

UI3="$TMPDIR_WIN/cct_ui_b2_perms.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI3" > /dev/null
if grep -q "A few permissions" "$UI3"; then
  echo "  PASS: page 3 (permissions intro) reached"
else
  echo "  FAIL: page 3 not reached"
  grep -oE 'text="[^"]*"' "$UI3" | head -5
  ((fails++))
fi
if grep -q "3 of 3" "$UI3"; then
  echo "  PASS: step counter '3 of 3' rendered"
else
  echo "  FAIL: step 3 counter missing"; ((fails++))
fi

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-2-onboarding.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
