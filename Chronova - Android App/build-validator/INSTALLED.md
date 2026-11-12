# Mobile build validator (installed copy)

- platform: android (Native Android)
- installed: 2026-11-13
- source: /Users/noelsamonte/Projects/AI Knowledge and Skills Repo/mobile-build-validator/Native Android
- rule sets: shared android
- threat cards: 41 cards vendored into core/threats/ from /Users/noelsamonte/Projects/AI Knowledge and Skills Repo/threats
- informed_by: AI Knowledge and Skills Repo threat library (threats/android, threats/ios, threats/shared). The rules cite card ids; the library is not a runtime dependency.

Commit this folder so every developer and CI build runs the same gate.
Re-run the installer to update it. Accepted findings go in ../.build-validator-ignore with a reason.
