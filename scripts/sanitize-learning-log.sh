#!/bin/sh

set -u

if [ "$#" -ne 1 ]; then
  printf 'Usage: sh scripts/sanitize-learning-log.sh <learning-log.md>\n' >&2
  exit 2
fi

file=$1
status=0

fail() {
  printf 'FAIL: %s\n' "$1"
  status=1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

warn() {
  printf 'WARN: %s\n' "$1"
}

check_metadata() {
  key=$1
  pattern=$2
  label=$3

  if grep -Ei "$pattern" "$file" >/dev/null 2>&1; then
    pass "$label"
  else
    warn "$label missing or incomplete"
  fi
}

if [ ! -f "$file" ]; then
  printf 'ERROR: missing %s\n' "$file" >&2
  exit 1
fi

case "$file" in
  learning-inbox/*.md) ;;
  *) fail "learning logs must live under learning-inbox/" ;;
esac

printf 'Learning log sanitizer\n'
printf '======================\n'
printf 'file: %s\n' "$file"

if grep -E 'sk-[A-Za-z0-9_-]{20,}|ghp_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}' "$file" >/dev/null 2>&1; then
  fail "possible API token or cloud key pattern found"
else
  pass "no common API token patterns found"
fi

if grep -E 'BEGIN (RSA |EC |OPENSSH |DSA |)PRIVATE KEY|PRIVATE KEY-----' "$file" >/dev/null 2>&1; then
  fail "possible private key material found"
else
  pass "no private key block found"
fi

if grep -Ei '(password|passwd|pwd|secret|token|credential|api[_-]?key)[[:space:]]*[:=][[:space:]]*[^[:space:]]+' "$file" >/dev/null 2>&1; then
  fail "possible secret assignment found"
else
  pass "no obvious secret assignment found"
fi

if grep -Ei 'privacy_reviewed:[[:space:]]*yes' "$file" >/dev/null 2>&1; then
  pass "privacy_reviewed is yes"
else
  fail "privacy_reviewed must be yes before promotion review"
fi

if grep -Ei 'promote_candidate:[[:space:]]*(yes|no)' "$file" >/dev/null 2>&1; then
  pass "promote_candidate is explicit"
else
  fail "promote_candidate must be yes or no"
fi

printf '\nLearning metadata checks\n'
check_metadata "confidence" '^confidence:[[:space:]]*(low|medium|high)[[:space:]]*$' "confidence is low, medium, or high"
check_metadata "source_task" '^source_task:[[:space:]]*[^[:space:]].*' "source_task is present"
check_metadata "evidence_count" '^evidence_count:[[:space:]]*[1-9][0-9]*[[:space:]]*$' "evidence_count is positive"
check_metadata "reuse_target" '^reuse_target:[[:space:]]*(skill|reference|route|benchmark|decision|template|learn|unknown)[[:space:]]*$' "reuse_target is recognized"
check_metadata "promotion_destination" '^promotion_destination:[[:space:]]*[^[:space:]].*' "promotion_destination is present"
check_metadata "revisit_date" '^revisit_date:[[:space:]]*([0-9]{4}-[0-9]{2}-[0-9]{2}|none)[[:space:]]*$' "revisit_date is YYYY-MM-DD or none"
check_metadata "stale_or_forget_condition" '^stale_or_forget_condition:[[:space:]]*[^[:space:]].*' "stale_or_forget_condition is present"

if [ "$status" -eq 0 ]; then
  printf '\nResult: PASS\n'
else
  printf '\nResult: FAIL\n'
fi

exit "$status"
