---
name: sac-optimize
description: Analyze SAC scripts for performance optimization opportunities
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[script-file-or-symptom]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return performance findings, backend-call risks, caching or batching suggestions, and validation steps. Default to read-only analysis and do not edit scripts.


Analyze SAC scripts for performance issues and provide optimization recommendations.

## Performance Analysis Workflow

### Step 1: Collect Script Information

Ask the user to provide:
1. **Scripts to analyze** - paste code or describe functionality
2. **Performance symptoms** - slow load, laggy interactions, timeouts?
3. **Number of data points** - how much data is being processed?
4. **User complaints** - specific slow operations?

### Step 2: Performance Anti-Patterns Checklist

#### High-Impact Issues (Fix First)

**1. getMembers() without accessMode**
```javascript
// SLOW: Hits backend every time
var members = ds.getMembers("Location");

// FAST: Uses cached booked values
var members = ds.getMembers("Location", {
    accessMode: MemberAccessMode.BookedValues
});
```
**Impact**: Each call = 1 backend roundtrip (~200-500ms)

**2. Missing setRefreshPaused() Batching**
```javascript
// SLOW: 3 separate backend calls
ds.setDimensionFilter("Dim1", value1);
ds.setDimensionFilter("Dim2", value2);
ds.setDimensionFilter("Dim3", value3);

// FAST: 1 backend call
ds.setRefreshPaused(true);
ds.setDimensionFilter("Dim1", value1);
ds.setDimensionFilter("Dim2", value2);
ds.setDimensionFilter("Dim3", value3);
ds.setRefreshPaused(false);
```
**Impact**: Reduces N calls to 1 call

**3. Heavy onInitialization Code**
```javascript
// BAD: Slow startup
// onInitialization
var members = Chart_1.getDataSource().getMembers("Location");
members.forEach(function(m) {
    // Heavy processing
});

// GOOD: Empty or minimal initialization
// onInitialization
// (keep empty - defer to lazy loading)
```
**Impact**: Blocks initial render

#### Medium-Impact Issues

**4. getData() Instead of getResultSet()**
```javascript
// SLOWER: May hit backend
var data = ds.getData();

// FASTER: Uses cached result set
var resultSet = ds.getResultSet();
```

**5. Repeated getDataSource() Calls**
```javascript
// INEFFICIENT: Multiple lookups
Chart_1.getDataSource().setDimensionFilter("A", "1");
Chart_1.getDataSource().setDimensionFilter("B", "2");

// BETTER: Cache reference
var ds = Chart_1.getDataSource();
ds.setDimensionFilter("A", "1");
ds.setDimensionFilter("B", "2");
```

**6. Backend Calls in Loops**
```javascript
// TERRIBLE: N backend calls
for (var i = 0; i < items.length; i++) {
    ds.getMembers(items[i]); // Backend call each iteration!
}

// BETTER: Single call, process in memory
var resultSet = ds.getResultSet();
// Process resultSet in memory
```

### Step 3: Performance Optimization Patterns

#### Pattern 1: Efficient Data Access

```javascript
// For reading dimension values
var ds = Chart_1.getDataSource();

// Option A: From result set (fastest, cached)
var resultSet = ds.getResultSet();
var uniqueValues = {};
resultSet.forEach(function(row) {
    uniqueValues[row["Location"].id] = row["Location"].description;
});

// Option B: Booked values only (fast, cached)
var members = ds.getMembers("Location", {
    accessMode: MemberAccessMode.BookedValues
});

// Option C: All master data (slowest, hits backend)
// Only use when you need unbooked values
var allMembers = ds.getMembers("Location", {
    accessMode: MemberAccessMode.MasterData
});
```

#### Pattern 2: Batched Filter Updates

```javascript
function applyMultipleFilters(ds, filters) {
    ds.setRefreshPaused(true);

    try {
        for (var dim in filters) {
            if (filters[dim] === null) {
                ds.removeDimensionFilter(dim);
            } else {
                ds.setDimensionFilter(dim, filters[dim]);
            }
        }
    } finally {
        ds.setRefreshPaused(false); // Always unpause
    }
}

// Usage
applyMultipleFilters(Chart_1.getDataSource(), {
    "Location": "US",
    "Product": ["A", "B", "C"],
    "Year": null  // Remove this filter
});
```

#### Pattern 3: Lazy Loading

```javascript
// Script variable to track initialization
var initialized = false;

// In onResultChanged or first interaction
if (!initialized) {
    // Perform one-time heavy setup
    initialized = true;
}
```

#### Pattern 4: Debounced Updates

```javascript
// For rapid user input (e.g., typing in search)
var debounceTimer = null;

function onSearchInput(value) {
    if (debounceTimer) {
        clearTimeout(debounceTimer);
    }

    debounceTimer = setTimeout(function() {
        // Actual filter operation
        ds.setDimensionFilter("Search", value);
    }, 300); // Wait 300ms after last input
}
```

### Step 4: Performance Metrics

#### Enable Performance Logging

Add URL parameter: `?APP_PERFORMANCE_LOGGING=true`

This shows:
- Script execution times
- Backend call durations
- Widget render times

#### Manual Timing

```javascript
console.time("operationName");
// ... operation ...
console.timeEnd("operationName"); // Logs duration
```

### Step 5: Optimization Report Template

**Current State Analysis:**
- onInitialization: [Empty/Light/Heavy]
- Backend calls per interaction: [Count]
- Main bottlenecks: [List]

**Recommendations:**

| Priority | Issue | Current Impact | Fix | Expected Improvement |
|----------|-------|---------------|-----|---------------------|
| High | getMembers without accessMode | 5 calls/action | Add BookedValues | -80% calls |
| High | No refresh batching | 3 calls/filter | Add setRefreshPaused | -66% calls |
| Medium | Heavy onInit | 2s startup | Move to lazy load | -1.5s startup |

**Optimized Code:**
[Provide rewritten code with fixes]

---

## Quick Performance Checklist

- [ ] onInitialization is empty or minimal
- [ ] All getMembers() use accessMode
- [ ] Multiple filters batched with setRefreshPaused()
- [ ] No backend calls in loops
- [ ] Data source references cached in variables
- [ ] Using getResultSet() instead of getData() where possible
- [ ] No unnecessary refreshData() calls

---

Analyze the provided scripts and generate a detailed performance report with specific code fixes.
