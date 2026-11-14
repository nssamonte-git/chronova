#!/bin/sh

set -u

if [ "$#" -lt 3 ]; then
  printf 'Usage: sh scripts/capture-learning.sh <project> <task_type> <skill_used>\n' >&2
  exit 2
fi

project=$1
task_type=$2
skill_used=$3
dir=learning-inbox

mkdir -p "$dir"

stamp=$(date -u '+%Y%m%dT%H%M%SZ')
safe_project=$(printf '%s' "$project" | tr ' /:	' '____' | tr -cd 'A-Za-z0-9._-')
[ -n "$safe_project" ] || safe_project=project
file="$dir/${stamp}-${safe_project}.md"

{
  printf '# Learning Log\n\n'
  printf 'date: %s\n' "$(date -u '+%Y-%m-%d')"
  printf 'project: %s\n' "$project"
  printf 'task_type: %s\n' "$task_type"
  printf 'skill_used: %s\n' "$skill_used"
  printf 'route_expected: unknown\n'
  printf 'route_actual: %s\n' "$task_type"
  printf 'validation_result: unknown\n'
  printf 'promote_candidate: no\n'
  printf 'privacy_reviewed: no\n'
  printf 'confidence: low\n'
  printf 'source_task: TODO sanitized task summary\n'
  printf 'evidence_count: 1\n'
  printf 'reuse_target: unknown\n'
  printf 'promotion_destination: unknown\n'
  printf 'revisit_date: none\n'
  printf 'stale_or_forget_condition: TODO when this lesson should be revised or ignored\n\n'
  printf '## What Worked\n\n'
  printf -- '- TODO\n\n'
  printf '## What Failed\n\n'
  printf -- '- TODO\n\n'
  printf '## Missing Guidance\n\n'
  printf -- '- TODO\n\n'
  printf '## Promotion Idea\n\n'
  printf -- '- none\n\n'
  printf '## Privacy Notes\n\n'
  printf -- '- Confirm no secrets, credentials, private code, customer data, or sensitive logs were copied.\n'
} > "$file"

printf 'Created learning draft: %s\n' "$file"
printf 'Edit it, set privacy_reviewed: yes, then run:\n'
printf '  sh scripts/sanitize-learning-log.sh %s\n' "$file"
