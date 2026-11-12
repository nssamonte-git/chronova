---
id: android-intent-redirection
title: Intent Redirection
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-940, CWE-441]
severity: high
last_verified: 2026-06-29
---

# Intent Redirection

## Why It Is a Threat

An exported component extracts a nested/attacker-supplied Intent and forwards (`startActivity`/`sendBroadcast`) it without validation, so a malicious app uses your app's privileges to reach internal, non-exported components or access protected resources — a confused-deputy (`confused-deputy`).

## How to Find or Test

Authorized testing only.

- Find components that read an Intent from a received Intent's extras and launch it; test whether an external app can redirect to internal components.

## How to Fix

- Do not launch attacker-controllable nested Intents; validate the target component/action against an allowlist; mark internal components non-exported.
- Avoid forwarding Intents derived from untrusted input. Map to MASVS-PLATFORM.
