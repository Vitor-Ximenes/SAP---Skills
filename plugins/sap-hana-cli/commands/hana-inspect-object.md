---
name: hana-inspect-object
description: Inspect a HANA database object safely using hana-cli metadata checks, dependency review, and non-mutating SQL
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<schema.object> [table|view|procedure|function]"
arguments:
  - name: object
    description: HANA object name, preferably schema-qualified
    required: true
  - name: object_type
    description: Optional object type such as table, view, procedure, function, or synonym
    required: false
---

# HANA Inspect Object

Inspect HANA object metadata with read-only checks. Do not alter data or object definitions.

## Workflow

1. Confirm object name, schema, object type, and connection profile.
2. Use metadata-oriented queries or hana-cli commands only.
3. Inspect columns, keys, privileges, dependencies, invalid status, row count estimates, and performance-relevant properties.
4. Avoid selecting business data unless the user explicitly requests a limited sample.
5. Recommend follow-up SQLScript, modeling, or authorization fixes as a plan.

## Output Contract

Return:
- Object summary.
- Dependencies and privilege notes.
- Performance or modeling risks.
- Read-only commands/queries used or recommended.
- Follow-up actions and pending checks.
