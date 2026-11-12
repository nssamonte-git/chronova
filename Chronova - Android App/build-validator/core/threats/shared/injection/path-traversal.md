---
id: path-traversal
title: Path Traversal
category: injection
platforms: [shared, web, backend]
owasp_refs: [A01:2021, A05:2021, WSTG-ATHZ-01]
cwe: [CWE-22]
severity: high
last_verified: 2026-06-29
aliases: [directory-traversal, lfi]
---

# Path Traversal

## Why It Is a Threat

Untrusted input is used to build a filesystem path without constraint, letting an attacker use `../` sequences (or absolute paths) to read or write files outside the intended directory — config, secrets, source, or arbitrary write leading to code execution.

## How to Find or Test

Authorized testing only.

- In file download/upload/include parameters, submit traversal sequences (encoded and double-encoded) toward a known-safe marker file and confirm out-of-directory access.
- Test both read and write paths, and null-byte/encoding variants.

## How to Fix

- Resolve the canonical path and verify it stays within an allowlisted base directory; reject traversal sequences.
- Prefer opaque ids mapped server-side to paths over user-supplied filenames. Map to ASVS V12.
