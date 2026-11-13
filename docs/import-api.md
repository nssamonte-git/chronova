# Schedules from images

A user photographs or picks an image of a schedule or Gantt chart. The server reads it and answers with a schedule the app previews, and applies only when the user confirms:
- **Meta reads it:** Muse Spark reads the text and SAM 3.1 finds the bars.
- **OpenAI builds the schedule** from Meta's results. It never sees the image.

## Request

`POST /v1/import/image`: signed in (`auth:sanctum`, ability `sync`), `multipart/form-data`.

| Field | | |
|---|---|---|
| `image` | required | JPEG, PNG or WebP, ≤ 4 MB and ≤ 4096 px a side. The app sends a JPEG ≤ 2048 px with no EXIF. |
| `mode` | required | `new_project` or `add_to_project` |
| `timeZone` | required | A zone name or offset (`Asia/Manila`, `+08:00`) |
| `today` | required | `Y-m-d`, the user's today (it helps read "Mar 3" without a year) |
| `projectName`, `projectStart`, `projectEnd` | with `add_to_project` | The open project's name and dates (`Y-m-d`) |

## Answer

`200`, always this shape:

```json
{
  "result": "ok",
  "dates": "relative",
  "project": {"name": "Project Timeline", "startDate": null, "endDate": null, "days": 49},
  "tasks": [
    {"ref": "t1", "title": "Initial Client Consult", "startDate": null, "endDate": null, "startDay": 0, "endDay": 6,
     "milestone": false, "progress": 0, "predecessorRefs": []}
  ],
  "uncertain": ["No bar found for \"Install\"."]
}
```

- **`result`:** `ok`; `not_a_schedule` (no tasks); or `unreadable` (a schedule, but nothing usable).
- **Days are whole and inclusive:** a one-day task starts and ends on the same day.
  - `dates: absolute` means the chart showed calendar dates: `startDate` and `endDate` (`Y-m-d`) are set, and the project's dates hold every task.
  - `dates: relative` means only columns such as "Week 1": `startDay` and `endDay` count from 0, the chart's first day. `project.days` is the length, and the app asks for a start date.
  - The other pair is always `null`.
- **Milestones:** a milestone is one day, with progress 0 or 100.
- **`ref` and links:** `ref` names a task within this answer; `predecessorRefs` are finish-to-start links between the answer's tasks, never in a loop.
- **`uncertain`:** up to 20 short notes for the preview.
- **The app's choices** (none of them the server's):
  - placing a task from 00:00 of its first day to 24:00 of its last;
  - colours and order;
  - unticked rows;
  - tasks outside an open project's dates.

| Error | Status |
|---|---|
| Validation (`errors` per field) | 422 |
| `invalid_image` (not an image, or too many pixels) | 422 |
| Rate limit | 429 |
| `provider_unavailable` (Meta or OpenAI failed) | 502 |
| `invalid_import` (the built schedule failed validation) | 502 |
| `import_disabled` (kill switch) | 503 |

## How it is read

1. **Meta Muse Spark** (Chat Completions, strict schema `ChartReadingSchema`) reads the chart: its kind, title, time-axis labels with their x positions, and rows (label, vertical centre, text), links and notes.
2. **Meta SAM 3.1** (Responses API) finds the shapes, one concept per request: "gantt chart bar" and "diamond shape", as pixel boxes. `SamBoxes` reads the box tokens and skips the masks (`metadata.mask_encoding` `one_bit`, `store` `false`). All three Meta calls run at once (`Http::pool`), each with the image as a base64 data URL: the photo never gets a link.
3. **`BarMatcher`** puts each shape on the row whose band holds its centre. It drops a row's pale full-width track when a shorter bar shares the row, and measures bars in the axis's columns ("Week 1" = 0 to 1). Without axis labels, positions are shares of the image's width.
4. **OpenAI** (the voice model, `store: false`, strict schema `ImportSchema`) turns the matched rows into the schedule.
5. **`ImportValidator`** checks the answer:
   - exact keys and real dates;
   - at most 300 tasks and 366 days;
   - titles at most 200 characters;
   - unique refs, links only to known refs and with no loops;
   - milestones are one day.

   A new project's dates widen to hold every task. A failure answers 502.

## Privacy and limits

- **The image** goes to Meta only, is held in memory for the request, and is never stored or logged.
- **Meta mode:** Meta's Standard models are used, never the `-contributor` variants, so Meta doesn't train on the images.
- **What OpenAI gets:** text and numbers only: row labels, axis labels, bar positions, notes.
- **Metrics:** `voice_events` rows of kind `import` (status, outcome, duration, tokens), never content. The admin dashboard counts imports, and their tokens stay out of the voice cost estimate.
- **Limits per user:** 5 a minute and 50 a day (`IMPORT_REQUESTS_PER_MINUTE`, `IMPORT_REQUESTS_PER_DAY`).

## Configuration

| Variable | Default | |
|---|---|---|
| `IMPORT_ENABLED` | `true` | Kill switch |
| `IMPORT_ANALYZER` | `fake` | `meta`: Muse Spark + SAM 3.1. `fake` always "sees" the test chart (nine steps over seven weeks). |
| `IMPORT_BUILDER` | `fake` | `openai`. `fake` makes tasks from the matched bars by rule. |
| `META_API_KEY` | | Meta Model API key (server only) |
| `IMPORT_META_READER_MODEL` / `IMPORT_META_SEGMENTER_MODEL` | `muse-spark-1.3` / `sam-3.1` | Standard models only: a `-contributor` model is refused |
| `IMPORT_META_BAR_PHRASE` / `IMPORT_META_MILESTONE_PHRASE` | `gantt chart bar` / `diamond shape` | What SAM looks for |
| `IMPORT_OPENAI_REASONING_EFFORT` | `low` | |
| `IMPORT_META_TIMEOUT` / `IMPORT_OPENAI_TIMEOUT` | `30` / `30` | Seconds per call |

The OpenAI key and model are the voice assistant's (`OPENAI_API_KEY`, `VOICE_OPENAI_MODEL`). In production `upload_max_filesize` is 6 MB; a local PHP may still cap uploads at 2 MB.
