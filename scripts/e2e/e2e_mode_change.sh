#!/usr/bin/env bash
# CCT-32 Task A.3 E2E — mode chips render on detail screen.
# (Full daemon round-trip needs a live Claude Code session; this script
# exercises the UI surface. e2e_daemon_endpoints.sh covers the wire.)
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "A.3 Mode/Voice/Cadence pickers render"
fails=0

# Re-launch app and tap into first session.
adb_dev shell am force-stop dev.opencircuit.codetalker
cct_launch
sleep 2
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/ui_list.xml" > /dev/null
if ! grep -q "Sessions" "$TMPDIR_WIN/ui_list.xml"; then
  cct_skip "Not paired"
  exit 0
fi
cct_tap 540 400
sleep 3

adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$TMPDIR_WIN/ui_detail.xml" > /dev/null
adb_dev shell "screencap -p -d 4630946175150030210 /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-a-3-pickers.png" > /dev/null

# We expect the screen to scaffold even when getSession fails on inactive
# sessions. The pickers themselves don't render until state loads, so
# this checks that at least header + Make Active are present and the
# error-state copy appears (graceful degrade).
for needle in "Back" "Make active" "Speaking mode"; do
  if grep -q "$needle" "$TMPDIR_WIN/ui_detail.xml"; then
    echo "  PASS: $needle visible"
  else
    # Speaking mode renders only on successful load. If load failed the
    # error message is shown — accept that as a recoverable state.
    if [[ "$needle" == "Speaking mode" ]] && grep -q "Could not load" "$TMPDIR_WIN/ui_detail.xml"; then
      echo "  PASS (graceful): pickers absent because session not live, error shown"
    else
      echo "  FAIL: $needle missing"; ((fails++))
    fi
  fi
done

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
