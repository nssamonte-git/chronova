---
id: android-dynamic-code-loading
title: Unsafe Dynamic Code Loading
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-RESILIENCE, MASTG-TEST-0044]
cwe: [CWE-494, CWE-829]
severity: high
last_verified: 2026-06-29
---

# Unsafe Dynamic Code Loading

## Why It Is a Threat

Loading code at runtime (DexClassLoader, downloaded JARs/SO files, or executing from writable storage) lets an attacker who controls that source or path run arbitrary code in your app — a `remote-code-execution`-class risk and a Play policy violation.

## How to Find or Test

Authorized testing only.

- Identify dynamic code loading and where the code comes from (network/writable storage); test whether the source/path can be influenced.

## How to Fix

- Avoid dynamic code loading; if unavoidable, load only signed, integrity-verified code from app-private (non-writable) locations over TLS.
- Never execute code from external/shared storage. Map to MASVS-RESILIENCE.
