---
name: ui5-cli-troubleshoot
description: Troubleshoot UI5 CLI configuration, framework resolution, custom middleware, build failures, and serve issues
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<error-or-app-path> [build|serve|middleware|framework]"
arguments:
  - name: issue
    description: Error text, app path, or failing UI5 CLI command
    required: true
  - name: category
    description: Optional category such as build, serve, middleware, framework, or workspace
    required: false
---

# UI5 CLI Troubleshoot

Diagnose UI5 CLI failures without changing project files by default.

## Workflow

1. Inspect `ui5.yaml`, package scripts, lockfiles, workspace config, custom tasks/middleware, and the exact failing command.
2. Classify the failure: dependency resolution, YAML schema, framework version, custom task, middleware proxy, build output, or Node/package manager mismatch.
3. Suggest read-only diagnostics and minimal fixes.
4. If a file edit is needed, explain the exact change and risk first.
5. Provide a verification command.

## Output Contract

Return:
- Failure classification.
- Evidence from config or logs.
- Minimal fix plan.
- Commands to verify.
- Known version/tooling caveats.
