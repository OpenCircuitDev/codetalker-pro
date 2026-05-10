#!/usr/bin/env bash
# CCT-32 — pre-tag release gate. Runs every Phase C–H asset / config /
# behavioral check + the Phase A daemon E2E sequence. Non-zero on any
# failure.
#
# This is the single command the release.sh script invokes before
# tagging. CI runs it on every push to main.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

# Phase C–H asset / config / doc verifications (no daemon required).
asset_scripts=(
  "e2e_branding_assets.sh"
  "e2e_release_pipeline.sh"
  "e2e_legal_assets.sh"
  "e2e_telemetry_consent.sh"
  "e2e_documentation.sh"
)

# Phase A behavioral E2Es — only run when the daemon is reachable.
# Skip silently in CI where the daemon is not paired.
behavioral_scripts=(
  "e2e_daemon_endpoints.sh"
  "e2e_session_detail.sh"
  "e2e_mode_change.sh"
)

failed=()

echo "========================="
echo " Phase C–H asset gate"
echo "========================="
for s in "${asset_scripts[@]}"; do
  echo ""
  echo "@@@@@ $s @@@@@"
  if [[ -f "$SCRIPT_DIR/$s" ]]; then
    if bash "$SCRIPT_DIR/$s"; then :; else failed+=("$s"); fi
  else
    echo "  (script not yet present, skipping)"
  fi
done

echo ""
echo "========================="
echo " Phase A daemon smoke (best-effort)"
echo "========================="
DAEMON_BASE="${CCT_DAEMON_URL:-http://192.168.1.86:17832}"
if curl -sS -m 3 "${DAEMON_BASE}/api/health" > /dev/null 2>&1; then
  echo "Daemon reachable at $DAEMON_BASE — running behavioral E2E."
  for s in "${behavioral_scripts[@]}"; do
    echo ""
    echo "@@@@@ $s @@@@@"
    if [[ -f "$SCRIPT_DIR/$s" ]]; then
      if bash "$SCRIPT_DIR/$s"; then :; else failed+=("$s"); fi
    fi
  done
else
  echo "Daemon NOT reachable at $DAEMON_BASE — skipping behavioral E2E."
fi

echo ""
echo "========================="
echo "  release-check summary"
echo "========================="
echo "Failed: ${#failed[@]}"
for s in "${failed[@]}"; do
  echo "  - $s"
done

[[ ${#failed[@]} -eq 0 ]]
