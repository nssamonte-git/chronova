# Chronova — AI Usage Writeup

*Prepared for the shared Google Doc. Every item below is taken from the development log (`docs/AI_USAGE_LOG.md`) and the git history; nothing is reconstructed or estimated after the fact.*

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

## 2. AI mistakes and corrections

These were all found during this project. They're listed in the order they occurred.

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

## 3. Technical assessment

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

## 4. Timeline

Git commits run from 20:09 (template baseline) to the final documentation commit. Feature implementation (B0–B7) was committed between 20:09 and 20:42. Planning and revalidation came before the first commit. The breakdown per batch is in `docs/AI_USAGE_LOG.md` and `git log`.
