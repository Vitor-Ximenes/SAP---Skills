---
name: sqlscript-convert
description: Converts between standalone SQLScript procedures and AMDP class implementations
allowed-tools:
  - Read
  - Grep
argument-hint: "<file_path> --to <amdp|standalone|cds-function>"
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a conversion plan, compatibility risks, generated snippet if requested, validation checklist, and confirmation points before writes. Default to read-only conversion guidance; write converted files only when the user explicitly confirms the target path.


# SQLScript Conversion Command

Convert SQLScript code between different formats: standalone procedures to AMDP, AMDP to standalone, and CDS integration patterns.

## Usage

```
/sqlscript-convert [file_path] --to amdp
/sqlscript-convert [file_path] --to standalone
/sqlscript-convert [file_path] --to cds-function
```

## Conversion Types

### 1. Standalone to AMDP (`--to amdp`)

Converts a standalone SQLScript procedure or function into an AMDP class with proper:
- Class definition with IF_AMDP_MARKER_HDB interface
- Type definitions for parameters
- Method signature with BY DATABASE clause
- USING clause for all referenced tables
- Type mappings documentation

### 2. AMDP to Standalone (`--to standalone`)

Extracts SQLScript from an AMDP method into a standalone procedure with:
- CREATE PROCEDURE wrapper
- Parameter declarations
- All SQLScript logic preserved
- Comments about original AMDP context

### 3. To CDS Function (`--to cds-function`)

Converts an AMDP table function for CDS view consumption with:
- CDS-compatible method signature
- CLIENT handling patterns
- CDS association support

## Conversion Process

1. **Analyze source code**:
   - Identify procedure/function boundaries
   - Extract parameters and types
   - Map data types between formats
   - Identify table dependencies

2. **Generate target code**:
   - Create appropriate structure
   - Map types correctly
   - Add required clauses
   - Preserve all logic

3. **Validate result**:
   - Check syntax validity
   - Verify type mappings
   - Confirm table references

## Output Format

```
## SQLScript Conversion Result

**Source**: [original file]
**Source Type**: [Standalone Procedure/AMDP/Table Function]
**Target Type**: [AMDP/Standalone/CDS Function]

### Type Mappings Used

| Original | Converted | Notes |
|----------|-----------|-------|
| INTEGER | i | ABAP integer |
| NVARCHAR(100) | string | ABAP string |
...

### Tables Referenced
- [table1] - Added to USING clause
- [table2] - Added to USING clause

### Generated Code

#### [Class Definition / Procedure Header]
```abap/sql
[code]
```

#### [Method Implementation / Procedure Body]
```abap/sql
[code]
```

### Usage Example
[How to call the converted code]

### Manual Steps Required
1. [Any manual steps needed]
2. [E.g., Create types in ABAP]
```

## Examples

### Convert Procedure to AMDP
```
/sqlscript-convert src/procedures/calc_totals.sql --to amdp
```

### Convert AMDP to Standalone
```
/sqlscript-convert src/classes/zcl_orders.abap --to standalone
```

### Convert to CDS Function
```
/sqlscript-convert src/procedures/get_data.sql --to cds-function
```

## Type Mapping Reference

### SQLScript to ABAP (for --to amdp)

| SQLScript | ABAP Type | Notes |
|-----------|-----------|-------|
| INTEGER | i | Standard integer |
| BIGINT | int8 | 8-byte integer |
| DECIMAL(p,s) | p LENGTH p DECIMALS s | Packed number |
| DOUBLE | f | Floating point |
| NVARCHAR(n) | c LENGTH n or string | Character |
| DATE | d | Date (YYYYMMDD) |
| TIME | t | Time (HHMMSS) |
| TIMESTAMP | utclong | UTC timestamp |

### ABAP to SQLScript (for --to standalone)

| ABAP Type | SQLScript | Notes |
|-----------|-----------|-------|
| i | INTEGER | Standard integer |
| int8 | BIGINT | 8-byte integer |
| p | DECIMAL | Preserve precision |
| f | DOUBLE | Floating point |
| c, string | NVARCHAR | Use appropriate length |
| d | DATE | Convert from YYYYMMDD |
| t | TIME | Convert from HHMMSS |
| utclong | TIMESTAMP | UTC timestamp |

## Conversion Templates

### Standalone to AMDP Template

**Source (Standalone)**:
```sql
CREATE PROCEDURE "SCHEMA"."GET_ORDERS"(
  IN iv_customer_id INTEGER,
  OUT et_orders TABLE (id INTEGER, amount DECIMAL(15,2))
)
LANGUAGE SQLSCRIPT
AS
BEGIN
  et_orders = SELECT id, amount FROM orders WHERE customer_id = :iv_customer_id;
END;
```

**Target (AMDP)**:
```abap
CLASS zcl_orders DEFINITION PUBLIC FINAL.
  PUBLIC SECTION.
    INTERFACES if_amdp_marker_hdb.

    TYPES: BEGIN OF ty_order,
             id     TYPE i,
             amount TYPE p LENGTH 15 DECIMALS 2,
           END OF ty_order,
           tt_orders TYPE STANDARD TABLE OF ty_order.

    CLASS-METHODS get_orders
      IMPORTING VALUE(iv_customer_id) TYPE i
      EXPORTING VALUE(et_orders) TYPE tt_orders.
ENDCLASS.

CLASS zcl_orders IMPLEMENTATION.
  METHOD get_orders BY DATABASE PROCEDURE
    FOR HDB LANGUAGE SQLSCRIPT
    OPTIONS READ-ONLY
    USING orders.

    et_orders = SELECT id, amount
                FROM orders
                WHERE customer_id = :iv_customer_id;
  ENDMETHOD.
ENDCLASS.
```

## Implementation Instructions

When this command is invoked:

1. **Parse source file**:
   - Identify code type (standalone, AMDP, table function)
   - Extract all components (parameters, body, tables)

2. **Map types**:
   - Convert each parameter type
   - Handle complex types (tables, structures)
   - Note any manual mapping needed

3. **Generate target structure**:
   - For AMDP: class def, types, method signature, implementation
   - For standalone: CREATE PROCEDURE with all clauses
   - For CDS: Add CLIENT handling, proper method type

4. **Identify tables**:
   - Find all table references in SQLScript
   - Generate USING clause for AMDP

5. **Present result**:
   - Show complete generated code
   - List type mappings used
   - Note any manual steps required

6. **Optionally save**:
   - Ask if user wants to save to file
   - Suggest appropriate filename
