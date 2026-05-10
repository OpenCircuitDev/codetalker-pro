#!/usr/bin/env bash
# CCT-32 Task B.3 E2E — daemon unreachable -> ErrorBanner with Retry.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"
TMPDIR_WIN="C:/tmp/cct_e2e"
TMPDIR="/c/tmp/cct_e2e"
mkdir -p "$TMPDIR"

cct_section "B.3 ErrorBanner — daemon unreachable"
fails=0

cct_install || ((fails++))
sleep 2
adb_dev shell am force-stop dev.opencircuit.codetalker

# Block daemon traffic so listSessions() throws. Two strategies:
#  1. If the daemon is actually running, drop its IP via the device hosts
#     overrides — but Beam Pro is rooted-restricted, so easiest is just to
#     ask the user to confirm the daemon is down before this E2E. We'll
#     check the local machine: if curl reaches the daemon, skip.
DAEMON="${CCT_DAEMON_URL:-http://192.168.1.86:17832}"
if curl -s --max-time 2 "$DAEMON/api/health" > /dev/null 2>&1; then
  cct_skip "Daemon is up at $DAEMON. To run this E2E, stop the daemon first."
  exit 0
fi

cct_launch
sleep 4

UI="$TMPDIR_WIN/cct_ui_b3_unreach.xml"
adb_dev shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb_dev pull /sdcard/ui.xml "$UI" > /dev/null

if grep -q "Daemon unreachable" "$UI"; then
  echo "  PASS: ErrorBanner rendered for daemon-unreachable"
else
  echo "  FAIL: expected 'Daemon unreachable' banner — got:"
  grep -oE 'text="[^"]*"' "$UI" | head -8
  ((fails++))
fi

if grep -q "Retry" "$UI"; then
  echo "  PASS: Retry action present"
else
  echo "  FAIL: Retry action missing"
  ((fails++))
fi

adb_dev shell "screencap -p /sdcard/cct.png"
adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/cct-task-b-3-unreachable.png" > /dev/null

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
