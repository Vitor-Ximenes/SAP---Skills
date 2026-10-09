---
name: sqlscript-optimize
description: Analyzes SQLScript code for performance issues and suggests optimizations with auto-fix capability
allowed-tools:
  - Read
  - Grep
argument-hint: "<file_path> [--fix|--explain]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return performance findings, evidence, suggested rewrites, risk level, and validation steps. Default to analysis-only; apply edits only with an explicit fix request and exact file confirmation.


# SQLScript Performance Optimization Command

Analyze SQLScript procedures and functions for performance anti-patterns and suggest optimizations. Offers auto-fix for common performance issues.

## Usage

```
/sqlscript-optimize [file_path]
/sqlscript-optimize [file_path] --fix
/sqlscript-optimize [file_path] --explain
```

## Performance Categories

### 1. Critical Performance Issues

- **Cursor Loops**: Row-by-row processing that could be set-based
- **Nested Loops**: O(n*m) operations in SQLScript
- **Dynamic SQL in Loops**: Query compilation overhead
- **Large Result Sets Without LIMIT**: Memory pressure
- **SELECT * in Production**: Unnecessary data transfer

### 2. Optimization Opportunities

- **Imperative to Declarative**: Convert IF/WHILE to CASE/SQL
- **Join Optimization**: Reorder joins, add hints
- **Subquery to JOIN**: Often more efficient
- **UNION to UNION ALL**: When duplicates aren't possible
- **Filter Pushdown**: Move WHERE clauses earlier

### 3. HANA-Specific Optimizations

- **Engine Mixing**: Avoid Row/Column store mixing
- **CE Function Usage**: Deprecated, use SQL instead
- **Partition Pruning**: Leverage table partitioning
- **Parallel Hints**: Enable parallel execution
- **Result Caching**: Use pragma for cacheable results

## Optimization Process

1. **Read the specified file**
2. **Analyze execution patterns**:
   - Identify loop structures
   - Map data flow
   - Detect engine mixing
3. **Calculate impact scores** (High/Medium/Low)
4. **Generate recommendations** with:
   - Current code
   - Optimized alternative
   - Expected improvement
5. **Offer auto-fixes** where applicable

## Auto-Fix Capabilities

| Issue | Auto-Fix Available | Notes |
|-------|-------------------|-------|
| Cursor loop to set-based | Partial | Simple patterns only |
| UNION to UNION ALL | Yes | When safe to change |
| SELECT * to column list | No | Requires column selection |
| Subquery to JOIN | Partial | Standard patterns |
| Add LIMIT clause | Yes | Adds configurable limit |
| Filter pushdown | Yes | Moves WHERE earlier |

## Output Format

```
## SQLScript Performance Analysis

**File**: [filename]
**Procedure/Function**: [name]
**Estimated Impact**: [High/Medium/Low]

### Critical Performance Issues

#### Issue 1: [Name] (Impact: High)
**Location**: Lines X-Y
**Current Pattern**:
```sql
[current code]
```
**Optimized Pattern**:
```sql
[optimized code]
```
**Expected Improvement**: [description]
**Auto-fix**: [Yes/No]

### Optimization Opportunities
[Similar format]

### Performance Summary
| Metric | Before | After (Est.) |
|--------|--------|--------------|
| Execution Model | Imperative | Declarative |
| Parallel Potential | Low | High |
| Data Movement | High | Low |

### Recommendations (Priority Order)
1. [Most impactful fix]
2. [Second priority]
...

### Apply Optimizations?
Would you like me to apply the X auto-fixable optimizations?
```

## Examples

### Analyze Performance
```
/sqlscript-optimize src/procedures/process_orders.sql
```

### Analyze with Detailed Explanations
```
/sqlscript-optimize src/procedures/process_orders.sql --explain
```

### Analyze and Apply Fixes
```
/sqlscript-optimize src/procedures/process_orders.sql --fix
```

## Common Optimizations

### Cursor Loop to Set-Based

```sql
-- BEFORE (Slow: Row-by-row)
FOR row AS cur DO
  UPDATE orders SET status = 'PROCESSED' WHERE id = row.id;
END FOR;

-- AFTER (Fast: Set-based)
UPDATE orders SET status = 'PROCESSED'
WHERE id IN (SELECT id FROM :lt_ids);
```

### Nested SELECT to JOIN

```sql
-- BEFORE (Slow: Subquery per row)
SELECT
  id,
  (SELECT name FROM customers WHERE id = o.customer_id) as customer_name
FROM orders o;

-- AFTER (Fast: Single JOIN)
SELECT o.id, c.name as customer_name
FROM orders o
LEFT JOIN customers c ON o.customer_id = c.id;
```

### UNION to UNION ALL

```sql
-- BEFORE (Slow: Removes duplicates)
SELECT id FROM table1
UNION
SELECT id FROM table2;

-- AFTER (Fast: No deduplication when not needed)
SELECT id FROM table1
UNION ALL
SELECT id FROM table2;
```

### Filter Pushdown

```sql
-- BEFORE (Slow: Filter after join)
lt_result = SELECT * FROM t1 JOIN t2 ON t1.id = t2.id;
lt_filtered = SELECT * FROM :lt_result WHERE status = 'A';

-- AFTER (Fast: Filter in source)
lt_result = SELECT * FROM t1
            JOIN t2 ON t1.id = t2.id
            WHERE t1.status = 'A';
```

## Implementation Instructions

When this command is invoked:

1. Read the specified SQLScript file
2. Parse to identify:
   - Loop structures (FOR, WHILE, LOOP)
   - Cursor usage
   - Subquery patterns
   - Join structures
   - UNION operations
3. For each pattern, check against optimization rules
4. Calculate potential impact based on:
   - Loop depth
   - Data volume indicators
   - Pattern complexity
5. Generate optimized alternatives
6. Present findings with before/after comparisons
7. If `--fix` specified, apply safe transformations
8. Re-analyze to confirm improvements
