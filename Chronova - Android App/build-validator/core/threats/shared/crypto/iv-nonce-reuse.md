---
id: iv-nonce-reuse
title: IV / Nonce Reuse
category: crypto
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, MASVS-CRYPTO]
cwe: [CWE-323, CWE-329]
severity: high
last_verified: 2026-06-29
aliases: [static-iv, reused-iv, nonce-reuse]
---

# IV / Nonce Reuse

## Why It Is a Threat

Reusing or hard-coding an initialization vector or nonce breaks the security of stream and AEAD modes — for AES-GCM, nonce reuse can leak the authentication key and plaintext relationships, enabling forgery and decryption.

## How to Find or Test

Authorized testing only.

- Review encryption code for static/predictable IVs or nonces and for counters that can repeat under the same key.

## How to Fix

- Generate a fresh, unique IV/nonce per encryption (random for CBC, unique/counter for GCM) and never reuse under one key.
- Rotate keys before nonce space is exhausted. Map to ASVS V6.
