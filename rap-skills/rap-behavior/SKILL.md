---
name: rap-behavior
description: "Implement and enhance RAP behavior definitions and behavior implementations on ABAP Cloud/BTP. Covers adding validations, determinations, actions (instance/static/factory), draft handling, authorization, side effects, business events, and feature control to managed RAP BOs. Use this skill whenever the user wants to add business logic to a RAP BO, implement validations, create actions, add draft support, implement authorization checks, add determinations, or enhance behavior. Trigger on: 'validation', 'determination', 'action', 'draft', 'authorization', 'side effect', 'business event', 'BDEF', 'behavior definition', 'behavior implementation', 'handler method', 'saver class', 'EML'."
---

# RAP Behavior Definition & Implementation

This skill helps you add business logic to managed RAP BOs on ABAP Cloud/BTP through behavior definitions (BDEF) and their implementations in ABAP Behavior Pools (ABP).

## Tools Used

- `GetSource` (object_type: "BDEF") — read behavior definitions
- `GetSource` (object_type: "CLAS") — read behavior implementation classes
- `WriteSource` / `EditSource` — modify BDEF and class source
- `SyntaxCheck` — validate changes
- `Activate` — activate objects
- `RunUnitTests` — verify behavior

## Architecture Overview

RAP separates behavior declaration from implementation:

1. **BDEF** (BDL syntax) — declares WHAT operations/features exist
2. **ABP class** (ABAP) — implements HOW they work
   - **Handler class** (CCIMP include / Local Types) — handles individual operations
   - **Saver class** (CCIMP include) — handles save sequence

## Adding Validations

Validations check data consistency before saving. They reject invalid instances with error messages.

### Step 1: Declare in BDEF

Add inside the entity behavior body `{ ... }`:

```
validation validateDates on save { create; field BeginDate, EndDate; }
validation validateStatus on save { create; update; }
```

The trigger conditions specify WHEN the validation runs:
- `create` — on new instance creation
- `update` — only valid together with `create` for `on save`
- `delete` — on deletion
- `field FieldA, FieldB` — when these fields change

### Step 2: Implement in Handler Class

In the ABP's Local Types (CCIMP include):

```abap
CLASS lhc_Travel DEFINITION INHERITING FROM cl_abap_behavior_handler.
  PRIVATE SECTION.
    METHODS validateDates FOR VALIDATE ON SAVE
      IMPORTING keys FOR Travel~validateDates.
    METHODS validateStatus FOR VALIDATE ON SAVE
      IMPORTING keys FOR Travel~validateStatus.
ENDCLASS.

CLASS lhc_Travel IMPLEMENTATION.
  METHOD validateDates.
    " Read the relevant instances
    READ ENTITIES OF ZR_Travel IN LOCAL MODE
      ENTITY Travel
      FIELDS ( BeginDate EndDate )
      WITH CORRESPONDING #( keys )
      RESULT DATA(travels)
      FAILED DATA(read_failed).

    LOOP AT travels INTO DATA(travel).
      " Check: begin date must be before end date
      IF travel-BeginDate >= travel-EndDate.
        APPEND VALUE #( %tky = travel-%tky ) TO failed-travel.
        APPEND VALUE #( %tky = travel-%tky
                        %msg = new_message_with_text(
                          severity = if_abap_behv_message=>severity-error
                          text     = 'Begin date must be before end date' )
                        %element-BeginDate = if_abap_behv=>mk-on
                        %element-EndDate   = if_abap_behv=>mk-on
        ) TO reported-travel.
      ENDIF.

      " Check: begin date must be in the future
      IF travel-BeginDate < cl_abap_context_info=>get_system_date( ).
        APPEND VALUE #( %tky = travel-%tky ) TO failed-travel.
        APPEND VALUE #( %tky = travel-%tky
                        %msg = new_message_with_text(
                          severity = if_abap_behv_message=>severity-error
                          text     = 'Begin date must be in the future' )
                        %element-BeginDate = if_abap_behv=>mk-on
        ) TO reported-travel.
      ENDIF.
    ENDLOOP.
  ENDMETHOD.

  METHOD validateStatus.
    READ ENTITIES OF ZR_Travel IN LOCAL MODE
      ENTITY Travel
      FIELDS ( OverallStatus )
      WITH CORRESPONDING #( keys )
      RESULT DATA(travels).

    LOOP AT travels INTO DATA(travel).
      IF travel-OverallStatus IS NOT INITIAL
        AND NOT travel-OverallStatus CA 'OAXR'. " Open, Accepted, Rejected
        APPEND VALUE #( %tky = travel-%tky ) TO failed-travel.
        APPEND VALUE #( %tky = travel-%tky
                        %msg = new_message_with_text(
                          severity = if_abap_behv_message=>severity-error
                          text     = 'Invalid status value' )
                        %element-OverallStatus = if_abap_behv=>mk-on
        ) TO reported-travel.
      ENDIF.
    ENDLOOP.
  ENDMETHOD.
ENDCLASS.
```

### Validation Best Practices
- Use `IN LOCAL MODE` for EML reads within the handler — bypasses authorization checks
- Always populate both `failed` and `reported` for rejected instances
- Use `%element-FieldName = if_abap_behv=>mk-on` to highlight the problematic field on the UI
- Keep validations focused on one concern each

## Adding Determinations

Determinations automatically compute/modify field values based on triggers.

### Step 1: Declare in BDEF

```
determination setTravelID on modify { create; }
determination calculateTotalPrice on modify { field BookingFee, FlightPrice; }
determination setStatusOpen on save { create; }
```

Two timing options:
- `on modify` — runs immediately when buffer changes (result available during transaction)
- `on save` — runs during save sequence

### Step 2: Implement

```abap
METHOD setTravelID.
  " Read max travel ID and increment
  READ ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    FIELDS ( TravelID )
    WITH CORRESPONDING #( keys )
    RESULT DATA(travels).

  " Find instances that need an ID
  DELETE travels WHERE TravelID IS NOT INITIAL.
  CHECK travels IS NOT INITIAL.

  " Get max existing ID
  SELECT MAX( travel_id ) FROM ztravel INTO @DATA(max_id).

  " Set IDs for new instances
  MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    UPDATE FIELDS ( TravelID )
    WITH VALUE #( FOR travel IN travels INDEX INTO idx
      ( %tky     = travel-%tky
        TravelID = max_id + idx ) )
    REPORTED DATA(update_reported).

  reported = CORRESPONDING #( DEEP update_reported ).
ENDMETHOD.
```

## Adding Actions

Actions are custom operations beyond standard CRUD.

### Instance Actions (bound to a specific instance)

**BDEF:**
```
action ( features : instance ) acceptTravel result [1] $self;
action ( features : instance ) rejectTravel result [1] $self;
action deductDiscount parameter ZA_DISCOUNT result [1] $self;
```

**Implementation:**
```abap
METHOD acceptTravel.
  MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    UPDATE FIELDS ( OverallStatus )
    WITH VALUE #( FOR key IN keys
      ( %tky          = key-%tky
        OverallStatus = 'A' ) ) " Accepted
    FAILED failed
    REPORTED reported.

  " Return the updated instance
  READ ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    ALL FIELDS
    WITH CORRESPONDING #( keys )
    RESULT DATA(travels).

  result = VALUE #( FOR travel IN travels
    ( %tky   = travel-%tky
      %param = travel ) ).
ENDMETHOD.
```

### Static Actions (not bound to an instance)

**BDEF:**
```
static action createFromTemplate parameter ZA_TEMPLATE result [1] $self;
```

### Factory Actions (create new instances)

**BDEF:**
```
factory action copyTravel [1];
```

**Implementation:**
```abap
METHOD copyTravel.
  READ ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    ALL FIELDS
    WITH CORRESPONDING #( keys )
    RESULT DATA(travels).

  LOOP AT travels INTO DATA(travel).
    " Create a copy with modified fields
    MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate
                      Description OverallStatus )
      WITH VALUE #( (
        %cid          = keys[ KEY entity %tky = travel-%tky ]-%cid_ref
        AgencyID      = travel-AgencyID
        CustomerID    = travel-CustomerID
        BeginDate     = cl_abap_context_info=>get_system_date( )
        EndDate       = cl_abap_context_info=>get_system_date( ) + 14
        Description   = |Copy of { travel-Description }|
        OverallStatus = 'O' ) )
      MAPPED mapped
      FAILED failed
      REPORTED reported.
  ENDLOOP.
ENDMETHOD.
```

### Dynamic Feature Control

Control which actions/operations are available based on instance state:

**BDEF:**
```
action ( features : instance ) acceptTravel result [1] $self;
action ( features : instance ) rejectTravel result [1] $self;
```

**Implementation:**
```abap
METHOD get_instance_features.
  READ ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    FIELDS ( OverallStatus )
    WITH CORRESPONDING #( keys )
    RESULT DATA(travels)
    FAILED failed.

  result = VALUE #( FOR travel IN travels
    ( %tky = travel-%tky
      " Disable accept/reject for already accepted/rejected travels
      %action-acceptTravel = COND #(
        WHEN travel-OverallStatus = 'A'
        THEN if_abap_behv=>fc-o-disabled
        ELSE if_abap_behv=>fc-o-enabled )
      %action-rejectTravel = COND #(
        WHEN travel-OverallStatus = 'X'
        THEN if_abap_behv=>fc-o-disabled
        ELSE if_abap_behv=>fc-o-enabled )
    ) ).
ENDMETHOD.
```

## Draft Handling

For Fiori apps, draft enables save-as-you-go editing.

### BDEF Additions

In the header:
```
managed implementation in class ZBP_R_TRAVEL unique;
strict ( 2 );
with draft;
```

On the root entity:
```
draft table ztravel_d
...
lock master total etag LastChangedAt
...
draft action Resume;
draft action Edit;
draft action Activate optimized;
draft action Discard;
draft determine action Prepare
{
  validation validateDates;
  validation validateStatus;
}
```

On child entities, add `with draft` to the association:
```
association _Booking { create; with draft; }
```

The draft determine action `Prepare` lists which validations/determinations to run when the user clicks "Save" (activating the draft). Only `on save` validations and determinations can be assigned.

## Authorization

### Global Authorization

**BDEF:**
```
authorization master ( global )
```

**Implementation:**
```abap
METHOD get_global_authorizations.
  " Check authorization object
  AUTHORITY-CHECK OBJECT 'ZTRAVEL'
    ID 'ACTVT' FIELD '01'. " Create

  DATA(is_authorized) = COND #(
    WHEN sy-subrc = 0
    THEN if_abap_behv=>auth-allowed
    ELSE if_abap_behv=>auth-unauthorized ).

  result = VALUE #(
    %create = is_authorized
    %update = is_authorized
    %delete = is_authorized ).
ENDMETHOD.
```

### Instance Authorization

**BDEF:**
```
authorization master ( global, instance )
```

**Implementation:**
```abap
METHOD get_instance_authorizations.
  READ ENTITIES OF ZR_Travel IN LOCAL MODE
    ENTITY Travel
    FIELDS ( OverallStatus )
    WITH CORRESPONDING #( keys )
    RESULT DATA(travels).

  LOOP AT travels INTO DATA(travel).
    " Example: only allow changes to open travels
    DATA(is_open) = COND #(
      WHEN travel-OverallStatus = 'O'
      THEN if_abap_behv=>auth-allowed
      ELSE if_abap_behv=>auth-unauthorized ).

    APPEND VALUE #(
      %tky    = travel-%tky
      %update = is_open
      %delete = is_open
    ) TO result.
  ENDLOOP.
ENDMETHOD.
```

## Side Effects

Side effects trigger UI refreshes when fields change. Declared in the BDEF, no implementation needed.

```
side effects {
  field BeginDate affects field TotalPrice;
  field EndDate affects field TotalPrice;
  field FlightPrice affects field TotalPrice;
  determine action Prepare executed on field OverallStatus affects messages;
  action acceptTravel affects field OverallStatus, field *, messages;
}
```

## Business Events

For async communication between BOs:

**BDEF:**
```
event TravelAccepted parameter ZA_TRAVEL_EVENT;
```

**Raising in saver class:**
```abap
METHOD save_modified.
  IF create-travel IS NOT INITIAL.
    RAISE ENTITY EVENT ZR_Travel~TravelAccepted
      FROM VALUE #( FOR travel IN create-travel
        ( %key = travel-%key
          %param = VALUE #( travel_id = travel-TravelID ) ) ).
  ENDIF.
ENDMETHOD.
```

## EML Quick Reference

Entity Manipulation Language is the ABAP syntax for working with RAP BO instances:

```abap
" Read
READ ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  FIELDS ( TravelID OverallStatus )
  WITH CORRESPONDING #( keys )
  RESULT DATA(travels).

" Read by association
READ ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel BY \_Booking
  ALL FIELDS
  WITH CORRESPONDING #( keys )
  RESULT DATA(bookings).

" Modify (update)
MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  UPDATE FIELDS ( OverallStatus )
  WITH VALUE #( ( %tky = travel-%tky OverallStatus = 'A' ) )
  FAILED failed REPORTED reported.

" Create
MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  CREATE FIELDS ( AgencyID CustomerID ... )
  WITH VALUE #( ( %cid = 'NEW1' AgencyID = '001' ... ) )
  MAPPED mapped FAILED failed REPORTED reported.

" Delete
MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  DELETE FROM VALUE #( ( %tky = travel-%tky ) )
  FAILED failed REPORTED reported.

" Execute action
MODIFY ENTITIES OF ZR_Travel IN LOCAL MODE
  ENTITY Travel
  EXECUTE acceptTravel FROM CORRESPONDING #( keys )
  FAILED failed REPORTED reported.
```

## Workflow for Adding Behavior

1. **Read current BDEF**: `GetSource(name: "ZR_TRAVEL", object_type: "BDEF")`
2. **Read current ABP class**: `GetSource(name: "ZBP_R_TRAVEL", object_type: "CLAS", include: "implementations")`
3. **Edit BDEF** to add declaration: `EditSource` on the BDEF URL
4. **Edit ABP** to add method declaration and implementation
5. **Syntax check** both BDEF and class
6. **Activate** both (BDEF first, then class)
7. **Run unit tests** to verify
