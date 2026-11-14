# Handoff

Chronova is an offline-first project scheduler with a Kotlin + Jetpack Compose Android app, a Laravel 13 + PostgreSQL + Filament 5 server, and AWS CDK. The original PicCollage take-home is tagged `submission-v1`. Module names and packages are in [the project map](Learn/01-project-map.md).

## Layout

- Run Gradle inside `Chronova - Android App/`. See its `settings.gradle.kts` for modules and `build-logic/convention` for build settings. Keep `local.properties` local.
- Run Laravel commands from `server/` and CDK commands from `server/infra/`. Historical AI usage log paths before the directory move are relative to the Android folder.
- Live deployment: Sydney EC2 with Docker Compose. See [EC2 operations](server/infra/EC2.md) and [client handoff status](CURRENT_TASK.md).

## Guardrails

- Work lands on `main` in small batches. Every change needs a passing build, a factual entry in `docs/AI_USAGE_LOG.md`, and a conventional commit (`feat:`, `fix:`, `docs:`, `test:`, `chore:`); a lesson useful beyond Chronova also goes to `learning-inbox/` (Learn/03, step 5).
- Share the project AI kit and `Learn/` in git. Keep secrets, device and account details, and raw learning captures out of commits.
- App copy describes features. Disclose providers and data handling in the privacy policy and Play Data safety form.
- Log only AI mistakes and human actions that actually happened. Never report a build, test, or device check that wasn't run.
- No new dependencies without approval. Versions are pinned exactly and were at least two weeks old when chosen (check release dates, not tag rebuild dates).
- Get explicit approval to deploy, push, call real model providers, or contact real users. Use fake providers for tests. Keep local secrets in the ignored `server/.env` and `server/storage/credentials/`, and hosted secrets in AWS Secrets Manager. Never print or commit secrets. Read and restore any phone settings you change.

## Invariants

Short version; the full rules are in `Learn/02-architecture.md` ("Invariants in detail", "Server rules").

- Time is epoch millis; pixels come only from `TimelineMath`, from the window start. Snapping goes through `SnapGrid`; configuration never changes timestamps.
- Projects, tasks and links live in Room (`:core:database`, schema v6 with migrations). Every schedule write goes through `ApplyScheduleChanges`: one transaction that refuses stale changes and returns the Undo inverse. Sync runs only on "Sync now".
- `presentation -> domain <- data`; features never depend on each other; core never on features. ViewModels read through `*Queries` and write through use cases; screens render UI models. `verifyModuleGraph` and `verifyUiModels` enforce this.
- The timeline has one scroll position (`TimelineViewportState`) and measures nothing at full width; every task has a row (an arrow points to a bar off-screen). A drag is recomputed from the gesture-start task (`pointerInput` keyed on the task id); pinch consumes every move until all fingers lift.
- UI comes from `:core:designsystem` tokens (both themes set every colour role; composables never ask which theme is on) and the shared pieces in `:core:ui/components`; every screen has light, dark and 1.5× text screenshots.
- Rotation recreates `MainActivity`: keep flags in `rememberSaveable` or a ViewModel, and check `isChangingConfigurations` before treating `ON_STOP` as "the user left".
- Push (`docs/push.md`): data messages only, worded by the app; reminders are posted once between push and alarm (`ReminderLog`), and an "account changed" push signs out only after the server answers 401.
- Schedules from images (`docs/import-api.md`): the image goes to Meta only (Standard models, never stored), OpenAI gets Meta's results; the app previews and writes only on Apply, with Undo. Geometry comes from SAM's boxes, not Muse Spark's drifting pixel positions.
- Voice: the server only proposes, the app plans against current data and writes only on a spoken yes; keys stay on the server and no audio or words are stored. A model's clash-free alternative is offered only if `SensibleAlternative` accepts it (no unasked tasks moved, working hours kept).
- Working time is a fixed 09:00–17:00, Monday to Friday (`WorkingTime`, `:core:domain`; user choice, 2026-11-14), used by the critical path and the voice alternatives check. Nothing else (drags, Push, imports) knows it.
- Requests that wait on two provider calls in a row (image import, transcription, spoken explanation) raise PHP's 30 s limit to 55 s for that request only; their provider timeouts must add up to less (tests check the defaults). CloudFront stops waiting at 60 s.

## Validation

Commands only; procedures, baselines and phone quirks are in `Learn/04-validation.md` ("Validation in detail").

- App: the pre-push hook runs `tools/check.sh` (515 unit tests, lint, architecture checks, 180 screenshots and the security gate). Add `--devices` for the 110 managed emulator tests. CI lives in `.github/workflows/android.yml`.
- Phone: never `connectedAndroidTest` (it deletes the app's data). App tests through `tools/qa/rotation-stress.sh instrumented`; every other module as its own test app with `am instrument`, then uninstall it.
- Screenshots: after an intended visual change, `./gradlew updateDebugScreenshotTestDefaultTestSuite`, look at the PNGs, commit them.
- Build gate: `sh build-validator/validate-build.sh`; never lower its threshold or remove the hook. Releases are signed by hand in Android Studio.
- Server: `cd server && docker compose up -d && composer test && composer lint` (514 tests; always the fake push sender). Infra: `cd server/infra && npx tsc --noEmit && npm test && npx cdk synth -c mailFrom=no-reply@example.com` (23 tests).

## On-Demand Context

- `Learn/README.md`: the project map, architecture, workflow, and validation pages.
- `DECISIONS.md`: active design decisions. `CURRENT_TASK.md`: present status.
- `README.md`, `server/README.md`, `server/infra/DEPLOY.md`, `docs/sync-protocol.md`, `docs/voice-api.md`, `docs/push.md`, `docs/import-api.md` (with measured results and its release checklist), `docs/AI_USAGE_LOG.md`.
- Android guidance (reference only, not installed): the canonical `skills/software/mobile/android/mobile-android-production/SKILL.md`. Its path is in `manifest.yaml`.
