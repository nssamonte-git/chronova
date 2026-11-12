---
id: android-webview-tls-bypass
title: WebView TLS / Mixed-Content Bypass
category: android-webview
platforms: [android]
owasp_refs: [MASVS-NETWORK, MASTG-TEST-0020]
cwe: [CWE-295, CWE-319]
severity: high
last_verified: 2026-06-29
aliases: [webview-certificate-error-bypass, mixed-content-webview]
---

# WebView TLS / Mixed-Content Bypass

## Why It Is a Threat

Overriding `onReceivedSslError` with `handler.proceed()` makes the WebView accept invalid certificates, and `MIXED_CONTENT_ALWAYS_ALLOW` loads HTTP resources on HTTPS pages — both enable attacker-in-the-middle interception of WebView traffic (`incorrect-certificate-validation`, `attacker-in-the-middle`).

## How to Find or Test

Authorized testing only.

- Review `onReceivedSslError` handling and `setMixedContentMode`; present an invalid cert via a test proxy and confirm whether the WebView proceeds.

## How to Fix

- Never call `handler.proceed()` on SSL errors; cancel on invalid certs; set `MIXED_CONTENT_NEVER_ALLOW`.
- Apply network-security-config/pinning to WebView traffic. Map to MASVS-NETWORK.
