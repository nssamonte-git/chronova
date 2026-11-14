# Current Task

## Current Status

The app is polished for a pre-production handoff. The Sydney EC2 instance has Docker and Docker Compose installed. The backend deployment and Cloudflare DNS are pending. The existing CDK stack provisions ECS Fargate, RDS, and SES separately from the EC2 instance.

Latest application work:

- **Walkthrough video bugs, all fixed** (`65a21ee` … `b7ced22`). Found while recording the product video (`video/walkthrough/`, local only):
  - voice: an empty project can get its first tasks; noise removal (`VOICE_ISOLATE_NOISE`, off by default); the orb waits 20 s for an answer;
  - import sheet: the Add button stays on screen; each opening starts afresh;
  - image import: a one-day item keeps the hours written on it ("10–1" → 10:00–13:00, `startTime`/`endTime` in the answer); without them it stays a whole day (user choice). The assistant's preview shows whole days as days, not "00:00 – 00:00";
  - critical path: slack in working time, a fixed 09:00–17:00, Monday to Friday (`WorkingTime`; user choice);
  - clash-free alternatives: `SensibleAlternative` refuses a model's offer that moves tasks nobody asked about or leaves working hours; the interpreter's rules say the same;
  - server: image import, transcription and spoken explanation may run 55 s instead of PHP's 30 (each route's own limit).
  - None of today's import, voice-alternative or time-limit changes has been tried with the real providers or on the phone.
- **Handoff polish** (`aee85f8` … `a81052a`):
  - **Copy:** one wording per idea; "this device" throughout. The app's text names no provider and mentions no API or server (DECISIONS, 2026-11-14).
  - **Screens:**
    - empty states are a title plus a line;
    - image-import results get a title and a banner;
    - the timeline header gives way to dates; narrow bars draw no label;
    - with a task selected, the top bar is Edit plus a More menu;
    - the name column grows with large text.
  - **Account:** the password pages have "Send code" / "Save password" buttons; the time picker has a heading.
  - Every screen and dialog now has light, dark and 1.5× screenshots.
- **Aurora design pass** (`93074ec` … `cb3766f`):
  - the logo's palette in both themes, Inter, and shape and spacing tokens;
  - shared components in `:core:ui`;
  - project cards with a small chart of their tasks (packed into lanes beyond five);
  - the account page as cards, scale chips, light motion.
- **`tools/check.sh`** (`559c55f`): device tests run one module at a time, and a crashed test app now fails the check.
- Earlier (13 Nov): timeline rows at every zoom, schedules from images, Firebase push.

Baselines: `tools/check.sh` PASS (515 unit tests, 180 screenshots, lint 23 version warnings); 110 device tests (last passed on the managed emulator on 14 Nov, before today's fixes; `ProjectTimelineScreenTest` now expects the "of working time" wording and has not been run since); server 507; infra 22.

## Exact Next Steps

1. **Client handoff, the client's decisions:**
   - the application ID: `noel.chronova.app` is permanent once published, and Firebase is bound to it, so a new ID needs a new Firebase app and `google-services.json`;
   - a support contact (the disabled-account message says "Contact support.");
   - privacy policy, terms and open-source licences (the Inter OFL), linked from an About item on the account page;
   - the Play Data safety form. It must name OpenAI, ElevenLabs and Meta and the 90-day voice history, because the app no longer says so;
   - British or US spelling (now British: "Colour").
2. **Check today's fixes for real (needs approval for the providers):** read the whiteboard photo with the real reader and builder (does "10–1" come back as hours?); ask for a clashing move and see which alternative the real model offers; run `tools/check.sh --devices-only`; install on the phone.
3. **Small, open:**
   - the assistant's spoken answers say "3 p.m." while its preview uses 24-hour times. That wording is written on the server (`OpenAiExplainer`); it also still words a whole-day task by the clock ("midnight on November 18");
   - "Wednesday" means the next Wednesday after today; in the walkthrough the user meant the task's own week. Ask the user before changing the rule;
   - working hours are fixed for every project; a per-project setting would replace the one constant in `WorkingTime`.
4. **GitHub:** push the existing history and project AI kit. Review the first run of `.github/workflows/android.yml`.
5. **Import follow-ups:**
   - untested: real camera photos (glare, curved paper), handwriting, and charts with dozens of rows or months of columns;
   - before release, the items in `docs/import-api.md` ("Before release");
   - going live on AWS needs the Meta key in the `VoiceKeys` secret and `-c import=on`, only with approval.
6. **Push follow-ups:**
   - before release, the Firebase items in `docs/push.md`;
   - a second real phone (or the emulator) signed in would exercise sign-in alerts from a real login;
   - going live on AWS needs the `PushKey` secret and `-c push=on` (DEPLOY.md), only with approval.
7. **Offered, not started:**
   - Kover coverage reports;
   - contract tests for sign-in, sync and voice against the Laravel server;
   - a small Macrobenchmark suite (start-up, a 500-task timeline, a large sync);
   - splitting `AccountScreen` and `SchedulerTimeline`;
   - shared test builders in `:core:testing`.
8. **Still open from earlier QA:**
   - the "intro" rotation scenario tests nothing with the 1.5 s fake clip;
   - the sideways-assistant scenario draft (`.tmp-ai/rotation/sc_assistant_landscape.sh`);
   - unexplained short-run memory growth in the dialog, notice and burst scenarios;
   - the voice release checklist;
   - AWS deploy only with approval.

## Validation Notes

- **Phone:** The last recorded install was a debug build of `d3ae557` against the local server. Later fixes still need a phone check.
  - Install over the app (`adb install -r`), never uninstall. Build with `chronova.apiUrl=http://localhost:8000` appended to `local.properties`, then restore the file: an empty URL would lock the app out of signing in.
  - The USB link drops now and then, or shows "unauthorized" for a minute or two; `adb kill-server; adb start-server` and waiting brings it back.
  - `adb reverse tcp:8000 tcp:8000` must be re-added after any USB reconnect.
  - The photo picker asks which app each time ("Don't ask again" was left off).
- **Local server:**
  - Start it: `php artisan serve --port=8000` plus `queue:work`, and `schedule:work` for pushed reminders.
  - `server/.env` (mode 600, git-ignored) holds the OpenAI, ElevenLabs and Meta keys, `PUSH_SENDER=fcm` and `FCM_CREDENTIALS`. Voice and imports default to the fakes; for real imports start the server with `IMPORT_ANALYZER=meta IMPORT_BUILDER=openai` (costs about 1.4¢ of Meta per image plus OpenAI).
  - Local PHP caps uploads at 2 MB; the app's images fit.
  - Docker Postgres and Mailpit (SMTP 1025, API 8025).
- **Mac:** `local.properties` is restored (no `apiUrl`); its last line has no newline, so append with `printf '\n…'`. The Mac has the API 35 Google APIs arm64 emulator image (3.8 GB) and one managed AVD under `~/.android/avd/gradle-managed`.
