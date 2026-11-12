---
id: weak-obsolete-algorithms
title: Weak or Obsolete Cryptographic Algorithms
category: crypto
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, MASVS-CRYPTO, WSTG-CRYP-04]
cwe: [CWE-327]
severity: high
last_verified: 2026-06-29
aliases: [weak-cipher-suites]
---

# Weak or Obsolete Cryptographic Algorithms

## Why It Is a Threat

The system uses broken or outdated primitives (MD5, SHA-1, DES/3DES, RC4) or weak TLS cipher suites, letting attackers break confidentiality/integrity through known cryptanalysis.

## How to Find or Test

Authorized testing only.

- Inventory crypto usage and TLS configuration; flag deprecated algorithms, short keys, and weak cipher suites (scan TLS with an authorized tool).

## How to Fix

- Use modern, standard algorithms (AES-GCM, SHA-256+, RSA-2048+/ECDSA P-256+); disable weak TLS suites and enforce TLS 1.2+.
- Keep crypto libraries current and avoid custom crypto. Map to ASVS V6/V9.
