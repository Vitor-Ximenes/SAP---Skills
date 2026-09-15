---
name: rap-cds
description: "CDS view entity development for SAP RAP on ABAP Cloud/BTP. Covers creating and modifying CDS root view entities, child view entities, projection views, and metadata extensions with proper annotations for Fiori UI, search help, value help, and OData. Use this skill whenever the user wants to create or modify CDS views, add annotations, build data models, define associations/compositions, create projection views, or add metadata extensions. Trigger on: 'CDS view', 'annotations', 'data model', 'view entity', 'projection', 'metadata extension', '@UI', '@Search', 'value help', 'composition', 'association'."
---

# RAP CDS View Entity Development

This skill helps create and modify CDS view entities for RAP Business Objects on ABAP Cloud/BTP, with proper annotations for Fiori Elements UI rendering.

## Tools Used

- `GetSource` (object_type: "DDLS") — read existing CDS views
- `WriteSource` (object_type: "DDLS") — create/update CDS views
- `EditSource` — surgical edits to existing CDS views
- `Activate` — activate CDS objects
- `SyntaxCheck` — validate before activation
- `GetCDSDependencies` — understand CDS dependency trees
- `SearchObject` — find existing CDS entities

## CDS View Entity Types in RAP

RAP uses a layered CDS architecture:

### 1. Interface View Entities (R-layer)
The core data model, prefix `_R_` or `_I_`. These define the business object structure.

```sql
define root view entity ZR_TRAVEL
  as select from ztravel
  composition [0..*] of ZR_BOOKING as _Booking
{ ... }
```

### 2. Projection View Entities (C-layer)
Service-specific projections, prefix `_C_`. These adapt the interface layer for specific consumers.

```sql
define root view entity ZC_TRAVEL
  provider contract transactional_query
  as projection on ZR_TRAVEL
{ ... }
```

### 3. CDS Metadata Extensions
Separate annotation files that decouple UI metadata from the core data model.

```sql
@Metadata.layer: #CONSUMER
annotate view ZC_TRAVEL with
{ ... }
```

## Composition and Association Patterns

### Root to Child (Composition)
```sql
// In root entity
composition [0..*] of ZR_BOOKING as _Booking

// In child entity
association to parent ZR_TRAVEL as _Travel
  on $projection.TravelUUID = _Travel.TravelUUID
```

### Child to Grandchild
```sql
// In child entity
composition [0..*] of ZR_BOOKING_SUPPLEMENT as _BookingSupplement

// In grandchild entity
association to parent ZR_BOOKING as _Booking
  on $projection.BookingUUID = _Booking.BookingUUID
```

### Cross-BO Associations
```sql
association [0..1] to I_Currency as _Currency
  on $projection.CurrencyCode = _Currency.Currency
```

### Cardinality Options
- `[0..1]` — zero or one (optional to-one)
- `[1..1]` or `[1]` — exactly one (mandatory to-one)
- `[0..*]` or `[*]` — zero or many (to-many)
- `[1..*]` — one or many

## Essential Annotations

### Semantic Annotations (Interface Layer)

These annotations enable the RAP framework to handle admin fields automatically in managed BOs:

```sql
@Semantics.user.createdBy: true
created_by as CreatedBy,

@Semantics.systemDateTime.createdAt: true
created_at as CreatedAt,

@Semantics.user.localInstanceLastChangedBy: true
local_last_changed_by as LocalLastChangedBy,

@Semantics.systemDateTime.localInstanceLastChangedAt: true
local_last_changed_at as LocalLastChangedAt,

@Semantics.systemDateTime.lastChangedAt: true
last_changed_at as LastChangedAt,
```

### Amount and Quantity
```sql
@Semantics.amount.currencyCode: 'CurrencyCode'
total_price as TotalPrice,

@Semantics.quantity.unitOfMeasure: 'QuantityUnit'
quantity as Quantity,
```

### UI Annotations (Projection/Metadata Extension Layer)

**Header info:**
```sql
@UI.headerInfo: {
  typeName: 'Travel',
  typeNamePlural: 'Travels',
  title: { type: #STANDARD, value: 'TravelID' },
  description: { type: #STANDARD, value: 'Description' }
}
```

**List report and object page field positioning:**
```sql
@UI.lineItem: [{ position: 10 }]
@UI.identification: [{ position: 10 }]
@UI.selectionField: [{ position: 10 }]
TravelID,

@UI.lineItem: [{ position: 20, importance: #HIGH }]
@UI.identification: [{ position: 20 }]
AgencyID,

@UI.lineItem: [{ position: 30, criticality: 'OverallStatusCriticality' }]
OverallStatus,
```

**Facets for object page layout:**
```sql
@UI.facet: [
  { id: 'GeneralInfo',
    type: #COLLECTION,
    label: 'General Information',
    position: 10 },
  { id: 'TravelData',
    parentId: 'GeneralInfo',
    type: #FIELDGROUP_REFERENCE,
    targetQualifier: 'TravelData',
    position: 10 },
  { id: 'BookingTable',
    type: #LINEITEM_REFERENCE,
    label: 'Bookings',
    position: 20,
    targetElement: '_Booking' }
]
```

**Field groups:**
```sql
@UI.fieldGroup: [{ qualifier: 'TravelData', position: 10 }]
AgencyID,

@UI.fieldGroup: [{ qualifier: 'TravelData', position: 20 }]
CustomerID,
```

### Search Annotations
```sql
@Search.searchable: true

// On specific fields:
@Search.defaultSearchElement: true
@Search.fuzzinessThreshold: 0.7
Description,
```

### Value Help Annotations
```sql
@Consumption.valueHelpDefinition: [{
  entity: { name: 'I_Currency', element: 'Currency' }
}]
CurrencyCode,

@Consumption.valueHelpDefinition: [{
  entity: { name: '/DMO/I_Agency', element: 'AgencyID' },
  additionalBinding: [{ localElement: 'AgencyName', element: 'Name' }]
}]
AgencyID,
```

## Projection View Pattern

```sql
@AccessControl.authorizationCheck: #NOT_REQUIRED
@EndUserText.label: 'Travel - Projection'
@Search.searchable: true
@Metadata.allowExtensions: true

define root view entity ZC_TRAVEL
  provider contract transactional_query
  as projection on ZR_TRAVEL
{
  key TravelUUID,
      @Search.defaultSearchElement: true
      TravelID,

      @Search.defaultSearchElement: true
      @ObjectModel.text.element: ['AgencyName']
      @Consumption.valueHelpDefinition: [{ entity: { name: '/DMO/I_Agency', element: 'AgencyID' } }]
      AgencyID,
      _Agency.Name as AgencyName,

      @Consumption.valueHelpDefinition: [{ entity: { name: '/DMO/I_Customer', element: 'CustomerID' } }]
      CustomerID,

      BeginDate,
      EndDate,

      @Semantics.amount.currencyCode: 'CurrencyCode'
      TotalPrice,

      @Consumption.valueHelpDefinition: [{ entity: { name: 'I_Currency', element: 'Currency' } }]
      CurrencyCode,

      Description,
      OverallStatus,

      CreatedBy,
      CreatedAt,
      LocalLastChangedBy,
      LocalLastChangedAt,
      LastChangedAt,

      /* Associations */
      _Booking : redirected to composition child ZC_BOOKING,
      _Agency,
      _Currency
}
```

Key points for projections:
- Use `provider contract transactional_query` for RAP projections
- Use `redirected to composition child` for composition associations
- Use `redirected to parent` for parent associations in child projections
- Add `@Metadata.allowExtensions: true` to enable metadata extensions

## Metadata Extension Pattern

```sql
@Metadata.layer: #CONSUMER
@UI.headerInfo: {
  typeName: 'Travel',
  typeNamePlural: 'Travels',
  title: { type: #STANDARD, value: 'TravelID' },
  description: { type: #STANDARD, value: 'Description' }
}

annotate view ZC_TRAVEL with
{
  @UI.facet: [
    { id: 'GeneralInfo', type: #COLLECTION, label: 'General Information', position: 10 },
    { id: 'TravelDetails', parentId: 'GeneralInfo', type: #FIELDGROUP_REFERENCE,
      targetQualifier: 'TravelDetails', position: 10 },
    { id: 'Dates', parentId: 'GeneralInfo', type: #FIELDGROUP_REFERENCE,
      targetQualifier: 'Dates', position: 20 },
    { id: 'Bookings', type: #LINEITEM_REFERENCE, label: 'Bookings',
      position: 20, targetElement: '_Booking' }
  ]

  @UI.lineItem: [{ position: 10 }]
  @UI.selectionField: [{ position: 10 }]
  @UI.identification: [{ position: 10 }]
  TravelID;

  @UI.lineItem: [{ position: 20, importance: #HIGH }]
  @UI.fieldGroup: [{ qualifier: 'TravelDetails', position: 10 }]
  AgencyID;

  @UI.lineItem: [{ position: 30, importance: #HIGH }]
  @UI.fieldGroup: [{ qualifier: 'TravelDetails', position: 20 }]
  CustomerID;

  @UI.fieldGroup: [{ qualifier: 'Dates', position: 10 }]
  BeginDate;

  @UI.fieldGroup: [{ qualifier: 'Dates', position: 20 }]
  EndDate;

  @UI.lineItem: [{ position: 40 }]
  TotalPrice;

  @UI.lineItem: [{ position: 50, criticality: 'StatusCriticality' }]
  @UI.selectionField: [{ position: 20 }]
  OverallStatus;

  @UI.hidden: true
  TravelUUID;

  @UI.hidden: true
  CreatedBy;

  @UI.hidden: true
  CreatedAt;

  @UI.hidden: true
  LocalLastChangedBy;

  @UI.hidden: true
  LocalLastChangedAt;

  @UI.hidden: true
  LastChangedAt;
}
```

## Common Tasks

### Adding a new field to an existing CDS view
1. `GetSource` to read the current CDS DDL
2. `EditSource` to add the field in the select list
3. Update the BDEF mapping if needed (`EditSource` on BDEF)
4. `Activate` the CDS view, then the BDEF

### Adding an association
1. Add the association definition after `as select from`
2. Expose the association in the select list
3. For projections, use `redirected to` syntax

### Checking CDS dependencies
Use `GetCDSDependencies` to understand what tables/views a CDS entity reads from.

## Validation Checklist

Before activating a CDS view, verify:
- All field aliases use CamelCase (ABAP Cloud convention)
- Semantic annotations are on the correct fields
- Compositions use `composition [cardinality] of ChildEntity`
- Parent associations use `association to parent ParentEntity`
- Currency/quantity reference fields point to exposed fields
- `@AccessControl.authorizationCheck` is specified (use `#NOT_REQUIRED` as default)
