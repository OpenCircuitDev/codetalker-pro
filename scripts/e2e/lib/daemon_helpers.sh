#!/usr/bin/env bash
# CCT-32 Phase A E2E — daemon helpers (curl wrappers + token mgmt).

DAEMON_HOST="${CCT_DAEMON_HOST:-192.168.1.86}"
DAEMON_PORT="${CCT_DAEMON_PORT:-17832}"
DAEMON_BASE="http://$DAEMON_HOST:$DAEMON_PORT"

# Most companion endpoints require X-CCT-Pairing-Token. Read from env or
# fall back to a dev token file. Set CCT_TOKEN before running tasks that
# need authenticated calls.
DAEMON_TOKEN="${CCT_TOKEN:-}"

cct_token_header() {
  if [[ -n "$DAEMON_TOKEN" ]]; then
    echo "X-CCT-Pairing-Token: $DAEMON_TOKEN"
  fi
}

cct_get() {
  local path="$1"
  curl -sS -m 5 "$DAEMON_BASE$path" -H "$(cct_token_header)"
}

cct_get_unauth() {
  curl -sS -m 5 "$DAEMON_BASE$1"
}

cct_put() {
  local path="$1"; shift
  local body="$1"
  curl -sS -m 5 -X PUT "$DAEMON_BASE$path" \
    -H "$(cct_token_header)" -H "Content-Type: application/json" \
    -d "$body"
}

cct_post() {
  local path="$1"; shift
  local body="$1"
  curl -sS -m 5 -X POST "$DAEMON_BASE$path" \
    -H "$(cct_token_header)" -H "Content-Type: application/json" \
    -d "$body"
}

cct_delete() {
  curl -sS -m 5 -X DELETE "$DAEMON_BASE$1" -H "$(cct_token_header)"
}

cct_health() {
  cct_get_unauth /api/health
}

cct_first_session_id() {
  cct_get /api/companion/sessions | python -c "import sys,json; d=json.load(sys.stdin); print(d[0]['session_id'] if d else '')"
}
