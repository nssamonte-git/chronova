---
id: android-instrumentation-hooking
title: Instrumentation / Hooking
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-RESILIENCE, MASTG-TEST-0045]
cwe: [CWE-489, CWE-494]
severity: medium
last_verified: 2026-06-29
aliases: [frida-hooking, runtime-method-replacement, debugger-attachment]
---

# Instrumentation / Hooking

## Why It Is a Threat

Frameworks like Frida/Xposed hook and replace methods at runtime to bypass checks (auth, pinning, root detection), read memory, and manipulate logic; an attached debugger does similar. Used to defeat client-side controls and extract secrets.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Demonstrate whether hooking/debugging can bypass client-side checks or read sensitive state — for evidence of weakness, not to weaponize.

## How to Fix

- Do not trust the client: enforce security server-side. As defense-in-depth, add tamper/hook/debugger detection and `android:debuggable=false` in release.
- Use hardware-backed keys requiring user auth. Map to MASVS-RESILIENCE.
