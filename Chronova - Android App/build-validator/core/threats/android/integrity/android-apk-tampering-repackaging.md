---
id: android-apk-tampering-repackaging
title: APK Tampering / Repackaging
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-RESILIENCE, MASTG-TEST-0042]
cwe: [CWE-494, CWE-565]
severity: high
last_verified: 2026-06-29
aliases: [apk-tampering, application-repackaging, malicious-clone-applications, signature-spoofing, resource-modification, native-library-tampering]
---

# APK Tampering / Repackaging

## Why It Is a Threat

Attackers modify and re-sign the APK — injecting ads/malware, removing protections, altering resources or native libs, or cloning the app — and distribute it via third-party stores. Users run a trojanized version of your app. Signature-spoofing attempts to mimic the original.

## How to Find or Test

Authorized testing only.

- Repackage with a modified flag/resource and confirm whether the app detects tampering and verifies its signature at runtime.

## How to Fix

- Verify the app's signing certificate at runtime and via Play Integrity; distribute through trusted stores; detect tampering/resource changes (defense-in-depth).
- Enforce critical logic server-side. Map to MASVS-RESILIENCE; see `signing-key-theft`.
