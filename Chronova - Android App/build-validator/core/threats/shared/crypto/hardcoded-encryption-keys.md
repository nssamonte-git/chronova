---
id: hardcoded-encryption-keys
title: Hard-Coded Encryption Keys
category: crypto
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, MASVS-CRYPTO]
cwe: [CWE-321, CWE-798]
severity: high
last_verified: 2026-06-29
---

# Hard-Coded Encryption Keys

## Why It Is a Threat

Encryption/signing keys embedded in source, config, or client binaries are extractable by anyone with the code or app, defeating the encryption entirely. Especially severe in mobile/client binaries. Related: `secrets-leakage`, `api-key-theft`.

## How to Find or Test

Authorized testing only.

- Search source, configs, and mobile IPA/APK for embedded keys/secrets; check whether a single shared key protects all data.

## How to Fix

- Store keys in a KMS/HSM or platform keystore (Keychain/Keystore/Secure Enclave); never embed in code or clients.
- Derive per-user/per-record keys where possible. Map to ASVS V6 and `improper-key-management`.
