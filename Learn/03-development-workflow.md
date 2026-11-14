# Development Workflow

1. **Plan small:** one feature or fix per batch, with its own verification. Ask before adding dependencies; pin exact versions at least two weeks old.
2. **Engine first:** put logic in `core/`, `domain/` or the server's own classes with a unit test, then wire the UI.
3. **Build and test:** app: `cd "Chronova - Android App" && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`; server: `cd server && composer test && composer lint`; infra: `cd server/infra && npm test`.
4. **Check it for real, predicting first:** write down the expected values, then check.
   - App UI: `./gradlew :app:installDebug` (from the Android folder) on the phone, then `adb exec-out screencap -p`; `uiautomator dump` finds control bounds. Read and restore any phone setting you change.
   - End to end: local server (`php artisan serve --port=8000`), `adb reverse tcp:8000 tcp:8000`, `chronova.apiUrl=http://localhost:8000` in `Chronova - Android App/local.properties` for the check only; a throwaway test account whose credentials stay in a scratch file. Remove the account and restore `local.properties` afterwards.
   - Admin panel: the built-in browser against the local server, with a throwaway staff account; delete it afterwards.
   - Voice: fakes by default; real providers only with the user's approval. Check that logs and metrics hold no spoken or typed words.
5. **Log it:** append a factual entry to `docs/AI_USAGE_LOG.md`. Record AI mistakes in the five-part form: what was generated, why it was wrong, how it was found, the correction, and the decision.
   - **Capture a lesson** when that entry records an AI mistake, a guidance gap or a canonical tool's false positive that would apply beyond Chronova (another Android app, the canonical skills, the build validator, the project kit). One lesson per gap: `sh scripts/capture-learning.sh chronova <task_type> <skill_used>`, fill in the draft from `md-templates/LEARNING_LOG.md` (the lesson goes under "Missing Guidance"), check it holds no secrets, private code, account or device details, set `privacy_reviewed: yes`, then `sh scripts/sanitize-learning-log.sh learning-inbox/<file>.md` must pass. Mention the capture in the log entry.
   - Chronova-only facts stay in the log. Promotion is report-only (`sh scripts/promote-learning-candidates.sh`) and the user decides what becomes a decision, route, Learn page or canonical proposal.
6. **Commit:** a conventional message ending with the `Co-Authored-By` trailer the session specifies. Never push or deploy without the user's explicit approval.

The project AI kit and `Learn/` are tracked. Check `git status` from the repository root before committing. Keep local credentials and raw learning captures outside the shared history.
