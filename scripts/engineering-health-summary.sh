#!/bin/sh
# Chronova engineering health summary (report-only).
# Aggregates the checks this project actually uses: AI context, relationship map,
# learning inbox, and the Gradle unit-test / build / lint gates. No device needed.
#
# Usage: sh scripts/engineering-health-summary.sh [--no-gradle] [--json]
set -u

run_gradle=1
json=0
for arg in "$@"; do
  case "$arg" in
    --no-gradle) run_gradle=0 ;;
    --json) json=1 ;;
    *) printf 'Usage: sh scripts/engineering-health-summary.sh [--no-gradle] [--json]\n' >&2; exit 2 ;;
  esac
done

tmpdir=$(mktemp -d "${TMPDIR:-/tmp}/chronova-health.XXXXXX") || exit 1
trap 'rm -rf "$tmpdir"' EXIT HUP INT TERM
overall=0
results="$tmpdir/results"
: > "$results"

run_check() {
  name=$1
  shift
  if "$@" > "$tmpdir/$name.out" 2>&1; then
    printf '%s\tPASS\n' "$name" >> "$results"
  else
    printf '%s\tFAIL\n' "$name" >> "$results"
    overall=1
  fi
}

skip_check() {
  printf '%s\tSKIP\n' "$1" >> "$results"
}

run_check ai-context sh scripts/validate-ai-context.sh
run_check relationship-map sh scripts/validate-relationship-map.sh --edge-file project-relationships.tsv --map-file PROJECT_RELATIONSHIP_MAP.md
run_check learning-summary sh scripts/summarize-learning.sh

tests="n/a"
lint="n/a"
if [ "$run_gradle" -eq 1 ]; then
  # The Android Gradle project lives in its own folder.
  android="Chronova - Android App"
  run_check unit-tests sh -c 'cd "$1" && ./gradlew --console=plain -q :app:testDebugUnitTest' _ "$android"
  run_check build sh -c 'cd "$1" && ./gradlew --console=plain -q :app:assembleDebug' _ "$android"
  run_check lint sh -c 'cd "$1" && ./gradlew --console=plain -q :app:lintDebug' _ "$android"
  if ls "$android"/app/build/test-results/testDebugUnitTest/*.xml >/dev/null 2>&1; then
    tests=$(sed -n 's/.*<testsuite [^>]*tests="\([0-9]*\)"[^>]*failures="\([0-9]*\)".*/\1 \2/p' \
      "$android"/app/build/test-results/testDebugUnitTest/*.xml | awk '{t += $1; f += $2} END {printf "%d tests, %d failures", t, f}')
  fi
  if [ -f "$android"/app/build/reports/lint-results-debug.txt ]; then
    lint=$(tail -1 "$android"/app/build/reports/lint-results-debug.txt)
  fi
else
  skip_check unit-tests
  skip_check build
  skip_check lint
fi

if [ "$json" -eq 1 ]; then
  printf '{\n  "overall": "%s",\n  "tests": "%s",\n  "lint": "%s",\n  "checks": {\n' \
    "$([ "$overall" -eq 0 ] && echo PASS || echo FAIL)" "$tests" "$lint"
  awk -F '\t' '{ lines[NR] = sprintf("    \"%s\": \"%s\"", $1, $2) } END { for (i = 1; i <= NR; i++) printf "%s%s\n", lines[i], (i < NR ? "," : "") }' "$results"
  printf '  }\n}\n'
else
  printf 'Chronova engineering health\n'
  printf '===========================\n'
  awk -F '\t' '{ printf "- %-18s %s\n", $1, $2 }' "$results"
  printf -- '- tests:             %s\n' "$tests"
  printf -- '- lint:              %s\n' "$lint"
  printf '\nOverall: %s\n' "$([ "$overall" -eq 0 ] && echo PASS || echo FAIL)"
  if [ "$overall" -ne 0 ]; then
    printf '\nFailing output:\n'
    awk -F '\t' '$2 == "FAIL" { print $1 }' "$results" | while IFS= read -r name; do
      printf -- '--- %s\n' "$name"
      tail -15 "$tmpdir/$name.out"
    done
  fi
fi
exit "$overall"
