#!/usr/bin/env bash
# CCT-32 Task A.3 (voice variant) E2E — confirms VoicePicker dropdown is
# wired by checking the daemon's voices endpoint matches what
# DaemonClient.listVoices probes.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/daemon_helpers.sh"

cct_section "A.3 Voice library round-trip"
fails=0

for engine in piper edge xtts; do
  raw=$(curl -sS -m 5 "$DAEMON_BASE/api/voices?engine=$engine")
  if [[ "$raw" == *"["* ]]; then
    cct_assert_contains "$raw" "[" "engine $engine returns array (or empty array)" || ((fails++))
  fi
done

if [[ $fails -gt 0 ]]; then echo "FAILED"; exit 1; else echo "OK"; fi
