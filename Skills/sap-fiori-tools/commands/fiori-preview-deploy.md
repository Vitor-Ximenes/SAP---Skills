---
name: fiori-preview-deploy
description: Check Fiori preview and deployment readiness across local preview, build output, HTML5 repo, and launchpad content
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[app-path] [local|html5-repo|work-zone|cf]"
arguments:
  - name: app_path
    description: Optional Fiori app directory to inspect
    required: false
  - name: target
    description: Optional preview or deployment target
    required: false
---

# Fiori Preview Deploy

Review local preview and deployment readiness. Do not deploy unless the user explicitly asks for deployment.

## Workflow

1. Locate app root, `ui5.yaml`, `package.json`, `webapp/manifest.json`, and deployment descriptors.
2. Check preview scripts, destination/proxy settings, OData metadata, build output, cache buster/preload settings, and app ID.
3. Validate deployment target requirements for HTML5 repository, launchpad/Work Zone, or Cloud Foundry.
4. Suggest read-only build/preview checks and identify missing prerequisites.
5. Separate local app fixes from subaccount/tenant setup.

## Output Contract

Return:
- Preview readiness and deployment target readiness.
- Blocking configuration issues.
- Safe commands to run.
- Deployment checklist with owner split.
- Pending tenant-side checks.
