---
id: android-overlay-tapjacking
title: Overlay / Tapjacking Attacks
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-1021]
severity: high
last_verified: 2026-06-29
aliases: [overlay-attacks, tapjacking, screen-overlay-credential-theft]
---

# Overlay / Tapjacking Attacks

## Why It Is a Threat

A malicious app draws overlays (`SYSTEM_ALERT_WINDOW`) on top of your app to capture taps/credentials or trick the user into actions they did not intend — the mobile equivalent of `clickjacking`, used for credential theft and consent fraud.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Test sensitive screens (login, payment, permission grants) with an overlay present; check whether touches are filtered and overlays detected.

## How to Fix

- Set `filterTouchesWhenObscured=true` (or check `MotionEvent` obscured flags) on sensitive views; use `FLAG_SECURE`; avoid critical confirmations that an overlay could spoof.
- Detect active overlays before high-risk actions. Map to MASVS-PLATFORM; see `android-task-hijacking`.
