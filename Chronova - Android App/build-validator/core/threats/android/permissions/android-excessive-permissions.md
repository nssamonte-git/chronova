---
id: android-excessive-permissions
title: Excessive / Misused Android Permissions
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASVS-PRIVACY]
cwe: [CWE-250, CWE-272]
severity: medium
last_verified: 2026-06-29
aliases: [runtime-permission-misuse]
---

# Excessive / Misused Android Permissions

## Why It Is a Threat

Requesting more permissions than needed (or dangerous ones up front) widens the attack surface and privacy exposure: a compromised or malicious-SDK-laden app can access contacts, location, SMS, camera, and storage beyond its purpose. Also a Play-policy and trust risk.

## How to Find or Test

Authorized testing only.

- Map declared permissions to actual feature need; flag dangerous/rarely-justified permissions and SDKs that pull extra permissions.

## How to Fix

- Request the minimum permissions, at the moment of use, with graceful denied-state handling; remove unused and SDK-imposed permissions.
- Prefer scoped/photo-picker/least-privilege APIs. Map to MASVS-PRIVACY; see `excessive-data-collection`.
