#!/usr/bin/env bash
# CCT-32 Task A.3 (cadence variant) E2E.
# Smoke test: cadence catalog is in code; this script verifies the
# unit-test catalog and the daemon's resolver tolerate slow/normal/fast.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

cct_section "A.3 Cadence picker catalog"
fails=0

cd "$REPO_ROOT" && \
  ./gradlew testDebugUnitTest --tests "dev.opencircuit.codetalker.ui.pickers.PickerCatalogTest.cadence picker covers slow normal fast" --quiet 2>&1 | tail -2
if [[ $? -eq 0 ]]; then
  echo "  PASS: cadence catalog covers slow/normal/fast"
else
  echo "  FAIL: cadence catalog test"; ((fails++))
fi

if [[ $fails -gt 0 ]]; then echo "FAILED"; exit 1; else echo "OK"; fi
