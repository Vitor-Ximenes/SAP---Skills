---
name: fiori-app-generate
description: Generate or plan a SAP Fiori app scaffold using Fiori tools conventions, templates, and safe project setup checks
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
  - AskUserQuestion
argument-hint: "[list-report|object-page|freestyle|worklist] [app-name]"
arguments:
  - name: template
    description: Optional Fiori template or app type to generate
    required: false
  - name: app_name
    description: Optional project or application name
    required: false
---

# Fiori App Generate

Generate or plan a Fiori app scaffold using the existing repository conventions. Default to a plan; scaffold only when the user explicitly requests generation and confirms the target directory. Ask before overwriting files.

## Workflow

1. Inspect package manager, UI5 tooling, existing app folders, namespace conventions, OData service metadata, and target UI5 version.
2. Choose a Fiori Elements or freestyle template based on service type and user intent.
3. Ask for missing required inputs before writing files.
4. Generate only inside a clearly named target directory and avoid overwriting existing user work.
5. Provide run, preview, lint, and deploy-preparation steps.

## Output Contract

Return:
- Selected template and assumptions.
- Generated or proposed file tree.
- Required service metadata and configuration.
- Next commands for install, preview, lint, and tests.
- Follow-up checks for launchpad/deployment readiness.
