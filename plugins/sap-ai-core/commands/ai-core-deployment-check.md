---
name: ai-core-deployment-check
description: Check SAP AI Core deployment descriptors, resource plans, secrets, and runtime readiness before release
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[ai-core-config-path] [runtime]"
arguments:
  - name: config_path
    description: Optional path to AI Core YAML, Dockerfile, workflow, or deployment configuration
    required: false
  - name: runtime
    description: Optional runtime or scenario name, such as orchestration, serving, training, or batch
    required: false
---

# AI Core Deployment Check

Perform a preflight review for SAP AI Core deployment assets. Default to inspection only; do not deploy.

## Workflow

1. Locate AI Core descriptors, Dockerfile/container assets, workflow templates, secret references, and environment configuration.
2. Check resource requests, scenario/executable naming, artifact paths, model references, and image/tag immutability.
3. Flag secrets in files, broad permissions, unpinned image/package references, and missing observability.
4. Validate expected lifecycle: build, register, deploy, observe, rollback.
5. List tenant/runtime checks that require SAP AI Core access.

## Output Contract

Return:
- Deployment readiness status.
- Blocking configuration issues.
- Security and secret-handling findings.
- Suggested commands or checks to run locally.
- Pending tenant-only verification items.
