---
name: job-schedule-check
description: Check SAP Job Scheduling Service schedules, callbacks, OAuth setup, retry behavior, observability, and REST API usage
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<job-config-or-app-folder> [cf|kyma|node|java]"
arguments:
  - name: target
    description: Job configuration, app folder, callback code, REST API example, or scheduling issue
    required: true
  - name: runtime
    description: Optional runtime or platform lens
    required: false
---

# Job Schedule Check

Review SAP Job Scheduling Service usage. Default to read-only inspection; do not create, trigger, pause, or delete jobs.

## Workflow

1. Identify schedules, callback endpoints, authentication, scopes, service binding, retry policy, and runtime environment.
2. Inspect configuration files, REST examples, application handlers, `xs-security.json`, deployment descriptors, and environment variables.
3. Check OAuth client setup, callback protection, idempotency, retry/timeouts, failure notifications, logs, and time zone assumptions.
4. Verify docs-only REST/API examples when possible and leave live scheduler checks pending without credentials.
5. Recommend safe read-only API or CLI checks before runtime action.

## Output Contract

Return:
- Scheduling readiness status.
- Callback, auth, retry, and operations findings.
- File/API evidence.
- Safe verification commands.
- Pending service-instance checks.
