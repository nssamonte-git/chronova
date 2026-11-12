---
id: android-deep-link-abuse
title: Deep-Link / App-Link Abuse
category: android-webview
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASTG-TEST-0028]
cwe: [CWE-939, CWE-20]
severity: high
last_verified: 2026-06-29
aliases: [custom-scheme-hijacking, deep-link-parameter-injection, unverified-app-links, deep-link-authorization-bypass]
---

# Deep-Link / App-Link Abuse

## Why It Is a Threat

Custom URL schemes can be claimed by any app (hijacking links/tokens), and unverified App Links or deep-link handlers that trust parameters let attackers inject data, redirect flows, or invoke authenticated actions without proper authorization.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Enumerate intent filters/schemes; send crafted deep links from another app; test parameter injection and whether sensitive actions are reachable without auth; verify App Links are autoVerified.

## How to Fix

- Use verified Android App Links (autoVerify + Digital Asset Links) instead of custom schemes for sensitive flows; validate/authorize every deep-link parameter and action server-side.
- Do not perform privileged actions purely from a deep link. Map to MASVS-PLATFORM.
