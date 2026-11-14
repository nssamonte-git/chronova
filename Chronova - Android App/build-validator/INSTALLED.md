# Mobile build validator (installed copy)

- platform: android (Native Android)
- installed: 2026-11-13
- source: /Users/noelsamonte/Projects/AI Knowledge and Skills Repo/mobile-build-validator/Native Android
- rule sets: shared android
- threat cards: 41 cards vendored into core/threats/ from /Users/noelsamonte/Projects/AI Knowledge and Skills Repo/threats
- informed_by: AI Knowledge and Skills Repo threat library (threats/android, threats/ios, threats/shared). The rules cite card ids; the library is not a runtime dependency.

Commit this folder so every developer and CI build runs the same gate.
Re-run the installer to update it. Accepted findings go in ../.build-validator-ignore with a reason.

## Local change (15 Nov 2026)

`build-validator.gradle` finds Git for Windows' `bash.exe` in Git's usual install folders (then on PATH, skipping `System32`), instead of calling a bare `bash` that only works when Git's folder is on PATH, and stops with a clear message when it's missing. macOS and Linux still use `sh`; the scan, rules and threshold are unchanged. The canonical `mobile-build-validator/Native Android/build-validator.gradle` doesn't have this yet: port it there through the canonical repo's admission process, or a re-install will undo it here.
