---
id: android-insecure-data-storage
title: Insecure Android Data Storage
category: android-storage
platforms: [android]
owasp_refs: [MASVS-STORAGE, MASTG-TEST-0052]
cwe: [CWE-312, CWE-922]
severity: high
last_verified: 2026-06-29
aliases: [sensitive-data-sharedpreferences, sensitive-data-datastore, sensitive-data-room-sqlite, sensitive-data-files, sensitive-data-external-storage]
---

# Insecure Android Data Storage

## Why It Is a Threat

Sensitive data stored unencrypted in SharedPreferences, DataStore, Room/SQLite, internal files, or (worse) external/shared storage is readable by device access, backups, or other apps — especially external storage which is world-accessible historically. The most common Android weakness (MASVS-STORAGE).

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- After key flows, inspect `/data/data/<pkg>` (prefs/DataStore/DB/files) and external storage for secrets, tokens, and PII.

## How to Fix

- Store secrets in the Android Keystore (`android-keystore-misuse`); encrypt sensitive local data with Keystore-backed keys; never put sensitive data on external/shared storage.
- Minimize what is persisted. Map to MASVS-STORAGE.
