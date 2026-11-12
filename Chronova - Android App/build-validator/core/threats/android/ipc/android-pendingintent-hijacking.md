---
id: android-pendingintent-hijacking
title: PendingIntent Hijacking
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-927, CWE-200]
severity: high
last_verified: 2026-06-29
aliases: [mutable-pendingintent-abuse]
---

# PendingIntent Hijacking

## Why It Is a Threat

A mutable `PendingIntent` with an implicit base Intent lets a recipient app fill in/modify the Intent and have it executed with your app's identity and permissions — leaking data or invoking protected components on your behalf.

## How to Find or Test

Authorized testing only.

- Review `PendingIntent` creation for mutability and implicit base Intents; test whether a recipient can alter the Intent.

## How to Fix

- Use `FLAG_IMMUTABLE` for PendingIntents (required default on modern Android) and explicit base Intents; only use `FLAG_MUTABLE` when necessary with a fully specified component.
- Minimize what PendingIntents can do. Map to MASVS-PLATFORM.
