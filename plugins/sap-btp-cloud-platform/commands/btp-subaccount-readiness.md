---
name: btp-subaccount-readiness
description: Check SAP BTP subaccount readiness for regions, entitlements, services, roles, destinations, connectivity, and operations
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<subaccount-notes-or-project> [cf|kyma|html5|integration|ai]"
arguments:
  - name: target
    description: Subaccount notes, repository, deployment descriptor, or readiness issue to inspect
    required: true
  - name: workload
    description: Optional workload lens such as Cloud Foundry, Kyma, HTML5, Integration Suite, or AI
    required: false
---

# BTP Subaccount Readiness

Assess a SAP BTP subaccount or planned workload for readiness. Default to read-only inspection; do not modify entitlements, roles, destinations, or service instances.

## Workflow

1. Identify region, environment, workload type, service dependencies, identity model, connectivity path, and delivery target.
2. Inspect repository descriptors such as `mta.yaml`, `xs-security.json`, `xs-app.json`, Helm files, destination notes, and CI/CD configuration.
3. Check entitlements, service plans, role collections, destinations, trust setup, quotas, transport path, monitoring, and cost controls.
4. Separate local evidence from live BTP checks requiring cockpit or CLI access.
5. Provide a short readiness path that prioritizes blockers before optimization.

## Output Contract

Return:
- Subaccount readiness status.
- Blocking gaps and service prerequisites.
- Security, identity, and connectivity findings.
- Suggested local and BTP CLI/cockpit checks.
- Pending tenant-only checks.
