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
6. **Commit:** a conventional message ending with the `Co-Authored-By` trailer the session specifies. Never push or deploy without the user's explicit approval.

`HANDOFF.md`, `DECISIONS.md` and `Learn/` are tracked; the rest of the AI kit is excluded locally. Run git from the repository root (`Chronova/`) and check that `git status` shows only intended project files before committing.
