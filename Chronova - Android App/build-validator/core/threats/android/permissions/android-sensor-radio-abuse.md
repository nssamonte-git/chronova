---
id: android-sensor-radio-abuse
title: Sensor / Radio Permission Abuse
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PRIVACY, MASVS-PLATFORM]
cwe: [CWE-359, CWE-200]
severity: medium
last_verified: 2026-06-29
aliases: [bluetooth-permission-abuse, nfc-intent-interception, location-permission-abuse, background-sensor-collection]
---

# Sensor / Radio Permission Abuse

## Why It Is a Threat

Bluetooth, NFC, location, and motion/sensor access are abused to track users, collect data in the background, or intercept NFC intents — privacy violations and fingerprinting vectors, often pulled in by SDKs beyond the app's stated purpose. Related: `proximity-wireless-interception`, `tracking-fingerprinting`.

## How to Find or Test

Authorized testing only.

- Review BT/NFC/location/sensor permissions and background access vs need; check NFC intent filters and SDK collection.

## How to Fix

- Request these only when needed, with foreground/least access; avoid background location/sensor collection without clear consent; validate NFC intents.
- Audit SDKs for covert collection. Map to MASVS-PRIVACY; see `tracking-fingerprinting`.
