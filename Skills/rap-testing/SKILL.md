---
name: rap-testing
description: "Create and run ABAP Unit tests for RAP Business Objects on ABAP Cloud/BTP. Covers testing RAP BOs with the RAP test doubles framework (CL_BOTD_TXBUFDBL_BO_TEST_ENV), mocking EML APIs, testing validations/determinations/actions via EML, and integration testing with the local OData client proxy. Use this skill whenever the user wants to write tests for RAP BOs, test validations, test actions, create test classes, run unit tests, or debug test failures. Trigger on: 'unit test', 'test class', 'ABAP Unit', 'test double', 'CL_BOTD', 'test RAP', 'test validation', 'test action', 'test determination', 'FOR TESTING'."
---

# RAP Unit Testing

This skill helps create ABAP Unit tests for managed RAP Business Objects on ABAP Cloud/BTP using the RAP test doubles framework.

## Tools Used

- `GetSource` (object_type: "CLAS", include: "testclasses") — read existing test classes
- `WriteSource` (object_type: "CLAS", test_source: "...") — create/update test classes
- `EditSource` — modify test class source
- `RunUnitTests` — execute tests and see results
- `SyntaxCheck` — validate before running

## Testing Approaches for RAP

There are two main approaches for testing RAP BOs:

### 1. EML-Based Integration Tests
Test the full RAP BO stack by calling EML operations and verifying results. This is the most common and recommended approach.

### 2. RAP Test Doubles (CL_BOTD)
Mock the transactional buffer for isolated unit testing of behavior implementations. Use when you want to test handler methods in isolation.

## EML-Based Integration Test Pattern

This is the standard way to test RAP BOs. The test class creates, modifies, and reads BO instances using EML, then asserts the expected outcomes.

### Test Class Structure

```abap
"! @testing ZR_TRAVEL
CLASS ltcl_travel DEFINITION FINAL FOR TESTING
  DURATION SHORT
  RISK LEVEL HARMLESS.

  PRIVATE SECTION.
    CLASS-DATA: environment TYPE REF TO if_botd_txbufdbl_bo_test_env.

    CLASS-METHODS class_setup.
    CLASS-METHODS class_teardown.
    METHODS setup.
    METHODS teardown.

    " Test methods
    METHODS create_travel             FOR TESTING.
    METHODS update_travel             FOR TESTING.
    METHODS delete_travel             FOR TESTING.
    METHODS validate_dates_success    FOR TESTING.
    METHODS validate_dates_fail       FOR TESTING.
    METHODS action_accept_travel      FOR TESTING.
    METHODS action_reject_travel      FOR TESTING.
    METHODS determine_travel_id       FOR TESTING.
    METHODS create_booking_by_assoc   FOR TESTING.
ENDCLASS.

CLASS ltcl_travel IMPLEMENTATION.

  METHOD class_setup.
    " Create the test environment with transactional buffer doubles
    environment = cl_botd_txbufdbl_bo_test_env=>create(
      VALUE #( ( name = 'ZR_TRAVEL' ) ) ).
  ENDMETHOD.

  METHOD class_teardown.
    environment->destroy( ).
  ENDMETHOD.

  METHOD setup.
    environment->clear_doubles( ).
  ENDMETHOD.

  METHOD teardown.
    " Rollback any open transactions
    ROLLBACK ENTITIES.
  ENDMETHOD.

  METHOD create_travel.
    " Given: No travels exist

    " When: Create a new travel
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate
                      Description CurrencyCode )
      WITH VALUE #( (
        %cid        = 'CID_1'
        AgencyID    = '000001'
        CustomerID  = '000001'
        BeginDate   = cl_abap_context_info=>get_system_date( ) + 10
        EndDate     = cl_abap_context_info=>get_system_date( ) + 20
        Description = 'Test Travel'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " Then: Creation succeeds
    cl_abap_unit_assert=>assert_initial( failed ).
    cl_abap_unit_assert=>assert_not_initial( mapped-travel ).

    " Commit to trigger determinations and validations
    COMMIT ENTITIES
      RESPONSE OF zr_travel
      FAILED DATA(commit_failed)
      REPORTED DATA(commit_reported).

    cl_abap_unit_assert=>assert_initial( commit_failed ).
  ENDMETHOD.

  METHOD update_travel.
    " Given: A travel exists
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'CID_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 10
        EndDate      = cl_abap_context_info=>get_system_date( ) + 20
        Description  = 'Original'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " When: Update the description
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      UPDATE FIELDS ( Description )
      WITH VALUE #( (
        %tky        = mapped-travel[ 1 ]-%tky
        Description = 'Updated Description' ) )
      FAILED DATA(upd_failed)
      REPORTED DATA(upd_reported).

    " Then: Update succeeds
    cl_abap_unit_assert=>assert_initial( upd_failed ).

    " Verify the update
    READ ENTITIES OF zr_travel
      ENTITY Travel
      FIELDS ( Description )
      WITH VALUE #( ( %tky = mapped-travel[ 1 ]-%tky ) )
      RESULT DATA(travels).

    cl_abap_unit_assert=>assert_equals(
      exp = 'Updated Description'
      act = travels[ 1 ]-Description ).
  ENDMETHOD.

  METHOD delete_travel.
    " Given: A travel exists
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'CID_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 10
        EndDate      = cl_abap_context_info=>get_system_date( ) + 20
        Description  = 'To Delete'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " When: Delete the travel
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      DELETE FROM VALUE #( ( %tky = mapped-travel[ 1 ]-%tky ) )
      FAILED DATA(del_failed)
      REPORTED DATA(del_reported).

    " Then: Deletion succeeds
    cl_abap_unit_assert=>assert_initial( del_failed ).
  ENDMETHOD.

  METHOD validate_dates_fail.
    " Given/When: Create travel with invalid dates (begin > end)
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'CID_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 20
        EndDate      = cl_abap_context_info=>get_system_date( ) + 10
        Description  = 'Bad Dates'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " Commit to trigger on-save validations
    COMMIT ENTITIES
      RESPONSE OF zr_travel
      FAILED DATA(commit_failed)
      REPORTED DATA(commit_reported).

    " Then: Validation should fail
    cl_abap_unit_assert=>assert_not_initial(
      msg = 'Validation should reject invalid dates'
      act = commit_failed ).
  ENDMETHOD.

  METHOD validate_dates_success.
    " Given/When: Create travel with valid dates
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'CID_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 10
        EndDate      = cl_abap_context_info=>get_system_date( ) + 20
        Description  = 'Good Dates'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    COMMIT ENTITIES
      RESPONSE OF zr_travel
      FAILED DATA(commit_failed)
      REPORTED DATA(commit_reported).

    " Then: Should succeed
    cl_abap_unit_assert=>assert_initial(
      msg = 'Valid dates should pass validation'
      act = commit_failed ).
  ENDMETHOD.

  METHOD action_accept_travel.
    " Given: A travel exists with open status
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate
                      Description CurrencyCode OverallStatus )
      WITH VALUE #( (
        %cid          = 'CID_1'
        AgencyID      = '000001'
        CustomerID    = '000001'
        BeginDate     = cl_abap_context_info=>get_system_date( ) + 10
        EndDate       = cl_abap_context_info=>get_system_date( ) + 20
        Description   = 'Action Test'
        CurrencyCode  = 'EUR'
        OverallStatus = 'O' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " When: Execute accept action
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      EXECUTE acceptTravel FROM VALUE #( (
        %tky = mapped-travel[ 1 ]-%tky ) )
      RESULT DATA(action_result)
      FAILED DATA(act_failed)
      REPORTED DATA(act_reported).

    " Then: Status should be 'Accepted'
    cl_abap_unit_assert=>assert_initial( act_failed ).
    cl_abap_unit_assert=>assert_equals(
      exp = 'A'
      act = action_result[ 1 ]-%param-OverallStatus ).
  ENDMETHOD.

  METHOD action_reject_travel.
    " Similar pattern to accept, but verify status = 'X'
    " ... (follow same pattern as accept)
  ENDMETHOD.

  METHOD determine_travel_id.
    " Given/When: Create a travel (determination should assign TravelID)
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'CID_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 10
        EndDate      = cl_abap_context_info=>get_system_date( ) + 20
        Description  = 'ID Test'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " Read to check if determination set the TravelID
    READ ENTITIES OF zr_travel
      ENTITY Travel
      FIELDS ( TravelID )
      WITH VALUE #( ( %tky = mapped-travel[ 1 ]-%tky ) )
      RESULT DATA(travels).

    " Then: TravelID should be assigned (if on modify determination)
    cl_abap_unit_assert=>assert_not_initial(
      msg = 'Determination should assign TravelID'
      act = travels[ 1 ]-TravelID ).
  ENDMETHOD.

  METHOD create_booking_by_assoc.
    " Given: A travel exists
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE FIELDS ( AgencyID CustomerID BeginDate EndDate Description CurrencyCode )
      WITH VALUE #( (
        %cid         = 'TRAVEL_1'
        AgencyID     = '000001'
        CustomerID   = '000001'
        BeginDate    = cl_abap_context_info=>get_system_date( ) + 10
        EndDate      = cl_abap_context_info=>get_system_date( ) + 20
        Description  = 'With Booking'
        CurrencyCode = 'EUR' ) )
      MAPPED DATA(mapped)
      FAILED DATA(failed)
      REPORTED DATA(reported).

    " When: Create booking by association
    MODIFY ENTITIES OF zr_travel
      ENTITY Travel
      CREATE BY \_Booking
      FIELDS ( FlightDate CarrierID ConnectionID )
      WITH VALUE #( (
        %tky          = mapped-travel[ 1 ]-%tky
        %target       = VALUE #( (
          %cid          = 'BOOKING_1'
          FlightDate    = cl_abap_context_info=>get_system_date( ) + 15
          CarrierID     = 'LH'
          ConnectionID  = '0400' ) ) ) )
      MAPPED DATA(book_mapped)
      FAILED DATA(book_failed)
      REPORTED DATA(book_reported).

    " Then: Booking created successfully
    cl_abap_unit_assert=>assert_initial( book_failed ).
    cl_abap_unit_assert=>assert_not_initial( book_mapped-booking ).
  ENDMETHOD.

ENDCLASS.
```

## Running Tests with MCP Tools

### Execute tests
```
RunUnitTests(object_url: "/sap/bc/adt/oo/classes/ZBP_R_TRAVEL")
```

### Interpreting results
The tool returns pass/fail status for each test method. Common failure patterns:
- `FAILED is not initial` — the EML operation returned errors
- `COMMIT_FAILED is not initial` — a validation rejected the data
- `Assertion failed` — the expected value doesn't match actual

## Test Naming Conventions

Follow a clear pattern:
- `create_[entity]` — test basic creation
- `update_[entity]` — test update
- `delete_[entity]` — test deletion
- `validate_[name]_success` — test validation with valid data
- `validate_[name]_fail` — test validation with invalid data
- `action_[name]` — test action execution
- `determine_[name]` — test determination runs
- `create_[child]_by_assoc` — test create-by-association

## Test Best Practices

1. **Always use `ROLLBACK ENTITIES` in teardown** — prevents test data leaking between tests
2. **Use `cl_botd_txbufdbl_bo_test_env`** — provides proper test isolation
3. **Test both happy path and error cases** — especially for validations
4. **For `on save` validations, use `COMMIT ENTITIES`** — triggers the save sequence
5. **For `on modify` determinations, just `READ` after `MODIFY`** — the result should already be available
6. **Don't rely on specific key values** — managed numbering assigns UUIDs
7. **Use `CORRESPONDING #( keys )` patterns** — aligns with how handler methods receive data
8. **Keep tests independent** — each test should set up its own data

## Adding Tests to an Existing Class

Use `WriteSource` with the `test_source` parameter:

```
WriteSource(
  name: "ZBP_R_TRAVEL",
  object_type: "CLAS",
  source: "<existing class source>",
  test_source: "<test class source>"
)
```

Or use `GetSource` with `include: "testclasses"` to read existing tests, then `EditSource` to modify them.
