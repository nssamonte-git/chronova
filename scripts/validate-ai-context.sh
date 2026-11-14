#!/bin/sh
# Chronova AI context validation.
# Started from the canonical safe-fix version; adds the always-read token budget,
# manifest/startup consistency and shared file checks.
set -u

status=0
fail() { printf 'FAIL: %s\n' "$1"; status=1; }
pass() { printf 'PASS: %s\n' "$1"; }

BUDGET_TOKENS=${AI_CONTEXT_BUDGET_TOKENS:-1300}

[ -f AGENTS.md ] && pass "found AGENTS.md" || fail "missing AGENTS.md"
[ -f HANDOFF.md ] && pass "found HANDOFF.md" || fail "missing HANDOFF.md"

for file in AGENTS.md CLAUDE.md GEMINI.md; do
  [ -f "$file" ] || continue
  if grep -E '^[[:space:]]*(- )?(Read|read|Auto-read|auto-read|Require|require|Load|load)' "$file" | grep -E '(README.md|PROJECT_CONTEXT.md|TODO.md|docs/|learning-inbox/)' | grep -v -E '(Do not|do not|only when|on-demand|legacy|optional)' >/dev/null 2>&1; then
    fail "$file may require broad auto-read context"
  else
    pass "$file has no obvious broad auto-read requirement"
  fi
done

# Always-read budget: ~4/3 tokens per word over AGENTS.md + HANDOFF.md
# (same estimate as scripts/compare-context-baseline.sh).
words=$(cat AGENTS.md HANDOFF.md 2>/dev/null | wc -w | tr -d ' ')
tokens=$(( (words * 4 + 2) / 3 ))
if [ "$tokens" -le "$BUDGET_TOKENS" ]; then
  pass "always-read estimate ${tokens} tokens <= budget ${BUDGET_TOKENS}"
else
  fail "always-read estimate ${tokens} tokens exceeds budget ${BUDGET_TOKENS}"
fi

if [ -f manifest.yaml ]; then
  pass "found manifest.yaml"
  always=$(awk '/^always_read:/ { r = 1; next } /^[^ ]/ { r = 0 } r && /^  - / { sub(/^  - /, ""); printf "%s ", $0 }' manifest.yaml)
  if [ "$always" = "AGENTS.md HANDOFF.md " ]; then
    pass "manifest always_read is exactly AGENTS.md + HANDOFF.md"
  else
    fail "manifest always_read should be AGENTS.md + HANDOFF.md (found: $always)"
  fi
else
  fail "missing manifest.yaml"
fi

for file in CURRENT_TASK.md DECISIONS.md PROJECT_CONTEXT.md Learn/README.md; do
  [ -f "$file" ] && pass "found on-demand $file" || fail "missing on-demand $file"
done

# Every clone should include the project AI kit.
if git rev-parse --git-dir >/dev/null 2>&1; then
  for file in AGENTS.md HANDOFF.md CURRENT_TASK.md DECISIONS.md PROJECT_CONTEXT.md manifest.yaml \
    PROJECT_RELATIONSHIP_MAP.md project-relationships.tsv Learn/README.md \
    learning-inbox/README.md md-templates/LEARNING_LOG.md scripts/validate-ai-context.sh; do
    if git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
      pass "$file is tracked"
    else
      fail "$file is missing from the Git index"
    fi
  done
  if git check-ignore -q learning-inbox/example-capture.md; then
    pass "raw learning captures are ignored"
  else
    fail "raw learning captures must be ignored"
  fi
fi

[ "$status" -eq 0 ] && printf '\nResult: PASS\n' || printf '\nResult: FAIL\n'
exit "$status"
