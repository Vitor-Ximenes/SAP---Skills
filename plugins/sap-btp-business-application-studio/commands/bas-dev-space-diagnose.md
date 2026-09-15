---
name: bas-dev-space-diagnose
description: Diagnose SAP Business Application Studio dev space setup, extensions, generators, runtime tools, and project readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<project-or-error> [fiori|cap|mobile|workflow]"
arguments:
  - name: target
    description: BAS project folder, dev-space issue, error text, or generated application to inspect
    required: true
  - name: dev_space_type
    description: Optional dev space type or workload lens
    required: false
---

# BAS Dev Space Diagnose

Diagnose Business Application Studio development readiness. Default to read-only inspection and do not change dev space settings.

## Workflow

1. Identify app type, generated tooling, package manager, project structure, extensions, and runtime target.
2. Inspect `package.json`, generator output, `ui5.yaml`, CAP descriptors, `.vscode`, deployment files, and destination/proxy configuration.
3. Check common dev space gaps: missing extensions, stale generator assumptions, proxy/destination setup, npm auth, memory/timeouts, and command mismatch.
4. Distinguish local repository findings from BAS cockpit/dev space checks that require tenant access.
5. Recommend the smallest safe next diagnostic command before any repair action.

## Output Contract

Return:
- Dev space/project readiness status.
- Likely root cause for the reported symptom.
- File-level findings and configuration gaps.
- Safe local commands to verify.
- Pending BAS tenant checks.
