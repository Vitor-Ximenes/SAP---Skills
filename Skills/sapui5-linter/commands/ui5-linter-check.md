---
name: ui5-linter-check
description: Run or plan UI5 Linter checks with project-aware scope, severity triage, and non-mutating defaults
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[app-path] [file-pattern]"
arguments:
  - name: app_path
    description: Optional UI5 project path to inspect
    required: false
  - name: file_pattern
    description: Optional file or glob pattern to focus the linter review
    required: false
---

# UI5 Linter Check

Run or plan UI5 Linter analysis. Default to read-only linting; do not auto-fix.

## Workflow

1. Locate UI5 app root, linter config, `package.json`, UI5 version, and target files.
2. Prefer an existing local linter script when present.
3. If running the linter, use non-mutating options and capture findings.
4. Group findings by severity, file, migration risk, and likely effort.
5. Recommend safe next steps or route to `ui5-linter-fix-plan` for remediation planning.

## Output Contract

Return:
- Linter command selected or recommended.
- Findings summary by severity.
- Key file references.
- False-positive or version caveats.
- Next fix plan.
