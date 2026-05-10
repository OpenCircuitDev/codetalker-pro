#!/usr/bin/env bash
# CCT-32 Task D.5 — release script.
#
# Usage:
#   scripts/release.sh check                # run unit + E2E gate, no build
#   scripts/release.sh build                # bundleRelease + assembleRelease
#   scripts/release.sh bump patch|minor|major   # version bump in build.gradle.kts
#   scripts/release.sh tag                  # git tag vX.Y.Z + push
#   scripts/release.sh dry-run              # everything except the tag push
#   scripts/release.sh full                 # bump → check → build → tag → push
#
# Reads the version from app/build.gradle.kts. Writes the bumped version
# back into the same file. Dry-run mode is the default safety net — pass
# 'tag' explicitly to push.
#
# Pre-requisites:
#   - Working tree is clean.
#   - ~/.gradle/gradle.properties has CCT_KEYSTORE_FILE et al. for a
#     fully-signed bundle. Without it, the bundle is unsigned (CI ok,
#     not Play-ready).
#
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." &> /dev/null && pwd)"
GRADLE_FILE="$REPO_ROOT/app/build.gradle.kts"

# ----------------------------------------------------------------------
# helpers
# ----------------------------------------------------------------------
log()  { printf "\033[36m[release]\033[0m %s\n" "$*"; }
warn() { printf "\033[33m[release]\033[0m %s\n" "$*"; }
die()  { printf "\033[31m[release]\033[0m %s\n" "$*" >&2; exit 1; }

current_version() {
  grep -E '^\s*versionName\s*=\s*"' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/'
}

current_version_code() {
  grep -E '^\s*versionCode\s*=\s*' "$GRADLE_FILE" | head -1 | sed -E 's/.*=\s*([0-9]+).*/\1/'
}

bump_version() {
  local kind="$1"
  local cur
  cur="$(current_version)"
  IFS='.' read -r MAJ MIN PAT <<< "$cur"
  case "$kind" in
    patch) PAT=$((PAT + 1));;
    minor) MIN=$((MIN + 1)); PAT=0;;
    major) MAJ=$((MAJ + 1)); MIN=0; PAT=0;;
    *) die "bump kind must be patch|minor|major (got '$kind')";;
  esac
  local new="${MAJ}.${MIN}.${PAT}"
  local code
  code=$(($(current_version_code) + 1))
  log "Bumping versionName $cur -> $new, versionCode -> $code"
  # Use a temp file because in-place sed varies across BSD / GNU / Win.
  awk -v new="$new" -v code="$code" '
    /^\s*versionName\s*=/ { sub(/"[^"]+"/, "\"" new "\""); print; next }
    /^\s*versionCode\s*=/ { sub(/[0-9]+/, code); print; next }
    { print }
  ' "$GRADLE_FILE" > "$GRADLE_FILE.new"
  mv "$GRADLE_FILE.new" "$GRADLE_FILE"
}

run_check() {
  log "Running unit tests..."
  (cd "$REPO_ROOT" && ./gradlew testDebugUnitTest --console=plain --no-daemon)
  log "Running E2E release gate..."
  bash "$SCRIPT_DIR/e2e/run_release_check.sh"
}

run_build() {
  log "Building release bundle + APK..."
  (cd "$REPO_ROOT" && ./gradlew bundleRelease assembleRelease --console=plain --no-daemon)
  local aab="$REPO_ROOT/app/build/outputs/bundle/release/app-release.aab"
  local apk="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
  if [[ -f "$aab" ]]; then
    log "AAB: $aab ($(du -h "$aab" | cut -f1))"
  else
    warn "AAB missing — keystore may not be configured."
  fi
  if [[ -f "$apk" ]]; then
    log "APK: $apk ($(du -h "$apk" | cut -f1))"
  else
    warn "APK missing — keystore may not be configured."
  fi
}

run_tag() {
  local v
  v="v$(current_version)"
  log "Tagging $v..."
  if git -C "$REPO_ROOT" rev-parse "$v" > /dev/null 2>&1; then
    die "Tag $v already exists. Bump first."
  fi
  git -C "$REPO_ROOT" tag -a "$v" -m "Release $v"
  log "Pushing $v to origin..."
  git -C "$REPO_ROOT" push origin "$v"
}

# ----------------------------------------------------------------------
# main
# ----------------------------------------------------------------------
cmd="${1:-help}"
case "$cmd" in
  check) run_check ;;
  build) run_build ;;
  bump)
    [[ -n "${2:-}" ]] || die "bump <patch|minor|major>"
    bump_version "$2"
    ;;
  tag) run_tag ;;
  dry-run)
    log "Current version: $(current_version) (build $(current_version_code))"
    run_check
    run_build
    log "DRY-RUN complete. Tag with 'release.sh tag' if happy."
    ;;
  full)
    [[ -n "${2:-}" ]] || die "full <patch|minor|major>"
    bump_version "$2"
    run_check
    run_build
    run_tag
    ;;
  help|--help|-h|*)
    grep '^#' "$0" | head -25 | sed 's/^# \?//'
    exit 0
    ;;
esac
