# Project Context

On demand only. This is a stable profile; current status lives in `CURRENT_TASK.md` and decisions in `DECISIONS.md`.

## Project

- Name: Chronova, "Shape Your Time". App id `noel.chronova.app`.
- Purpose: an offline-first project scheduler. Projects have date ranges; tasks are bars on a calendar timeline that can be moved, resized and snapped, with finish-to-start dependencies. Optional sync with a Laravel server, a staff admin panel, and a voice-only assistant (a 3D orb) that says each change and applies it only on a spoken yes. It began as the PicCollage Android take-home (tag `submission-v1`).
- Layout: one git repository rooted at `Chronova/`. `Chronova - Android App/` is the Android Gradle project (single `app` module, phone-first, checked on an Android 10 phone at 360 dp). `server/` is the Laravel app, with AWS CDK in `server/infra/` (not deployed). `docs/` and `tools/brand/` sit at the root.

## Stack

- App: Kotlin 2.3.21 (built into AGP 9.5.1), Jetpack Compose (BOM 2026.04.01) with Material 3, Room 2.8.4 (schema v3), Navigation Compose 2.9.8, OkHttp 5.3.2 with kotlinx-serialization 1.10.0, core-splashscreen 1.2.0. `compileSdk`/`targetSdk` 37, `minSdk` 26, Gradle 9.6.0 wrapper. Manual dependency injection (`ChronovaApp` / `AppContainer`).
- App tests: JUnit 4 JVM tests with coroutines-test and MockWebServer; three instrumented Room tests.
- Server: Laravel 13 on PHP 8.4, Postgres 16, Sanctum tokens, Filament 5.11 admin panel, Pest and Pint; one FrankenPHP container image with `web`, `worker`, `scheduler` and `migrate` roles.
- Voice providers (server only): ElevenLabs Scribe for speech to text and Flash for text to speech, OpenAI for interpreting and explaining. Fakes are the default; real providers need keys in `server/.env` and the user's approval.

## Entry Points

- App: `Chronova - Android App/app/src/main/java/noel/chronova/app/` — `MainActivity` → `navigation/ChronovaNavHost` (project list → project timeline, account). Wiring in `ChronovaApp.kt` (`AppContainer`).
- App tests: `Chronova - Android App/app/src/test/java/noel/chronova/app/**`; instrumented: `app/src/androidTest/`.
- Server: `server/routes/api.php` (`/v1/auth`, `/v1/sync`, `/v1/voice/*`), controllers in `server/app/Http/Controllers/Api/`, the admin panel in `server/app/Providers/Filament/AdminPanelProvider.php` and `server/app/Filament/`.
- Infra: `server/infra/lib/chronova-stack.ts`; deploy guide `server/infra/DEPLOY.md`.

## Commands

App commands run from `Chronova - Android App/` (the Gradle project); server commands from `server/`; infra commands from `server/infra/`.

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
- `./gradlew :app:installDebug`
- `composer test`, `composer lint` (server; `docker compose up -d` first for Postgres)
- `npm test`, `npx cdk synth -c mailFrom=no-reply@example.com` (infra)
- `python3 tools/brand/generate.py` (from the root; rebuilds icons and logos)

## Design Rules

- The app's palette is defined once in `ui/theme/Color.kt` and exposed through the Material 3 scheme, including container roles. Task colours map in `ui/theme/TaskColors.kt`. The app is light-themed.
- Touch targets are at least 48 dp (IconButtons, handle zones). The timeline is forced to LTR.
- The admin panel is dark-only in the logo's colours (`AdminPanelProvider` palettes, `server/resources/css/admin-theme.css`).
- Brand images are generated from `docs/brand/chronova-logo.webp` by `tools/brand/generate.py`; regenerate rather than editing them by hand.

## Deeper Docs

- `README.md`: features, layout, running the app and server, architecture, tests, decisions and limitations.
- `docs/sync-protocol.md` (sync contract), `docs/voice-api.md` (voice endpoints, privacy, cost, release checklist), `server/README.md`, `server/infra/DEPLOY.md`.
- `docs/AI_USAGE_LOG.md`: per-batch development log. `docs/AI_USAGE_WRITEUP.md`: the AI usage summary.
- `Learn/README.md`: onboarding pages.
