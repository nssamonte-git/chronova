---
id: android-webview-javascript-interface
title: Unsafe WebView JavaScript Interface
category: android-webview
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASTG-TEST-0031]
cwe: [CWE-749, CWE-79]
severity: high
last_verified: 2026-06-29
aliases: [addjavascriptinterface-abuse, unsafe-javascript-interface]
---

# Unsafe WebView JavaScript Interface

## Why It Is a Threat

`addJavascriptInterface` exposes native methods to JavaScript. If the WebView loads untrusted/remote content (or is XSS-able), that JS can call the bridge to reach native capabilities — data theft or, historically, RCE. The bridge is only as safe as the content loaded.

## How to Find or Test

Authorized testing only.

- Review `addJavascriptInterface` usage, exposed methods, and whether the WebView ever loads untrusted/remote/mixed content.

## How to Fix

- Only add JS interfaces for fully-trusted, local content; minimize and validate bridge methods; use `@JavascriptInterface` and a strict allowlist of loaded origins.
- Prefer message channels over broad native bridges. Map to MASVS-PLATFORM.
