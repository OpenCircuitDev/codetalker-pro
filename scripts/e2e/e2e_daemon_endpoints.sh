#!/usr/bin/env bash
# CCT-32 Task A.1 E2E — daemon-side smoke that the endpoints DaemonClient
# now wraps actually exist on the running daemon. JVM unit tests already
# cover the parsing; this catches schema drift between Android wire layer
# and the live daemon.
set -uo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
source "$SCRIPT_DIR/lib/daemon_helpers.sh"
source "$SCRIPT_DIR/lib/assert_helpers.sh"

cct_section "A.1 Daemon endpoint smoke"
fails=0

health=$(cct_health)
cct_assert_contains "$health" '"ok":true' "daemon /api/health responds" || ((fails++))

# /api/voices?engine=piper — flat string list (matches DaemonClient.listVoices parser fallback).
voices=$(cct_get_unauth /api/voices?engine=piper)
cct_assert_contains "$voices" '[' "voices/piper returns array" || ((fails++))

# /api/characters — full object list.
chars=$(cct_get_unauth /api/characters)
cct_assert_contains "$chars" '"id"' "characters returns objects with id" || ((fails++))
cct_assert_contains "$chars" '"voice_ref"' "characters has voice_ref field" || ((fails++))
cct_assert_contains "$chars" '"persona"' "characters has persona field" || ((fails++))

# Session endpoints require auth; verify they at least respond with auth header semantics.
sessions_unauth=$(curl -sS -o /dev/null -w "%{http_code}" -m 5 "$DAEMON_BASE/api/companion/sessions")
cct_assert_eq "$sessions_unauth" "401" "companion/sessions rejects no-token request" || ((fails++))

if [[ $fails -gt 0 ]]; then
  echo "FAILED: $fails assertion(s)"; exit 1
else
  echo "OK: A.1 daemon endpoints reachable + parseable"
fi
