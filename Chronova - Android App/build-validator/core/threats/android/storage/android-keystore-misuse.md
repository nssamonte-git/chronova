---
id: android-keystore-misuse
title: Improper Android Keystore Use
category: android-storage
platforms: [android]
owasp_refs: [MASVS-CRYPTO, MASVS-AUTH]
cwe: [CWE-922, CWE-320]
severity: high
last_verified: 2026-06-29
aliases: [weak-key-accessibility-android, weak-key-authentication-android, deprecated-encryptedsharedprefs]
---

# Improper Android Keystore Use

## Why It Is a Threat

Sensitive keys are not protected by the Android Keystore, or are configured with weak access controls — keys usable without user authentication, not requiring StrongBox/TEE, or not invalidated on biometric enrollment change. Note: OWASP/Jetpack deprecated `EncryptedSharedPreferences`/security-crypto, so teams must use Keystore directly with sound settings.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Review key generation/`KeyGenParameterSpec`: hardware backing (StrongBox/TEE), `setUserAuthenticationRequired`, and `setInvalidatedByBiometricEnrollment`; check for deprecated crypto libs.

## How to Fix

- Generate keys in the Keystore (StrongBox/TEE where available); require user authentication for sensitive keys and invalidate on enrollment change; keep server authorization independent of local auth.
- Migrate off deprecated EncryptedSharedPreferences to Keystore-backed encryption. Map to MASVS-CRYPTO.
