---
name: sac-debug
description: Debug SAC script issues with guided analysis and common error resolution
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[error-or-widget]"
arguments:
  - name: error
    description: "Error message or description of the issue (optional)"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return the likely script error cause, reproduction questions, API/version caveats, safe code suggestions, and pending SAC runtime checks. Default to read-only debugging.


Provide guided SAC script debugging assistance.

## Debugging Workflow

### Step 1: Gather Information

Ask the user for:
1. **Error message** (if any) - exact text from console or SAC
2. **Script location** - which event handler (onSelect, onClick, onInitialization)?
3. **Widget types involved** - Chart, Table, Button, Dropdown?
4. **SAC environment** - Analytics Designer or Optimized Story Experience?
5. **What should happen** vs **what actually happens**

### Step 2: Common Error Analysis

#### "undefined is not a function"

**Cause**: Calling a method that doesn't exist on the object

**Check**:
```javascript
// Wrong: Method name typo or doesn't exist
Chart_1.getDataSoruce(); // Typo!

// Correct:
Chart_1.getDataSource();
```

**Resolution**:
1. Verify method name spelling
2. Check if method exists for this object type
3. Verify widget type supports the API

#### "Cannot read property 'X' of undefined"

**Cause**: Object is null/undefined when accessing property

**Check**:
```javascript
// Wrong: selections might be empty
var value = Chart_1.getSelections()[0]["Location"];

// Correct: Check array length first
var selections = Chart_1.getSelections();
if (selections.length > 0) {
    var value = selections[0]["Location"];
}
```

**Resolution**:
1. Add null/undefined checks
2. Verify widget reference exists
3. Check if data source is assigned

#### "TypeError: X is not iterable"

**Cause**: Trying to iterate over non-array

**Check**:
```javascript
// Wrong: getSelections() might return null
for (var sel of Chart_1.getSelections()) { }

// Correct: Check for array
var selections = Chart_1.getSelections() || [];
for (var i = 0; i < selections.length; i++) { }
```

#### Filter Not Applied (Silent Failure)

**Cause**: Dimension name doesn't match model exactly

**Check**:
```javascript
// Wrong: Case mismatch or wrong name
ds.setDimensionFilter("location", "US");  // lowercase!

// Correct: Use exact dimension name from model
ds.setDimensionFilter("Location", "US");  // Match model case
```

**Resolution**:
1. Open model in SAC
2. Copy exact dimension technical name
3. Names are case-sensitive

### Step 3: Debugging Techniques

#### Console Logging

```javascript
// Add at script start
console.log("=== Script started ===");

// Log variables
console.log("Widget:", typeof Chart_1, Chart_1);
console.log("DataSource:", Chart_1.getDataSource());
console.log("Selections:", JSON.stringify(Chart_1.getSelections()));

// Log before operations
console.log("Applying filter:", dimensionName, filterValue);
```

#### View Console Output

1. Press **F12** to open Browser DevTools
2. Go to **Console** tab
3. Filter by **Info** level
4. Look for script output (may be in `sandbox.worker.main.*.js`)

#### Safe Widget Access Pattern

```javascript
// Check widget exists
if (typeof Chart_1 !== "undefined" && Chart_1 !== null) {
    var ds = Chart_1.getDataSource();
    if (ds !== null) {
        // Safe to proceed
        ds.setDimensionFilter("Location", "US");
    } else {
        console.error("Chart_1 has no data source assigned");
    }
} else {
    console.error("Chart_1 widget not found");
}
```

### Step 4: Common Issues Checklist

**Widget Issues:**
- [ ] Widget name spelled correctly (case-sensitive)?
- [ ] Widget exists on the current page?
- [ ] Widget visible and not hidden by conditional?
- [ ] Data source assigned to widget?

**Data Source Issues:**
- [ ] Dimension/measure names match model exactly?
- [ ] Filter values exist in the data?
- [ ] Model has data for the filter combination?

**API Issues:**
- [ ] Method exists for this widget/object type?
- [ ] Correct number and type of parameters?
- [ ] Using Analytics Designer API in OSE (may not work)?

**Selection Issues:**
- [ ] Checking if selections array is empty?
- [ ] Using correct dimension key from selection object?
- [ ] Selection event actually fires (test with console.log)?

### Step 5: Test Patterns

#### Minimal Test Script

```javascript
// Test 1: Widget exists
console.log("Test 1 - Widget:", Chart_1);

// Test 2: Data source
console.log("Test 2 - DS:", Chart_1.getDataSource());

// Test 3: Get data
console.log("Test 3 - ResultSet:", Chart_1.getDataSource().getResultSet());

// Test 4: Selections
console.log("Test 4 - Selections:", Chart_1.getSelections());
```

#### Event Fire Test

```javascript
// Add to event handler to confirm it fires
console.log("EVENT FIRED: onSelect at " + new Date().toISOString());
```

---

## Quick Reference: Error Messages

| Error | Likely Cause | Fix |
|-------|-------------|-----|
| undefined is not a function | Wrong method name | Check API docs |
| Cannot read property X of undefined | Null object access | Add null checks |
| X is not iterable | Non-array iteration | Verify array type |
| No error but no effect | Wrong dimension name | Check model schema |
| Script doesn't run | Event not connected | Check widget events |

---

Based on the provided error information, analyze the issue and provide:
1. **Root cause** explanation
2. **Step-by-step fix** with code
3. **Prevention tip** for future
