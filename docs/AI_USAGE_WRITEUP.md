# Chronova — AI Usage Writeup

*Prepared for the shared Google Doc. Every item below is taken from the development log (`docs/AI_USAGE_LOG.md`) and the git history; nothing is reconstructed or estimated after the fact.*

Chronova started as the PicCollage Android take-home (batches B0–B9, preserved at git tag `submission-v1`) and grew, with the same tool and process, into an offline-first scheduler with a Laravel server (live on one AWS EC2 instance), a staff admin panel, a voice assistant and its own branding. Sections 1–4 cover the take-home as submitted; section 5 covers the work after it.

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

The way of working stayed the same. I said what I wanted, Claude Code checked it against the code and proposed a plan in small steps, I answered its questions and approved the plan, and each step ended with real tests, a check on the phone or in the browser against numbers written down first, a log entry and a commit. Claude never pushed or deployed anything: I pushed to GitHub myself and ran every command on the live server myself.

### 5.1 What was built, and what I decided

| When | What was added | What I decided |
|---|---|---|
| 11–12 Nov night (B10–B16) | Snap fixes, saving projects on the phone (Room), a project list, long projects that scroll smoothly, Hours/Days/Weeks/Months, task dependencies | Asked for a review against the brief; asked for any project to work, offline first |
| 12 Nov night (B17–B34) | Sync with a "sync now?" prompt, a Laravel + Postgres server, email-code sign-up, the admin panel, a production container, AWS CDK code | Tried a serverless backend first, then switched to Laravel for a proper admin panel |
| 12 Nov early morning (V0–V10) | Voice commands: speak, see a preview, Apply, Undo, hear a short explanation | Always preview and confirm; the API keys stay on the server |
| 12 Nov morning (O1–O5) | The 3D assistant orb and a voice-only loop: it proposes, asks for a spoken yes, applies and explains; clash warnings and safer alternatives; a notifications page | Supplied the orb model and the voice; "the model suggests, the app checks"; keep past decisions for 90 days; sign-in required |
| 12 Nov evening | Rotation fixes and about 23 small modules, with a build check that keeps the layers apart | Approved the module plan |
| 13 Nov | Pinch zoom, the build security gate, full code shrinking for releases, screenshot tests, one check script and CI; then progress, notes, milestones, search, dark theme, exports, reminders, the critical path and many more voice commands; then push reminders through Firebase and turning a photo of a schedule into tasks | Every gap closed; notes never go to voice; reading commands run at once, changes need a yes; OpenAI never gets the photo |
| 13–14 Nov | A new look built from the logo ("Aurora"), a copy review, previews for every screen; fixes found while recording the walkthrough video | The look follows the logo; app text describes features, not providers; a 09:00–17:00 Mon–Fri working day |
| 14–15 Nov | The take-home PDF; email domain setup; the server live on EC2 at `chronova.meenux.tech`; the app pointed at it; a 1,000-task demo account and a "Try demo" button; setup, troubleshooting and admin-panel pages | Hard-code the demo login and publish the Support test login (both on purpose, both recorded); the CDK stack waits |
| 15 Nov | A build that works from a fresh clone on any computer, the voice and noise removal as server settings, a more upbeat voice (Eleven v4 Turbo), the orb kept off emulators without a GPU, a smoother resting orb, Claude Code removed as co-author of the commits | Asked for the upbeat voice, a performance rating and the orb fix |

**What I did myself:** gave the provider keys, the Firebase files, the orb model and the voice IDs; approved every call to a real AI provider; reconnected the phone's USB debugging many times; on EC2, set up the secrets, ran the seed script, changed settings and redeployed; typed every production password myself (Claude never did); pushed to GitHub; and checked by ear and eye that the new voice and the smoother orb were right.

**How Claude's work was checked:**
- **Tests on every step.** App unit tests went from 39 at the take-home to 523; device tests from 0 to 112; screenshot tests from 0 to 183; server tests to 527; infrastructure tests to 23.
- **Numbers written before looking.** For example "drag 192 px → Prepare dinner 17:00–18:00", then compared with the phone.
- **Planting a fault to prove a check works.** A planted unsafe web setting failed the security gate; a 4 dp padding change failed the screenshot tests.
- **Real runs with the AI providers**, only with my go-ahead: 21 spoken requests all handled; 9 of 9 rows right in a photo import after tuning.
- **Checks on the live server:** health page, a signed-out request refused, the voice settings read back.
- **A fresh GitHub clone** built with nothing but the Android SDK path set.
- **Frame counts on a budget phone** before and after the orb fix (276 → 101 frames in 10 s on a still screen).

### 5.2 What the AI got wrong after the take-home

A selection; each is in the log with its batch.

| What the AI did | Why it was wrong | How it was found | Fix |
|---|---|---|---|
| Ignored the snap grid in the drag limits | At 60-minute snap an edge could land on :45 | A review against the brief I asked for | Limits round to the grid |
| Ran database and network calls on the main thread | The app crashed on the phone; unit tests can't see this | Checking on the phone | Moved off the main thread |
| Let a pinch end as a drag | The last finger lifted moved two bars | I pinched on the phone | Ignore touches until every finger lifts |
| Wrote in the log that test suites had passed | They hadn't been run yet | Its own re-check | Ran them and fixed the entry |
| Made the check script count tests too early | It said PASS when the test app crashed, and counted 82 of 102 tests | Running it on the emulator | Reads the real totals and fails on any failure |
| Let the server's AI calls take up to 70 s | PHP stops a request at 30 s, so slow calls ended in an error | Recording the walkthrough video | A 55 s limit on those routes and shorter timeouts |
| Wrote that the demo's first sync takes two requests | It takes three | The admin panel's sync history | Report corrected |
| Left `MAIL_PASSWORD` out of the redeploy commands, and wrote a check it hadn't run | The redeploy stopped on EC2; the check printed only an error | My EC2 output | Key added; a check that works |
| Started the 3D orb on every device | It crashed on GitHub's emulators, which draw without a GPU | GitHub's device-test job failed | A plain icon on those devices |
| Drew the resting orb on every frame | A budget phone stayed busy on still screens | Counting frames on my phone | About 12 frames a second while resting |
| Tapped the phone before the app was in front | The tap hit the home screen and opened the phone's assistant | Seen in the screenshot | Checks the app is in front first |
| Took a task rename as saved | The USB link dropped, so Save never ran | A final check of the whole list | Renamed it again |

### 5.3 Where it stands now

**What works well**
- **The server is live** on one EC2 instance with HTTPS, and the app uses it by default. Voice works from a phone against it.
- **One way to change the schedule.** Drags, the editor, Undo and voice all go through the same checked, undoable path.
- **The build keeps the design honest.** It fails if modules break the layer rules, if screens use data types directly, or if the security gate finds a blocking issue.
- **One command checks everything** (`tools/check.sh`), and it names any test that fails.
- **Voice suggests; the user decides.** Every change is previewed and needs a yes; keys stay on the server.

**Trade-offs I accepted**
- One server with the database on it, not the full AWS design (that stays as tested CDK code).
- The demo and Support logins are public, so anyone can change the demo.
- Last write wins per task when two devices disagree; no merging of fields.
- The upbeat voice model costs about twice as much per character as the one used to measure cost.

**Not done yet**
- Checking that email is delivered, push between two phones on the live server, and a tested database restore.
- A green run of GitHub's device tests with the orb fix.
- A signed release build, and its speed measured.
- A privacy policy and Play Data safety form that name the AI providers.

**Where my own judgment mattered:** making the demo and Support logins public, removing the co-author line from the commits, signing releases by hand, the working-day rule, how much the image provider may keep, approving real provider calls, and every password and command on the live server.

### 5.4 Timeline

257 commits from 20:09 on 11 Nov to 15 Nov: 29 on 11 Nov (the take-home and the first polish), 71 on 12 Nov, 76 on 13 Nov, 40 on 14 Nov and 41 on 15 Nov. The step-by-step detail is in `docs/AI_USAGE_LOG.md` and `git log`.
