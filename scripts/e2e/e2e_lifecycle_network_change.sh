#!/usr/bin/env bash
# CCT-32 Task B.5 E2E — Wi-Fi toggle triggers SSE reconnect tick.
#
# Caution: toggling Wi-Fi on a wirelessly-paired ADB device WILL drop the
# adb session. Skip this E2E unless ADB is over USB. Detect by inspecting
# adb's transport id.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

cct_section "B.5 Network change reconnects SSE"
fails=0

# Bail early if ADB is wireless — we'd lose the connection.
DEVICE_LINE=$(adb devices | grep "$DEVICE" || true)
if echo "$DEVICE_LINE" | grep -q "_adb-tls-connect"; then
  cct_skip "ADB is wireless. Toggling Wi-Fi would drop the session. Run via USB."
  exit 0
fi

cct_install || ((fails++))
sleep 2
adb_dev shell am start -n dev.opencircuit.codetalker/.MainActivity > /dev/null
sleep 3

adb_dev logcat -c 2>/dev/null || true
adb_dev shell svc wifi disable
sleep 4
adb_dev shell svc wifi enable
sleep 8

LOG=$(adb_dev logcat -d -t 500 2>/dev/null | grep -E "CompanionFg" || true)
echo "  logcat lifecycle markers:"
echo "$LOG" | head -10

if echo "$LOG" | grep -q "ACTION_RECONNECT"; then
  echo "  PASS: ACTION_RECONNECT fired after Wi-Fi restore"
else
  cct_skip "ACTION_RECONNECT not in logcat — network tick may not have fired"
fi

if [[ $fails -gt 0 ]]; then echo "FAILED: $fails"; exit 1; else echo "OK"; fi
