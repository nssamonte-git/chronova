---
id: sensitive-data-in-logs
title: Sensitive Data in Logs, Analytics, and Crash Reports
category: data-privacy
platforms: [shared, web, backend, android, ios]
owasp_refs: [A09:2021, MASVS-STORAGE]
cwe: [CWE-532, CWE-200]
severity: medium
last_verified: 2026-06-29
aliases: [sensitive-data-in-analytics, sensitive-data-in-crash-reports]
---

# Sensitive Data in Logs, Analytics, and Crash Reports

## Why It Is a Threat

Sensitive values (credentials, tokens, PII, full requests) are written to application logs, analytics events, or crash reports, then exposed to operators, third-party SDKs, and anyone with log access — a common, low-visibility leak.

## How to Find or Test

Authorized testing only.

- Exercise key flows and inspect logs, analytics payloads, and crash dumps for tokens, PII, request bodies, and headers.
- Review third-party analytics/crash SDKs for what they collect.

## How to Fix

- Redact/deny-list sensitive fields before logging; never log secrets, full auth headers, or full PII.
- Scrub crash reports and constrain analytics to non-sensitive events. Map to ASVS V7.
