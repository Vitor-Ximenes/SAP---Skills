---
name: isa-export-readiness
description: Check SAP Intelligent Situation Automation content export readiness, dependencies, archive risk, and migration evidence
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<isa-content-or-notes> [export|migration|support]"
arguments:
  - name: target
    description: ISA content export, configuration notes, migration plan, or support issue to inspect
    required: true
  - name: focus
    description: Optional focus such as export, migration, or support
    required: false
---

# ISA Export Readiness

Review Intelligent Situation Automation content export or migration readiness. Default to read-only analysis and keep archived-service risk prominent.

## Workflow

1. Identify situation types, actions, teams, notification dependencies, business object links, and target replacement/migration path.
2. Inspect exported content, configuration notes, integration dependencies, and business ownership evidence.
3. Check for archived or limited-support constraints, missing owners, unsupported runtime assumptions, and unverified tenant behavior.
4. Mark live service checks, export/import execution, and migration validation as pending unless current tenant evidence is provided.
5. Recommend documentation and risk actions before operational changes.

## Output Contract

Return:
- Export or migration readiness status.
- Archived-service and supportability warnings.
- Dependency and ownership gaps.
- Safe evidence collection steps.
- Pending tenant/runtime checks.
