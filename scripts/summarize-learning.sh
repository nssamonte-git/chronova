#!/bin/sh

set -u

dir=learning-inbox
tmp=${TMPDIR:-/tmp}/ai-knowledge-learning-summary.$$
trap 'rm -f "$tmp"' EXIT HUP INT TERM

: > "$tmp"

printf 'Learning inbox summary\n'
printf '======================\n'

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
  task_type=$(awk -F ': *' 'tolower($1)=="task_type" { print $2; exit }' "$file")
  skill_used=$(awk -F ': *' 'tolower($1)=="skill_used" { print $2; exit }' "$file")
  expected=$(awk -F ': *' 'tolower($1)=="route_expected" { print $2; exit }' "$file")
  actual=$(awk -F ': *' 'tolower($1)=="route_actual" { print $2; exit }' "$file")
  promote=$(awk -F ': *' 'tolower($1)=="promote_candidate" { print tolower($2); exit }' "$file")
  privacy=$(awk -F ': *' 'tolower($1)=="privacy_reviewed" { print tolower($2); exit }' "$file")
  confidence=$(awk -F ': *' 'tolower($1)=="confidence" { print tolower($2); exit }' "$file")
  reuse_target=$(awk -F ': *' 'tolower($1)=="reuse_target" { print tolower($2); exit }' "$file")
  promotion_destination=$(awk -F ': *' 'tolower($1)=="promotion_destination" { print $2; exit }' "$file")
  revisit_date=$(awk -F ': *' 'tolower($1)=="revisit_date" { print $2; exit }' "$file")
  stale_or_forget=$(awk -F ': *' 'tolower($1)=="stale_or_forget_condition" { print $2; exit }' "$file")

  [ -n "$task_type" ] || task_type=unknown
  [ -n "$skill_used" ] || skill_used=unknown
  [ -n "$expected" ] || expected=unknown
  [ -n "$actual" ] || actual=unknown
  [ -n "$promote" ] || promote=unknown
  [ -n "$privacy" ] || privacy=unknown
  [ -n "$confidence" ] || confidence=unknown
  [ -n "$reuse_target" ] || reuse_target=unknown
  [ -n "$promotion_destination" ] || promotion_destination=unknown
  [ -n "$revisit_date" ] || revisit_date=unknown
  [ -n "$stale_or_forget" ] || stale_or_forget=unknown

  printf 'task\t%s\t%s\n' "$task_type" "$file" >> "$tmp"
  printf 'skill\t%s\t%s\n' "$skill_used" "$file" >> "$tmp"
  printf 'promote\t%s\t%s\n' "$promote" "$file" >> "$tmp"
  printf 'privacy\t%s\t%s\n' "$privacy" "$file" >> "$tmp"
  printf 'confidence\t%s\t%s\n' "$confidence" "$file" >> "$tmp"
  printf 'reuse_target\t%s\t%s\n' "$reuse_target" "$file" >> "$tmp"
  printf 'promotion_destination\t%s\t%s\n' "$promotion_destination" "$file" >> "$tmp"
  printf 'revisit_date\t%s\t%s\n' "$revisit_date" "$file" >> "$tmp"
  if [ "$stale_or_forget" != "unknown" ] && [ "$stale_or_forget" != "none" ]; then
    printf 'forget_condition\t%s\t%s\n' "$stale_or_forget" "$file" >> "$tmp"
  fi
  if [ "$expected" != "unknown" ] && [ "$actual" != "unknown" ] && [ "$expected" != "$actual" ]; then
    printf 'route_mismatch\t%s -> %s\t%s\n' "$expected" "$actual" "$file" >> "$tmp"
  fi

  awk '
    /^## Missing Guidance/ { in_section = 1; next }
    /^## / { in_section = 0 }
    in_section && /^[[:space:]]*- / {
      line = $0
      sub(/^[[:space:]]*- /, "", line)
      gsub(/`/, "", line)
      gsub(/[[:space:]]+/, " ", line)
      if (tolower(line) != "todo" && tolower(line) != "none" && length(line) > 8) {
        printf "gap\t%s\t%s\n", tolower(line), FILENAME
      }
    }
  ' "$file" >> "$tmp"
done

printf '\nTask types\n'
awk -F '\t' '$1=="task" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nSkills used\n'
awk -F '\t' '$1=="skill" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nPromotion flags\n'
awk -F '\t' '$1=="promote" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nPrivacy review flags\n'
awk -F '\t' '$1=="privacy" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nConfidence flags\n'
awk -F '\t' '$1=="confidence" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nReuse targets\n'
awk -F '\t' '$1=="reuse_target" { count[$2]++ } END { for (k in count) printf "%4d  %s\n", count[k], k }' "$tmp" | sort -nr

printf '\nPromotion destinations\n'
awk -F '\t' '$1=="promotion_destination" && $2!="unknown" { printf "%s  %s\n", $2, $3; found=1 } END { if (!found) print "No explicit promotion destinations recorded." }' "$tmp"

printf '\nRevisit dates\n'
awk -F '\t' '$1=="revisit_date" && $2!="unknown" && $2!="none" { printf "%s  %s\n", $2, $3; found=1 } END { if (!found) print "No revisit dates recorded." }' "$tmp"

printf '\nStale or forget conditions\n'
awk -F '\t' '$1=="forget_condition" { printf "%s  %s\n", $2, $3; found=1 } END { if (!found) print "No stale or forget conditions recorded." }' "$tmp"

printf '\nRepeated missing guidance\n'
awk -F '\t' '$1=="gap" { count[$2]++ } END { found=0; for (k in count) if (count[k] > 1) { printf "%4d  %s\n", count[k], k; found=1 } if (!found) print "No repeated gaps yet." }' "$tmp" | sort -nr

printf '\nRoute mismatches\n'
awk -F '\t' '$1=="route_mismatch" { printf "%s  %s\n", $2, $3; found=1 } END { if (!found) print "No route mismatches recorded." }' "$tmp"
