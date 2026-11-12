---
id: signing-key-theft
title: Signing-Key or Certificate Theft
category: supply-chain
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, A08:2021]
cwe: [CWE-321, CWE-522]
severity: critical
last_verified: 2026-06-29
aliases: [certificate-theft, code-signing-key-theft]
---

# Signing-Key or Certificate Theft

## Why It Is a Threat

Theft of code-signing keys or certificates lets attackers sign malware/updates that clients and stores trust, defeating signature verification and enabling wide distribution of trusted-looking malicious software.

## How to Find or Test

Authorized testing only.

- Review where signing keys live, access controls, and whether they are in an HSM/KMS; check for keys in CI/repos/dev machines.

## How to Fix

- Store signing keys in an HSM/KMS with strict access and audit; never on dev machines or in CI plaintext; rotate and support revocation.
- Use short-lived signing and provenance (e.g. keyless/transparency logs). Map to A08:2021.
