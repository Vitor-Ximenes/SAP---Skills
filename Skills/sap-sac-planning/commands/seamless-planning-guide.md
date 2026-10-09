---
name: seamless-planning-guide
description: Quick guidance on implementing Seamless Planning with SAP Datasphere
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[model-or-scenario]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return Seamless Planning guidance, architecture prerequisites, modeling tradeoffs, validation steps, and unresolved tenant checks. Default to read-only analysis and do not change models.


# Seamless Planning Implementation Guide

Quick reference for implementing SAP Analytics Cloud Seamless Planning with SAP Datasphere.

---

## Overview

**Seamless Planning** unifies SAC planning with Datasphere for enterprise-grade data governance.

```
┌─────────────────────────────────────────────────────────────┐
│                    SAP Analytics Cloud                       │
│  • Planning calculations & formulas                          │
│  • Version management logic                                  │
│  • Data actions & multi actions                              │
│  • Calendar workflows                                        │
│  • User interface & experience                               │
└───────────────────────┬─────────────────────────────────────┘
                        │ Direct Persistence
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    SAP Datasphere                            │
│  • Fact data (planning transactions)                         │
│  • Public dimension tables                                   │
│  • Physical storage                                          │
│  • Data governance & security                                │
│  • Cross-system integration                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Prerequisites Checklist

### 1. SAC Tenant on HANA Cloud
- [ ] SAC tenant provisioned on SAP HANA Cloud infrastructure
- [ ] Verify: **System → About** shows HANA Cloud version

### 2. Tenant Co-Location
- [ ] SAC and Datasphere tenants in **same SAP data center region**
- [ ] 1:1 tenant relationship configured

### 3. Tenant Linkage
- [ ] Link configured in both tenants
- [ ] SAC: **System → Administration → Tenant Links**
- [ ] Datasphere: **System → Administration → Tenant Links**

### 4. Identity Provider
- [ ] Same SAML-based Identity Provider for both tenants
- [ ] Consistent user identity mapping

### 5. Datasphere Space Roles
Assign users appropriate space-level roles:

| Role | Required For |
|------|--------------|
| **DW Modeler** | Create and edit models |
| **DW Integrator** | Import and export data |
| **DW Space Administrator** | Manage space settings |

---

## Implementation Steps

### Step 1: Create Planning Model with Datasphere Storage

1. Navigate to **Modeler → Create New Model**
2. Select **Planning Model**
3. In **Data Storage Location**, select **SAP Datasphere Space**
4. Choose target Datasphere space from dropdown

```
┌─────────────────────────────────────┐
│ Create New Model                    │
├─────────────────────────────────────┤
│ Model Type: Planning Model          │
│                                     │
│ Data Storage Location:              │
│ ┌─────────────────────────────────┐ │
│ │ ○ SAP Analytics Cloud (default) │ │
│ │ ● SAP Datasphere Space:         │ │
│ │   [Your_Planning_Space       ▼] │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### Step 2: Configure Dimensions

Configure dimensions as normal:
- **Version dimension** (required)
- **Date dimension** (required)
- **Account dimension** (recommended)
- Other business dimensions

**Note**: Public dimensions are stored in Datasphere as dimension tables.

### Step 3: Expose Model to Datasphere

1. Open **Model Details**
2. Enable **Expose to Datasphere**
3. Select target Datasphere space

**Objects Created in Datasphere**:

| Object Type | Location | Description |
|-------------|----------|-------------|
| Fact Object | Datasphere Space | Read-only view of planning data |
| Physical Table | `sap.sac.<GUID>` | Actual storage |
| Dimension Tables | Shared in Space | Public dimensions |

### Step 4: Execute Planning in SAC

Continue planning normally in SAC:
- Data entry in stories/applications
- Data actions and multi actions
- Version management
- Calendar workflows

**All changes automatically persist to Datasphere!**

---

## Cross-Model Planning

**Important**: All models for cross-model operations must be in the **same Datasphere space**.

This applies to:
- Data actions with cross-model copy steps
- Multi actions spanning multiple models
- Shared public dimensions
- Currency rate tables

---

## Key Constraints

| Constraint | Details |
|------------|---------|
| **Same Space Required** | Cross-model operations require same Datasphere space |
| **Public Dimensions** | Must be in same space as referencing models |
| **Currency Tables** | Must reside in same space |
| **Hierarchies** | SAC hierarchies not exposed; recreate in Datasphere |
| **Import Models Only** | Only import data models supported |

---

## Benefits Summary

| Benefit | Description |
|---------|-------------|
| **Unified Data** | Single source of truth in Datasphere |
| **Direct Persistence** | No manual export needed |
| **Reduced SAC Load** | Offloads storage from SAC |
| **Enterprise Reuse** | Datasphere modeling for planning data |
| **Real-Time Analysis** | Live plan vs. actuals reporting |
| **Governed Data** | Enterprise security on plan data |

---

## Troubleshooting

### Datasphere Not Appearing as Option

**Check**:
1. Tenant linkage configured correctly?
2. User has Datasphere space roles?
3. Both tenants in same data center?
4. Same Identity Provider?

**Reference**: SAP KBA 3515100

### Data Not Persisting

**Check**:
1. Model configured for Datasphere storage?
2. User has write permissions in space?
3. Connection between tenants active?
4. Job monitoring for errors?

### Cross-Model Operations Failing

**Check**:
1. All models in same Datasphere space?
2. Public dimensions in same space?
3. Currency rate tables accessible?

---

## Quick Validation

```javascript
// Check if model is Datasphere-enabled
// (No direct API - check model properties in UI)

// Verify data source connection
var ds = Table_1.getDataSource();
console.log("Data source connected: " + (ds !== null));

// Test data entry
try {
    Table_1.getPlanning().setUserInput(selection, value);
    Table_1.getPlanning().submitData();
    console.log("Data persisted successfully");
} catch (e) {
    console.log("Error: " + e.message);
}
```

---

## Official Documentation

- **Seamless Planning Help**: https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/00f68c2e08b941f081002fd3691d86a7/6d81dcce234b417e8afb8450abab785e.html
- **Datasphere Live Connection**: https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/00f68c2e08b941f081002fd3691d86a7/ad4281e2875949f0b4d45d1072ff4c38.html
- **SAP Roadmap Explorer**: https://roadmaps.sap.com/board?q=seamless%20planning

---

## Related References

For detailed information, consult:
- `references/seamless-planning-datasphere.md` - Complete architecture and configuration
- `references/modeling-basics.md` - Planning model fundamentals

---

**Guide Version**: 1.0.0
**Last Updated**: 2025-12-27
**SAC Version**: 2025.25
