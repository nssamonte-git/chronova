---
id: android-input-capture
title: Malicious Keyboard / Input Capture
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASVS-PRIVACY]
cwe: [CWE-200]
severity: medium
last_verified: 2026-06-29
aliases: [malicious-keyboard-capture, ime-keylogging]
---

# Malicious Keyboard / Input Capture

## Why It Is a Threat

A malicious or insecure third-party IME (keyboard) can capture everything the user types — passwords, card numbers, messages — including input to your app, and may send it off-device. Input entered into your sensitive fields is exposed beyond your control.

## How to Find or Test

Authorized testing only (resilience review).

- Assess exposure of sensitive input fields to third-party keyboards; review whether high-risk fields use measures to limit capture.

## How to Fix

- For the most sensitive inputs (PINs), consider secure/randomized in-app input or platform secure-input where appropriate; avoid disabling system keyboards broadly (usability).
- Tokenize/limit what is typed (e.g. server-side card entry). Map to MASVS-PRIVACY.
