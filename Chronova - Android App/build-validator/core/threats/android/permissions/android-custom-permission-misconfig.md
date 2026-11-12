---
id: android-custom-permission-misconfig
title: Custom Permission Misconfiguration
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-280, CWE-732]
severity: medium
last_verified: 2026-06-29
aliases: [signature-permission-mistakes, custom-permission-race]
---

# Custom Permission Misconfiguration

## Why It Is a Threat

Custom permissions with the wrong protection level (`normal`/`dangerous` instead of `signature`), or vulnerable to define-before-install races, let a malicious app obtain or pre-empt them and access your protected components — defeating the intended app-to-app boundary.

## How to Find or Test

Authorized testing only.

- Review custom permission protection levels and which components they guard; assess whether another app could acquire or define them first.

## How to Fix

- Use `signature` protection for permissions guarding sensitive inter-app access; do not rely on custom permissions alone for security-critical boundaries.
- Verify caller signature/UID for high-risk operations. Map to MASVS-PLATFORM.
