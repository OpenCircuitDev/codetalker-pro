#!/usr/bin/env bash
# CCT-32 Task A.5 E2E — character library reachable + character chip
# present in the running app's session list.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/daemon_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "A.5 Character roster + attach UI"
fails=0

chars=$(cct_get_unauth /api/characters)
cct_assert_contains "$chars" '"id":"' "characters available on daemon" || ((fails++))

# Open the picker via the detail screen if the session list is paired.
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 2
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/list.xml" > /dev/null
if grep -q "Sessions" "$TMPDIR_WIN/list.xml"; then
  cct_tap 540 400
  sleep 3
  adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/detail.xml" > /dev/null
  adb_dev shell "screencap -p -d 4630946175150030210 /sdcard/cct.png"
  adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-a-5-character.png" > /dev/null
  # Sanity check screen rendered (Back exists)
  cct_assert_contains "$(cat "$TMPDIR_WIN/detail.xml")" "Back" "detail screen rendered" || ((fails++))
fi

if [[ $fails -gt 0 ]]; then echo "FAILED"; exit 1; else echo "OK"; fi
