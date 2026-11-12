---
id: secrets-leakage
title: Credential, Token, or Key Leakage
category: data-privacy
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, A05:2021]
cwe: [CWE-200, CWE-522, CWE-321]
severity: high
last_verified: 2026-06-29
aliases: [credential-leakage, token-leakage, cryptographic-key-leakage]
---

# Credential, Token, or Key Leakage

## Why It Is a Threat

Secrets (passwords, tokens, API keys, crypto keys) leak through responses, logs, error pages, client bundles, mobile binaries, or URLs, then get reused by attackers. Closely related to `api-key-theft`, `secrets-in-source-control` (supply-chain), and `hardcoded-encryption-keys` (crypto).

## How to Find or Test

Authorized testing only.

- Grep responses, logs, error output, client bundles, and mobile IPA/APK for secret patterns; check URLs and referer leakage.

## How to Fix

- Keep secrets server-side in a secrets manager; never log, return, or embed them in clients/repos.
- Scope, rotate, and revoke secrets; add automated secret-scanning in CI. Map to ASVS V6 and `secrets-handling`.
