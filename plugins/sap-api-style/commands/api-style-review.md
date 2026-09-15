---
name: api-style-review
description: Review SAP API style compliance for REST, OData, OpenAPI, SDK naming, documentation, and lifecycle readiness
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<api-spec-or-folder> [rest|odata|openapi|sdk]"
arguments:
  - name: target
    description: API specification file, documentation folder, SDK source folder, or issue summary to review
    required: true
  - name: api_type
    description: Optional API style lens such as REST, OData, OpenAPI, or SDK
    required: false
---

# API Style Review

Review API style quality against the SAP API style guidance. Default to read-only inspection; do not rewrite specifications or source files unless separately requested.

## Workflow

1. Identify the API type, target artifact, audience, lifecycle state, and applicable style references.
2. Inspect names, paths, operations, events, parameters, error handling, examples, extensibility, and deprecation metadata.
3. Check documentation completeness, terminology consistency, accessibility of examples, and SDK/API surface alignment.
4. Use `rg` against bundled references for the relevant topic instead of loading large guides wholesale.
5. Separate style defects from product or tenant checks that require owner confirmation.

## Output Contract

Return:
- Style readiness status.
- Findings grouped by naming, resource modeling, protocol semantics, documentation, lifecycle, and compatibility.
- File/line references or API paths where available.
- Suggested remediation wording or checklist items.
- Pending owner/system checks that could not be verified locally.
