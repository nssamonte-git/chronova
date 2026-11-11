# Chronova — AI Usage Writeup

*Prepared for the shared Google Doc. Every item below is taken from the development log (`docs/AI_USAGE_LOG.md`) and the git history; nothing is reconstructed or estimated after the fact.*

Chronova started as the PicCollage Android take-home (batches B0–B9, preserved at git tag `submission-v1`) and grew, with the same tool and process, into an offline-first scheduler with a Laravel server, a staff admin panel, AWS infrastructure (not deployed), voice commands and its own branding. Sections 1–4 cover the take-home as submitted; section 5 covers the work after it.

**Repository layout today:** one git repository. The Android app is the Gradle project in `Chronova - Android App/`; `server/` (Laravel, admin panel, Docker, `server/infra/` AWS CDK), `docs/` and `tools/brand/` sit next to it at the root. Paths in the log written before the move (12 Nov, 07:47) are relative to the Android folder.

## 1. AI tools and how the work was divided

**Tool:** Claude Code (Claude Opus 5.5) in the Claude desktop app. It had access to the project folder, the Gradle build, `adb` to a physical Android phone, and git. No other AI tools were used.

**How the work was divided:**

1. **Specification → plan.**
   - I wrote a detailed implementation prompt covering:
     - requirements F01–F09
     - architecture and state-management rules
     - required unit-test scenarios
     - git and AI-logging rules
     - an explicit instruction not to start with a large unreviewed code generation pass
   - My first request was: *"Revalidate this implementation plan, then make the plan in small practical batches for best result."*
2. **Revalidation.**
   - Claude inspected the Android Studio project, the toolchain and the Gradle cache before writing code. It found that the prompt's assumptions didn't match the project:
     - the project was a Views template, not Compose, with no activity
     - the package name differed
     - AGP 9 has built-in Kotlin
     - `java.time` needs API 26, but `minSdk` was 24
   - It also found specification gaps: no timeline range, snapping rounding rule, minimum duration or drag-commit model was defined.
   - It asked me three questions:
     - the mockup was missing (answer: proceed without it)
     - where the git repo should live (answer: in the existing folder)
     - whether a drag on a bar should move it directly or need a long-press (answer: direct drag; empty space scrolls)
3. **Batches.**
   - The approved plan had ten batches (B0–B9). Each one ended with a real build/test run, an on-device check, an entry in the log, and a commit.
   - I started each batch explicitly ("Continue to Batch N") after reading the previous batch's summary.
   - I chose to skip the optional polish batch (B8).
4. **My direct actions during development:**
   - authorizing USB debugging on the phone
   - enabling auto-rotate and rotating the phone for the configuration-change check (Claude offered to force rotation over `adb` instead; I chose to do it on the phone)
   - approving the plan and each batch

**How AI suggestions were reviewed:**
- **Unit tests:** written alongside the code. All 39 run on every batch.
- **Predictions before device checks:** for each on-device check, Claude calculated the expected values before running it. Examples: "+150 px drag at 3.2 px/min should give 09:45–11:45", and "after zooming to 125 % the 09:00 line should be at x = 438 px". It then compared them with screenshots or pixel measurements.
- **Results reported as they happened:** a failing first test run was reported as 2 failures, not as a pass. Each batch summary listed what was not verified.

## 2. AI mistakes and corrections (take-home)

These were all found during the take-home. They're listed in the order they occurred.

| # | What AI generated or suggested | Why it was wrong | How it was discovered | Correction and decision |
|---|---|---|---|---|
| 1 | The plan said an emulator system image was installed and an AVD could be created | The `system-images/android-35/google_apis` folder existed but was empty | Listing it while trying to launch in B0 | Used the physical phone instead. Check contents before planning on tooling. |
| 2 | `pxToMillis` converted pixels to exact milliseconds | Float x positions at ~1300 px can't resolve milliseconds, so time → x → time came back 1 ms early | 2 of 24 tests failed on the first run (round-trip and zoom-invariance tests) | Pixel → time now rounds to whole minutes, the timeline's real resolution |
| 3 | The plan rendered bars in B3 with no scrolling until B4 | The first task is at 09:00, about 860 dp right of the origin, so nothing would have been on screen to verify | Noticed while implementing B3 | Moved the single shared `ScrollState` into B3. Each batch must be verifiable on its own. |
| 4 | The first gesture-detector draft measured drag distance from the touch-slop point | The bar would trail the finger by ~8 dp | Code review against the gesture design, before running | Distance is measured from the pointer-down position |
| 5 | The first `TaskRow` draft cleared the drag preview on release | The bar would flash back to its old position for one frame before the `StateFlow` update arrived | Code review, before running | The preview is kept until the committed task arrives |
| 6 | `Icons.Filled.Remove` was used after adding `material-icons-core` | That icon is only in the much larger extended icon set | Compile error | Defined the one glyph locally. Don't add a large library for one icon. |
| 7 | The header labelled only major ticks | In Days scale those are only midnights, so a typical Days view showed a header with no text | On-device screenshot | Minor ticks (6 h / 30 min) are labelled when at least 56 dp apart. Label density follows on-screen spacing. |
| 8 | The colour scheme omitted Material 3 container roles | Segmented buttons fell back to the default lavender selection colour | On-device screenshot | Defined brand-tinted `primaryContainer`, `secondaryContainer` and `tertiaryContainer` |

Smaller issues fixed along the way:
- A scripted edit left an extra closing brace, which caused a compile error.
- Lint flagged locale captured in static formatters and an obsolete `-v26` resource folder. Both were fixed.
- After the locale fix, the corner day label would have built a formatter on every scroll frame. It now derives a day index and formats once per day.

## 3. Technical assessment (at submission, tag `submission-v1`)

**Strengths**
- **Correctness lives in pure Kotlin.** All time math, snapping, edits and hit-testing are Android-free and unit-tested. The UI only converts time to pixels and back.
- **Header and body can't drift apart.** There is one scroll state, and the header reads it during drawing rather than running a second scroller. Scrolling also doesn't trigger recomposition.
- **Robust drag edits.** Edits are recomputed from gesture-start values plus the total finger distance, so there's no cumulative rounding.
- **Move and resize can't conflict.** A single gesture handler per bar picks the drag type once, at pointer-down.
- **Every required interaction was checked on a real device against predicted values,** not just eyeballed.

**Trade-offs**
- Fixed 3-day window, chosen for simplicity.
- Fixed 15-minute minimum duration.
- Resize handles only on the selected bar: fewer accidental edits, but one extra tap.
- In-memory state only.
- Dependency versions pinned to the cached, verified set.

**Unfinished or optional**
- Optional enhancements were not done: dark theme, current-time indicator, animated selection.
- No auto-scroll while dragging near the edge.
- One-frame jump on zoom changes.
- No instrumented UI tests.

**Possible improvements**
- Auto-scroll during edge drags.
- Apply the zoom anchor in the layout phase to remove the one-frame jump.
- Compose UI tests for the gestures.
- Persistence, and task creation and editing.
- An unbounded or virtualised timeline.
- Accessibility actions such as "move 15 minutes later" for screen-reader users.

Persistence, task creation and editing, and a virtualised timeline with user-defined project ranges were all built after the submission (section 5).

**Where AI was most useful**
- Revalidating the spec against the real project before writing code.
- Producing the pure timeline engine together with its tests.
- Working through Compose specifics: draw-phase state reads, pointer-input consumption order, `rememberUpdatedState`.
- Running the build, install, screenshot and pixel-measure loop on the device for every batch.

**Where independent engineering judgment was needed**
- Choosing the gesture model (direct drag vs long-press) and accepting the resize-only-on-selected trade-off.
- Deciding to skip optional polish in favour of documentation.
- Physical device actions (USB authorization, rotation).

Several AI-generated defects (items 2, 6, 7 and 8) were only caught because the process required real test runs and device screenshots rather than trusting the generated code.

## 4. Timeline (take-home)

Git commits run from 20:09 (template baseline) to the final documentation commit at 20:46 on 11 Nov. Feature implementation (B0–B7) was committed between 20:09 and 20:42. Planning and revalidation came before the first commit. The breakdown per batch is in `docs/AI_USAGE_LOG.md` and `git log`.

## 5. After the take-home

### 5.1 What was built and how the work was divided

The same tool and loop continued: I described a goal, Claude Code revalidated it against the code and proposed a plan in small batches, I answered its questions and approved the plan, and every batch ended with real test runs, a device or browser check against predicted values where relevant, a log entry and a commit. Nothing was pushed or deployed. Besides the build and `adb`, Claude used the desktop app's built-in browser for the admin panel, the local Laravel server for end-to-end checks, Pillow for image processing, and — only with my keys and go-ahead — the real OpenAI and ElevenLabs APIs.

| Phase | Batches | What it added | My decisions |
|---|---|---|---|
| Polish and multi-project | B10–B16 | Snap-grid fixes from a requirements review; Room persistence and a project list; a virtualized timeline for long projects; project and task create/edit/delete with undo; Hours/Days/Weeks/Months on the real calendar; finish-to-start dependencies | Asked for a check against the brief and approved the snap fixes; asked for the scheduler to work for any project, choosing Room and offline-first sync with a prompt on reconnect |
| Sync and server | B17–B34 | Offline-first sync with a reconnect prompt ("sync now?"); a first serverless backend (Cognito, Lambda, DynamoDB) replaced, before deploying, by Laravel 13 + Postgres; email-code sign-up; a Filament admin panel with required MFA, roles, audit log, health dashboard and time-limited audited support access; a production container; AWS CDK (not deployed) | First chose an AWS serverless backend, then switched to Laravel + Filament for an admin back-office and relational data; no deploy without approval |
| Voice commands | V0–V10 | One transactional, undoable apply path shared by drags and commands; a command planner and "push everything after it"; server endpoints for transcription (ElevenLabs Scribe), interpretation (OpenAI, strict JSON schema) and a spoken explanation; push-to-talk, preview, Apply, Undo; admin usage metrics; AWS secrets for the keys; tuning against the real providers | Providers as in the brief; always preview then confirm, with a spoken explanation; dependents move only via voice; on for every signed-in user; supplied the API keys in the git-ignored `server/.env`; agreed to send task names to the transcriber |
| Branding | brand B1–B4 | Icons, launch screen and admin logo generated from the supplied logo; a dark-only admin theme in the logo's colours; a layout fix on the admin user page | Use the image as is (not a vector redraw); dark-only admin; logo on the launcher and launch screen |
| Repository layout | — | One repository rooted at the project folder: the Android Gradle project in its own folder, backend and docs at the root; README and handoff files updated | Backend in `server/` at the root, one repository with history kept; delete the old bundle backup |

**My direct actions in this phase:** re-authorizing USB debugging when the phone revoked it, putting the provider keys in `server/.env`, approving each plan, and telling Claude to sign in to the local admin panel with the throwaway test credentials after a safety check had stopped it from reading them.

**How AI output was reviewed in this phase:**
- **Tests on every batch:** app JVM tests grew from 39 to 195, plus 3 instrumented Room tests run on the phone; 227 Pest tests for the server (the suite also ran inside the production image when it was built, at 99 tests); 16 infrastructure assertions and a clean `cdk synth`.
- **Predictions before every device and browser check,** for example "push build by 2 hours → Build: Thu 12 09:00 – 10:00 → Thu 12 11:00 – 12:00", then compared with the screen.
- **Scripted end-to-end checks:** sync between the phone and a simulated second device against the local server; a 15-command sample set for the real interpreter with the expected answers written first (15 of 15 after two of my expected answers were corrected: one test was contradictory, and "delete every task" is a legitimate request that still needs Apply); measured latency (~4 s from Done to preview) and cost (≈ $0.006 per spoken command).
- **Privacy checks:** logs and metrics searched for spoken or typed words and keys after each voice check; throwaway accounts and test data removed afterwards.

### 5.2 AI mistakes and corrections (after the take-home)

A selection; each is in the log with its batch.

| Batch | What AI generated or did | Why it was wrong | How it was discovered | Correction |
|---|---|---|---|---|
| B10 | Clamps (minimum duration, window end) ignored the snap grid | At 60-minute snap a dragged edge could land at :45 or :30 | A requirements review I asked for | Limits are rounded inward to the grid |
| B12 | Bars used `width` inside a narrower lane | Bars wider than the visible lane were clamped; a bar ended 16 px short of its grid line | Screenshot and pixel measurement on the phone after zooming | `wrapContentWidth(unbounded = true)` |
| B19a | Sync protocol: a stale push was simply rejected | The winning copy could sit behind the client's cursor, so the client would keep its losing edit marked as synced | Walking through clock-skew cases while designing the app's sync engine, before writing it | Stale pushes return the winning copy |
| B19 | A connectivity test re-enabled Wi-Fi and mobile data | Mobile data had been off; phone settings must be restored | The value read at the start of the same check | Turned it off straight away; later network toggles restore the value read first |
| B21 | Pest and Pint resolved to versions under two weeks old | Broke the version-age rule | Checked release dates before committing | Pinned exactly to older releases |
| B27 | `clearAllTables()` ran on the main thread; the session store used `apply()` | Sign-out and delete account crashed, then the app came back "signed in" to a deleted account | End-to-end check on the phone (predicted outcome didn't happen) | Moved to `Dispatchers.IO`; session writes with `commit` |
| B27 | Unauthenticated API calls without `Accept: application/json` got 500 | Tests always sent that header; the app didn't | The app showed "server problem" instead of "sign in again" | `/v1` always answers 401 JSON |
| B33 | Forwarded `Authorization` by name in a CloudFront origin request policy | CloudFront rejects it there | CDK synth error, then AWS docs | All-viewer-headers policy with caching disabled |
| V0 | The stale-edit check compared against the task as currently shown | It could never fire, so a drag could overwrite a synced change | Writing the ViewModel test | The task as it was at drag start is passed along |
| V3 | The voice client read the response on the main thread | The first Send crashed (`NetworkOnMainThreadException`); JVM tests can't catch it | Phone check | The whole call runs on `Dispatchers.IO` |
| V3 | A scripted tap after that crash hit "Sync now" | Pushed a test project to the local dev server | Seen in the result | Removed with the test account; later checks avoided that spot |
| V9 | ElevenLabs' `eng` language code was passed to text to speech | Text to speech only accepts two-letter codes, so spoken answers silently became text only | Live test with the real providers | Codes are converted; only two-letter codes are sent |
| Brand B1 | Icon art scaled to 68 % of the adaptive layer | The hourglass corners reached past Android's 66 dp safe circle, so round masks would clip them | Arithmetic before previewing, then mask previews | 62 %, checked under circle and squircle masks |
| Brand B4 | Container-width breakpoints without `gridContainer()` | At 1440 px both admin cards stayed in one column | Measuring the grid in the browser after the change | Each card is a grid container; checked at six widths with an overlap script |

### 5.3 Technical assessment now

**Strengths**
- **One way to change the schedule.** Drags, the task editor, undo and voice commands all go through one Room transaction that refuses changes computed from an outdated copy and returns the inverse for Undo.
- **Sync is predictable.** Last write wins per record; sync runs only when the user agrees; the server passes the protocol's conformance scenarios, and the phone was checked against a simulated second device.
- **Voice proposes; the user decides.** The model can only return allowed operations on listed tasks (strict schema, re-validated by the server and planned again on the phone), every change is previewed and needs Apply, and Undo follows. Provider keys stay on the server, OpenAI is called with `store: false`, and no audio or words are stored or logged.
- **Admin panel built for least access:** separate staff accounts, required MFA, an append-only audit log, and support access to a user's projects only through a reason, a 30-minute grant and an audit entry.

**Trade-offs**
- Last write wins per record, not field-level merging.
- An always-on database and containers (≈ $60–70/month for a dev stack on AWS, per the deploy guide) instead of serverless.
- Voice is on for every signed-in user; cost is bounded by rate limits, a kill switch and the dashboard rather than an opt-in.
- The icons are cut from the supplied raster logo, not traced as vectors.

**Unfinished**
- Nothing is deployed; the AWS stack is synthesized and tested only.
- Before voice reaches real users: the privacy policy, the Play Data safety form, provider spending limits and the choice of ElevenLabs voice (`docs/voice-api.md`).
- The README's known limitations (for example no auto-scroll at the viewport edge, toolbar overflow on a 360 dp phone, no UI test suite).

**Where independent engineering judgment was needed**
- Product choices: asking before syncing, Laravel over the serverless prototype, how voice should confirm and speak, voice for everyone, sending task names to ElevenLabs, using the logo as is.
- Approving anything that leaves the machine: real provider calls, and (still pending) any deploy or push.

### 5.4 Timeline (whole project)

56 commits, from 20:09 on 11 Nov to 07:54 on 12 Nov: the take-home (11 commits, to 20:46), polish and multi-project (7, to 22:46), sync and server (20, to 00:31), voice (11, 00:58–06:54), branding (4, 07:04–07:35) and the repository move and README (3, 07:47–07:54). The per-batch detail is in `docs/AI_USAGE_LOG.md` and `git log`.
