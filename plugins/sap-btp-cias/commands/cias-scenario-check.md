---
name: cias-scenario-check
description: Check SAP Cloud Integration Automation Service scenario readiness, prerequisites, roles, systems, and evidence gaps
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<scenario-name-or-notes> [source-system target-system]"
arguments:
  - name: scenario
    description: CIAS scenario name, configuration notes, exported checklist, or implementation issue
    required: true
  - name: systems
    description: Optional source and target system context
    required: false
---

# CIAS Scenario Check

Review SAP Cloud Integration Automation Service scenario readiness. Default to read-only checklist analysis; do not start or alter scenarios.

## Workflow

1. Identify the CIAS scenario, involved systems, integration product, required roles, destinations, and communication arrangements.
2. Compare provided notes against prerequisites, supported products, connectivity, authentication, and transport expectations.
3. Check for missing evidence such as system URLs, subaccount entitlements, role collections, communication users, certificates, and data protection approvals.
4. Mark live CIAS cockpit validation and execution status as pending unless evidence is provided.
5. Recommend a safe sequence for collecting missing prerequisites.

## Output Contract

Return:
- Scenario readiness status.
- Missing prerequisites and blockers.
- Role/connectivity/system evidence required.
- Safe validation steps.
- Pending live CIAS checks.
