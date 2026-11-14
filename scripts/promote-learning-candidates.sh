#!/bin/sh

set -u

dir=learning-inbox
tmp=${TMPDIR:-/tmp}/ai-knowledge-learning-promote.$$
trap 'rm -f "$tmp"' EXIT HUP INT TERM

: > "$tmp"

printf 'Learning promotion candidates (report-only)\n'
printf '===========================================\n'

if [ ! -d "$dir" ]; then
  printf 'No learning-inbox directory found.\n'
  exit 0
fi

files=$(find "$dir" -maxdepth 1 -type f -name '*.md' ! -name 'README.md' -print)
if [ -z "$files" ]; then
  printf 'No learning logs found.\n'
  exit 0
fi

printf '%s\n' "$files" |
while IFS= read -r file; do
  privacy=$(awk -F ': *' 'tolower($1)=="privacy_reviewed" { print tolower($2); exit }' "$file")
  promote=$(awk -F ': *' 'tolower($1)=="promote_candidate" { print tolower($2); exit }' "$file")
  task_type=$(awk -F ': *' 'tolower($1)=="task_type" { print $2; exit }' "$file")
  skill_used=$(awk -F ': *' 'tolower($1)=="skill_used" { print $2; exit }' "$file")
  confidence=$(awk -F ': *' 'tolower($1)=="confidence" { print tolower($2); exit }' "$file")
  evidence_count=$(awk -F ': *' 'tolower($1)=="evidence_count" { print $2; exit }' "$file")
  reuse_target=$(awk -F ': *' 'tolower($1)=="reuse_target" { print tolower($2); exit }' "$file")
  promotion_destination=$(awk -F ': *' 'tolower($1)=="promotion_destination" { print $2; exit }' "$file")
  revisit_date=$(awk -F ': *' 'tolower($1)=="revisit_date" { print $2; exit }' "$file")
  stale_or_forget=$(awk -F ': *' 'tolower($1)=="stale_or_forget_condition" { print $2; exit }' "$file")

  [ -n "$task_type" ] || task_type=unknown
  [ -n "$skill_used" ] || skill_used=unknown
  [ -n "$confidence" ] || confidence=unknown
  [ -n "$evidence_count" ] || evidence_count=1
  [ -n "$reuse_target" ] || reuse_target=unknown
  [ -n "$promotion_destination" ] || promotion_destination=unknown
  [ -n "$revisit_date" ] || revisit_date=unknown
  [ -n "$stale_or_forget" ] || stale_or_forget=unknown

  if [ "$privacy" != "yes" ]; then
    printf 'SKIP privacy not reviewed: %s\n' "$file"
    continue
  fi

  sh scripts/sanitize-learning-log.sh "$file" >/dev/null 2>&1 || {
    printf 'SKIP sanitizer failed: %s\n' "$file"
    continue
  }

  awk -v task_type="$task_type" \
    -v skill_used="$skill_used" \
    -v promote="$promote" \
    -v confidence="$confidence" \
    -v evidence_count="$evidence_count" \
    -v reuse_target="$reuse_target" \
    -v promotion_destination="$promotion_destination" \
    -v revisit_date="$revisit_date" \
    -v stale_or_forget="$stale_or_forget" '
    /^## Missing Guidance/ { in_section = 1; next }
    /^## / { in_section = 0 }
    in_section && /^[[:space:]]*- / {
      line = $0
      sub(/^[[:space:]]*- /, "", line)
      gsub(/`/, "", line)
      gsub(/[[:space:]]+/, " ", line)
      low = tolower(line)
      if (low != "todo" && low != "none" && length(low) > 8) {
        printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", task_type, skill_used, promote, low, FILENAME, confidence, evidence_count, reuse_target, promotion_destination, revisit_date "|" stale_or_forget
      }
    }
  ' "$file" >> "$tmp"
done

printf '\nRecommended candidates\n'
awk -F '\t' '
  {
    key = $1 SUBSEP $2 SUBSEP $4
    count[key]++
    task[key] = $1
    skill[key] = $2
    gap[key] = $4
    files[key] = files[key] " " $5
    confidence[key] = merge_value(confidence[key], $6)
    evidence[key] += int($7)
    reuse_target[key] = merge_value(reuse_target[key], $8)
    promotion_destination[key] = merge_value(promotion_destination[key], $9)
    lifecycle[key] = merge_value(lifecycle[key], $10)
    if ($3 == "yes") promoted[key] = 1
  }
  END {
    found = 0
    for (k in count) {
      if (count[k] > 1 || promoted[k]) {
        found = 1
        path = suggested_path(skill[k], gap[k])
        printf "\n- task_type: %s\n", task[k]
        printf "  skill: %s\n", skill[k]
        printf "  evidence_count: %d\n", count[k]
        printf "  declared_evidence_count: %d\n", evidence[k]
        printf "  confidence: %s\n", confidence[k]
        printf "  reuse_target: %s\n", reuse_target[k]
        printf "  missing_guidance: %s\n", gap[k]
        printf "  recommendation: draft compact reference or route benchmark update\n"
        printf "  suggested_path: %s\n", choose_path(path, promotion_destination[k])
        printf "  revisit_or_forget: %s\n", lifecycle[k]
        printf "  evidence_files:%s\n", files[k]
      }
    }
    if (!found) print "No promotion candidates yet. Need promote_candidate: yes or repeated gaps."
  }
  function suggested_path(skill, gap,    base, slug) {
    base = skill
    sub(/^skills\//, "skills/", base)
    if (base == "" || base == "unknown") base = "skills/software/coding/refactor-coding"
    slug = gap
    gsub(/[^a-z0-9]+/, "-", slug)
    gsub(/^-|-$/, "", slug)
    if (length(slug) > 40) slug = substr(slug, 1, 40)
    if (slug == "") slug = "learned-guidance"
    return base "/references/" slug ".md"
  }
  function merge_value(existing, value) {
    if (value == "" || value == "unknown") return existing == "" ? "unknown" : existing
    if (existing == "" || existing == "unknown") return value
    if (index(existing, value) > 0) return existing
    return existing ", " value
  }
  function choose_path(fallback, declared) {
    if (declared == "" || declared == "unknown") return fallback
    return declared
  }
' "$tmp"

printf '\nNext steps\n'
printf '1. Review candidates manually.\n'
printf '2. Add or update a compact reference, route, benchmark, or decision only if the lesson is durable.\n'
printf '3. Run validate-handoff, validate-router, check-routed-budgets, detect-duplicates, and estimate-tokens.\n'
