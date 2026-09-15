---
name: abap-cloud-review
description: Review ABAP code for ABAP Cloud readiness, released API usage, syntax constraints, and upgrade-safe remediation steps
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "<file-or-package> [target-release]"
arguments:
  - name: target
    description: ABAP source file, package, pasted snippet, or object name to review
    required: true
  - name: target_release
    description: Optional target ABAP platform or SAP BTP ABAP Environment release
    required: false
---

# ABAP Cloud Review

Review the requested ABAP code or object for ABAP Cloud compatibility. Default to read-only analysis unless the user explicitly asks for edits.

## Workflow

1. Locate the target source and related includes/classes/interfaces.
2. Check for unreleased APIs, classic extension points, forbidden statements, direct database access risks, and authorization gaps.
3. Identify runtime assumptions that differ between classic ABAP, S/4HANA, and SAP BTP ABAP Environment.
4. Prefer released APIs, RAP patterns, CDS access, and dependency injection friendly designs.
5. Separate findings into must-fix blockers, migration risks, and optional modernization improvements.

## Output Contract

Return:
- Compatibility summary with target release assumptions.
- Findings with file/object references and concrete evidence.
- Suggested remediation with safest replacement pattern.
- Tests or ATC checks to run.
- Production or system-only checks that remain pending.
