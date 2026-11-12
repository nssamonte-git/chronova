---
id: android-exported-components
title: Improperly Exported Android Components
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASTG-TEST-0024]
cwe: [CWE-926]
severity: high
last_verified: 2026-06-29
---

# Improperly Exported Android Components

## Why It Is a Threat

Activities, services, broadcast receivers, and content providers that are exported (explicitly, or implicitly via an intent filter) are reachable by other apps on the device. Without permission checks they allow unauthorized apps to invoke privileged actions, read or write provider data (including SQL injection / path traversal in providers), or drive the app into unintended states.

## How to Find or Test

Authorized testing only, on an owner-provided build and authorized test device.

- Decompile the APK (`jadx`/`apktool`) and review `AndroidManifest.xml` for `android:exported="true"` and components with intent filters but no `android:permission`.
- Enumerate and invoke exported components from an unprivileged context using `drozer` (and `adb am`/`content`); probe content providers for SQL injection and path traversal.
- Fuzz exported deep-link/intent parameters for unvalidated actions, auth bypass, or intent redirection.

## How to Fix

- Set `android:exported="false"` unless export is required; for required exports, enforce a signature-level or custom permission.
- Validate and authorize all incoming intent data server-of-trust side; never trust the caller. Parameterize content-provider queries and constrain file paths.
- Avoid leaking sensitive data through implicit intents. Map remediation to MASVS-PLATFORM and the Android app components guidance.
