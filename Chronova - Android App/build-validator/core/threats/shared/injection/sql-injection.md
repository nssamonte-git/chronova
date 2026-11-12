---
id: sql-injection
title: SQL Injection
category: injection
platforms: [shared, web, backend]
owasp_refs: [A03:2021, WSTG-INPV-05]
cwe: [CWE-89]
severity: high
last_verified: 2026-06-29
---

# SQL Injection

## Why It Is a Threat

Untrusted input is concatenated into SQL, letting an attacker alter the query to read, modify, or delete data, bypass authentication, or in some cases execute commands on the database host. High impact and widespread.

## How to Find or Test

Authorized testing only (confirmation methodology: `smx-ai-vapt` `web-injection-recipes.md`).

- Send a syntax-breaking vs syntax-neutral value and compare responses/errors; confirm with a boolean-differential or bounded time-based check — never destructive/stacked statements.
- Demonstrate access to a single non-sensitive marker row; do not dump tables.

## How to Fix

- Use parameterized queries / prepared statements or a vetted ORM; never build SQL by string concatenation.
- Validate and constrain input types; apply least-privilege DB accounts. Add tests. Map to ASVS V5.
