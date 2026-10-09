---
name: hana-connect-check
description: Diagnose hana-cli connection setup, profiles, TLS, authentication, and safe connectivity verification steps
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[profile-or-host] [cloud|onprem|container]"
arguments:
  - name: profile_or_host
    description: Optional hana-cli profile, HANA host, or connection error text
    required: false
  - name: landscape
    description: Optional landscape type such as cloud, onprem, or container
    required: false
---

# HANA Connect Check

Diagnose HANA CLI connectivity and configuration. Do not print or persist credentials.

## Workflow

1. Inspect local hana-cli configuration files, environment variables, package scripts, and connection snippets without exposing secrets.
2. Check host/port, TLS certificate handling, authentication mode, proxy/network path, and driver compatibility.
3. Suggest safe commands that redact output or verify connectivity without querying business data.
4. Classify failures into DNS/network, TLS, authentication, authorization, driver, or HANA availability.
5. Provide next steps for HANA Cloud cockpit or database administrator checks when needed.

## Output Contract

Return:
- Connectivity diagnosis with evidence.
- Safe read-only verification commands.
- Secret redaction notes.
- Fix plan by owner.
- Pending database/system checks.
