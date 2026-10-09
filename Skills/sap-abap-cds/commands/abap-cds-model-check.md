---
name: abap-cds-model-check
description: Analyze ABAP CDS views for modeling correctness, annotations, performance, authorization, and activation risks
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "<cds-file-or-view-name> [analytical|transactional|reuse]"
arguments:
  - name: target
    description: CDS source file, view name, or pasted CDS definition to analyze
    required: true
  - name: usage
    description: Optional intended usage such as analytical, transactional, reuse, value-help, or extraction
    required: false
---

# ABAP CDS Model Check

Analyze the requested CDS model for correctness and lifecycle risk. Do not change source by default.

## Workflow

1. Inspect the CDS definition, dependencies, associations, annotations, access control, and consumption context.
2. Check keys, cardinalities, path expressions, parameters, currency/unit handling, text/value-help annotations, and authorization behavior.
3. Look for performance risks such as broad joins, missing filters, weak cardinalities, and unnecessary calculated fields.
4. Validate that annotations match the intended consumer and release target.
5. Recommend focused fixes and activation checks.

## Output Contract

Return:
- Model purpose and assumptions.
- Critical activation or semantic issues.
- Performance and authorization findings.
- Annotation improvements by consumer type.
- Validation steps for ADT/ATC and runtime checks.
