---
name: btp-transport-check
description: Review SAP Cloud Transport Management readiness, route configuration, content artifacts, and import failure diagnostics
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<node-or-transport-id-or-error> [source-target]"
arguments:
  - name: target
    description: Transport node, transport ID, content artifact, or import/export error text
    required: true
  - name: route
    description: Optional source-to-target route or landscape description
    required: false
---

# BTP Transport Check

Review Transport Management configuration and diagnose transport issues. Do not import, export, or approve transports unless explicitly requested.

## Workflow

1. Identify source node, target node, route, content type, artifact producer, and import state.
2. Inspect MTA/HTML5/content packaging files and user-provided transport logs.
3. Check common problems: missing routes, node credentials, service keys, content type mismatch, artifact naming, and import queue locks.
4. Suggest read-only CLI/API checks where credentials are already configured.
5. Separate content packaging fixes from landscape configuration work.

## Output Contract

Return:
- Transport readiness or failure summary.
- Configuration/artifact issues with evidence.
- Safe diagnostics to run.
- Remediation sequence with rollback notes.
- Production import checks that remain pending.
