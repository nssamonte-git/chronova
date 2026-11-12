---
id: android-reverse-engineering
title: APK Reverse Engineering
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-RESILIENCE, MASTG-TEST-0044]
cwe: [CWE-656]
severity: medium
last_verified: 2026-06-29
aliases: [apk-reverse-engineering]
---

# APK Reverse Engineering

## Why It Is a Threat

Attackers decompile the APK to understand logic, extract secrets/endpoints (`android-hardcoded-secrets`), find weaknesses, and prepare tampering/repackaging. Client code is fully visible; security-through-obscurity fails.

## How to Find or Test

Authorized testing only.

- Decompile (`jadx`/`apktool`) and assess what sensitive logic/secrets/endpoints are exposed and whether security depends on hidden client logic.

## How to Fix

- Assume the client is readable: keep secrets and trust decisions server-side. Use obfuscation (R8) to raise effort, not as a control.
- Remove debug artifacts and dead sensitive code. Map to MASVS-RESILIENCE.
