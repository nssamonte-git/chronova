#!/bin/sh
# threat-scan.sh - source-level threat gate for mobile builds.
#
# Scans a project tree with rule files derived from the threat library
# (one rule per detectable "How to Find or Test" indicator) and exits
# non-zero when a finding at or above the blocking threshold exists.
#
# Usage:
#   sh threat-scan.sh --platform <android|ios|flutter|react-native> [options]
#
# Options:
#   --root <dir>          project root to scan (default: current directory)
#   --threshold <level>   block on findings at/above level:
#                         critical | high (default) | medium | low | none
#   --format <fmt>        console (default) | json | markdown | xcode | ids
#   --report <file>       also write a JSON report to <file>
#   --include-tests       scan test source sets too (excluded by default)
#   --no-fix-detail       do not print the card's "How to Fix" section
#   --threats-dir <dir>   threat library folder for fix detail (or $THREATS_DIR)
#   --list-rules          print the active rules and exit
#   -h, --help            this help
#
# Exit codes: 0 no blocking findings, 1 blocking findings, 2 usage/config error.
#
# Rule files (core/rules/*.rules) are tab-separated, one rule per line:
#   rule_id threat_id severity type file_globs pattern message card [flags [exclude]]
# Use "-" for an empty field. type: match | imatch | absent | tree-absent | file | check.
# flags (";"-separated, skippath= must come last):
#   redact        never print the matched text (secrets)
#   gitignored-ok skip files that git ignores (they are not committed)
#   nodebug       skip debug-only source sets (src/debug/)
#   skippath=ERE  skip files whose relative path matches ERE
# exclude: ERE (case-sensitive); a matched line that also matches it is dropped.
#
# Suppression (a reason is mandatory, otherwise the suppression is ignored):
#   inline, on the finding's line or the line above:
#     // build-validator:ignore AND-WV-001 reason: bridge only loads bundled assets
#   project file <root>/.build-validator-ignore, one per line:
#     AND-WV-001<TAB>app/src/main/java/legacy/*<TAB>reason text
set -u

VERSION=1.0.0
core_dir=$(cd "$(dirname "$0")" && pwd)
rules_dir=$core_dir/rules
lib_dir=$core_dir/lib
tab=$(printf '\t')
nl='
'

platform=""
root=""
threshold=high
format=console
report=""
include_tests=0
list_rules=0
fix_detail=1
threats_dir=${THREATS_DIR:-}

usage() {
  sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 2
}

need_arg() {
  [ "$#" -ge 2 ] || die "option $1 needs a value"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --platform) need_arg "$@"; platform=$2; shift 2 ;;
    --platform=*) platform=${1#*=}; shift ;;
    --root) need_arg "$@"; root=$2; shift 2 ;;
    --root=*) root=${1#*=}; shift ;;
    --threshold) need_arg "$@"; threshold=$2; shift 2 ;;
    --threshold=*) threshold=${1#*=}; shift ;;
    --format) need_arg "$@"; format=$2; shift 2 ;;
    --format=*) format=${1#*=}; shift ;;
    --report) need_arg "$@"; report=$2; shift 2 ;;
    --report=*) report=${1#*=}; shift ;;
    --threats-dir) need_arg "$@"; threats_dir=$2; shift 2 ;;
    --threats-dir=*) threats_dir=${1#*=}; shift ;;
    --include-tests) include_tests=1; shift ;;
    --list-rules) list_rules=1; shift ;;
    --no-fix-detail) fix_detail=0; shift ;;
    --version) printf 'threat-scan %s\n' "$VERSION"; exit 0 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

case "$platform" in
  android) rule_sets="shared android" ;;
  ios) rule_sets="shared ios" ;;
  flutter) rule_sets="shared flutter android ios" ;;
  react-native) rule_sets="shared react-native android ios" ;;
  "") usage >&2; die "--platform is required" ;;
  *) die "unknown platform: $platform (android|ios|flutter|react-native)" ;;
esac

case "$threshold" in critical|high|medium|low|none) ;; *) die "bad --threshold: $threshold" ;; esac
case "$format" in console|json|markdown|xcode|ids) ;; *) die "bad --format: $format" ;; esac

[ -n "$root" ] || root=$(pwd)
[ -d "$root" ] || die "project root not found: $root"
root=$(cd "$root" && pwd)

# Resolve the threat library for fix detail: flag/env, vendored copy, canonical repo.
if [ -z "$threats_dir" ]; then
  if [ -d "$core_dir/threats" ]; then
    threats_dir=$core_dir/threats
  elif [ -d "${AI_KNOWLEDGE_REPO:-$HOME/Projects/AI Knowledge and Skills Repo}/threats" ]; then
    threats_dir="${AI_KNOWLEDGE_REPO:-$HOME/Projects/AI Knowledge and Skills Repo}/threats"
  fi
fi

sev_rank() {
  case "$1" in
    critical) echo 4 ;; high) echo 3 ;; medium) echo 2 ;; low) echo 1 ;; none) echo 0 ;;
    *) echo 0 ;;
  esac
}
threshold_rank=$(sev_rank "$threshold")
[ "$threshold" = none ] && threshold_rank=99

tmp=$(mktemp -d "${TMPDIR:-/tmp}/threat-scan.XXXXXX") || die "cannot create temp dir"
trap 'rm -rf "$tmp"' EXIT INT TERM

# ---------------------------------------------------------------- rules
: > "$tmp/rules.tsv"
for set in $rule_sets; do
  f=$rules_dir/$set.rules
  [ -f "$f" ] || die "missing rule file: $f"
  tr -d '\r' < "$f" | grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$' >> "$tmp/rules.tsv"
  [ -f "$lib_dir/checks-$set.sh" ] && . "$lib_dir/checks-$set.sh"
done
[ -f "$lib_dir/checks-common.sh" ] && . "$lib_dir/checks-common.sh"

if [ "$list_rules" -eq 1 ]; then
  printf 'rule_id\tthreat_id\tseverity\ttype\tfile_globs\tpattern\n'
  while IFS="$tab" read -r rule_id threat_id severity type globs pattern message card flags exclude; do
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$rule_id" "$threat_id" "$severity" "$type" "$globs" "$pattern"
  done < "$tmp/rules.tsv"
  exit 0
fi

# ---------------------------------------------------------------- file list
cd "$root" || die "cannot enter $root"
if [ "$include_tests" -eq 1 ]; then
  find . \( -name .git -o -name node_modules -o \( -name build ! -path '*/src/*' \) -o -name .gradle -o -name .idea \
            -o -name Pods -o -name DerivedData -o -name .dart_tool -o -name .build -o -name vendor \
            -o -name build-validator -o -name .expo -o -name dist -o -name .svn -o -name .hg \
            -o -name .cxx -o -name .pub-cache -o -name .fvm -o -name .symlinks -o -name ephemeral \
            -o -name Carthage -o -name .plugin_symlinks \) -prune \
       -o -type f -size -2048k -print
else
  find . \( -name .git -o -name node_modules -o \( -name build ! -path '*/src/*' \) -o -name .gradle -o -name .idea \
            -o -name Pods -o -name DerivedData -o -name .dart_tool -o -name .build -o -name vendor \
            -o -name build-validator -o -name .expo -o -name dist -o -name .svn -o -name .hg \
            -o -name .cxx -o -name .pub-cache -o -name .fvm -o -name .symlinks -o -name ephemeral \
            -o -name Carthage -o -name .plugin_symlinks \
            -o -name test -o -name tests -o -name androidTest -o -name __tests__ -o -name '*Tests' -o -name '*UITests' \) -prune \
       -o -type f -size -2048k -print
fi | sed 's#^\./##' | grep -v '^\.build-validator-ignore$' | LC_ALL=C sort > "$tmp/files"

in_git=0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 && in_git=1

# glob list ("a;b;c") -> ERE over relative paths. "*" never crosses "/", "**" does.
globs_to_re() {
  printf '%s' "$1" | tr ';' '\n' | sed \
    -e 's/[.+?^$(){}|]/\\&/g' \
    -e 's/\*\*/~~/g' \
    -e 's#\*#[^/]*#g' \
    -e 's/~~/.*/g' | paste -sd '|' - | sed -e 's/^/(^|\/)(/' -e 's/$/)$/'
}

files_for_globs() {
  if [ "$1" = "-" ] || [ -z "$1" ]; then
    cat "$tmp/files"
    return
  fi
  # many rules share a glob list: cache the filtered file list per glob string
  key=$(printf '%s' "$1" | cksum | tr ' ' '_')
  if [ ! -f "$tmp/globs.$key" ]; then
    grep -E "$(globs_to_re "$1")" "$tmp/files" > "$tmp/globs.$key" || true
  fi
  cat "$tmp/globs.$key"
}

# findings.tsv: severity rule_id threat_id file line excerpt message card flags
: > "$tmp/findings.tsv"
emit() {
  # $1 sev $2 rule $3 threat $4 file $5 line $6 excerpt $7 message $8 card $9 flags
  # Fields are tab-separated and read back with IFS=tab, where adjacent tabs
  # collapse, so no field may be empty: "-" stands for "none".
  excerpt=$(printf '%s' "$6" | tr '\t' ' ' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' | cut -c1-160)
  [ -n "$excerpt" ] || excerpt="-"
  fl=${9:--}
  case "$fl" in *nodebug*) case "$4" in src/debug/*|*/src/debug/*) return 0 ;; esac ;; esac
  case "$fl" in
    *skippath=*) sp=${fl#*skippath=}
                 printf '%s\n' "$4" | grep -qE -e "$sp" && return 0 ;;
  esac
  case "$fl" in
    *gitignored-ok*) [ "$in_git" -eq 1 ] && [ "$4" != "." ] && git check-ignore -q -- "$4" 2>/dev/null && return 0 ;;
  esac
  case "$fl" in *redact*) excerpt="<redacted: secret-like value, open the file to review>" ;; esac
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "${5:-0}" "$excerpt" "${7:--}" "${8:--}" "$fl" >> "$tmp/findings.tsv"
}

run_grep() {
  # $1 file-list $2 grep flags $3 pattern -> file:line:text
  [ -s "$1" ] || return 0
  tr '\n' '\0' < "$1" | xargs -0 grep -nHI $2 -e "$3" -- 2>/dev/null || true
}

# ---------------------------------------------------------------- scan
while IFS="$tab" read -r rule_id threat_id severity type globs pattern message card flags exclude; do
  [ -n "$rule_id" ] || continue
  flags=${flags:--}
  exclude=${exclude:--}
  files_for_globs "$globs" > "$tmp/rf"
  case "$type" in
    match|imatch)
      gf=-E; [ "$type" = imatch ] && gf="-iE"
      run_grep "$tmp/rf" "$gf" "$pattern" | EXCL="$exclude" awk '
        BEGIN { ex = ENVIRON["EXCL"] }
        ex == "-" { print; next }
        { t = $0; p = index(t, ":"); t = substr(t, p + 1); p = index(t, ":"); t = substr(t, p + 1)
          if (t !~ ex) print }' | while IFS= read -r hit; do
        file=${hit%%:*}; rest=${hit#*:}; line=${rest%%:*}; text=${rest#*:}
        emit "$severity" "$rule_id" "$threat_id" "$file" "$line" "$text" "$message" "$card" "$flags"
      done
      ;;
    absent)
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        grep -qIE -e "$pattern" -- "$f" 2>/dev/null || \
          emit "$severity" "$rule_id" "$threat_id" "$f" 0 "no line matches: $pattern" "$message" "$card" "$flags"
      done < "$tmp/rf"
      ;;
    tree-absent)
      # fires only when the globs match some file and none of them contains the pattern
      if [ -s "$tmp/rf" ] && ! tr '\n' '\0' < "$tmp/rf" | xargs -0 grep -lIE -e "$pattern" -- >/dev/null 2>&1; then
        emit "$severity" "$rule_id" "$threat_id" "." 0 "nothing in the tree matches: $pattern" "$message" "$card" "$flags"
      fi
      ;;
    file)
      while IFS= read -r f; do
        [ -n "$f" ] || continue
        emit "$severity" "$rule_id" "$threat_id" "$f" 0 "file present" "$message" "$card" "$flags"
      done < "$tmp/rf"
      ;;
    check)
      fn=$(printf '%s' "${pattern%%;;*}" | tr '-' '_')
      args=${pattern#*;;}
      [ "$args" = "$pattern" ] && args=""
      if command -v "check_$fn" >/dev/null 2>&1; then
        "check_$fn" "$tmp/rf" "$args" | while IFS="$tab" read -r f line text; do
          [ -n "$f" ] || continue
          emit "$severity" "$rule_id" "$threat_id" "$f" "${line:-0}" "$text" "$message" "$card" "$flags"
        done
      else
        printf 'WARN: rule %s uses unknown check "%s" (skipped)\n' "$rule_id" "$fn" >&2
      fi
      ;;
    *)
      printf 'WARN: rule %s has unknown type "%s" (skipped)\n' "$rule_id" "$type" >&2
      ;;
  esac
done < "$tmp/rules.tsv"

# ---------------------------------------------------------------- suppression
: > "$tmp/active.tsv"
: > "$tmp/suppressed.tsv"
: > "$tmp/badsuppress.tsv"
ignore_file=".build-validator-ignore"
while IFS="$tab" read -r sev rule_id threat_id file line excerpt message card flags; do
  reason=""
  status=""
  if [ "$line" -gt 0 ] 2>/dev/null && [ -f "$file" ]; then
    status=$(awk -v n="$line" -v id="$rule_id" '
      NR == n - 1 || NR == n {
        if ($0 ~ ("build-validator:ignore[^\n]*(" id "|all)([^A-Za-z0-9-]|$)")) {
          if ($0 ~ /reason:[[:space:]]*[^[:space:]]/) { r = $0; sub(/.*reason:[[:space:]]*/, "", r); sub(/[[:space:]]*(-->|\*\/)[[:space:]]*$/, "", r); print "ok\t" r; exit }
          else { print "noreason\t"; exit }
        }
      }' "$file")
  fi
  if [ -z "$status" ] && [ -f "$ignore_file" ]; then
    status=$(tr -d '\r' < "$ignore_file" | awk -v id="$rule_id" -v f="$file" -F '\t' '
      /^[[:space:]]*#/ || NF < 2 { next }
      $1 == id || $1 == "all" {
        pat = $2; gsub(/\./, "\\.", pat); gsub(/\*/, ".*", pat); gsub(/\?/, ".", pat)
        if (f ~ ("^" pat "$")) {
          if (NF >= 3 && $3 ~ /[^[:space:]]/) { print "ok\t" $3; exit } else { print "noreason\t"; exit }
        }
      }')
  fi
  case "$status" in
    ok*) reason=${status#ok$tab}
         printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$sev" "$rule_id" "$threat_id" "$file" "$line" "$excerpt" "$message" "$card" "$flags" "$reason" >> "$tmp/suppressed.tsv" ;;
    noreason*) printf '%s\t%s\n' "$rule_id" "$file:$line" >> "$tmp/badsuppress.tsv"
         printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$sev" "$rule_id" "$threat_id" "$file" "$line" "$excerpt" "$message" "$card" "$flags" >> "$tmp/active.tsv" ;;
    *)   printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$sev" "$rule_id" "$threat_id" "$file" "$line" "$excerpt" "$message" "$card" "$flags" >> "$tmp/active.tsv" ;;
  esac
done < "$tmp/findings.tsv"

# order: critical, high, medium, low; then file, line
awk -F '\t' -v OFS='\t' '{ r = ($1=="critical")?4:($1=="high")?3:($1=="medium")?2:1; print r, $0 }' "$tmp/active.tsv" \
  | sort -t "$tab" -k1,1nr -k5,5 -k6,6n | cut -f2- > "$tmp/sorted.tsv"

blocking=0; warnings=0
while IFS="$tab" read -r sev rest; do
  [ -n "$sev" ] || continue
  if [ "$(sev_rank "$sev")" -ge "$threshold_rank" ]; then blocking=$((blocking + 1)); else warnings=$((warnings + 1)); fi
done < "$tmp/sorted.tsv"
count_lines() { if [ -f "$1" ]; then wc -l < "$1" | tr -d ' '; else echo 0; fi; }
suppressed=$(count_lines "$tmp/suppressed.tsv")
files_scanned=$(count_lines "$tmp/files")
rules_count=$(count_lines "$tmp/rules.tsv")

# ---------------------------------------------------------------- report
. "$lib_dir/report.sh"

case "$format" in
  console)  report_console ;;
  markdown) report_markdown ;;
  json)     report_json ;;
  xcode)    report_xcode ;;
  ids)      report_ids ;;
esac

if [ -n "$report" ]; then
  mkdir -p "$(dirname "$report")" 2>/dev/null
  report_json > "$report" || die "cannot write report: $report"
fi

[ "$blocking" -eq 0 ] && exit 0
exit 1
