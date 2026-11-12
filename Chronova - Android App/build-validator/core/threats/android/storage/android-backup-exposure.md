---
id: android-backup-exposure
title: Android Backup Exposure
category: android-storage
platforms: [android]
owasp_refs: [MASVS-STORAGE]
cwe: [CWE-530, CWE-312]
severity: medium
last_verified: 2026-06-29
aliases: [unencrypted-application-backups, android-backup-extraction]
---

# Android Backup Exposure

## Why It Is a Threat

With `allowBackup=true` (historic default), app data can be extracted via `adb backup` or cloud backup, exposing sensitive files/databases to anyone with device or backup access — bypassing in-app protections.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Check the manifest `allowBackup` and `fullBackupContent`/data-extraction rules; attempt a backup and inspect for sensitive data.

## How to Fix

- Set `android:allowBackup="false"` for sensitive apps, or use backup rules to exclude sensitive files; keep secrets in Keystore so backups are useless.
- Encrypt anything that must be backed up. Map to MASVS-STORAGE.
