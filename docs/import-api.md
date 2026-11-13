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

1. **Meta Muse Spark** (Chat Completions, strict schema `ChartReadingSchema`, reasoning effort `minimal`) reads the chart:
   - its kind and title;
   - the time-axis labels with their x positions;
   - its rows: label, vertical centre, text (values after their column headings, e.g. "After: Demolition"), and the axis labels under the ends of the row's bar (`barStart`, `barEnd`);
   - links and notes.
2. **Meta SAM 3.1** (Responses API) finds the shapes, one concept per request: "bar" and "diamond shape", as pixel boxes.
   - `SamBoxes` reads the box tokens and skips the masks (`metadata.mask_encoding` `one_bit`, `store` `false`).
   - All three Meta calls run at once (`Http::pool`), each with the image as a base64 data URL: the photo never gets a link.
   - **`BoxColours`** measures each box's colour saturation from the image (PHP GD).
3. **`BarMatcher`** puts the shapes on rows and measures them in the axis's columns ("Week 1" = 0 to 1):
   - **Geometry:**
     - Muse Spark's pixel positions drift by tens of pixels between runs, so the geometry comes from SAM's boxes when it can.
     - Boxes grouped into horizontal rows give the rows' centres when the groups match the rows read (in order).
     - Full-width background tracks give the axis's ends, split into as many columns as it has labels.
     - Otherwise (a photo at an angle, a chart without tracks) Muse Spark's positions are used.
   - **Placing a shape:** it belongs to the row whose band holds its centre.
   - **Which shapes are bars:**
     - a row keeps the boxes coloured at least half as strongly as its strongest, since SAM also finds the pale track, often in pieces either side of the bar;
     - without colours, a full-width track is dropped when a shorter bar shares the row;
     - duplicate boxes count once.
   - **Without axis labels,** positions are shares of the image's width.
   - Each row also carries what Muse Spark **"seen"** under its bar's ends.
4. **OpenAI** (the voice model, `store: false`, strict schema `ImportSchema`) turns the matched rows into the schedule:
   - measured bars where they agree with what was seen;
   - the seen span where a row has no measured bar or its bar plainly belongs to another row (noted in `uncertain`);
   - links from arrows, from wording, or from an "After" / "Depends on" column.
5. **`ImportValidator`** checks the answer:
   - exact keys and real dates;
   - at most 300 tasks and 366 days;
   - titles at most 200 characters;
   - unique refs, links only to known refs and with no loops;
   - milestones are one day.

   A new project's dates widen to hold every task. A failure answers 502.

## Measured (13 Nov 2026, real providers)

Your 7-week "Project Timeline" template and three generated images, through the local server:

| Image | Time | Result |
|---|---|---|
| The chart (1632×1056) | 20 s | 9 of 9 tasks on the right days (relative, 49 days) |
| The chart tilted 5°, in perspective, blurred | 21 s | 9 of 9 (seen spans used where tilt moved bars off their rows, noted under "Check these") |
| A table with dates, % done and an "After" column | 13 s | 6 of 6 tasks, dates and progress, and all 6 links |
| A landscape | 5 s | "Not a schedule" |

- **On the phone:** the same chart was read in 19 s and the table in 12 s.
- **Over 12 real imports:** a successful one averaged 16.5 s and ~4,350 input / ~1,380 output tokens (Meta and OpenAI together).
- **Meta's part costs about 1.4¢ an import:** Muse Spark ~3,000 tokens in / ~1,300 out, plus two SAM requests. OpenAI's part (~1,400 tokens in) is priced by the voice configuration.
- **What tuning changed:**
  - "gantt chart bar" found nothing (SAM wants a plain noun);
  - Muse Spark's positions alone put bars on the wrong rows;
  - reasoning effort `minimal` read as well as the default and halved the time (~14 s against ~30 s).
- **Not measured yet:** real camera photos (glare, curved paper), handwriting, charts with dozens of rows or months of columns, and dates written inside the bars.

## Before release

- [ ] Privacy policy: images the user picks or photographs go to Meta (Muse Spark, SAM 3.1; Standard mode, no training) to be read; what is read (text and positions, not the image) goes to OpenAI; neither keeps them for Chronova (`store: false` at both).
- [ ] Play Console Data safety: photos (user-chosen images) shared with service providers, for app functionality, not stored; app content (task titles read from them) shared with OpenAI.
- [ ] Meta Model API: a key with a spending limit; consider zero data retention when Meta offers it for the account.
- [ ] Deploy with `-c import=on` once the Meta key is in the `VoiceKeys` secret (`server/infra/DEPLOY.md`).

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
| `IMPORT_ANALYZER` | `fake` | `meta`: Muse Spark + SAM 3.1. `fake` always "sees" the test chart (nine steps over seven weeks; the template itself is third-party and not in the repository). |
| `IMPORT_BUILDER` | `fake` | `openai`. `fake` makes tasks from the matched bars by rule. |
| `META_API_KEY` | | Meta Model API key (server only) |
| `IMPORT_META_READER_MODEL` / `IMPORT_META_SEGMENTER_MODEL` | `muse-spark-1.3` / `sam-3.1` | Standard models only: a `-contributor` model is refused |
| `IMPORT_META_BAR_PHRASE` / `IMPORT_META_MILESTONE_PHRASE` | `bar` / `diamond shape` | What SAM looks for (a plain noun works; "gantt chart bar" found nothing) |
| `IMPORT_META_REASONING_EFFORT` | `minimal` | Muse Spark's thinking before it answers |
| `IMPORT_OPENAI_REASONING_EFFORT` | `low` | |
| `IMPORT_META_TIMEOUT` / `IMPORT_OPENAI_TIMEOUT` | `40` / `30` | Seconds per call |

The OpenAI key and model are the voice assistant's (`OPENAI_API_KEY`, `VOICE_OPENAI_MODEL`). In production `upload_max_filesize` is 6 MB; a local PHP may still cap uploads at 2 MB.
