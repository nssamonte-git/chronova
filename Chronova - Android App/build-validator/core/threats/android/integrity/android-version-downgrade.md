---
id: android-version-downgrade
title: App Version Downgrade
category: android-integrity
platforms: [android]
owasp_refs: [MASVS-RESILIENCE, A06:2021]
cwe: [CWE-757, CWE-1104]
severity: medium
last_verified: 2026-06-29
aliases: [downgrade-to-vulnerable-version]
---

# App Version Downgrade

## Why It Is a Threat

An attacker installs or tricks the user into an older app version with known vulnerabilities (disabled by a fix), re-enabling exploitation — bypassing client-side patches that the server still accepts.

## How to Find or Test

Authorized testing only.

- Check whether the backend accepts requests from old/vulnerable app versions and whether minimum-version enforcement exists.

## How to Fix

- Enforce a minimum supported app version server-side (reject or force-upgrade old clients); do not rely on clients being current.
- Fix vulnerabilities server-side where possible. Map to A06:2021 and `unpatched-vulnerabilities`.
