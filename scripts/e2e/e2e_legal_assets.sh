#!/usr/bin/env bash
# CCT-32 Phase F — privacy + legal asset verification.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/assert_helpers.sh"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." &> /dev/null && pwd)"

cct_section "F — Privacy + legal asset checks"
fails=0

# F.1 — Privacy policy.
priv="$REPO_ROOT/docs/PRIVACY-POLICY.md"
cct_assert_file_exists "$priv" "F.1 PRIVACY-POLICY.md present" || ((fails++))
for section in 'Effective date' 'What data the app handles' 'Permissions' 'Crash reporting' 'Storage' 'Sharing' 'Deletion' 'Children' 'Contact'; do
  if ! grep -q "$section" "$priv"; then
    echo "  FAIL: PRIVACY-POLICY missing '$section' section"; ((fails++))
  fi
done
# Verify it explicitly states no PII / no audio storage.
for claim in 'never written' 'No PII' 'opt-in' 'AES-GCM'; do
  if ! grep -qi "$claim" "$priv"; then
    echo "  FAIL: PRIVACY-POLICY missing claim '$claim'"; ((fails++))
  fi
done

# F.2 — Terms of service.
terms="$REPO_ROOT/docs/TERMS.md"
cct_assert_file_exists "$terms" "F.2 TERMS.md present" || ((fails++))
for section in 'License' 'Permitted use' 'No warranty' 'Limitation of liability' 'Indemnity' 'Termination' 'Governing law'; do
  if ! grep -q "$section" "$terms"; then
    echo "  FAIL: TERMS missing '$section' section"; ((fails++))
  fi
done

# F.3 — Manifest disclosures.
mf="$REPO_ROOT/app/src/main/AndroidManifest.xml"
for tag in 'PRIVACY_POLICY_URL' 'TERMS_URL' 'RATIONALE_RECORD_AUDIO' 'RATIONALE_CAMERA' 'RATIONALE_POST_NOTIFICATIONS'; do
  if ! grep -q "$tag" "$mf"; then
    echo "  FAIL: AndroidManifest missing meta-data $tag"; ((fails++))
  fi
done

# Manifest must comment-document each dangerous permission.
for perm in 'RECORD_AUDIO' 'CAMERA' 'FOREGROUND_SERVICE' 'POST_NOTIFICATIONS' 'RECEIVE_BOOT_COMPLETED'; do
  if ! grep -q "android.permission.${perm}" "$mf"; then
    echo "  FAIL: AndroidManifest missing $perm declaration"; ((fails++))
  fi
done

# F.4 — Data Safety form responses.
ds="$REPO_ROOT/store-assets/google-play/data-safety-form.md"
cct_assert_file_exists "$ds" "F.4 data-safety-form.md present" || ((fails++))
for q in 'Personal info' 'Audio files' 'Crash logs' 'Diagnostics' 'Privacy policy URL' 'Children'; do
  if ! grep -q "$q" "$ds"; then
    echo "  FAIL: data-safety-form missing question '$q'"; ((fails++))
  fi
done
# Crashes must be marked opt-in.
if ! grep -q 'opt-in' "$ds"; then
  echo "  FAIL: data-safety-form should state crash logs are opt-in"; ((fails++))
fi

cct_section_summary "$fails" "F — Privacy + legal"
[[ $fails -eq 0 ]]
