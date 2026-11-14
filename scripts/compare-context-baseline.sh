#!/bin/sh

set -u

# A/B evaluation harness for always-read / context edits (see docs and Learn).
# Context files must be evaluated, not assumed helpful: this captures routing and
# budget state before an edit and compares it after, so a trim is only kept when
# the data shows no routing regression and a real token saving.
#
# Usage:
#   sh scripts/compare-context-baseline.sh --snapshot DIR    capture current state
#   sh scripts/compare-context-baseline.sh BEFORE_DIR AFTER_DIR   report the delta
#
# Typical flow:
#   sh scripts/compare-context-baseline.sh --snapshot /tmp/ctx-before
#   ...edit AGENTS.md / HANDOFF.md...
#   sh scripts/compare-context-baseline.sh --snapshot /tmp/ctx-after
#   sh scripts/compare-context-baseline.sh /tmp/ctx-before /tmp/ctx-after
#
# Snapshots live in /tmp (or any path given); no persistent runs/** artifacts.
#
# Run from the root of the repo being edited. Where the canonical router
# scripts (check-routed-budgets.sh, benchmark-routing.sh) are absent, as in
# downstream projects, it falls back to always-read tokens plus a manifest
# route signature (route names and required files), so it runs unchanged there.

always_read_tokens() {
  # ~4/3 tokens/word over AGENTS.md + HANDOFF.md (matches other budget scripts)
  w=$(cat AGENTS.md HANDOFF.md 2>/dev/null | wc -w)
  printf '%d' "$(( (w * 4 + 2) / 3 ))"
}

# route signature from manifest.yaml (or CONTEXT_MANIFEST.md): one line per
# route name and per required file, for repos without the canonical router
route_signature() {
  if [ -f manifest.yaml ]; then
    awk '
      /^  [A-Za-z0-9_.-]+:[[:space:]]*$/ { route=$1; sub(/:$/, "", route); key=""; print route "\troute"; next }
      /^    [A-Za-z0-9_]+:/ { key=$1; sub(/:.*/, "", key); next }
      route != "" && key == "required" && /^      - / { v=$0; sub(/^      - /, "", v); print route "\trequired\t" v }
    ' manifest.yaml
  elif [ -f CONTEXT_MANIFEST.md ]; then
    grep -E '^#{2,3} ' CONTEXT_MANIFEST.md
  fi
}

has_router() {
  [ -f scripts/check-routed-budgets.sh ] && [ -f scripts/benchmark-routing.sh ]
}

snapshot() {
  dir=$1
  mkdir -p "$dir"
  always_read_tokens > "$dir/always-read-tokens"
  if has_router; then
    sh scripts/check-routed-budgets.sh > "$dir/routed-budgets.txt" 2>&1 || true
    sh scripts/benchmark-routing.sh > "$dir/benchmark-routing.txt" 2>&1 || true
    mode=router
  else
    route_signature | sort > "$dir/route-signature.txt"
    mode=manifest
  fi
  printf '%s\n' "$mode" > "$dir/mode"
  printf 'Snapshot written to %s\n' "$dir"
  printf '  mode: %s\n' "$mode"
  printf '  always-read tokens: %s\n' "$(cat "$dir/always-read-tokens")"
}

snapshot_mode() {
  if [ -f "$1/mode" ]; then
    cat "$1/mode"
  elif [ -f "$1/routed-budgets.txt" ]; then
    printf 'router\n'
  else
    printf 'unknown\n'
  fi
}

# signed delta, or NA when either side has no numeric value
delta() {
  case "$1$2" in
    *[!0-9-]*|'') printf 'NA' ;;
    *) printf '%d' "$(( $2 - $1 ))" ;;
  esac
}

compare_manifest() {
  before=$1; after=$2
  ar_b=$(cat "$before/always-read-tokens" 2>/dev/null || printf 0)
  ar_a=$(cat "$after/always-read-tokens" 2>/dev/null || printf 0)

  printf 'Context A/B comparison (manifest mode)\n'
  printf '======================================\n'
  printf 'before: %s   after: %s\n\n' "$before" "$after"
  printf '%-28s %10s %10s %10s\n' "metric" "before" "after" "delta"
  printf '%-28s %10s %10s %10s\n' "always-read tokens" "$ar_b" "$ar_a" "$(delta "$ar_b" "$ar_a")"

  printf '\nRoute-signature changes (should be none for a pure context trim):\n'
  changed=$(diff "$before/route-signature.txt" "$after/route-signature.txt" 2>/dev/null)
  if [ -n "$changed" ]; then
    printf '%s\n' "$changed"
  else
    printf '  none\n'
  fi

  printf '\nVerdict: '
  if [ -n "$changed" ]; then
    printf 'REVIEW — route names or required files changed; do not keep the edit without justification.\n'
  elif [ "$ar_a" -le "$ar_b" ]; then
    printf 'SAFE — routing unchanged and always-read tokens held or dropped.\n'
  else
    printf 'SAFE (with cost) — routing unchanged, but always-read tokens grew; confirm the added context is worth it.\n'
  fi
}

# tightest full-load margin (min budget - tokens) from a check-routed-budgets file
tightest_margin() {
  awk 'NF>=4 && $2 ~ /^[0-9]+$/ && $3 ~ /^[0-9]+$/ { m=$2-$3; if(n++==0||m<mn){mn=m;t=$1} } END{ if(n) printf "%d\t%s", mn, t; else printf "NA\t-" }' "$1"
}

# total estimated routed tokens across sampled routes
total_routed_tokens() {
  awk 'NF>=4 && $3 ~ /^[0-9]+$/ { s+=$3 } END{ printf "%d", s+0 }' "$1"
}

# count of FAIL route/budget lines in a benchmark-routing file
# (grep -c prints the count but exits 1 on zero matches, so capture then default)
bench_fail() {
  c=$(grep -c 'FAIL' "$1" 2>/dev/null) || c=0
  [ -n "$c" ] || c=0
  printf '%s' "$c"
}

compare() {
  before=$1; after=$2
  for d in "$before" "$after"; do
    [ -d "$d" ] || { printf 'ERROR: missing snapshot dir: %s\n' "$d" >&2; exit 1; }
  done
  mode_b=$(snapshot_mode "$before"); mode_a=$(snapshot_mode "$after")
  if [ "$mode_b" != "$mode_a" ]; then
    printf 'ERROR: snapshot modes differ (before=%s after=%s); re-snapshot both from the same repo root.\n' "$mode_b" "$mode_a" >&2
    exit 1
  fi
  if [ "$mode_b" = manifest ]; then
    compare_manifest "$before" "$after"
    return
  fi

  ar_b=$(cat "$before/always-read-tokens" 2>/dev/null || printf 0)
  ar_a=$(cat "$after/always-read-tokens" 2>/dev/null || printf 0)
  set -- $(tightest_margin "$before/routed-budgets.txt"); mb=$1; mbt=$2
  set -- $(tightest_margin "$after/routed-budgets.txt"); ma=$1; mat=$2
  tb=$(total_routed_tokens "$before/routed-budgets.txt")
  ta=$(total_routed_tokens "$after/routed-budgets.txt")
  fb=$(bench_fail "$before/benchmark-routing.txt")
  fa=$(bench_fail "$after/benchmark-routing.txt")

  printf 'Context A/B comparison\n'
  printf '======================\n'
  printf 'before: %s   after: %s\n\n' "$before" "$after"
  printf '%-28s %10s %10s %10s\n' "metric" "before" "after" "delta"
  printf '%-28s %10s %10s %10s\n' "always-read tokens" "$ar_b" "$ar_a" "$(delta "$ar_b" "$ar_a")"
  printf '%-28s %10s %10s %10s\n' "sampled routed tokens" "$tb" "$ta" "$(delta "$tb" "$ta")"
  printf '%-28s %10s %10s %10s\n' "tightest budget margin" "$mb" "$ma" "$(delta "$mb" "$ma")"
  printf '%-28s %10s %10s %10s\n' "benchmark route FAILs" "$fb" "$fa" "$(delta "$fb" "$fa")"
  printf '  (tightest route: before=%s after=%s)\n' "$mbt" "$mat"

  # route-decision changes: key each sampled task by its text, compare the routed
  # task_type before vs after. A pure context trim must not change any route.
  printf '\nRoute-decision changes (should be none for a pure context trim):\n'
  kb=${TMPDIR:-/tmp}/ctx-ab-kb.$$; ka=${TMPDIR:-/tmp}/ctx-ab-ka.$$
  awk 'NF>=5 && $2 ~ /^[0-9]+$/ { s=$5; for(i=6;i<=NF;i++) s=s" "$i; print s"\t"$1 }' "$before/routed-budgets.txt" | sort > "$kb"
  awk 'NF>=5 && $2 ~ /^[0-9]+$/ { s=$5; for(i=6;i<=NF;i++) s=s" "$i; print s"\t"$1 }' "$after/routed-budgets.txt" | sort > "$ka"
  changed=$(diff "$kb" "$ka" 2>/dev/null)
  rm -f "$kb" "$ka"
  if [ -n "$changed" ]; then
    printf '%s\n' "$changed"
  else
    printf '  none\n'
  fi

  printf '\nVerdict: '
  if [ "$fa" -gt "$fb" ] || [ -n "$changed" ]; then
    printf 'REVIEW — routing changed or new failures; do not keep the edit without justification.\n'
  elif [ "$ar_a" -le "$ar_b" ] && { [ "$ma" = NA ] || [ "$mb" = NA ] || [ "$ma" -ge "$mb" ]; }; then
    printf 'SAFE — no route regressions, no decision changes, budget margin held or improved.\n'
  else
    printf 'SAFE (with cost) — no route regressions, but tokens grew / margin shrank; confirm the added structure is worth its per-route cost.\n'
  fi
}

case "${1:-}" in
  --snapshot)
    [ "$#" -ge 2 ] || { printf 'Usage: sh scripts/compare-context-baseline.sh --snapshot DIR\n' >&2; exit 2; }
    snapshot "$2"
    ;;
  "" | -h | --help)
    printf 'Usage:\n  sh scripts/compare-context-baseline.sh --snapshot DIR\n  sh scripts/compare-context-baseline.sh BEFORE_DIR AFTER_DIR\n'
    [ -z "${1:-}" ] && exit 2 || exit 0
    ;;
  *)
    [ "$#" -eq 2 ] || { printf 'Usage: sh scripts/compare-context-baseline.sh BEFORE_DIR AFTER_DIR\n' >&2; exit 2; }
    compare "$1" "$2"
    ;;
esac
