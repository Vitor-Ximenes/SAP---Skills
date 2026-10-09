---
name: work-zone-content-check
description: Check SAP Build Work Zone Advanced content packages, site artifacts, roles, destinations, and transport readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<work-zone-content-or-site-folder> [standard|advanced]"
arguments:
  - name: target
    description: Work Zone content package, site folder, role notes, or deployment issue to inspect
    required: true
  - name: edition
    description: Optional Work Zone edition or content type
    required: false
---

# Work Zone Content Check

Check Work Zone content readiness without publishing, importing, or changing tenant configuration.

## Workflow

1. Identify content package type, site/channel target, roles, destinations, UI integration cards, and app dependencies.
2. Inspect package structure, manifest/configuration files, role collection assumptions, navigation entries, and transport notes.
3. Check destination naming, HTML5 app references, content federation assumptions, theme dependencies, and launchpad/workspace constraints.
4. Flag tenant-only checks for content manager, channel manager, role assignment, and import/export execution.
5. Keep archived or product-version-sensitive behavior documented as pending unless current tenant evidence is available.

## Output Contract

Return:
- Content readiness status.
- Structural and configuration findings.
- Role, destination, and transport prerequisites.
- Suggested read-only verification commands or cockpit checks.
- Pending tenant checks that require Work Zone access.
