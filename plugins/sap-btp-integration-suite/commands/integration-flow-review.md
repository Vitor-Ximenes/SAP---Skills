---
name: integration-flow-review
description: Review SAP Integration Suite iFlows, adapters, security, error handling, observability, and transport readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<iflow-export-or-notes> [api-management|event-mesh|open-connectors]"
arguments:
  - name: target
    description: iFlow export, integration package notes, adapter issue, or repository folder to inspect
    required: true
  - name: capability
    description: Optional Integration Suite capability lens
    required: false
---

# Integration Flow Review

Review SAP Integration Suite artifacts for readiness. Default to read-only inspection; do not deploy, start, stop, or modify integration content.

## Workflow

1. Identify artifact type, adapters, endpoints, authentication, mapping, exception subprocesses, retry behavior, and deployment target.
2. Inspect iFlow exports, package metadata, scripts, mappings, parameters, credential aliases, and transport notes.
3. Check security, idempotency, timeout/retry behavior, payload size, monitoring, logging, alerting, and environment-specific externalization.
4. Separate local export findings from tenant-only deployment, message processing, and runtime checks.
5. Recommend safe next diagnostics before any content change.

## Output Contract

Return:
- Integration readiness status.
- Findings grouped by adapters, security, mapping, error handling, operations, and transport.
- File/package evidence.
- Safe validation commands or cockpit checks.
- Pending tenant runtime checks.
