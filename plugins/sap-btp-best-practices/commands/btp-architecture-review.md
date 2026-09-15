---
name: btp-architecture-review
description: Review SAP BTP architecture decisions for account model, services, security, integration, operations, and rollout readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<architecture-doc-or-project> [extension|integration|analytics|ai]"
arguments:
  - name: target
    description: Architecture document, repository, diagram notes, or scenario summary to review
    required: true
  - name: scenario
    description: Optional scenario lens such as extension, integration, analytics, or AI
    required: false
---

# BTP Architecture Review

Review a SAP BTP architecture for readiness and operating risk. Default to non-mutating document and repository inspection.

## Workflow

1. Identify landscape shape, account model, region assumptions, service dependencies, connectivity, identity, and delivery path.
2. Check separation of concerns across global account, directories, subaccounts, spaces, Kyma namespaces, and transport boundaries.
3. Review security posture: identity provider trust, role collections, service keys, destinations, secrets, network egress, and auditability.
4. Evaluate operations: observability, quotas, backup/restore, lifecycle ownership, CI/CD, cost controls, and incident response.
5. Call out tenant-only checks that require BTP cockpit, CLI, or system credentials.

## Output Contract

Return:
- Architecture readiness summary.
- Decision risks and missing prerequisites.
- Recommended BTP service/account changes.
- Evidence from local files or provided notes.
- Pending platform checks and suggested commands to run with credentials.
