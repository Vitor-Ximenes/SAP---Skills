---
name: btp-logging-ingestion-check
description: Check SAP Cloud Logging ingestion setup, source bindings, OpenTelemetry paths, and missing log or metric symptoms
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[cf|kyma|kubernetes|node|java] [symptom]"
arguments:
  - name: source
    description: Optional runtime or source type such as Cloud Foundry, Kyma, Kubernetes, Node.js, or Java
    required: false
  - name: symptom
    description: Optional symptom such as no logs, missing metrics, high cardinality, auth error, or delayed ingestion
    required: false
---

# BTP Logging Ingestion Check

Review Cloud Logging ingestion readiness and diagnose missing telemetry. Default to read-only inspection.

## Workflow

1. Identify runtime, logging source, service binding, endpoint, token/trust mechanism, and telemetry SDK/agent.
2. Inspect application logging configuration, OpenTelemetry exporters, container annotations, and relevant manifests.
3. Check timestamp format, severity mapping, resource attributes, tenant/subaccount labels, and cardinality risks.
4. Suggest safe local validation commands and tenant-side checks.
5. Separate app-code fixes from platform/operator actions.

## Output Contract

Return:
- Ingestion readiness summary.
- Missing or risky configuration.
- Safe diagnostics to run.
- Recommended fixes by owner.
- Pending checks that require Cloud Logging tenant access.
