# Chronova — AI Usage Log

Factual record of AI-assisted development. Entries are appended per batch; only issues that actually occurred are recorded.

## Tools

- **Claude Code (Claude Opus 5.5)** in the Claude desktop app — project inspection, planning, code generation, build/test execution, git commits.

## How work was divided

1. A detailed implementation prompt (requirements F01–F09, architecture, testing, AI-log rules) was given to Claude Code with the instruction to *revalidate it, then plan in small practical batches* before writing code.
2. Claude inspected the generated Android Studio project, checked the local toolchain and Gradle cache, asked three clarifying questions (mockup availability, git repo location, drag-vs-scroll gesture model), and produced a 10-batch plan (B0–B9). The plan was reviewed and approved before any code was written.
3. Each batch ends with a real build/test run, an entry here, and a commit.

## Batch log

### Plan revalidation (before B0)

Corrections to the original prompt, found by inspecting the actual project:

| Prompt assumption | Reality | Decision |
|---|---|---|
| Compose project, `com.chronova` package | Views "No Activity" template; namespace `noel.chronova.app`; no activity | Add Compose + `MainActivity`; keep the existing namespace |
| Standard Kotlin plugin setup | AGP 9.5.1 has built-in Kotlin; only the Compose compiler plugin is needed | Apply `org.jetbrains.kotlin.plugin.compose` only |
| (unstated) | `minSdk 24`, but `java.time` needs API 26 | Raise `minSdk` to 26 instead of adding desugaring |
| (unstated) | Timeline range, snap rounding rule, minimum duration, drag-commit model not specified | Fixed 3-day range; round-to-nearest with `floorDiv`; 15-min minimum; local preview from gesture-start values, commit on drag end |
| "Use the previously provided mockup" | Mockup not in the repository | Proceed from the written design principles and palette |

### B0 — Bootstrap

- Template committed unchanged as the baseline, then Compose BOM 2026.04.01 / Material 3 / activity-compose / lifecycle-compose added; Views-only deps (`appcompat`, `material`) and template example tests removed; XML theme reduced to a platform `NoActionBar` window theme; `MainActivity` + `ChronovaTheme` + placeholder `ChronovaScreen` added.
- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (47 s).

**AI mistake — emulator availability.**
1. *What AI said:* the plan stated that system image `android-35 google_apis` was installed and an AVD could be created from it.
2. *Why wrong:* the `system-images/android-35/google_apis` directory exists but is empty.
3. *How discovered:* listing the directory when trying to launch the app in B0.
4. *Correction:* manual verification uses the connected physical device instead (requires USB-debugging authorization) or a system image download.
5. *Decision:* don't infer installed tooling from directory names; check contents before planning on it.
