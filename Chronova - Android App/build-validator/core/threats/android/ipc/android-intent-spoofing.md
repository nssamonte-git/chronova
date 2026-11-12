---
id: android-intent-spoofing
title: Intent Spoofing / Interception
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASTG-TEST-0029]
cwe: [CWE-925, CWE-927]
severity: high
last_verified: 2026-06-29
aliases: [intent-interception, intent-hijacking, unsafe-implicit-intents]
---

# Intent Spoofing / Interception

## Why It Is a Threat

Implicit intents and components that trust incoming intents let a malicious app spoof intents to trigger actions, or intercept implicit intents (and the sensitive data they carry) intended for another component — bypassing authorization and leaking data. Builds on `android-exported-components`.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Review use of implicit intents carrying sensitive data and components acting on intents without validation; send crafted intents from an unprivileged app and observe behavior.

## How to Fix

- Use explicit intents (set package/component) for internal/sensitive actions; validate and authorize incoming intent data; require permissions on exported components.
- Avoid putting sensitive data in implicit intents. Map to MASVS-PLATFORM.
