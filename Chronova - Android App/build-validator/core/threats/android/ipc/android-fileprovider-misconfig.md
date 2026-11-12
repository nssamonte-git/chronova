---
id: android-fileprovider-misconfig
title: FileProvider Misconfiguration
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASVS-STORAGE]
cwe: [CWE-200, CWE-732]
severity: medium
last_verified: 2026-06-29
aliases: [uri-permission-leakage]
---

# FileProvider Misconfiguration

## Why It Is a Threat

Over-broad `FileProvider` paths (e.g. exposing the whole files dir or root) or leaked content URIs with persistable read/write grants let other apps access files they should not — exposing private app data.

## How to Find or Test

Authorized testing only.

- Review `file_paths.xml` for over-broad roots; check how content URIs are shared and whether grant flags are minimal and revoked.

## How to Fix

- Scope FileProvider paths to the minimum needed; grant temporary, least-privilege URI permissions (`FLAG_GRANT_READ_URI_PERMISSION`) and revoke when done; never expose sensitive directories.
- Avoid persistable grants for sensitive files. Map to MASVS-PLATFORM.
