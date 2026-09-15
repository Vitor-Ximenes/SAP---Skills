---
name: mdi-replication-check
description: Check SAP Master Data Integration replication readiness, business object subscriptions, mappings, errors, and evidence gaps
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<mdi-config-or-issue> [business-partner|cost-center|workforce]"
arguments:
  - name: target
    description: MDI configuration notes, replication issue, mapping file, or integration repository to inspect
    required: true
  - name: object_type
    description: Optional master data object type or domain
    required: false
---

# MDI Replication Check

Review SAP Master Data Integration replication readiness. Default to non-mutating analysis; do not trigger replication or change subscriptions.

## Workflow

1. Identify master data object type, source/target systems, subscriptions, event flow, mapping, and error symptom.
2. Inspect configuration notes, payload samples with secrets removed, mapping files, service bindings, and integration artifacts.
3. Check authentication, business object support, filtering, idempotency, error queues, monitoring, and data ownership.
4. Mark live replication status, tenant subscription changes, and payload reprocessing as pending unless evidence is supplied.
5. Recommend safe diagnostic sequence and missing evidence.

## Output Contract

Return:
- Replication readiness status.
- Mapping, subscription, security, and monitoring findings.
- Evidence from local files or supplied notes.
- Safe verification steps.
- Pending live replication checks.
