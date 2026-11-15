# Current Task

## Current Status

Chronova is live at `https://chronova.meenux.tech` on Sydney EC2 (`ap-southeast-2`). Cloudflare DNS points to the instance and Caddy serves HTTPS. The Android API uses `/v1`. The homepage redirects to `/admin`, and guests reach `/admin/login`. See [the EC2 runbook](server/infra/EC2.md) for the current deployment workflow. The ECS Fargate/RDS/SES CDK stack is a separate deployment option.

- **Verified on EC2:** `/up` returned 200, and signed-out `/v1/me` returned 401. The staff 2FA migration completed. EC2 retrieves `chronova/prod/runtime` through its IAM role. `APP_KEY` and `DB_PASSWORD` matched the running values before the cutover and were removed from `app.env`.
- **Voice:** Enabled in the web container with OpenAI and ElevenLabs: Eleven v4 Turbo with `[excited, cheerful]` audio tags, voice `tnSpp4vdxKPjI9w0GnoV`, noise removal on (all set in `app.env` or by default; 15 Nov, confirmed by ear on the phone). The web container passed its health check. A live Android voice command and preview still need confirmation.
- **Push:** `web`, `worker`, and `scheduler` reported healthy and each reported `sender=fcm, credential=SET`. The Compose override takes precedence over `PUSH_SENDER=fake` in `app.env`. Delivery to a second Android device still needs confirmation.
- **Image import and email:** Image import is off. `MAIL_MAILER=array` keeps mail in the application process, so public sign-up and password reset email delivery remain open.

Latest application work:

- **Try demo** (15 Nov): a button above the sign-in form signs into the shared demo account `test@chronova.com` and syncs once; the demo can't be deleted from the app. The demo login is committed by the user's choice (DECISIONS, 2026-11-15). Checked on the phone against the live server.
- **Walkthrough video bugs, all fixed** (`9ee75cb` … `c22281f`). Found while recording the product video (`video/walkthrough/`, local only):
  - voice: an empty project can get its first tasks; noise removal (`VOICE_ISOLATE_NOISE`, off by default); the orb waits 20 s for an answer;
  - import sheet: the Add button stays on screen; each opening starts afresh;
  - image import: a one-day item keeps the hours written on it ("10–1" → 10:00–13:00, `startTime`/`endTime` in the answer); without them it stays a whole day (user choice). The assistant's preview shows whole days as days, not "00:00 – 00:00";
  - critical path: slack in working time, a fixed 09:00–17:00, Monday to Friday (`WorkingTime`; user choice);
  - clash-free alternatives: `SensibleAlternative` refuses a model's offer that moves tasks nobody asked about or leaves working hours; the interpreter's rules say the same;
  - server: image import, transcription and spoken explanation may run 55 s instead of PHP's 30 (each route's own limit).
  - None of today's import, voice-alternative or time-limit changes has been tried with the real providers or on the phone.
- **Handoff polish** (`7280901` … `caa3736`):
  - **Copy:** one wording per idea; "this device" throughout. The app's text names no provider and mentions no API or server (DECISIONS, 2026-11-14).
  - **Screens:**
    - empty states are a title plus a line;
    - image-import results get a title and a banner;
    - the timeline header gives way to dates; narrow bars draw no label;
    - with a task selected, the top bar is Edit plus a More menu;
    - the name column grows with large text.
  - **Account:** the password pages have "Send code" / "Save password" buttons; the time picker has a heading.
  - Every screen and dialog now has light, dark and 1.5× screenshots.
- **Aurora design pass** (`359ac4e` … `2d4f654`):
  - the logo's palette in both themes, Inter, and shape and spacing tokens;
  - shared components in `:core:ui`;
  - project cards with a small chart of their tasks (packed into lanes beyond five);
  - the account page as cards, scale chips, light motion.
- **`tools/check.sh`** (`658a57e`): device tests run one module at a time, and a crashed test app now fails the check.
- Earlier (13 Nov): timeline rows at every zoom, schedules from images, Firebase push.

Baselines: `tools/check.sh` PASS (523 unit tests, 183 screenshots, lint 23 version warnings, 15 Nov); 112 device tests (110 last passed on the managed emulator on 14 Nov, and the 2 Try demo tests in the account module's own run on 15 Nov; on GitHub Actions the device-test job failed on every run until 15 Nov: the 3D orb's native code killed `:app`'s test process on the runner's software renderer; the orb now uses its plain icon on software renderers, waiting for a CI run to confirm; `ProjectTimelineScreenTest` now expects the "of working time" wording and has not been run since); server 514 tests and lint passed for the admin homepage release; infra baseline 23 tests.

## Exact Next Steps

1. **Client handoff, the client's decisions:**
   - the application ID: `noel.chronova.app` is permanent once published, and Firebase is bound to it, so a new ID needs a new Firebase app and `google-services.json`;
   - a support contact (the disabled-account message says "Contact support.");
   - privacy policy, terms and open-source licences (the Inter OFL), linked from an About item on the account page;
   - the Play Data safety form. It must name OpenAI, ElevenLabs and Meta and the 90-day voice history, because the app no longer says so;
   - British or US spelling (now British: "Colour").
2. **Check the hosted app from Android:** sign-in and sync work on the phone (15 Nov): the seeded test account `test@chronova.com` (confirmed, no code; "Sample Project" with 1,000 tasks, 16 Nov 2026 – 5 May 2027; password not recorded here) signed in and synced all 1,000 tasks. Remove it with a user delete in the `web` container (cascades). Still open: on a test project, confirm a live voice command transcribes and shows the expected preview before approval. Sign into the same test account on two Android devices, sync a change from one, and confirm the other receives “Changes on another device.” Run `tools/check.sh --devices-only` for the remaining local device coverage. Provider tests need approval.
3. **Small, open:**
   - the assistant's spoken answers say "3 p.m." while its preview uses 24-hour times. That wording is written on the server (`OpenAiExplainer`); it also still words a whole-day task by the clock ("midnight on November 18");
   - "Wednesday" means the next Wednesday after today; in the walkthrough the user meant the task's own week. Ask the user before changing the rule;
   - working hours are fixed for every project; a per-project setting would replace the one constant in `WorkingTime`.
4. **Handoff readiness:** configure transactional email if the client needs registration or self-service password resets, then verify both flows. Verify the backup and restore procedure before relying on EC2 for client data. The GitHub history and AI kit have been pushed. Review the Android workflow in GitHub Actions.
5. **Import follow-ups:**
   - untested: real camera photos (glare, curved paper), handwriting, and charts with dozens of rows or months of columns;
   - before release, the items in `docs/import-api.md` ("Before release");
   - EC2 already passes the Meta and OpenAI keys to `web`. If image import is in scope, enable it with approval and use a sample image to verify the preview, including “10–1” as hours.
6. **Push follow-ups:**
   - before release, the Firebase items in `docs/push.md`;
   - use two devices to verify sync alerts and, as needed, sign-in alerts and reminders. A local reminder alarm alone cannot verify Firebase delivery.
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

- **Phone:** The last install (15 Nov) is a debug build of `f0cc62e` against the live server, signed in as the test account. Later fixes from 14 Nov still need a phone check of their own.
  - Install over the app (`adb install -r`), never uninstall. Build with `chronova.apiUrl=http://localhost:8000` appended to `local.properties`, then restore the file: an empty URL would lock the app out of signing in.
  - The USB link drops now and then, or shows "unauthorized" for a minute or two; `adb kill-server; adb start-server` and waiting brings it back.
  - `adb reverse tcp:8000 tcp:8000` must be re-added after any USB reconnect.
  - The photo picker asks which app each time ("Don't ask again" was left off).
- **Local server:**
  - Start it: `php artisan serve --port=8000` plus `queue:work`, and `schedule:work` for pushed reminders.
  - `server/.env` (mode 600, git-ignored) holds the OpenAI, ElevenLabs and Meta keys, `PUSH_SENDER=fcm` and `FCM_CREDENTIALS`. Voice and imports default to the fakes; for real imports start the server with `IMPORT_ANALYZER=meta IMPORT_BUILDER=openai` (costs about 1.4¢ of Meta per image plus OpenAI).
  - Local PHP caps uploads at 2 MB; the app's images fit.
  - Docker Postgres and Mailpit (SMTP 1025, API 8025).
- **Mac:** `local.properties` holds only `sdk.dir`; the build defaults to the live server (DECISIONS, 2026-11-15). For a local-server check, add `chronova.apiUrl=http://localhost:8000` and remove it afterwards. The Mac has the API 35 Google APIs arm64 emulator image (3.8 GB) and one managed AVD under `~/.android/avd/gradle-managed`.
