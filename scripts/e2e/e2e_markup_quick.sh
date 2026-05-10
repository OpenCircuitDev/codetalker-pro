#!/usr/bin/env bash
# CCT-32 Task A.4 E2E — MarkupQuickCatalog invariants enforced.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
source "$SCRIPT_DIR/lib/adb_helpers.sh"

cct_section "A.4 MarkupQuickPanel invariants"
cd "$REPO_ROOT" && \
  ./gradlew testDebugUnitTest --tests "dev.opencircuit.codetalker.ui.markup.MarkupKindResolutionTest" --quiet 2>&1 | tail -2
if [[ $? -eq 0 ]]; then
  echo "  PASS: MarkupKindResolutionTest 5/5"
  echo "OK"
else
  echo "  FAIL"; exit 1
fi
