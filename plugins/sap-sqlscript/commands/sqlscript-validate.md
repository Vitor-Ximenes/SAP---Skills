---
name: sqlscript-validate
description: Validates SQLScript code for syntax, patterns, and best practices with auto-fix capability
allowed-tools:
  - Read
  - Grep
argument-hint: "<file_path|--all> [--fix]"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return syntax, security, performance, and style findings with file references and suggested fixes. Default to read-only validation; use `--fix` only when explicitly requested and exact files are confirmed.


# SQLScript Validation Command

Validate SQLScript code for syntax errors, pattern violations, and best practices. Reports issues and offers to auto-fix common problems.

## Usage

```
/sqlscript-validate [file_path]
/sqlscript-validate [file_path] --fix
/sqlscript-validate --all
```

## Validation Categories

### 1. Critical Issues (Must Fix)

- **Missing EXIT HANDLER**: Procedures without error handling
- **Unclosed Cursors**: Cursor opened but not closed in all paths
- **Hardcoded Schema Names**: Schema names embedded in code
- **SQL Injection Risk**: Dynamic SQL without proper escaping
- **Missing LANGUAGE SQLSCRIPT**: Required clause missing

### 2. Warnings (Should Fix)

- **Cursor in Loop**: Performance anti-pattern
- **SELECT * Usage**: Should specify columns explicitly
- **Missing WHERE Clause**: DELETE/UPDATE without filter
- **Imperative Logic Overuse**: Could be declarative
- **Missing Default Values**: Parameters without defaults

### 3. Suggestions (Consider)

- **Naming Convention**: Non-standard prefixes
- **Comment Density**: Insufficient documentation
- **Code Organization**: Logic ordering recommendations
- **AMDP Compliance**: AMDP-specific issues

## Validation Process

1. **Read the specified file(s)**
2. **Parse SQLScript structure**
3. **Run validation checks** in order:
   - Syntax validation
   - Error handling completeness
   - Performance patterns
   - Security checks
   - Style/naming conventions
4. **Categorize findings** by severity
5. **Report issues** with line numbers
6. **Offer auto-fixes** for applicable issues

## Auto-Fix Capabilities

The following issues can be auto-fixed:

| Issue | Auto-Fix Action |
|-------|-----------------|
| Missing EXIT HANDLER | Add generic exception handler |
| Unclosed cursor | Add CLOSE statement in finally block |
| Missing semicolon | Add semicolon at statement end |
| SELECT * in procedure | Cannot auto-fix (requires column selection) |
| Hardcoded schema | Replace with parameter or DEFAULT SCHEMA |
| Missing LANGUAGE SQLSCRIPT | Add the clause |

## Output Format

```
## SQLScript Validation Report

**File**: [filename]
**Lines**: [count]
**Status**: [PASS/FAIL]

### Critical Issues (X found)
1. [Line X]: [Issue description]
   **Current**: `[code snippet]`
   **Fix**: `[corrected code]`
   **Auto-fix available**: Yes/No

### Warnings (X found)
[Similar format]

### Suggestions (X found)
[Similar format]

### Summary
- Critical: X
- Warnings: X
- Suggestions: X

### Auto-Fix Available
Would you like me to automatically fix the X issues that have auto-fix available?
```

## Examples

### Validate Single File
```
/sqlscript-validate src/procedures/get_orders.sql
```

### Validate and Auto-Fix
```
/sqlscript-validate src/procedures/get_orders.sql --fix
```

### Validate All SQLScript Files
```
/sqlscript-validate --all
```

## Implementation Instructions

When this command is invoked:

1. If a file path is provided, read that file
2. If `--all` is specified, find all `.sql` files and `.abap` files containing AMDP
3. For each file:
   - Parse the content to identify procedure/function boundaries
   - Check for each validation rule
   - Record issues with line numbers
4. Present findings in the format above
5. If `--fix` is specified or user confirms, apply auto-fixes
6. After fixes, re-validate to confirm resolution

## Validation Rules Details

### Missing EXIT HANDLER
```sql
-- BAD: No error handling
CREATE PROCEDURE test()
AS
BEGIN
  INSERT INTO table VALUES (1);
END;

-- GOOD: Has error handling
CREATE PROCEDURE test()
AS
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
    SELECT ::SQL_ERROR_CODE, ::SQL_ERROR_MESSAGE FROM DUMMY;
  INSERT INTO table VALUES (1);
END;
```

### Unclosed Cursor
```sql
-- BAD: Cursor not closed
OPEN cur;
FETCH cur INTO var;
-- Missing: CLOSE cur;

-- GOOD: Cursor properly closed
OPEN cur;
FETCH cur INTO var;
CLOSE cur;
```

### Hardcoded Schema
```sql
-- BAD: Hardcoded schema
SELECT * FROM "MYSCHEMA"."MYTABLE";

-- GOOD: Use DEFAULT SCHEMA or parameter
SELECT * FROM "MYTABLE";  -- With DEFAULT SCHEMA clause
```
