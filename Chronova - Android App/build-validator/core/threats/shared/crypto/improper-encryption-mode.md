---
id: improper-encryption-mode
title: Improper Encryption Mode
category: crypto
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, MASVS-CRYPTO]
cwe: [CWE-327, CWE-326]
severity: high
last_verified: 2026-06-29
---

# Improper Encryption Mode

## Why It Is a Threat

Using an insecure block-cipher mode (ECB, or unauthenticated CBC without integrity) leaks plaintext patterns and enables tampering and padding-oracle attacks (`padding-oracle`). The mode choice undermines an otherwise strong cipher.

## How to Find or Test

Authorized testing only.

- Review encryption code for ECB usage and unauthenticated modes; check whether ciphertext integrity is verified.

## How to Fix

- Use authenticated encryption (AES-GCM or ChaCha20-Poly1305); never ECB.
- Verify integrity before decrypting; prefer a vetted high-level crypto library. Map to ASVS V6.
