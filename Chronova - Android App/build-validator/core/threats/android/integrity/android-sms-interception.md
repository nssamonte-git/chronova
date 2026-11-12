---
id: android-sms-interception
title: SMS / OTP Interception
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-PLATFORM, A07:2021]
cwe: [CWE-319, CWE-287]
severity: high
last_verified: 2026-06-29
aliases: [sms-otp-interception]
---

# SMS / OTP Interception

## Why It Is a Threat

Malware with SMS permissions, an abused default-SMS role, or notification-listener access reads incoming SMS OTPs, defeating SMS-based MFA and account recovery on the device itself (complements `otp-interception`, `sim-swapping`).

## How to Find or Test

Authorized testing only (resilience review).

- Assess reliance on SMS OTP and exposure if SMS-reading malware is present; review notification-based OTP display.

## How to Fix

- Prefer phishing-resistant factors (passkeys/authenticator) over SMS; if using SMS, scope the SMS Retriever API (no broad SMS permission) and keep OTPs out of notification previews.
- Bind OTP to session and expire quickly. Map to MASVS-PLATFORM; see `otp-interception`.
