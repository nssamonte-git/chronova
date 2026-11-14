#!/bin/sh

set -u

status=0
edge_file=""
map_file=""
manifest=""
tasks_tmp=${TMPDIR:-/tmp}/ai-knowledge-relationship-tasks.$$
required_tmp=${TMPDIR:-/tmp}/ai-knowledge-relationship-required.$$
workflows_tmp=${TMPDIR:-/tmp}/ai-knowledge-relationship-workflows.$$
trap 'rm -f "$tasks_tmp" "$required_tmp" "$workflows_tmp"' EXIT HUP INT TERM

usage() {
  printf 'Usage: sh scripts/validate-relationship-map.sh [--edge-file FILE] [--map-file FILE] [--manifest FILE]\n' >&2
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

detect_map_file() {
  if [ -n "${RELATIONSHIP_MAP_FILE:-}" ]; then
    printf '%s\n' "$RELATIONSHIP_MAP_FILE"
  elif [ -f Learn/07-repository-relationship-map.md ]; then
    printf '%s\n' "Learn/07-repository-relationship-map.md"
  elif [ -f PROJECT_RELATIONSHIP_MAP.md ]; then
    printf '%s\n' "PROJECT_RELATIONSHIP_MAP.md"
  else
    printf '%s\n' "Learn/07-repository-relationship-map.md"
  fi
}

detect_manifest() {
  if [ -n "${RELATIONSHIP_MANIFEST_FILE:-}" ]; then
    printf '%s\n' "$RELATIONSHIP_MANIFEST_FILE"
  elif [ -f manifest.yaml ]; then
    printf '%s\n' "manifest.yaml"
  elif [ -f CONTEXT_MANIFEST.md ]; then
    printf '%s\n' "CONTEXT_MANIFEST.md"
  else
    printf '%s\n' "manifest.yaml"
  fi
}

fail() {
  printf 'FAIL: %s\n' "$1"
  status=1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

endpoint_exists_in_edges() {
  endpoint=$1
  awk -F '	' -v endpoint="$endpoint" '$1 == endpoint || $3 == endpoint { found = 1 } END { exit found ? 0 : 1 }' "$edge_file"
}

edge_exists() {
  source=$1
  relation=$2
  target=$3
  awk -F '	' -v source="$source" -v relation="$relation" -v target="$target" \
    '$1 == source && $2 == relation && $3 == target { found = 1 } END { exit found ? 0 : 1 }' "$edge_file"
}

manifest_has_task() {
  task=$1
  awk -v task="$task" '$0 == "  " task ":" { found = 1 } END { exit found ? 0 : 1 }' "$manifest"
}

workflow_documents_required() {
  [ -d workflows ]
}

check_endpoint() {
  endpoint=$1
  line_no=$2
  field=$3

  case "$endpoint" in
    path:*)
      path=${endpoint#path:}
      case "$path" in
        ""|.git|.git/*|*/.git|*/.git/*|.firebase|.firebase/*|*/.firebase|*/.firebase/*|node_modules|*/node_modules|*/node_modules/*|dist|*/dist|*/dist/*|runs|runs/*|*/runs|*/runs/*)
          fail "line $line_no $field uses generated/cache path endpoint: $endpoint"
          ;;
        *)
          if [ -e "$path" ]; then
            :
          else
            fail "line $line_no $field path does not exist: $path"
          fi
          ;;
      esac
      ;;
    task:*)
      task=${endpoint#task:}
      if [ -n "$task" ] && manifest_has_task "$task"; then
        :
      else
        fail "line $line_no $field task is not in manifest: $endpoint"
      fi
      ;;
    workflow:*)
      workflow=${endpoint#workflow:}
      if [ -z "$workflow" ]; then
        fail "line $line_no $field workflow is empty"
      elif workflow_documents_required && [ ! -f "workflows/$workflow.md" ]; then
        fail "line $line_no $field workflow is missing: $endpoint"
      else
        :
      fi
      ;;
    concept:*)
      concept=${endpoint#concept:}
      if [ -n "$concept" ]; then
        :
      else
        fail "line $line_no $field concept is empty"
      fi
      ;;
    *)
      fail "line $line_no $field has unsupported endpoint prefix: $endpoint"
      ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --edge-file)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      edge_file=$2
      shift 2
      ;;
    --map-file)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      map_file=$2
      shift 2
      ;;
    --manifest)
      if [ "$#" -lt 2 ]; then usage; exit 2; fi
      manifest=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [ -z "$edge_file" ]; then edge_file=$(detect_edge_file); fi
if [ -z "$map_file" ]; then map_file=$(detect_map_file); fi
if [ -z "$manifest" ]; then manifest=$(detect_manifest); fi
manifest_endpoint=path:$manifest

printf 'Relationship map validation\n'
printf '===========================\n'

if [ -f "$map_file" ]; then
  pass "found $map_file"
else
  fail "missing $map_file"
fi

if [ -f "$edge_file" ]; then
  pass "found $edge_file"
else
  fail "missing $edge_file"
  exit "$status"
fi

if [ -f "$manifest" ]; then
  pass "found $manifest"
else
  fail "missing $manifest"
  exit "$status"
fi

expected_header=$(printf 'source\trelation\ttarget\tnote')
IFS= read -r header < "$edge_file"
if [ "$header" = "$expected_header" ]; then
  pass "edge list header is correct"
else
  fail "edge list header must be: source<TAB>relation<TAB>target<TAB>note"
fi

tab=$(printf '\t')
line_no=0
data_lines=0
while IFS="$tab" read -r source relation target note extra; do
  line_no=$((line_no + 1))
  [ "$line_no" -eq 1 ] && continue

  data_lines=$((data_lines + 1))

  if [ -z "$source" ] || [ -z "$relation" ] || [ -z "$target" ] || [ -z "$note" ]; then
    fail "line $line_no has an empty required field"
  fi

  if [ -n "${extra:-}" ]; then
    fail "line $line_no has more than four tab-separated fields"
  fi

  case "$relation" in
    starts_with|routes_to|loads|documents|validates|benchmarks|guards|produces|reviews|depends_on|informed_by|excludes)
      :
      ;;
    *)
      fail "line $line_no has unsupported relation: $relation"
      ;;
  esac

  check_endpoint "$source" "$line_no" "source"
  check_endpoint "$target" "$line_no" "target"
done < "$edge_file"

if [ "$data_lines" -gt 0 ]; then
  pass "edge list has data rows"
else
  fail "edge list has no data rows"
fi

awk '
  /^task_types:/ { in_tasks = 1; next }
  /^loading_rules:/ { in_tasks = 0 }
  in_tasks && /^  [A-Za-z0-9_-]+:/ {
    task = $1
    sub(/:$/, "", task)
    print task
  }
' "$manifest" > "$tasks_tmp"

while IFS= read -r task; do
  if endpoint_exists_in_edges "task:$task"; then
    pass "manifest task appears in relationship map: $task"
  else
    fail "manifest task missing from relationship map: $task"
  fi

  if edge_exists "$manifest_endpoint" "routes_to" "task:$task"; then
    pass "manifest routes_to edge exists for task: $task"
  else
    fail "missing manifest routes_to edge for task: $task"
  fi
done < "$tasks_tmp"

awk '
  /^task_types:/ { in_tasks = 1; next }
  /^loading_rules:/ { in_tasks = 0 }
  in_tasks && /^  [A-Za-z0-9_-]+:/ {
    task = $1
    sub(/:$/, "", task)
    section = ""
    next
  }
  in_tasks && /^    required:/ { section = "required"; next }
  in_tasks && /^    [A-Za-z0-9_-]+:/ { section = ""; next }
  in_tasks && section == "required" && /^[[:space:]]+- / {
    path = $0
    sub(/^[[:space:]]+- /, "", path)
    if (path != "" && path != "[]" && path !~ /\*\*/) print task "\t" path
  }
' "$manifest" > "$required_tmp"

while IFS='	' read -r task path; do
  if edge_exists "task:$task" "loads" "path:$path"; then
    pass "required load edge matches manifest: $task -> $path"
  else
    fail "missing required load edge for manifest route: $task -> $path"
  fi
done < "$required_tmp"

awk '
  /^task_types:/ { in_tasks = 1; next }
  /^loading_rules:/ { in_tasks = 0 }
  in_tasks && /^    workflow:/ {
    workflow = $0
    sub(/^.*workflow:[[:space:]]*/, "", workflow)
    if (workflow != "") print workflow
  }
' "$manifest" | sort -u > "$workflows_tmp"

while IFS= read -r workflow; do
  if edge_exists "$manifest_endpoint" "routes_to" "workflow:$workflow"; then
    pass "manifest routes_to edge exists for workflow: $workflow"
  else
    fail "missing manifest routes_to edge for workflow: $workflow"
  fi

  if workflow_documents_required; then
    if edge_exists "workflow:$workflow" "documents" "path:workflows/$workflow.md"; then
      pass "workflow document edge exists: $workflow"
    else
      fail "missing workflow document edge: $workflow"
    fi
  else
    pass "workflow document edge optional without workflows directory: $workflow"
  fi
done < "$workflows_tmp"

if [ -f "$map_file" ]; then
  if grep -F '```mermaid' "$map_file" >/dev/null 2>&1; then
    pass "relationship map includes Mermaid diagrams"
  else
    fail "relationship map should include Mermaid diagrams"
  fi

  edge_base=${edge_file##*/}
  if grep -F "$edge_file" "$map_file" >/dev/null 2>&1 || grep -F "$edge_base" "$map_file" >/dev/null 2>&1; then
    pass "relationship map links edge list"
  else
    fail "relationship map should link $edge_file"
  fi
fi

if [ -f scripts/query-relationship-map.sh ]; then
  smoke_source=$(awk -F '	' 'NR > 1 && $1 != "" { print $1; exit }' "$edge_file")
  smoke_target=$(awk -F '	' 'NR > 1 && $3 != "" { print $3; exit }' "$edge_file")
  if [ -n "$smoke_source" ] && sh scripts/query-relationship-map.sh --edge-file "$edge_file" --from "$smoke_source" | grep -F "$smoke_target" >/dev/null 2>&1; then
    pass "relationship query script returns expected route edge"
  else
    fail "relationship query script did not return expected edge"
  fi
else
  fail "missing scripts/query-relationship-map.sh"
fi

if [ "$status" -eq 0 ]; then
  printf '\nResult: PASS\n'
else
  printf '\nResult: FAIL\n'
fi

exit "$status"
