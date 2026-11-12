---
id: android-data-leakage-surfaces
title: Android Data-Leakage Surfaces
category: android-storage
platforms: [android]
owasp_refs: [MASVS-STORAGE, MASVS-PLATFORM]
cwe: [CWE-200, CWE-359]
severity: medium
last_verified: 2026-06-29
aliases: [clipboard-leakage-android, screenshot-leakage-android, recent-app-preview-leakage, notification-content-leakage]
---

# Android Data-Leakage Surfaces

## Why It Is a Threat

Sensitive data leaks through Android UI/system surfaces: clipboard (readable by other apps), screenshots, the recent-apps preview (task snapshot), and notification content on the lock screen — exposing secrets without any storage bug. Platform-specific instance of `sensitive-data-in-client-storage`.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- After sensitive flows, check clipboard contents, the recent-apps thumbnail, screenshots, and lock-screen notification content.

## How to Fix

- Set `FLAG_SECURE` on sensitive screens (blocks screenshots/recents); avoid copying secrets to clipboard (clear/expire if needed); keep sensitive data out of notifications.
- Mask the task snapshot on background. Map to MASVS-STORAGE.
