---
id: incorrect-certificate-validation
title: Incorrect Certificate Validation
category: crypto
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, A07:2021, MASVS-NETWORK]
cwe: [CWE-295]
severity: high
last_verified: 2026-06-29
aliases: [trusting-all-certificates, disabled-hostname-verification, expired-certificate-handling]
---

# Incorrect Certificate Validation

## Why It Is a Threat

The client accepts invalid TLS certificates — trusting all certs, skipping hostname verification, ignoring expiry, or using a permissive trust manager — enabling attacker-in-the-middle interception of "encrypted" traffic. Common in mobile and internal service clients.

## How to Find or Test

Authorized testing only, on an owner-authorized environment.

- Present an untrusted/mismatched/expired certificate via a test proxy and confirm whether the client rejects it; review code for disabled validation (`trustAllCerts`, `ALLOW_ALL_HOSTNAME_VERIFIER`).

## How to Fix

- Use the platform's default, strict certificate and hostname validation; never disable it for convenience.
- Consider pinning for high-risk clients; handle expiry correctly. Map to ASVS V9 and MASVS-NETWORK.
