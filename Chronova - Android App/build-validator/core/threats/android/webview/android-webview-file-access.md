---
id: android-webview-file-access
title: WebView File-Access Abuse
category: android-webview
platforms: [android]
owasp_refs: [MASVS-PLATFORM, MASVS-STORAGE]
cwe: [CWE-200, CWE-22]
severity: high
last_verified: 2026-06-29
aliases: [universal-file-access, webview-file-url-access]
---

# WebView File-Access Abuse

## Why It Is a Threat

WebView settings like `allowFileAccess`, `allowFileAccessFromFileURLs`, and `allowUniversalAccessFromFileURLs` let `file://` content read local app files and cross origins, so loaded or injected content exfiltrates private files and data.

## How to Find or Test

Authorized testing only.

- Review WebView file-access settings and whether `file://` or untrusted content is loaded; test reading app-private files.

## How to Fix

- Disable file access (`allowFileAccess=false`) and universal/file-URL cross-access; load local assets via `WebViewAssetLoader` over `https`-like origins, not `file://`.
- Never combine file access with untrusted content. Map to MASVS-PLATFORM.
