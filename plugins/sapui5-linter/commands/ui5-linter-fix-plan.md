---
name: ui5-linter-fix-plan
description: Convert UI5 Linter findings into a safe remediation plan with batching, risk notes, and verification commands
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<findings-file-or-app-path> [--apply-safe]"
arguments:
  - name: target
    description: Linter output, app directory, or file to plan fixes for
    required: true
  - name: apply_safe
    description: Optional flag indicating whether low-risk mechanical fixes may be applied after review
    required: false
---

# UI5 Linter Fix Plan

Turn UI5 Linter findings into an ordered remediation plan. Do not edit unless the user explicitly chooses an apply mode and confirms the exact target files.

## Workflow

1. Parse linter findings or run a read-only linter check if findings are not supplied.
2. Group issues into mechanical fixes, API migrations, behavior-sensitive changes, and manual review items.
3. Prioritize security and broken behavior before style or modernization.
4. For apply mode, only edit low-risk mechanical fixes after naming the exact files.
5. Provide verification commands after each batch.

## Output Contract

Return:
- Fix batches with risk level.
- Files and rules affected.
- Proposed code changes or applied edits.
- Commands to re-run lint/build/tests.
- Items that require manual UI/runtime validation.
