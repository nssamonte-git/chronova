---
id: android-task-hijacking
title: Task Hijacking (StrandHogg)
category: android-ipc
platforms: [android]
owasp_refs: [MASVS-PLATFORM]
cwe: [CWE-925, CWE-1021]
severity: high
last_verified: 2026-06-29
aliases: [activity-launch-manipulation, strandhogg]
---

# Task Hijacking (StrandHogg)

## Why It Is a Threat

A malicious app abuses task affinity / launch modes to insert its activities into your app's task, so when the user opens your app they see the attacker's screen (e.g. a fake login) — credential theft via UI confusion.

## How to Find or Test

Authorized testing only, on an owner-authorized device.

- Review `taskAffinity`, `launchMode`, and `allowTaskReparenting`; test whether another app can place activities in your task (StrandHogg-class).

## How to Fix

- Set `taskAffinity=""` and appropriate `launchMode` (e.g. `singleInstance`/`singleTask` for sensitive entry points); set `FLAG_ACTIVITY_NEW_TASK` carefully; target modern Android with stronger defaults.
- Detect overlay/task anomalies on sensitive screens. Map to MASVS-PLATFORM; see `android-overlay-tapjacking`.
