---
name: sac-planning-checklist
description: Generate a comprehensive checklist for SAC planning implementation projects
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[project-scope]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a planning implementation checklist tailored to the provided scope, open assumptions, risk areas, and tenant-only validation items. Default to read-only guidance.


# SAC Planning Implementation Checklist

Use this checklist to ensure comprehensive planning application implementation.

---

## 1. Planning Model Setup

### Required Dimensions
- [ ] **Version dimension** configured with public/private versioning
- [ ] **Date dimension** with appropriate granularity (day/month/quarter/year)
- [ ] **Account dimension** for measures and KPIs
- [ ] **Organization dimensions** (Cost Center, Profit Center, Company Code)

### Model Configuration
- [ ] Planning model type selected (not analytic model)
- [ ] Measure-based or account-based model determined
- [ ] Default aggregation types set for measures
- [ ] Currency conversion configured (if multi-currency)
- [ ] Data access control configured

### Version Strategy
- [ ] Public versions defined (Actual, Budget, Forecast, etc.)
- [ ] Private version naming convention established
- [ ] Version properties configured (description, validity)
- [ ] Publish workflow defined

---

## 2. Data Actions

### Copy Steps
- [ ] Source and target mappings defined
- [ ] Dimension filters configured
- [ ] "Set to 0" vs "Clear" behavior understood
- [ ] Cross-version copies tested

### Advanced Formulas
- [ ] Formula syntax validated
- [ ] Dimension context verified
- [ ] Lookup data accessible
- [ ] Performance tested with production data volumes

### Allocations
- [ ] Driver accounts identified and populated
- [ ] Allocation rules documented
- [ ] Target dimension members defined
- [ ] Allocation ratios validated

### Parameters
- [ ] Parameter types selected (Member, Number, String, DateTime)
- [ ] Default values configured
- [ ] Input control linkage planned
- [ ] Cross-model parameters defined (if needed)

---

## 3. Multi Actions

### Step Orchestration
- [ ] Step sequence documented
- [ ] Dependencies identified
- [ ] Cross-model operations planned
- [ ] Error handling defined

### Version Management Steps
- [ ] Publish operations configured
- [ ] Target versions specified
- [ ] Publish validation rules defined

### Data Locking Steps
- [ ] Lock scope defined
- [ ] Lock state transitions planned
- [ ] Event-based triggers configured

---

## 4. Planning Workflows (Calendar)

### Process Design
- [ ] Planning cycle timeline defined
- [ ] Task types selected (General, Review, Composite)
- [ ] Task dependencies configured
- [ ] Driving dimensions identified

### Task Configuration
- [ ] Assignees defined
- [ ] Due dates set
- [ ] Notifications enabled
- [ ] Work files attached

### Approval Workflows
- [ ] Approval levels defined
- [ ] Approvers assigned per level
- [ ] Rejection handling planned
- [ ] Data locking on approval configured

---

## 5. Story/Application Development

### Planning Tables
- [ ] Tables linked to planning model
- [ ] Planning enabled on tables
- [ ] Version selection configured
- [ ] Editable measures defined

### Data Entry
- [ ] Spreading behavior configured
- [ ] Validation rules implemented
- [ ] Copy/paste enabled (if needed)
- [ ] Cell commenting enabled (if needed)

### User Experience
- [ ] Input controls for parameters
- [ ] Busy indicators for long operations
- [ ] Error messages user-friendly
- [ ] Mobile compatibility tested (if required)

---

## 6. JavaScript Implementation

### API Usage
- [ ] Correct API selected (getPlanning vs PlanningModel vs DataSource)
- [ ] MDX filter syntax correct
- [ ] Error handling implemented
- [ ] Busy indicators used

### Version Handling
- [ ] Private version creation handled
- [ ] Publish operations implemented
- [ ] Version switching working
- [ ] isDirty() checks implemented

### Data Entry Scripts
- [ ] setUserInput() working correctly
- [ ] submitData() called after changes
- [ ] Refresh triggered after updates
- [ ] Lock state checked before edits

---

## 7. Data Locking

### Configuration
- [ ] Data locking enabled on model
- [ ] Driving dimensions defined
- [ ] Lock region owners assigned
- [ ] Default lock states set

### Workflow Integration
- [ ] Calendar tasks trigger lock changes
- [ ] Lock states align with process stages
- [ ] Override permissions defined
- [ ] Lock status visible to users

---

## 8. Integration

### S/4HANA Export (if applicable)
- [ ] Legacy mode enabled on model
- [ ] ACDOCP OData service activated
- [ ] Cloud Connector configured
- [ ] Dimension mappings defined
- [ ] Export scope documented

### Seamless Planning (if applicable)
- [ ] Datasphere tenant linked
- [ ] Space roles assigned
- [ ] Model exposed to Datasphere
- [ ] Cross-model planning in same space

### BPC Live Connection (if applicable)
- [ ] BPC Embedded configured
- [ ] Planning sequences exposed
- [ ] OData services activated
- [ ] SSO configured

---

## 9. Testing

### Functional Testing
- [ ] Data entry works correctly
- [ ] Spreading behaves as expected
- [ ] Data actions produce correct results
- [ ] Version publishing works
- [ ] Data locking functions properly

### Integration Testing
- [ ] Multi actions execute completely
- [ ] Exports succeed
- [ ] Workflows progress correctly
- [ ] Approvals trigger expected actions

### Performance Testing
- [ ] Data action execution time acceptable
- [ ] Large table rendering tested
- [ ] Concurrent user load tested
- [ ] Background jobs monitored

---

## 10. Documentation

### Technical Documentation
- [ ] Model design documented
- [ ] Data action logic documented
- [ ] Integration points documented
- [ ] Known limitations documented

### User Documentation
- [ ] User guide created
- [ ] Training materials prepared
- [ ] FAQ documented
- [ ] Support contacts listed

---

## Quick Validation Commands

```javascript
// Check planning enabled
console.log("Planning enabled: " + Table_1.getPlanning().isEnabled());

// List public versions
var versions = Table_1.getPlanning().getPublicVersions();
console.log("Public versions: " + versions.length);

// Check data locking
// Note: selection must be defined with the cell/member selection to check
// Example: var selection = Table_1.getSelection();
var selection = Table_1.getSelection(); // Get current table selection
var lockState = Table_1.getPlanning().getDataLocking().getState(selection);
console.log("Lock state: " + lockState);
```

---

**Version**: 1.0.0
**Last Updated**: 2025-12-27
