---
name: sap-dependency-upgrade-plan
description: Create a safe SAP dependency upgrade plan with pinning, changelog review, lockfile checks, and rollback steps
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[package-or-ecosystem] [target-version]"
arguments:
  - name: package_or_ecosystem
    description: Optional package, MCP server, npm/pip ecosystem, or plugin dependency area to plan
    required: false
  - name: target_version
    description: Optional target version or version range to evaluate
    required: false
---

# SAP Dependency Upgrade Plan

Create a dependency upgrade plan. Do not update files unless the user explicitly asks for implementation.

## Workflow

1. Inventory package manifests, lockfiles, MCP configs, plugin manifests, and runtime version constraints.
2. Flag unpinned specs, `@latest`, bare executable package names, deprecated packages, and security-sensitive config.
3. Check changelogs or package metadata when network access is available.
4. Plan upgrades in small batches with test commands and rollback steps.
5. Separate safe local updates from changes requiring SAP tenant/runtime validation.

## Output Contract

Return:
- Current dependency inventory.
- Recommended upgrade batches.
- Risks, breaking changes, and pinning fixes.
- Test and validation commands.
- Rollback plan and pending external verification.
