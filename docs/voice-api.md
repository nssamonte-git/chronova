# Voice API

Voice and typed commands for the timeline. The server only **proposes** changes; the app checks every proposal against its own copy of the schedule, shows a preview, and applies it only when the user taps Apply (with Undo afterwards).

All endpoints need a signed-in, active user (`Authorization: Bearer <token>` with the `sync` ability) and are limited to `VOICE_REQUESTS_PER_MINUTE` (default 10) per user and endpoint, so a spoken command (transcribe, then interpret) counts once against each. Provider keys live only on the server.

| Status | Body | Meaning |
|---|---|---|
| 503 | `{"error":"voice_disabled"}` | Switched off on the server (`VOICE_ENABLED=false`) |
| 502 | `{"error":"provider_unavailable"}` | The provider couldn't be reached or failed |
| 502 | `{"error":"invalid_proposal"}` | The provider's answer broke the contract below; nothing to apply |
| 422 | Laravel validation errors | The request is malformed |
| 422 | `{"error":"transcript_too_long"}` | Transcribe only: more than 1,000 characters were heard |
| 429 | | Rate limited |

## `POST /v1/voice/transcribe`

`multipart/form-data`:

| Field | |
|---|---|
| `audio` | The recording: AAC in MPEG-4 (`.m4a`, what Android's `MediaRecorder` writes) or another common audio format; ≤ 1 MB |
| `durationMs` | Its length, 100–60,000 ms |
| `language` | Optional ISO 639-1/639-3 hint (`en`); omitted = detected |

Response (200): `{"text": "push design by two hours", "language": "en"}`. `text` is `""` when nothing was said. The app shows the text, then sends it to `/interpret` as the transcript.

## `POST /v1/voice/interpret`

```json
{
  "transcript": "push design by two days and everything after it",
  "context": {
    "projectId": "p1",
    "projectName": "Launch",
    "startDate": "2026-11-12",
    "endDate": "2026-11-18",
    "timeZone": "America/Los_Angeles",
    "today": "2026-11-12",
    "now": "2026-11-12T10:15",
    "revision": "r17",
    "tasks": [
      { "id": "t1", "title": "Design", "start": "2026-11-12T09:00", "end": "2026-11-12T11:00", "predecessorIds": [] }
    ]
  }
}
```

Dates (`YYYY-MM-DD`) and date-times (`YYYY-MM-DDTHH:MM`, no offset) are local to `timeZone`. Limits: transcript ≤ 1,000 characters, ≤ 300 tasks.

Response (200):

```json
{
  "revision": "r17",
  "result": "operations",
  "operations": [
    { "type": "shift_task", "taskId": "t1", "amount": 2, "unit": "days" },
    { "type": "reschedule_dependents", "taskId": "t1" }
  ],
  "question": null,
  "reason": null
}
```

`result` is one of:
- `operations` — one to 20 operations, in order;
- `clarification` — no operations; `question` asks which task or what was meant;
- `unsupported` — no operations; `reason` says why (not about this schedule, refused).

Operations (every field is always present):

| `type` | Fields |
|---|---|
| `move_task` | `taskId`, `date`, `time` (`HH:MM` or null = keep the time of day) |
| `shift_task` | `taskId`, `amount` (signed, ≠ 0), `unit` (`minutes`/`hours`/`days`/`weeks`) |
| `set_times` | `taskId`, `start`, `end` (date-times; at least one non-null) |
| `change_duration` | `taskId`, `amount` (signed), `unit` |
| `set_duration` | `taskId`, `amount`, `unit` |
| `rename_task` | `taskId`, `title` (1–200 characters) |
| `create_task` | `title`, `start`, `end`, `predecessorIds` |
| `delete_task` | `taskId` |
| `add_dependency` / `remove_dependency` | `predecessorId`, `successorId` (different) |
| `reschedule_dependents` | `taskId` — the app pushes everything downstream just enough |

The server checks that every id is in the request's `tasks`, dates are real, amounts are sane and the shape is exact; otherwise the whole proposal is rejected. The app then plans the operations against its current data (`CommandPlanner`) and rejects anything stale or invalid before showing the preview.

## Privacy

- The recording goes to the transcriber (ElevenLabs Scribe) and is not stored by the server. ElevenLabs keeps request logs unless `VOICE_ELEVENLABS_ZERO_RETENTION=true` (`enable_logging=false`), which needs an enterprise plan.
- The words and the schedule context are sent to the interpreter (OpenAI, `store: false`) and are never stored or logged by the server.
- `voice_events` keeps only kind, status, outcome, duration, audio length, transcript length and token counts, for the admin dashboard.

## Configuration

| Variable | Default | |
|---|---|---|
| `VOICE_ENABLED` | `true` | Kill switch |
| `VOICE_INTERPRETER` | `fake` | `openai`, or `fake` (rule-based, no network) for development and tests |
| `VOICE_TRANSCRIBER` | `fake` | `elevenlabs`, or `fake` (always hears `VOICE_FAKE_TRANSCRIPT`) |
| `VOICE_FAKE_TRANSCRIPT` | `push design by 2 hours` | Development only |
| `OPENAI_API_KEY` | — | Secret |
| `ELEVENLABS_API_KEY` | — | Secret |
| `VOICE_ELEVENLABS_STT_MODEL` | `scribe_v2` | |
| `VOICE_ELEVENLABS_ZERO_RETENTION` | `false` | Enterprise plans only |
| `VOICE_OPENAI_MODEL` | `gpt-6.1-terra` | |
| `VOICE_OPENAI_REASONING_EFFORT` | `low` | |
| `VOICE_REQUESTS_PER_MINUTE` | `10` | Per user |
