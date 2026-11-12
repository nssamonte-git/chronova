---
id: android-contentprovider-vulnerabilities
title: ContentProvider Vulnerabilities
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASTG-TEST-0024]
cwe: [CWE-926, CWE-89, CWE-22]
severity: high
last_verified: 2026-06-29
aliases: [contentprovider-data-leakage, contentprovider-sql-injection, contentprovider-path-traversal]
---

# ContentProvider Vulnerabilities

## Why It Is a Threat

Exported or poorly-protected ContentProviders let other apps read/write data, inject SQL via query selection/projection, or traverse paths via `openFile` to read arbitrary app files — a direct cross-app data breach.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Enumerate providers (`drozer`); query/update them from an unprivileged app; test selection/projection for SQLi and `openFile` paths for traversal.

## How to Fix

- Set providers non-exported unless required; enforce permissions and URI-permission grants; parameterize all queries (`sql-injection`); canonicalize and confine `openFile` paths (`path-traversal`).
- Validate caller and inputs. Map to MASVS-PLATFORM.
