#!/usr/bin/env bash
# CCT-32 Phase A E2E — assert helpers.

GREEN="\033[32m"
RED="\033[31m"
YELLOW="\033[33m"
RESET="\033[0m"

cct_assert_eq() {
  local actual="$1" expected="$2" msg="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo -e "  ${GREEN}PASS${RESET}: $msg"
    return 0
  else
    echo -e "  ${RED}FAIL${RESET}: $msg (expected '$expected', got '$actual')"
    return 1
  fi
}

cct_assert_contains() {
  local haystack="$1" needle="$2" msg="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo -e "  ${GREEN}PASS${RESET}: $msg"
    return 0
  else
    echo -e "  ${RED}FAIL${RESET}: $msg (looking for '$needle' in '$haystack')"
    return 1
  fi
}

cct_assert_nonempty() {
  local val="$1" msg="$2"
  if [[ -n "$val" ]]; then
    echo -e "  ${GREEN}PASS${RESET}: $msg"
    return 0
  else
    echo -e "  ${RED}FAIL${RESET}: $msg (value was empty)"
    return 1
  fi
}

cct_skip() {
  echo -e "  ${YELLOW}SKIP${RESET}: $1"
}

cct_section() {
  echo ""
  echo -e "===== $1 ====="
}
