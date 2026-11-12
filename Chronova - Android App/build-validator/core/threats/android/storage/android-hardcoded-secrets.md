---
id: android-hardcoded-secrets
title: Hard-Coded Secrets in APKs
category: android-storage
platforms: [android]
owasp_refs: [MASVS-CRYPTO, MASVS-STORAGE]
cwe: [CWE-798, CWE-321]
severity: high
last_verified: 2026-06-29
aliases: [secrets-in-apk, hardcoded-keys-android]
---

# Hard-Coded Secrets in APKs

## Why It Is a Threat

API keys, credentials, and cryptographic keys compiled into the APK (code, resources, `BuildConfig`, native libs) are trivially extracted by decompiling the app — client binaries are not a secret store. Related: `secrets-leakage`, `hardcoded-encryption-keys`.

## How to Find or Test

Authorized testing only.

- Decompile the APK (`jadx`/`apktool`) and search code, resources, assets, and native libs for keys/secrets/endpoints.

## How to Fix

- Keep secrets server-side; have the app obtain short-lived tokens at runtime; never embed long-lived keys/credentials in the client.
- Use Keystore for device-local keys; obfuscation is not protection. Map to MASVS-CRYPTO.
