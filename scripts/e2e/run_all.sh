#!/usr/bin/env bash
# CCT-32 Phase A — run every E2E sequentially. Non-zero exit on any fail.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

scripts=(
  "e2e_daemon_endpoints.sh"
  "e2e_session_detail.sh"
  "e2e_mode_change.sh"
  "e2e_voice_change.sh"
  "e2e_cadence_change.sh"
  "e2e_markup_quick.sh"
  "e2e_character_attach.sh"
  "e2e_audio_play.sh"
  "e2e_stt_roundtrip.sh"
)

failed=()
for s in "${scripts[@]}"; do
  echo ""
  echo "@@@@@ $s @@@@@"
  if bash "$SCRIPT_DIR/$s"; then
    :
  else
    failed+=("$s")
  fi
done

echo ""
echo "===================="
echo "  Phase A E2E summary"
echo "===================="
echo "Total: ${#scripts[@]}"
echo "Failed: ${#failed[@]}"
for s in "${failed[@]}"; do
  echo "  - $s"
done

[[ ${#failed[@]} -eq 0 ]]
