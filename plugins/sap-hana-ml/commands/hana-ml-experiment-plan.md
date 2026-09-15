---
name: hana-ml-experiment-plan
description: Plan SAP HANA ML experiments with data readiness, APL/PAL selection, Python package constraints, and validation checks
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "<notebook-or-use-case> [classification|forecasting|clustering|regression]"
arguments:
  - name: target
    description: Notebook, Python project, use-case notes, or model idea to inspect
    required: true
  - name: task_type
    description: Optional ML task type
    required: false
---

# HANA ML Experiment Plan

Plan a SAP HANA ML experiment. Default to read-only project inspection and planning; do not train models or write data unless explicitly requested.

## Workflow

1. Identify business objective, target variable, data source, HANA connection pattern, algorithm family, and evaluation criteria.
2. Inspect notebooks, Python dependencies, SQL snippets, data access code, and package constraints.
3. Check PAL/APL fit, feature leakage risk, sampling strategy, train/test split, explainability, deployment path, and cost/runtime concerns.
4. Note package/source freshness separately from live HANA validation.
5. Recommend a safe experiment sequence with validation gates.

## Output Contract

Return:
- Experiment readiness status.
- Recommended algorithm/tooling path.
- Data, dependency, and validation findings.
- Non-mutating setup checks.
- Pending HANA/system checks before training or deployment.
