---
name: btp-app-readiness-review
description: Review SAP BTP application readiness across project structure, service bindings, security, deployment, and operations
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<app-folder-or-design-notes> [cap|ui5|node|java|python]"
arguments:
  - name: target
    description: Application repository, design notes, deployment issue, or readiness checklist to review
    required: true
  - name: stack
    description: Optional application stack or runtime lens
    required: false
---

# BTP App Readiness Review

Review a SAP BTP application for deployment and operations readiness. Default to non-mutating repository inspection.

## Workflow

1. Identify app stack, target environment, service dependencies, authentication model, destinations, persistence, and transport path.
2. Inspect local descriptors such as `mta.yaml`, `package.json`, `pom.xml`, `xs-security.json`, `xs-app.json`, Dockerfiles, and CI/CD workflows.
3. Check startup commands, build targets, service bindings, secret handling, role scopes, logging, health checks, rollback, and environment parity.
4. Avoid duplicating platform architecture advice; route account/subaccount design questions to the BTP platform skill.
5. List live BTP checks that require credentials or deployment history.

## Output Contract

Return:
- Application readiness status.
- Local project blockers and warnings.
- Security, binding, deployment, and operations recommendations.
- Safe verification commands.
- Pending platform or tenant checks.
