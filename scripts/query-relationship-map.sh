#!/bin/sh

set -u

json=0
from=""
to=""
relation=""
pattern=""
edge_file=""

usage() {
  printf 'Usage: sh scripts/query-relationship-map.sh [--json] [--edge-file FILE] [--from ENDPOINT] [--to ENDPOINT] [--relation RELATION] [PATTERN]\n' >&2
}

detect_edge_file() {
  if [ -n "${RELATIONSHIP_EDGE_FILE:-}" ]; then
    printf '%s\n' "$RELATIONSHIP_EDGE_FILE"
  elif [ -f Learn/repository-relationships.tsv ]; then
    printf '%s\n' "Learn/repository-relationships.tsv"
  elif [ -f project-relationships.tsv ]; then
    printf '%s\n' "project-relationships.tsv"
  else
    printf '%s\n' "Learn/repository-relationships.tsv"
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --json)
      json=1
      shift
      ;;
    --edge-file)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      edge_file=$2
      shift 2
      ;;
    --from)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      from=$2
      shift 2
      ;;
    --to)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      to=$2
      shift 2
      ;;
    --relation)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      relation=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      usage
      exit 2
      ;;
    *)
      if [ -n "$pattern" ]; then
        pattern="$pattern $1"
      else
        pattern=$1
      fi
      shift
      ;;
  esac
done

if [ -z "$edge_file" ]; then
  edge_file=$(detect_edge_file)
fi

if [ ! -f "$edge_file" ]; then
  printf 'ERROR: missing %s\n' "$edge_file" >&2
  exit 1
fi

if [ "$json" -eq 0 ]; then
  printf 'Relationship map query\n'
  printf '======================\n'
  printf 'edge_file: %s\n\n' "$edge_file"
  printf '%-44s %-12s %-44s %s\n' "source" "relation" "target" "note"
else
  printf '{\n'
  printf '  "edge_file": "%s",\n' "$edge_file"
  printf '  "edges": [\n'
fi

awk -F '	' \
  -v json="$json" \
  -v from="$from" \
  -v to="$to" \
  -v relation_filter="$relation" \
  -v pattern="$pattern" '
  function esc(s) {
    gsub(/\\/, "\\\\", s)
    gsub(/"/, "\\\"", s)
    gsub(/\t/, "\\t", s)
    gsub(/\r/, "\\r", s)
    gsub(/\n/, "\\n", s)
    return s
  }
  NR == 1 { next }
  {
    haystack = tolower($1 " " $2 " " $3 " " $4)
    needle = tolower(pattern)
    if (from != "" && $1 != from) next
    if (to != "" && $3 != to) next
    if (relation_filter != "" && $2 != relation_filter) next
    if (needle != "" && index(haystack, needle) == 0) next

    count++
    if (json == 1) {
      if (count > 1) printf ",\n"
      printf "    {"
      printf "\"source\":\"%s\",", esc($1)
      printf "\"relation\":\"%s\",", esc($2)
      printf "\"target\":\"%s\",", esc($3)
      printf "\"note\":\"%s\"", esc($4)
      printf "}"
    } else {
      printf "%-44s %-12s %-44s %s\n", $1, $2, $3, $4
    }
  }
  END {
    if (json == 1) {
      printf "\n"
    } else {
      printf "\n%d edge(s)\n", count + 0
    }
  }
' "$edge_file"

if [ "$json" -eq 1 ]; then
  printf '  ]\n'
  printf '}\n'
fi
