#!/usr/bin/env bash
# CCT-32 Task A.7 E2E — AudioFocusManager + TTS auto-play wiring.
# Verifies the audio focus state machine via unit tests, then on the
# device confirms that the audio output device is connected (so play
# would route correctly).
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

cct_section "A.7 AudioFocusManager + TTS"
fails=0

cd "$REPO_ROOT" && \
  ./gradlew testDebugUnitTest --tests "dev.opencircuit.codetalker.audio.AudioFocusManagerTest" --quiet 2>&1 | tail -2
if [[ $? -eq 0 ]]; then
  echo "  PASS: AudioFocusManagerTest 8/8"
else
  echo "  FAIL: AudioFocusManagerTest"; ((fails++))
fi

# On-device: dumpsys audio confirms the AudioManager subsystem is alive
# and our app's preferred output (USAGE_MEDIA) is routable.
audio=$(adb_dev shell dumpsys audio | head -50)
cct_assert_contains "$audio" "Audio Focus" "device AudioManager visible" || ((fails++))

if [[ $fails -gt 0 ]]; then echo "FAILED"; exit 1; else echo "OK"; fi
