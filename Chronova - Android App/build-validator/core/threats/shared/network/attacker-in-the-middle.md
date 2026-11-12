---
id: attacker-in-the-middle
title: Attacker-in-the-Middle
category: network
platforms: [shared, web, backend, android, ios]
owasp_refs: [A02:2021, MASVS-NETWORK]
cwe: [CWE-300, CWE-319]
severity: high
last_verified: 2026-06-29
aliases: [mitm, rogue-wifi-access-point, certificate-substitution, traffic-interception]
---

# Attacker-in-the-Middle

## Why It Is a Threat

An attacker positioned between client and server (rogue Wi-Fi, ARP/DNS poisoning, malicious proxy, substituted certificate) reads or alters traffic. Succeeds when transport is cleartext or certificate validation is weak (`unencrypted-data-in-transit`, `incorrect-certificate-validation`).

## How to Find or Test

Authorized testing only, on an owner-authorized network/device.

- Route client traffic through an intercepting proxy with a substituted cert; confirm whether the client detects/refuses; check for any cleartext or pinning gaps.

## How to Fix

- Enforce TLS everywhere with strict certificate + hostname validation; consider pinning for high-risk mobile clients.
- Use HSTS and detect downgrade (`cryptographic-downgrade`). Map to ASVS V9 and MASVS-NETWORK.
