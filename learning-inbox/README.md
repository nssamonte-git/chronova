# Learning Inbox

Inactive, sanitized project lessons go here before human review. Do not auto-load this folder during normal agent startup.

## Rules

- Capture a lesson only after a real task reveals it: `sh scripts/capture-learning.sh chronova <task_type> <skill_used>`.
- Fill in the log from `md-templates/LEARNING_LOG.md`. Leave out secrets, private code, device identifiers and transcripts.
- Sanitize before any summary or promotion: `sh scripts/sanitize-learning-log.sh learning-inbox/<file>.md`.
- Reports: `sh scripts/summarize-learning.sh` and `sh scripts/promote-learning-candidates.sh`. Promotion is report-only.
- A human reviews a lesson before it becomes `DECISIONS.md`, a route, Learn content, or a canonical-repo proposal.
- Factual submission history belongs in `docs/AI_USAGE_LOG.md`, not here. This inbox holds reusable lessons only.
