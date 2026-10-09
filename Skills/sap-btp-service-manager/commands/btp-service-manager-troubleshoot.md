---
name: btp-service-manager-troubleshoot
description: Troubleshoot SAP BTP Service Manager brokers, instances, bindings, credentials, and platform integration issues
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<issue-or-service-instance> [cf|kyma|kubernetes]"
arguments:
  - name: issue
    description: Error message, service instance, binding, or broker symptom to troubleshoot
    required: true
  - name: platform
    description: Optional platform context such as Cloud Foundry, Kyma, or Kubernetes
    required: false
---

# BTP Service Manager Troubleshoot

Diagnose Service Manager or Open Service Broker issues without changing service state by default.

## Workflow

1. Identify platform, broker, service offering, plan, instance, binding, and failing operation.
2. Inspect local manifests, service descriptors, binding consumption, and CLI output supplied by the user.
3. Classify the issue: catalog, entitlement, quota, broker registration, binding credentials, async operation, or deletion orphan.
4. Suggest read-only commands and safe evidence collection.
5. Provide a remediation plan that separates app, platform, and service owner work.

## Output Contract

Return:
- Failure classification and confidence.
- Evidence requested or found.
- Read-only diagnostics.
- Safe remediation sequence.
- Escalation data to collect if platform support is needed.
