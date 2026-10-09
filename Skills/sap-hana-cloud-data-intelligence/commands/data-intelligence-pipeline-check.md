---
name: data-intelligence-pipeline-check
description: Review SAP HANA Cloud Data Intelligence pipelines, operators, connections, scheduling, and migration readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<pipeline-export-or-notes> [graph|operator|connection|migration]"
arguments:
  - name: target
    description: Pipeline export, graph configuration, operator code, connection notes, or migration issue
    required: true
  - name: focus
    description: Optional focus such as graph, operator, connection, or migration
    required: false
---

# Data Intelligence Pipeline Check

Review Data Intelligence pipeline readiness. Default to read-only inspection; do not execute graphs, alter connections, or change schedules.

## Workflow

1. Identify graph purpose, operators, connections, runtime, scheduling, persistence, and migration status.
2. Inspect graph exports, operator code, configuration notes, connection references, secrets handling, and monitoring assumptions.
3. Check operator compatibility, retry/error handling, resource usage, credential externalization, data contracts, and ownership.
4. Treat live graph execution, connection tests, and tenant runtime behavior as pending without system evidence.
5. Recommend current supported-path alternatives when product lifecycle or migration risk appears.

## Output Contract

Return:
- Pipeline readiness status.
- Operator, connection, scheduling, and runtime findings.
- Product lifecycle or migration risks.
- Safe local checks.
- Pending tenant/runtime checks.
