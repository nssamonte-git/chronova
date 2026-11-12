---
id: android-webview-untrusted-content
title: WebView Loading Untrusted Content
category: android-webview
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-79, CWE-601]
severity: high
last_verified: 2026-06-29
aliases: [javascript-execution-untrusted, unsafe-url-redirects-webview, webview-cookie-token-leakage]
---

# WebView Loading Untrusted Content

## Why It Is a Threat

A WebView that loads untrusted/remote URLs (with JavaScript enabled) executes attacker-controlled script, can be redirected to malicious pages, and may leak cookies/tokens across WebViews or to other origins — combining with `android-webview-javascript-interface` for high impact.

## How to Find or Test

Authorized testing only.

- Review which URLs the WebView loads and whether it follows untrusted redirects; check JavaScript-enabled + untrusted content and cookie/token sharing across WebViews.

## How to Fix

- Restrict WebView navigation to an allowlist (`shouldOverrideUrlLoading`); avoid loading untrusted content with JS enabled; isolate cookies per WebView and clear sensitive cookies.
- Prefer Custom Tabs for arbitrary external URLs. Map to MASVS-PLATFORM; see `xss`.
