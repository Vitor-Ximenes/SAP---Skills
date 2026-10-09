---
name: data-action-template
description: Generate a data action configuration template based on requirements
allowed-tools:
  - Read
  - AskUserQuestion
argument-hint: "[data-action-name]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a data action design template, dimension/member assumptions, validation checklist, and performance caveats. Default to read-only planning and do not create SAC artifacts or local files unless the user explicitly confirms a target.


# Data Action Configuration Template

Use this template to design and document data action configurations.

---

## Data Action Overview

| Property | Value |
|----------|-------|
| **Name** | [Enter data action name] |
| **Description** | [Enter purpose and business logic] |
| **Model** | [Enter planning model name] |
| **Category** | [Copy / Calculation / Allocation / Mixed] |
| **Execution Context** | [Story / Application / Multi Action / Scheduled] |

---

## Parameters

### Parameter 1: [Name]
| Property | Value |
|----------|-------|
| **Type** | Member / Number / String / DateTime |
| **Dimension** | [If member type] |
| **Default Value** | [Optional] |
| **Required** | Yes / No |
| **Linked Input Control** | [Control name or N/A] |

### Parameter 2: [Name]
| Property | Value |
|----------|-------|
| **Type** | Member / Number / String / DateTime |
| **Dimension** | [If member type] |
| **Default Value** | [Optional] |
| **Required** | Yes / No |
| **Linked Input Control** | [Control name or N/A] |

*Add more parameters as needed*

---

## Steps

### Step 1: [Step Name]

**Step Type**: Copy / Advanced Formula / Allocation / Currency Conversion / Embedded

#### Copy Step Configuration

| Source | Target |
|--------|--------|
| Version = [value] | Version = [value] |
| Date = [value or parameter] | Date = [value or parameter] |
| Account = [value or all] | Account = [value or all] |
| [Dimension] = [value] | [Dimension] = [value] |

**Options**:
- [ ] Set to 0 (target scope set to 0 before copy)
- [ ] Clear (target scope cleared before copy)
- [ ] Neither (additive copy)

#### Advanced Formula Configuration

```
// Formula
[TargetMeasure] = [SourceMeasure1] * [SourceMeasure2]

// Or with conditions
IF [Measure1] > 0 THEN
    [Result] = [Measure1] * 1.1
ELSE
    [Result] = [Measure2]
ENDIF
```

**Scope Filter**:
| Dimension | Filter |
|-----------|--------|
| Version | [value or parameter] |
| Date | [value or parameter] |
| [Other] | [value] |

#### Allocation Configuration

| Property | Value |
|----------|-------|
| **Allocation Type** | Reference Data / Formula |
| **Driver Account** | [Account ID] |
| **Target Dimension** | [Dimension name] |
| **Target Members** | [List or hierarchy node] |

**Allocation Rule**:
```
Source: [Account] WHERE [filters]
Driver: [DriverAccount]
Target: Distribute to [Dimension] members based on driver ratios
```

---

### Step 2: [Step Name]

**Step Type**: [Type]

*Configure as above*

---

### Step 3: [Step Name]

**Step Type**: [Type]

*Configure as above*

---

## Tracepoints (for debugging)

| Location | Tracepoint Name | Purpose |
|----------|-----------------|---------|
| After Step 1 | TP01_AfterCopy | Verify source data copied |
| After Step 2 | TP02_AfterCalc | Verify calculation results |
| After Step 3 | TP03_AfterAlloc | Verify allocation distribution |

---

## Execution Script

```javascript
// Set parameters from input controls
DataAction_1.setParameterValue("Version", InputControl_Version.getSelectedKey());
DataAction_1.setParameterValue("Year", InputControl_Year.getSelectedKey());

// Show busy indicator
Application.showBusyIndicator("Running forecast calculation...");

// Execute data action
DataAction_1.execute().then(function() {
    // Refresh data after execution
    Table_1.getDataSource().refreshData();
    Application.hideBusyIndicator();
    Application.showMessage("Forecast calculation completed.");
}).catch(function(error) {
    Application.hideBusyIndicator();
    Application.showMessage("Error: " + error.message);
});
```

---

## Multi Action Integration (if applicable)

| Step Order | Step Type | Data Action/Operation | Parameters |
|------------|-----------|----------------------|------------|
| 1 | Data Action | [This data action] | [Inherited/Set] |
| 2 | Version Mgmt | Publish version | [Target version] |
| 3 | Data Locking | Lock data slice | [Scope] |

---

## Testing Checklist

### Pre-Execution Checks
- [ ] Source data exists
- [ ] Target version is writable
- [ ] Parameters have valid values
- [ ] User has execution permissions

### Post-Execution Validation
- [ ] Expected records created/updated
- [ ] Calculation results correct
- [ ] Allocation totals balance
- [ ] No unexpected side effects

### Performance Metrics
| Metric | Expected | Actual |
|--------|----------|--------|
| Execution time | < [X] seconds | |
| Records processed | ~[N] | |
| Memory usage | Normal | |

---

## Error Handling

| Error Scenario | Resolution |
|----------------|------------|
| Source data empty | Check filters, verify data exists |
| Target locked | Unlock data or change target version |
| Formula error | Check syntax, dimension context |
| Timeout | Reduce scope, optimize formula |

---

## Documentation Notes

**Business Logic**:
[Describe the business purpose and logic of this data action]

**Dependencies**:
[List any dependencies on other data actions, models, or processes]

**Maintenance Notes**:
[Document any special maintenance considerations]

---

**Template Version**: 1.0.0
**Created**: 2025-12-27
