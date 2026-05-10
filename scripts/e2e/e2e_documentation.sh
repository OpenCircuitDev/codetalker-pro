#!/usr/bin/env bash
# CCT-32 Phase H — documentation verification.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." &> /dev/null && pwd)"
# The docs/mockups page lives in the parent (public docs) repo, not in
# companion-android/. Resolve that path explicitly.
DOCS_ROOT="$(cd -- "$REPO_ROOT/.." &> /dev/null && pwd)/docs"

cct_section "H — Documentation shape checks"
fails=0

# H.1 — User Guide.
ug="$REPO_ROOT/docs/USER-GUIDE.md"
cct_assert_file_exists "$ug" "H.1 USER-GUIDE.md present" || ((fails++))
for section in 'Install' 'Pair with the codetalker desktop' 'Day-to-day' 'Glasses' 'Troubleshooting' 'FAQ'; do
  if ! grep -q "$section" "$ug"; then
    echo "  FAIL: USER-GUIDE missing '$section' section"; ((fails++))
  fi
done

# H.2 — Developer Guide (already present from D.1 work).
dg="$REPO_ROOT/docs/DEVELOPER-GUIDE.md"
cct_assert_file_exists "$dg" "H.2 DEVELOPER-GUIDE.md present" || ((fails++))
for section in 'Prerequisites' 'Building from source' 'Release signing' 'Versioning' 'Architecture overview' 'Contributing' 'Cutting a release'; do
  if ! grep -q "$section" "$dg"; then
    echo "  FAIL: DEVELOPER-GUIDE missing '$section' section"; ((fails++))
  fi
done

# H.3 — API reference.
api="$REPO_ROOT/docs/API.md"
cct_assert_file_exists "$api" "H.3 API.md present" || ((fails++))
for endpoint in 'GET /api/health' 'GET /api/companion/sessions' 'GET /api/sessions' 'PUT /api/sessions' 'GET /api/voices' 'GET /api/characters' 'POST /api/companion/start-buddy' 'POST /api/companion/inject'; do
  if ! grep -qF "$endpoint" "$api"; then
    echo "  FAIL: API.md missing endpoint '$endpoint'"; ((fails++))
  fi
done
# Must reference Kotlin types.
for ktype in 'SessionLite' 'SessionState' 'VoiceLite' 'CharacterLite' 'EventSource'; do
  if ! grep -q "$ktype" "$api"; then
    echo "  FAIL: API.md missing Kotlin type '$ktype'"; ((fails++))
  fi
done

# H.4 — Mockups page swap.
if [[ -d "$DOCS_ROOT/mockups" ]]; then
  mp="$DOCS_ROOT/mockups/index.html"
  if [[ -f "$mp" ]]; then
    # Count data-status="screenshot" occurrences on actual cards (not in
    # CSS rules). Look for `data-status="screenshot"` attributes on
    # neon-card divs.
    ss_count=$(grep -c 'data-status="screenshot"' "$mp" 2>/dev/null || echo 0)
    # Filter the count: subtract 5 known references to data-status in CSS
    # rules / docs / templates that are not real card swaps.
    real_screenshots=$((ss_count - 5))
    if [[ $real_screenshots -lt 1 ]]; then
      echo "  FAIL: H.4 mockups index.html has no real screenshot swaps (found $ss_count refs total, expected 5+ swaps + 5 CSS refs)"; ((fails++))
    else
      echo "  PASS: H.4 mockups page has $real_screenshots screenshot swap(s)"
    fi
    # Must reference at least one actual screenshot path.
    if ! grep -q 'screenshots/cct-' "$mp"; then
      echo "  FAIL: mockups index.html does not reference any cct-* screenshot files"; ((fails++))
    fi
  else
    echo "  SKIP: docs/mockups/index.html not present"
  fi
else
  echo "  SKIP: docs/mockups/ not present (run from public repo)"
fi

# H.5 — Final store copy.
gp="$REPO_ROOT/store-assets/google-play/listing.md"
if ! grep -qi 'Key features bullets' "$gp"; then
  echo "  FAIL: google-play/listing.md missing locked Key features bullets section"; ((fails++))
fi
xs="$REPO_ROOT/store-assets/xreal-store/listing.md"
if ! grep -qi 'Key features bullets' "$xs"; then
  echo "  FAIL: xreal-store/listing.md missing locked Key features bullets section"; ((fails++))
fi

cct_section_summary "$fails" "H — Documentation"
[[ $fails -eq 0 ]]
