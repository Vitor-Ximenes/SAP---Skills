---
name: rap-troubleshoot
description: "Diagnose and fix common RAP development issues on ABAP Cloud/BTP. Covers activation errors, syntax check failures, runtime dumps, behavior definition issues, CDS view problems, EML errors, draft handling issues, authorization problems, and performance debugging. Use this skill whenever the user encounters RAP errors, activation failures, runtime exceptions, OData errors, Fiori app issues, or says things like 'my RAP BO is broken', 'activation error', 'syntax error in BDEF', 'dump in behavior implementation', 'OData returns error', 'draft not working', 'validation not triggered', 'determination not running'. Also use for general RAP troubleshooting and debugging sessions."
---

# RAP Troubleshooting & Debugging

This skill helps diagnose and fix common RAP development issues on ABAP Cloud/BTP using the VSP MCP tools.

## Diagnostic Tools

| Tool | Purpose |
|------|---------|
| `SyntaxCheck` | Validate BDEF, CDS, or ABAP source before activation |
| `GetInactiveObjects` | See what needs activation |
| `RunATCCheck` | Code quality and best practice checks |
| `RunUnitTests` | Run tests to reproduce issues |
| `ListDumps` | Find runtime errors (short dumps) |
| `GetDump` | Get dump details including stack trace |
| `GetSource` | Read current source code |
| `GetClassInfo` | Check class metadata and methods |
| `FindReferences` | Find where an object is used |
| `GetCallersOf` / `GetCalleesOf` | Trace call hierarchy |
| `SetBreakpoint` / `DebuggerListen` | Interactive debugging |
| `ListTraces` / `GetTrace` | Performance analysis |

## Diagnostic Workflow

When a user reports a RAP issue, follow this systematic approach:

### Step 1: Identify the Problem Category

Ask the user or infer from their description:
- **Activation error** → syntax/dependency issue
- **Runtime dump** → logic error in handler/saver
- **Validation not triggering** → BDEF trigger conditions
- **Data not saving** → mapping/persistence issue
- **OData/Fiori error** → service binding/exposure issue
- **Draft issues** → draft table/draft action config
- **Performance** → trace analysis

### Step 2: Gather Information

```
GetInactiveObjects()           " What's not activated?
GetSource(name, object_type)   " Read the problematic source
SyntaxCheck(content, url)      " Check for syntax errors
ListDumps(user: "current")     " Recent runtime errors
```

### Step 3: Diagnose and Fix

## Common Issues & Solutions

### 1. BDEF Activation Errors

**Symptom:** BDEF won't activate, syntax check shows errors.

**Common causes and fixes:**

**Missing or wrong CDS entity name:**
```
" Error: Entity XYZ not found
" Fix: Ensure the CDS view entity exists and name matches exactly
define behavior for ZR_TRAVEL  " Must match CDS root view entity name
```

**Strict mode violations:**
```
" Error: strict(2) requires ...
" Fix: Follow strict mode rules - e.g., always specify authorization
authorization master ( global )  " or ( instance ) or ( none )
```

**Mapping mismatch:**
```
" Error: Field XYZ not found in persistent table
" Fix: Check mapping matches DB table field names exactly
mapping for ztravel corresponding
{
  TravelUUID = travel_uuid;  " CDS alias = DB field name
}
```

**Draft table issues:**
```
" Error: Draft table structure doesn't match
" Fix: Draft table must mirror CDS entity fields (using aliases)
"       plus include %admin draft admin fields
```

**Diagnosis command:**
```
GetSource(name: "ZR_TRAVEL", object_type: "BDEF")
SyntaxCheck(content: <bdef_source>, object_url: "/sap/bc/adt/bdef/zr_travel")
```

### 2. CDS View Activation Errors

**Symptom:** CDS view won't activate.

**Common causes:**

**Association target doesn't exist yet:**
```
" Fix: Activate child CDS views first, or activate all together
" Use ActivatePackage to handle dependency order
ActivatePackage(package: "$TMP")
```

**Annotation syntax errors:**
```
" Error: Unexpected token in annotation
" Fix: Check annotation syntax - common mistakes:
"   - Missing colon after annotation key
"   - Wrong nesting of annotation values
"   - Using single quotes instead of proper string syntax
```

**Field type mismatch in associations:**
```
" Error: Types not compatible in ON condition
" Fix: Ensure both sides of the association condition have same type
association [0..1] to I_Currency as _Currency
  on $projection.CurrencyCode = _Currency.Currency  " Both must be same type
```

### 3. Validation Not Triggering

**Symptom:** Validation exists but doesn't run.

**Diagnosis:**
```
GetSource(name: "ZR_TRAVEL", object_type: "BDEF")
" Check: Is the validation declared? Are trigger conditions correct?
```

**Common causes:**

**Wrong trigger conditions:**
```
" This validation only runs on create, NOT on update:
validation validateDates on save { create; }

" To run on field changes during create AND update:
validation validateDates on save { create; field BeginDate, EndDate; }
```

**Draft: Validation not in Prepare action:**
```
" For draft-enabled BOs, validations must be listed in Prepare:
draft determine action Prepare
{
  validation validateDates;    " Must be listed here!
  validation validateStatus;
}
```

**Handler method not properly declared:**
```
" Check the ABP class - method must match BDEF declaration exactly:
METHODS validateDates FOR VALIDATE ON SAVE
  IMPORTING keys FOR Travel~validateDates.
" The alias 'Travel' must match the BDEF alias
```

### 4. Determination Not Running

**Symptom:** Determination doesn't compute values.

**Common causes:**

**Wrong timing (`on modify` vs `on save`):**
```
" on modify: runs immediately when buffer changes
" on save: runs in save sequence
" If you need the result during the transaction, use on modify
determination setTravelID on modify { create; }
```

**EML read returns empty in determination:**
```
" The keys parameter only contains %tky (key + draft indicator)
" You must READ the instance to get field values:
READ ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  FIELDS ( field1 field2 )
  WITH CORRESPONDING #( keys )
  RESULT DATA(travels).
```

**Missing `IN LOCAL MODE`:**
```
" Inside handler methods, always use IN LOCAL MODE
" Otherwise authorization checks may block the read
READ ENTITIES OF ZR_Travel IN LOCAL MODE ...
MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE ...
```

### 5. Runtime Dumps in Behavior Implementation

**Symptom:** Short dump during RAP operation.

**Diagnosis:**
```
ListDumps(user: "<username>", date_from: "<today>")
GetDump(dump_id: "<id>")
```

**Common dump types:**

**CX_SY_ITAB_LINE_NOT_FOUND:**
```
" Cause: Accessing table line that doesn't exist
" Common in: mapped-travel[ 1 ] when create failed
" Fix: Always check if table is not empty before accessing
IF mapped-travel IS NOT INITIAL.
  DATA(travel_key) = mapped-travel[ 1 ]-%tky.
ENDIF.
```

**CX_SY_REF_IS_INITIAL:**
```
" Cause: Calling method on uninitialized reference
" Fix: Check reference before use
IF result IS BOUND.
  result->some_method( ).
ENDIF.
```

**CX_RAP_BO_UNKNOWN:**
```
" Cause: BO name in EML doesn't match any active BDEF
" Fix: Ensure BDEF is activated and name is correct
MODIFY ENTITIES OF zr_travel ...  " Must match BDEF root entity
```

### 6. Mapping / Persistence Issues

**Symptom:** Data not saving to database, or wrong data saved.

**Diagnosis:**
```
GetSource(name: "ZR_TRAVEL", object_type: "BDEF")
" Check the mapping section
GetTable(table_name: "ZTRAVEL")
" Compare table fields with mapping
```

**Common causes:**

**Missing mapping entries:**
```
" Every CDS field that differs from the DB field name needs mapping
mapping for ztravel corresponding
{
  TravelUUID = travel_uuid;  " If CDS alias ≠ DB field name, map it
}
" 'corresponding' means: fields with matching names map automatically
" Only explicitly map fields whose names differ
```

**Persistent table not specified:**
```
" For managed BOs, you must specify:
persistent table ztravel
" Without this, the framework doesn't know where to save
```

### 7. Draft Handling Issues

**Symptom:** Draft not working, exclusive lock errors, draft data lost.

**Common causes:**

**Missing `total etag`:**
```
" Required for draft-enabled BOs on the lock master entity:
lock master total etag LastChangedAt
" The total etag field must have @Semantics.systemDateTime.lastChangedAt: true
```

**Missing `with draft` on associations:**
```
" In the parent entity, child associations need:
association _Booking { create; with draft; }
" Without 'with draft', child drafts won't work
```

**Draft table structure mismatch:**
```
" Draft table must match CDS entity field names (aliases, not DB names)
" Plus it needs the admin include
" Check with: GetTable(table_name: "ZTRAVEL_D")
```

### 8. Authorization Issues

**Symptom:** Operations rejected with authorization error.

**Quick fix for development:**
```
" In BDEF, temporarily use:
authorization master ( none )

" Or in the handler, allow everything:
METHOD get_global_authorizations.
  result = VALUE #( %create = if_abap_behv=>auth-allowed
                    %update = if_abap_behv=>auth-allowed
                    %delete = if_abap_behv=>auth-allowed ).
ENDMETHOD.
```

### 9. OData / Fiori Preview Issues

**Symptom:** Fiori preview shows errors or missing data.

**Common causes:**
- Service binding not created or not published → create in ADT
- Service definition doesn't expose all needed entities
- Missing UI annotations → add `@UI` annotations or metadata extension
- Missing value help → add `@Consumption.valueHelpDefinition`

**Diagnosis:**
```
GetSource(name: "ZUI_TRAVEL", object_type: "SRVD")
" Check which entities are exposed
```

### 10. Performance Issues

**Diagnosis workflow:**
```
" Check SQL traces
GetSQLTraceState()
ListSQLTraces(user: "<username>")

" Check runtime traces
ListTraces(user: "<username>")
GetTrace(trace_id: "<id>")

" Analyze call graph
AnalyzeCallGraph(object_uri: "/sap/bc/adt/oo/classes/ZBP_R_TRAVEL/source/main")
```

**Common performance issues:**
- N+1 reads in handler methods → use batch reads with `CORRESPONDING #( keys )`
- Missing database indexes on frequently filtered fields
- Expensive logic in `on modify` determinations → consider `on save` instead

## Interactive Debugging

For complex issues, use the VSP debugger:

```
" 1. Set a breakpoint
SetBreakpoint(
  program: "ZBP_R_TRAVEL",
  method: "validateDates",
  line: 5,
  kind: "line"
)

" 2. Trigger the operation (via Fiori app or EML test)

" 3. Listen for the debuggee
DebuggerListen(timeout: 120)

" 4. Attach when hit
DebuggerAttach(debuggee_id: "<from listen result>")

" 5. Inspect variables
DebuggerGetVariables(variable_ids: ["@ROOT"])

" 6. Step through
DebuggerStep(step_type: "stepOver")

" 7. Detach when done
DebuggerDetach()
```

## ATC Quality Checks

Run ATC checks to find potential issues proactively:

```
RunATCCheck(object_url: "/sap/bc/adt/oo/classes/ZBP_R_TRAVEL")
RunATCCheck(object_url: "/sap/bc/adt/bdef/zr_travel")
```

Common ATC findings for RAP:
- Missing authorization checks
- Unused handler methods
- Hard-coded values instead of message classes
- Missing error handling in EML operations
