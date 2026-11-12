---
id: android-unsafe-binder-aidl
title: Unsafe Binder / AIDL IPC
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-749, CWE-862]
severity: medium
last_verified: 2026-06-29
aliases: [unsafe-binder-ipc, unsafe-aidl]
---

# Unsafe Binder / AIDL IPC

## Why It Is a Threat

Bound services exposing Binder/AIDL interfaces without verifying the caller let any app invoke privileged methods or read data, and unvalidated AIDL parameters reach injection/logic sinks — a cross-app privilege and data exposure path.

## How to Find or Test

Authorized testing only.

- Review exported bound services and AIDL methods for caller verification and input validation; bind from an unprivileged app and invoke methods.

## How to Fix

- Verify the caller (`Binder.getCallingUid()`/permissions/signature) on every AIDL method; validate all parameters; keep services non-exported unless required.
- Apply least privilege to exposed methods. Map to MASVS-PLATFORM.
