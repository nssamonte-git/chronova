# Handoff

Chronova is an offline-first project scheduler: a Kotlin + Jetpack Compose Android app (Gradle modules in three layers: `:app` shell, `:core:*`, `:feature:<name>:{domain,data,presentation}`; each module's package is its namespace, `noel.chronova.core.<module>` / `noel.chronova.feature.<name>.<layer>`, the shell `noel.chronova.app`), a Laravel 13 + Postgres + Filament 5 server (API, sync, voice endpoints, staff admin panel), and AWS CDK for it (not deployed). It began as the PicCollage take-home, preserved at tag `submission-v1`.

## Layout

- The git root is this folder (`Chronova/`). Here: `server/` (Laravel; `server/infra/` CDK), `docs/`, `tools/` (`check.sh`, `git-hooks/`, `qa/`, `brand/`), `.github/workflows/`, `README.md`, and this AI kit. No git remote yet.
- `Chronova - Android App/` holds only the Android Gradle project (open it in Android Studio; run `./gradlew` there). Its `local.properties` is uncommitted. Modules are listed in `settings.gradle.kts`; build settings live in the convention plugins in `build-logic/convention`, so module build files list only plugins and dependencies.
- Paths in `docs/AI_USAGE_LOG.md` entries before the move are relative to the Android folder.

## Guardrails

- Work lands on `main` in small batches. Every change needs a passing build, a factual entry in `docs/AI_USAGE_LOG.md`, and a conventional commit (`feat:`, `fix:`, `docs:`, `test:`, `chore:`).
- `HANDOFF.md`, `DECISIONS.md` and `Learn/` are shared in git (user decision, 2026-11-13): keep them free of secrets, keys and personal data, and commit their updates with the change they describe. The rest of the AI kit (`AGENTS.md`, `CURRENT_TASK.md`, `PROJECT_CONTEXT.md`, `manifest.yaml`, the relationship map files, `scripts/`, `learning-inbox/`, `md-templates/`) stays local-only through `.git/info/exclude`; never `git add -f` it. Don't mention the kit in the README.
- Log only AI mistakes and human actions that actually happened. Never report a build, test, or device check that wasn't run.
- No new dependencies without approval. Versions are pinned exactly and were at least two weeks old when chosen (check release dates, not tag rebuild dates).
- Never deploy to AWS, push, call the real voice providers, or send anything to real users without the user's explicit approval. Provider keys live only in `server/.env` (git-ignored). Device checks that change phone settings must read and restore them (mobile data was off).

## Invariants

Short version; the full rules are in `Learn/02-architecture.md` ("Invariants in detail", "Server rules").

- Time is epoch millis; pixels come only from `TimelineMath`, from the window start. Snapping goes through `SnapGrid`; configuration never changes timestamps.
- Projects, tasks and links live in Room (`:core:database`, schema v5 with migrations). Every schedule write goes through `ApplyScheduleChanges`: one transaction that refuses stale changes and returns the Undo inverse. Sync runs only on "Sync now".
- `presentation -> domain <- data`; features never depend on each other; core never on features. ViewModels read through `*Queries` and write through use cases; screens render UI models. `verifyModuleGraph` and `verifyUiModels` enforce this.
- The timeline has one scroll position (`TimelineViewportState`) and measures nothing at full width; rows list only on-screen tasks. A drag is recomputed from the gesture-start task (`pointerInput` keyed on the task id); pinch consumes every move until all fingers lift.
- Rotation recreates `MainActivity`: keep flags in `rememberSaveable` or a ViewModel, and check `isChangingConfigurations` before treating `ON_STOP` as "the user left".
- Voice: the server only proposes, the app plans against current data and writes only on a spoken yes; keys stay on the server and no audio or words are stored.

## Validation

Commands only; procedures, baselines and phone quirks are in `Learn/04-validation.md` ("Validation in detail").

- App: `tools/check.sh` (repository root) runs everything (373 unit tests, lint, both architecture checks, 58 screenshots, the security gate) and ends `result: PASS`; `--devices` adds the 83 device tests on the managed emulator (needs ~8 GB free disk; unverified so far). The pre-push hook runs it. CI (`.github/workflows/android.yml`) has never run: no GitHub remote yet.
- Phone: never `connectedAndroidTest` (it deletes the app's data). App tests through `tools/qa/rotation-stress.sh instrumented`; every other module as its own test app with `am instrument`, then uninstall it.
- Screenshots: after an intended visual change, `./gradlew updateDebugScreenshotTestDefaultTestSuite`, look at the PNGs, commit them.
- Build gate: `sh build-validator/validate-build.sh`; never lower its threshold or remove the hook. Releases are signed by hand in Android Studio.
- Server: `cd server && docker compose up -d && composer test && composer lint` (319 tests). Infra: `cd server/infra && npx tsc --noEmit && npm test && npx cdk synth -c mailFrom=no-reply@example.com` (16 tests).

## On-Demand Context

- `Learn/README.md`: the project map, architecture, workflow, and validation pages.
- `DECISIONS.md`: active design decisions. `CURRENT_TASK.md` (local only): present status.
- `README.md`, `server/README.md`, `server/infra/DEPLOY.md`, `docs/sync-protocol.md`, `docs/voice-api.md`, `docs/AI_USAGE_LOG.md`.
- Android guidance (reference only, not installed): the canonical `skills/software/mobile/android/mobile-android-production/SKILL.md`. Its path is in `manifest.yaml`.
