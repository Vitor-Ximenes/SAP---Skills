---
name: btp-destination-diagnose
description: Diagnose SAP BTP destination and Cloud Connector issues using configuration checks and safe troubleshooting steps
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<destination-name-or-error> [cf|kyma|bas|local]"
arguments:
  - name: destination
    description: Destination name, error text, or scenario to diagnose
    required: true
  - name: runtime
    description: Optional runtime context such as Cloud Foundry, Kyma, BAS, or local development
    required: false
---

# BTP Destination Diagnose

Diagnose a destination or Cloud Connector connectivity problem. Do not modify tenant configuration by default.

## Workflow

1. Identify destination type, authentication mode, proxy type, runtime, subaccount, and target host.
2. Inspect local application destination usage, environment bindings, and destination service configuration snippets.
3. Check common failure classes: DNS, trust, principal propagation, OAuth, HTML5 app routing, Cloud Connector mapping, and timeout.
4. Suggest safe read-only CLI or curl checks when credentials are already configured.
5. Mark tenant-only verification as pending when live access is not available.

## Output Contract

Return:
- Probable root cause ranked by evidence.
- Configuration fields to inspect.
- Read-only diagnostics to run.
- Fix plan with tenant owner actions separated from code changes.
- Verification checklist.
