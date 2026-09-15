---
name: identity-trust-check
description: Review SAP Cloud Identity Services trust, IAS/IPS configuration, role mapping, SSO, and tenant security readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<trust-config-or-issue> [ias|ips|btp|saml|oidc]"
arguments:
  - name: target
    description: Trust configuration export, identity issue, role mapping notes, or project folder to inspect
    required: true
  - name: flow
    description: Optional identity flow such as IAS, IPS, BTP, SAML, or OIDC
    required: false
---

# Identity Trust Check

Review SAP Cloud Identity Services trust and provisioning readiness. Default to non-mutating analysis; do not alter tenants, users, groups, or certificates.

## Workflow

1. Identify IdP/SP roles, protocol, subject/nameID mapping, groups, role collections, provisioning source/target, and tenant boundaries.
2. Inspect exported metadata, configuration notes, manifests, destination settings, and application security descriptors if available.
3. Check certificate expiry, assertion attributes, user uniqueness, group-to-role mapping, IPS transformation risks, and fallback admin access.
4. Flag tenant-only validation such as login simulation, token inspection, provisioning job execution, and trust changes.
5. Keep credential and certificate handling explicit; never ask for secrets in plain text.

## Output Contract

Return:
- Identity readiness status.
- Trust/provisioning findings grouped by protocol, mapping, roles, certificates, and operations.
- Evidence from files or supplied notes.
- Safe verification steps.
- Pending tenant checks.
