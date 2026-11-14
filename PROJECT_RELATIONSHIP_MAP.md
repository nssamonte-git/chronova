# Project Relationship Map

An orientation layer, read on demand. The query-backed source of truth is [`project-relationships.tsv`](project-relationships.tsv). Query it rather than reading it in full:

```sh
sh scripts/query-relationship-map.sh --edge-file project-relationships.tsv --from task:timeline-ui-change
sh scripts/query-relationship-map.sh --edge-file project-relationships.tsv --relation validates
```

## AI Context Flow

```mermaid
flowchart LR
  AGENTS[AGENTS.md] -->|starts_with| HANDOFF[HANDOFF.md]
  HANDOFF -->|documents| MANIFEST[manifest.yaml]
  MANIFEST -->|routes_to| GC[general-change]
  MANIFEST -->|routes_to| TUI[timeline-ui-change]
  MANIFEST -->|routes_to| SRV[server-change]
  MANIFEST -->|routes_to| VOICE[voice-change]
  MANIFEST -->|routes_to| DOCS[submission-docs]
  MANIFEST -->|routes_to| MEM[project-memory-review]
  MANIFEST -->|routes_to| MAP[project-relationship-map]
  TUI -->|loads| ARCH[Learn/02-architecture.md]
  TUI -->|loads| DEC[DECISIONS.md]
  TUI -.->|informed_by| SKILL[(canonical Android skill)]
  DOCS -->|loads| WF[Learn/03-development-workflow.md]
  SRV -->|loads| HANDOFF
  VOICE -->|loads| VAPI[docs/voice-api.md]
  VOICE -->|loads| DEC
```

## Code Dependencies

```mermaid
flowchart LR
  UI[feature/timeline/presentation components] -->|depends_on| CORE[core/timeline]
  VM[feature/timeline/presentation] -->|depends_on| DOM[core/domain]
  TESTS[core/timeline JVM tests] -->|validates| CORE
  LOG[docs/AI_USAGE_LOG.md] -->|produces| WRITE[docs/AI_USAGE_WRITEUP.md]
  README[README.md] -->|documents| SERVER[server/]
  VAPI[docs/voice-api.md] -->|documents| VOICESRV[server/app/Voice]
```

Relations used: `starts_with`, `documents`, `routes_to`, `loads`, `informed_by`, `depends_on`, `validates`, `produces`, `reviews`, `guards`.
