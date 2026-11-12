---
id: android-privileged-service-abuse
title: Privileged-Service Abuse (Device Admin / VPN / Media Projection)
category: android-permissions
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASVS-PRIVACY]
cwe: [CWE-250, CWE-269]
severity: high
last_verified: 2026-06-29
aliases: [device-administrator-abuse, vpn-service-abuse, media-projection-abuse]
---

# Privileged-Service Abuse (Device Admin / VPN / Media Projection)

## Why It Is a Threat

Powerful Android capabilities are abused by malware: Device Admin (lock/wipe/persistence), VpnService (intercept all traffic), and MediaProjection (record the screen, capturing your app's sensitive UI) — all operating with user-granted but easily-socially-engineered consent.

## How to Find or Test

Authorized testing only (resilience review).

- Assess what sensitive UI/data is exposed if MediaProjection/VPN malware is active; for your own app, review justification for requesting such capabilities.

## How to Fix

- Protect sensitive screens with `FLAG_SECURE` (blocks MediaProjection capture); enforce TLS + pinning so a rogue VPN cannot read traffic; do not request privileged capabilities without strong justification.
- Detect screen recording for high-risk flows where feasible. Map to MASVS-PLATFORM.
