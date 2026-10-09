---
name: rap-generator
description: "Generate a complete SAP RAP Business Object stack from requirements. Creates all artifacts: database table, CDS root/child view entities, behavior definition (BDEF), behavior implementation class, service definition, and service binding. Use this skill whenever the user wants to create a new RAP BO, build a Fiori app backend, scaffold a RAP service, create an OData service with ABAP Cloud, or says things like 'create a RAP BO for...', 'build a managed BO', 'I need a new business object for...', 'scaffold a RAP service', or 'generate CRUD for...'. This skill targets managed implementation on ABAP Cloud/BTP with strict(2) mode."
---

# RAP Business Object Generator

This skill generates a complete managed RAP Business Object stack on ABAP Cloud/BTP. It uses the VSP MCP tools (WriteSource, CreateTable, Activate, etc.) to create all artifacts directly in the SAP system.

## When to Use

Use this skill when the user wants to create a new RAP BO from scratch. The user might describe a business entity (e.g., "travel booking", "purchase order", "product catalog") and you need to generate the full stack.

## Prerequisites

Before starting, verify connectivity:
1. Call `GetConnectionInfo` to confirm system connection
2. Call `GetFeatures` to check available capabilities
3. Confirm the target package exists (use `GetPackage`) or create one with `CreatePackage`

## Workflow Overview

The generation follows the RAP layered architecture, bottom-up:

1. **Database Tables** → persistent storage
2. **CDS View Entities** → data model (root + children)
3. **Behavior Definition** → transactional behavior (BDEF)
4. **Service Definition** → expose entities
5. **Service Binding** → bind to OData protocol (manual step in ADT)

## Step 1: Gather Requirements

Ask the user for:
- **Business object name** (e.g., "Travel", "Product", "SalesOrder")
- **Namespace or prefix** — This is CRITICAL. Confirm whether the user is using:
  - A **registered SAP namespace** (e.g., `/ABU/`, `/DMO/`, `/MYCORP/`) — enclosed in forward slashes
  - A **customer prefix** (e.g., `Z`, `Y`, `ZMY_`)
  - If not specified, **always ask before generating** — never assume `Z` prefix
- **Entity structure**: What fields does each entity need? Root entity and any child entities?
- **Key strategy**: UUID-based (recommended for managed) or external numbering?
- **Package**: Target package name (e.g., `$TMP` for local, or transportable)
- **Draft support**: Does the user want draft-enabled BO for Fiori?

If the user is unsure, suggest sensible defaults:
- UUID key with managed numbering
- Draft-enabled for Fiori consumption
- Admin fields (created_by, created_at, last_changed_by, last_changed_at, last_changed_at global)

## Step 2: Determine Namespace & Naming Conventions

### IMPORTANT: Namespace vs. Prefix Detection

Before generating ANY artifact names, you MUST confirm the namespace/prefix with the user. There are two distinct patterns:

**Pattern A — Registered SAP Namespace** (e.g., `/ABU/`, `/DMO/`, `/MYCORP/`):
- Namespaces are enclosed in forward slashes: `/NAMESPACE/`
- ALL artifact names start with the namespace
- The namespace replaces the `Z` prefix entirely
- Example: `/ABU/TRAVEL`, `/ABU/R_TRAVEL`, `/ABU/BP_R_TRAVEL`

**Pattern B — Customer Prefix** (e.g., `Z`, `Y`, `ZMY_`):
- Standard Z/Y prefix for customer objects
- Example: `ZTRAVEL`, `ZR_TRAVEL`, `ZBP_R_TRAVEL`

### Naming Table — With Namespace

| Artifact | Namespace Pattern | Namespace Example (`/ABU/`) | Z-Prefix Example |
|----------|-------------------|----------------------------|------------------|
| Database table | `/NS/[ENTITY]` | `/ABU/TRAVEL` | `ZTRAVEL` |
| Draft table | `/NS/D_[ENTITY]` | `/ABU/D_TRAVEL` | `ZTRAVEL_D` |
| CDS root view entity | `/NS/R_[ENTITY]` | `/ABU/R_TRAVEL` | `ZR_TRAVEL` |
| CDS child view entity | `/NS/R_[CHILD]` | `/ABU/R_BOOKING` | `ZR_BOOKING` |
| CDS projection (root) | `/NS/C_[ENTITY]` | `/ABU/C_TRAVEL` | `ZC_TRAVEL` |
| CDS projection (child) | `/NS/C_[CHILD]` | `/ABU/C_BOOKING` | `ZC_BOOKING` |
| Behavior definition | Same as root CDS | `/ABU/R_TRAVEL` | `ZR_TRAVEL` |
| Behavior pool | `/NS/BP_R_[ENTITY]` | `/ABU/BP_R_TRAVEL` | `ZBP_R_TRAVEL` |
| Service definition | `/NS/UI_[ENTITY]` | `/ABU/UI_TRAVEL` | `ZUI_TRAVEL` |
| Service binding | `/NS/UI_[ENTITY]_O4` | `/ABU/UI_TRAVEL_O4` | `ZUI_TRAVEL_O4` |
| Metadata extension | `/NS/C_[ENTITY]` | `/ABU/C_TRAVEL` | `ZC_TRAVEL` |

The `_R_` prefix denotes the interface/root layer, `_C_` the consumption/projection layer.

### Namespace Considerations

- **Max name length**: ABAP object names have a 30-character limit. Namespaces consume characters (e.g., `/ABU/` = 5 chars), so keep entity names shorter when using namespaces.
- **Package alignment**: Namespace objects typically belong to packages within the same namespace (e.g., `/ABU/TRAVEL_PKG`). Verify the target package exists and is within the namespace.
- **Transport layer**: Namespace objects are always transportable — they cannot go in `$TMP`. Ensure the user has a transport request ready.
- **Escaping in source**: In CDS and ABAP source code, namespace object names must be used exactly as-is with the slashes (e.g., `define root view entity /ABU/R_TRAVEL`). No escaping is needed in DDL or ABAP.
- **Behavior pool reference**: In the BDEF, the class reference uses the full namespace: `managed implementation in class /ABU/BP_R_TRAVEL unique;`

## Step 3: Create Database Tables

Use `CreateTable` for each entity. Always include admin fields for managed BOs.

> **Namespace note**: Replace `ZTRAVEL` with `/ABU/TRAVEL` (or your namespace equivalent) throughout all examples below if using a registered namespace. Namespace tables **cannot** use `$TMP` — provide a transportable package and transport request.

**Root entity table pattern (Z-prefix):**
```json
{
  "name": "ZTRAVEL",
  "description": "Travel root entity",
  "fields": "[
    {\"name\": \"TRAVEL_UUID\", \"type\": \"UUID\", \"key\": true},
    {\"name\": \"TRAVEL_ID\", \"type\": \"NUMC8\"},
    {\"name\": \"AGENCY_ID\", \"type\": \"NUMC6\"},
    {\"name\": \"CUSTOMER_ID\", \"type\": \"NUMC6\"},
    {\"name\": \"BEGIN_DATE\", \"type\": \"DATS\"},
    {\"name\": \"END_DATE\", \"type\": \"DATS\"},
    {\"name\": \"TOTAL_PRICE\", \"type\": \"DEC16_2\"},
    {\"name\": \"CURRENCY_CODE\", \"type\": \"CHAR3\"},
    {\"name\": \"DESCRIPTION\", \"type\": \"CHAR256\"},
    {\"name\": \"OVERALL_STATUS\", \"type\": \"CHAR1\"},
    {\"name\": \"CREATED_BY\", \"type\": \"CHAR12\"},
    {\"name\": \"CREATED_AT\", \"type\": \"TIMESTAMPL\"},
    {\"name\": \"LOCAL_LAST_CHANGED_BY\", \"type\": \"CHAR12\"},
    {\"name\": \"LOCAL_LAST_CHANGED_AT\", \"type\": \"TIMESTAMPL\"},
    {\"name\": \"LAST_CHANGED_AT\", \"type\": \"TIMESTAMPL\"}
  ]",
  "package": "$TMP"
}
```

**Root entity table pattern (namespace — e.g., `/ABU/`):**
```json
{
  "name": "/ABU/TRAVEL",
  "description": "Travel root entity",
  "fields": "[
    {\"name\": \"TRAVEL_UUID\", \"type\": \"UUID\", \"key\": true},
    ...same fields as above...
  ]",
  "package": "/ABU/TRAVEL_PKG",
  "transport": "A4HK900123"
}
```

**Child entity table pattern:**
Include the parent UUID as a foreign key field:
```json
{
  "name": "ZBOOKING",
  "description": "Booking child entity",
  "fields": "[
    {\"name\": \"BOOKING_UUID\", \"type\": \"UUID\", \"key\": true},
    {\"name\": \"TRAVEL_UUID\", \"type\": \"UUID\"},
    {\"name\": \"BOOKING_ID\", \"type\": \"NUMC4\"},
    ...admin fields...
  ]"
}
```

**Draft table:**
For draft-enabled BOs, create a draft table mirroring the main table plus the draft admin include. The draft table must have the same fields as the CDS entity (using alias names), plus a `DRAFTUUID` field for late numbering scenarios. Since `CreateTable` doesn't support the draft admin include directly, note that the draft table will be created by the framework when you activate the BDEF with `with draft` — so you can skip manual draft table creation if using managed numbering.

## Step 4: Create CDS View Entities

Use `WriteSource` with `object_type: "DDLS"` to create CDS views.

**Root CDS view entity pattern:**

> Substitute all `Z`-prefixed names with namespace equivalents when applicable.
> For namespace `/ABU/`: `ZR_TRAVEL` → `/ABU/R_TRAVEL`, `ztravel` → `/abu/travel`, `ZR_BOOKING` → `/ABU/R_BOOKING`

```
@AccessControl.authorizationCheck: #NOT_REQUIRED
@EndUserText.label: 'Travel - Root Entity'
define root view entity ZR_TRAVEL
  as select from ztravel
  composition [0..*] of ZR_BOOKING as _Booking
{
  key travel_uuid          as TravelUUID,
      travel_id            as TravelID,
      agency_id            as AgencyID,
      customer_id          as CustomerID,
      begin_date           as BeginDate,
      end_date             as EndDate,
      @Semantics.amount.currencyCode: 'CurrencyCode'
      total_price          as TotalPrice,
      currency_code        as CurrencyCode,
      description          as Description,
      overall_status       as OverallStatus,

      @Semantics.user.createdBy: true
      created_by           as CreatedBy,
      @Semantics.systemDateTime.createdAt: true
      created_at           as CreatedAt,
      @Semantics.user.localInstanceLastChangedBy: true
      local_last_changed_by as LocalLastChangedBy,
      @Semantics.systemDateTime.localInstanceLastChangedAt: true
      local_last_changed_at as LocalLastChangedAt,
      @Semantics.systemDateTime.lastChangedAt: true
      last_changed_at      as LastChangedAt,

      _Booking
}
```

**Child CDS view entity pattern:**
```
@AccessControl.authorizationCheck: #NOT_REQUIRED
@EndUserText.label: 'Booking - Child Entity'
define view entity ZR_BOOKING
  as select from zbooking
  association to parent ZR_TRAVEL as _Travel
    on $projection.TravelUUID = _Travel.TravelUUID
{
  key booking_uuid         as BookingUUID,
      travel_uuid          as TravelUUID,
      booking_id           as BookingID,
      ...fields...,

      @Semantics.user.createdBy: true
      created_by           as CreatedBy,
      @Semantics.systemDateTime.createdAt: true
      created_at           as CreatedAt,
      @Semantics.user.localInstanceLastChangedBy: true
      local_last_changed_by as LocalLastChangedBy,
      @Semantics.systemDateTime.localInstanceLastChangedAt: true
      local_last_changed_at as LocalLastChangedAt,
      @Semantics.systemDateTime.lastChangedAt: true
      last_changed_at      as LastChangedAt,

      _Travel
}
```

**Key annotations to include:**
- `@Semantics.user.createdBy` / `localInstanceLastChangedBy` — for admin field automation
- `@Semantics.systemDateTime.createdAt` / `localInstanceLastChangedAt` / `lastChangedAt` — timestamps
- `@Semantics.amount.currencyCode` — for currency fields
- `@Semantics.quantity.unitOfMeasure` — for quantity fields

## Step 5: Create Behavior Definition

Use `WriteSource` with `object_type: "BDEF"`.

**Managed BDEF pattern (with draft):**

> For namespace `/ABU/`: use `managed implementation in class /ABU/BP_R_TRAVEL unique;`,
> `define behavior for /ABU/R_TRAVEL alias Travel`, `persistent table /abu/travel`,
> `draft table /abu/d_travel`, etc.

```
managed implementation in class ZBP_R_TRAVEL unique;
strict ( 2 );
with draft;

define behavior for ZR_TRAVEL alias Travel
persistent table ztravel
draft table ztravel_d
etag master LocalLastChangedAt
lock master total etag LastChangedAt
authorization master ( global )

{
  field ( readonly )
    TravelUUID,
    CreatedBy,
    CreatedAt,
    LocalLastChangedBy,
    LocalLastChangedAt,
    LastChangedAt;

  field ( numbering : managed )
    TravelUUID;

  field ( mandatory )
    AgencyID,
    CustomerID,
    BeginDate,
    EndDate;

  create;
  update;
  delete;

  draft action Resume;
  draft action Edit;
  draft action Activate optimized;
  draft action Discard;
  draft determine action Prepare;

  association _Booking { create; with draft; }

  mapping for ztravel corresponding
  {
    TravelUUID = travel_uuid;
    TravelID = travel_id;
    AgencyID = agency_id;
    CustomerID = customer_id;
    BeginDate = begin_date;
    EndDate = end_date;
    TotalPrice = total_price;
    CurrencyCode = currency_code;
    Description = description;
    OverallStatus = overall_status;
    CreatedBy = created_by;
    CreatedAt = created_at;
    LocalLastChangedBy = local_last_changed_by;
    LocalLastChangedAt = local_last_changed_at;
    LastChangedAt = last_changed_at;
  }
}

define behavior for ZR_BOOKING alias Booking
persistent table zbooking
draft table zbooking_d
etag master LocalLastChangedAt
lock dependent by _Travel
authorization dependent by _Travel

{
  field ( readonly )
    BookingUUID,
    TravelUUID;

  field ( numbering : managed )
    BookingUUID;

  update;
  delete;

  determination setBookingID on modify { create; }

  mapping for zbooking corresponding
  {
    BookingUUID = booking_uuid;
    TravelUUID = travel_uuid;
    BookingID = booking_id;
    ...
  }
}
```

**Key BDEF decisions:**
- Use `strict ( 2 )` — latest strict mode for ABAP Cloud
- Use `numbering : managed` for UUID keys — framework handles key generation
- Root entity gets `lock master`, children get `lock dependent by _Parent`
- Root entity gets `etag master` + `total etag`, children get `etag master` only
- For draft: add `with draft;` in header and `draft table` + draft actions on root
- Child entities need `with draft;` on the association in the parent
- `mapping for ... corresponding` maps CDS aliases to DB table field names

## Step 6: Create Behavior Implementation

Use `WriteSource` with `object_type: "CLAS"` to create the behavior pool class.

For a basic managed BO with no custom logic, the class can be minimal:

> For namespace `/ABU/`: class name becomes `/abu/bp_r_travel`, BDEF reference becomes `/abu/r_travel`.
> Note: ABAP class names in source code are case-insensitive but conventionally lowercase for namespace objects.

**Z-prefix:**
```abap
CLASS zbp_r_travel DEFINITION PUBLIC ABSTRACT FINAL
  FOR BEHAVIOR OF zr_travel.
ENDCLASS.

CLASS zbp_r_travel IMPLEMENTATION.
ENDCLASS.
```

**Namespace (`/ABU/`):**
```abap
CLASS /abu/bp_r_travel DEFINITION PUBLIC ABSTRACT FINAL
  FOR BEHAVIOR OF /abu/r_travel.
ENDCLASS.

CLASS /abu/bp_r_travel IMPLEMENTATION.
ENDCLASS.
```

The local handler class goes in the CCIMP include (use `WriteSource` with `include: "implementations"`).
The local handler class is the same regardless of namespace — it references the BDEF alias, not the full object name:
```abap
CLASS lhc_Travel DEFINITION INHERITING FROM cl_abap_behavior_handler.
  PRIVATE SECTION.
    METHODS get_global_authorizations FOR GLOBAL AUTHORIZATION
      IMPORTING REQUEST requested_authorizations FOR Travel RESULT result.
ENDCLASS.

CLASS lhc_Travel IMPLEMENTATION.
  METHOD get_global_authorizations.
    " Placeholder - implement authorization checks
    result = VALUE #( %create = if_abap_behv=>auth-allowed
                      %update = if_abap_behv=>auth-allowed
                      %delete = if_abap_behv=>auth-allowed ).
  ENDMETHOD.
ENDCLASS.
```

## Step 7: Create Service Definition

Use `WriteSource` with `object_type: "SRVD"`.

**Z-prefix:**
```
@EndUserText.label: 'Travel Service Definition'
define service ZUI_TRAVEL {
  expose ZR_TRAVEL as Travel;
  expose ZR_BOOKING as Booking;
}
```

**Namespace (`/ABU/`):**
```
@EndUserText.label: 'Travel Service Definition'
define service /ABU/UI_TRAVEL {
  expose /ABU/R_TRAVEL as Travel;
  expose /ABU/R_BOOKING as Booking;
}
```

If using projections, expose the projection views (e.g., `ZC_TRAVEL` / `/ABU/C_TRAVEL`) instead.

## Step 8: Activate All

Use `ActivatePackage` to activate all objects in the target package, or activate individually in dependency order:
1. Database tables
2. CDS view entities (parent before child if there are forward references)
3. Behavior definition
4. Behavior implementation class
5. Service definition

## Step 9: Verify

After activation:
1. Use `SyntaxCheck` on each artifact to confirm no errors
2. Use `RunATCCheck` on the behavior class for quality
3. Inform the user that the **Service Binding** must be created manually in ADT (or Eclipse) since there is no ADT REST API for creating service bindings programmatically
4. Provide the user with a summary of all created objects

## Post-Generation Guidance

After generating the base stack, suggest next steps:
- Add **validations** and **determinations** (see `rap-behavior` skill)
- Add **CDS annotations** for Fiori UI (see `rap-cds` skill)
- Create **ABAP Unit tests** (see `rap-testing` skill)
- Create the **Service Binding** in ADT and preview the Fiori app
- Add **CDS metadata extensions** for UI layout

## Error Handling

Common issues during generation:
- **Name conflicts**: Use `SearchObject` to check if names already exist
- **Activation errors**: Check syntax with `SyntaxCheck` before activating
- **Missing packages**: Create with `CreatePackage` first
- **Field type mismatches**: Ensure CDS aliases match BDEF mapping entries
- **Draft table issues**: Let the framework generate draft tables when possible
