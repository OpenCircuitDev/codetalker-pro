#!/usr/bin/env bash
# CCT-32 Phase A E2E — adb helpers shared across every task script.

# Beam Pro X4200 device id, set by ./pair.sh in CCT-31. Override with
# CCT_DEVICE if you have a different paired device.
DEVICE="${CCT_DEVICE:-adb-TGLM4CG1186540-zBd1x4._adb-tls-connect._tcp}"

# Path to platform-tools — ensures the scripts work in CI / fresh shells.
export PATH="/c/Users/brand/AppData/Local/Android/Sdk/platform-tools:$PATH"
export MSYS_NO_PATHCONV=1

# Resolve repo root no matter where the script is run from.
_ADB_HELPERS_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
REPO_ROOT="$(cd -- "$_ADB_HELPERS_DIR/../../.." &> /dev/null && pwd)"
# Convert to native Windows path so adb pull doesn't choke on /c/... mapping.
SCREENSHOT_DIR_RAW="$(cd -- "$REPO_ROOT/.." &> /dev/null && pwd)/docs/mockups/screenshots"
mkdir -p "$SCREENSHOT_DIR_RAW"
if command -v cygpath > /dev/null 2>&1; then
  SCREENSHOT_DIR="$(cygpath -w "$SCREENSHOT_DIR_RAW" | tr '\\' '/')"
else
  SCREENSHOT_DIR="$SCREENSHOT_DIR_RAW"
fi
# Ensure JAVA_HOME for any nested gradle calls.
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
export ANDROID_HOME="${ANDROID_HOME:-/c/Users/brand/AppData/Local/Android/Sdk}"

adb_dev() { adb -s "$DEVICE" "$@"; }

# Stop and clear app data — full reset for a clean E2E run.
cct_clear_app() {
  adb_dev shell am force-stop dev.opencircuit.codetalker || true
  adb_dev shell pm clear dev.opencircuit.codetalker > /dev/null
}

cct_install() {
  cd "$REPO_ROOT" && ./gradlew installDebug 2>&1 | tail -3
}

cct_launch() {
  adb_dev shell am start -n dev.opencircuit.codetalker/.MainActivity > /dev/null
  sleep 2
}

cct_screencap() {
  local name="$1"
  adb_dev shell "screencap -p /sdcard/cct.png"
  adb_dev pull /sdcard/cct.png "$SCREENSHOT_DIR/$name.png" > /dev/null
  echo "  screenshot: $SCREENSHOT_DIR/$name.png"
}

cct_tap() {
  adb_dev shell input tap "$1" "$2"
  sleep 0.5
}

cct_keyevent() {
  adb_dev shell input keyevent "$1"
}
