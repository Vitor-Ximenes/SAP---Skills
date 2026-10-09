---
name: ui5-cli-build
description: Review and run UI5 CLI build readiness checks for SAPUI5 and OpenUI5 apps, libraries, and custom tasks
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[app-path] [--production|--self-contained]"
arguments:
  - name: app_path
    description: Optional UI5 project directory to inspect
    required: false
  - name: build_mode
    description: Optional build mode such as production, self-contained, or library
    required: false
---

# UI5 CLI Build

Check UI5 CLI build readiness and optionally run safe local build commands when requested. Default to read-only inspection and do not alter project files.

## Workflow

1. Locate `ui5.yaml`, `package.json`, `webapp/manifest.json`, custom tasks, and workspace configuration.
2. Check UI5 CLI version, framework version, dependency specs, builder settings, preload/cache-buster requirements, and destination proxy assumptions.
3. Recommend the smallest build command for the requested app type.
4. If running commands, keep them local and non-deploying.
5. Summarize build output and next fixes.

## Output Contract

Return:
- Build readiness status.
- Configuration issues.
- Suggested build command.
- Output summary if command was run.
- Follow-up lint/test/deploy-prep steps.
