---
name: datasphere-view-template
description: Generate SAP Datasphere view templates for different modeling scenarios
allowed-tools:
  - Read
  - AskUserQuestion
argument-hint: "<graphical|sql|sqlscript> [dimension|fact|analytic|staging]"
arguments:
  - name: type
    description: "View type: graphical, sql, sqlscript"
    required: true
  - name: purpose
    description: "View purpose: dimension, fact, analytic, staging"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a view/modeling template, source assumptions, performance considerations, validation checklist, and pending tenant checks. Default to guidance and avoid modifying Datasphere artifacts or local files unless the user explicitly confirms the target.


Generate a Datasphere view template based on the requested type and purpose.

## Available View Types

### graphical
Graphical view using the visual editor with drag-and-drop modeling.

### sql
SQL view using standard ANSI SQL syntax.

### sqlscript
SQLScript view for procedural logic and complex transformations.

---

## Template: graphical (dimension)

### Graphical View - Dimension

**View Name**: `dim_<entity>`
**Semantic Usage**: Dimension
**Purpose**: Master data for analytical reporting

#### Configuration Steps

1. **Create View**
   - Open Data Builder > Views
   - Click "New Graphical View"
   - Select source table/view

2. **Configure Columns**
   ```yaml
   Columns:
     - Name: <ENTITY>_ID
       Key: Yes
       Semantic: Identifier

     - Name: <ENTITY>_NAME
       Key: No
       Semantic: Text

     - Name: <ENTITY>_DESC
       Key: No
       Semantic: Description

     - Name: VALID_FROM
       Key: No
       Semantic: Business Date (from)

     - Name: VALID_TO
       Key: No
       Semantic: Business Date (to)
   ```

3. **Set Semantic Usage**
   - Semantic Usage: **Dimension**
   - Expose for Consumption: **Yes**

4. **Configure Associations**
   ```yaml
   Associations:
     - Target: Text Entity (if separate)
       Type: Text Association
       Join: <ENTITY>_ID = TEXT_ENTITY_ID
       Language: LANGUAGE_KEY
   ```

5. **Add Hierarchy (Optional)**
   ```yaml
   Hierarchy:
     Type: Level-Based
     Levels:
       - Level 1: REGION
       - Level 2: COUNTRY
       - Level 3: CITY
   ```

---

## Template: graphical (fact)

### Graphical View - Fact

**View Name**: `fact_<subject>`
**Semantic Usage**: Fact
**Purpose**: Transactional data with measures

#### Configuration Steps

1. **Create View**
   - Open Data Builder > Views
   - Click "New Graphical View"
   - Add fact table as source

2. **Add Joins** (to dimensions)
   ```yaml
   Joins:
     - Type: Left Outer
       Source: fact_sales
       Target: dim_product
       On: PRODUCT_ID = PRODUCT_ID

     - Type: Left Outer
       Source: fact_sales
       Target: dim_customer
       On: CUSTOMER_ID = CUSTOMER_ID
   ```

3. **Configure Measures**
   ```yaml
   Measures:
     - Name: QUANTITY
       Aggregation: SUM

     - Name: REVENUE
       Aggregation: SUM

     - Name: UNIT_PRICE
       Aggregation: AVG

     - Name: DISCOUNT_AMOUNT
       Aggregation: SUM
   ```

4. **Configure Dimensions** (Foreign Keys)
   ```yaml
   Dimension Columns:
     - Name: PRODUCT_ID
       Association: dim_product

     - Name: CUSTOMER_ID
       Association: dim_customer

     - Name: DATE_KEY
       Association: dim_time
   ```

5. **Set Semantic Usage**
   - Semantic Usage: **Fact**
   - Expose for Consumption: **Yes**

---

## Template: sql (dimension)

### SQL View - Dimension

```sql
/*
 * Dimension View: dim_product
 * Source: raw_product (ERP product master)
 * Purpose: Product master data for analytics
 * Author: <author>
 * Created: <date>
 */

SELECT
    -- Key Column (Primary Identifier)
    PRODUCT_ID AS "Product ID",

    -- Descriptive Attributes
    PRODUCT_NAME AS "Product Name",
    PRODUCT_DESCRIPTION AS "Product Description",

    -- Category Hierarchy
    CATEGORY_L1 AS "Category Level 1",
    CATEGORY_L2 AS "Category Level 2",
    CATEGORY_L3 AS "Category Level 3",

    -- Additional Attributes
    BRAND AS "Brand",
    PRODUCT_GROUP AS "Product Group",
    UNIT_OF_MEASURE AS "Base Unit",

    -- Status and Validity
    PRODUCT_STATUS AS "Status",
    VALID_FROM AS "Valid From",
    VALID_TO AS "Valid To",

    -- Audit Fields
    LAST_MODIFIED AS "Last Modified"

FROM "raw_product"

WHERE
    -- Active products only
    PRODUCT_STATUS = 'ACTIVE'
    AND CURRENT_DATE BETWEEN VALID_FROM AND COALESCE(VALID_TO, '9999-12-31')

ORDER BY PRODUCT_ID;

/*
 * Post-Creation Configuration:
 * 1. Set Semantic Usage = Dimension
 * 2. Mark PRODUCT_ID as Key
 * 3. Create text association if separate text table
 * 4. Add hierarchy for category
 */
```

---

## Template: sql (fact)

### SQL View - Fact

```sql
/*
 * Fact View: fact_sales
 * Sources: raw_sales_header, raw_sales_item
 * Purpose: Sales transactions for analytics
 * Author: <author>
 * Created: <date>
 */

SELECT
    -- Transaction Keys
    h.SALES_ORDER_ID AS "Sales Order ID",
    i.LINE_ITEM_NUMBER AS "Line Item",

    -- Dimension Keys (for associations)
    i.PRODUCT_ID AS "Product ID",
    h.CUSTOMER_ID AS "Customer ID",
    h.SALES_ORG AS "Sales Organization",
    CAST(h.ORDER_DATE AS DATE) AS "Order Date",

    -- Measures (aggregatable)
    i.QUANTITY AS "Quantity",
    i.NET_PRICE AS "Net Price",
    i.GROSS_AMOUNT AS "Gross Amount",
    i.NET_AMOUNT AS "Net Amount",
    i.TAX_AMOUNT AS "Tax Amount",
    i.DISCOUNT_AMOUNT AS "Discount Amount",

    -- Calculated Measures
    (i.NET_AMOUNT / NULLIF(i.QUANTITY, 0)) AS "Avg Unit Price",

    -- Currency (for conversion)
    h.DOCUMENT_CURRENCY AS "Document Currency",

    -- Document Status
    h.ORDER_STATUS AS "Order Status",
    i.DELIVERY_STATUS AS "Delivery Status"

FROM "raw_sales_header" h
INNER JOIN "raw_sales_item" i
    ON h.SALES_ORDER_ID = i.SALES_ORDER_ID

WHERE
    h.ORDER_STATUS <> 'CANCELLED'
    AND h.ORDER_DATE >= '2020-01-01';

/*
 * Post-Creation Configuration:
 * 1. Set Semantic Usage = Fact
 * 2. Configure aggregations for measures (SUM, AVG)
 * 3. Create associations to dimensions
 * 4. Enable persistence for large datasets
 */
```

---

## Template: sql (staging)

### SQL View - Staging Layer

```sql
/*
 * Staging View: stg_sales_orders
 * Source: raw_s4_vbak (S/4HANA Sales Header)
 * Purpose: Cleansed and standardized sales data
 * Layer: Staging
 */

SELECT
    -- Standardize keys
    TRIM(VBELN) AS SALES_ORDER_ID,

    -- Map to standard formats
    CASE AUART
        WHEN 'OR' THEN 'STANDARD'
        WHEN 'RE' THEN 'RETURN'
        WHEN 'CR' THEN 'CREDIT_MEMO'
        ELSE 'OTHER'
    END AS ORDER_TYPE,

    -- Standardize customer ID
    LPAD(KUNNR, 10, '0') AS CUSTOMER_ID,

    -- Convert dates
    TO_DATE(ERDAT, 'YYYYMMDD') AS CREATED_DATE,
    TO_DATE(AUDAT, 'YYYYMMDD') AS ORDER_DATE,

    -- Clean amounts
    COALESCE(NETWR, 0) AS NET_VALUE,
    WAERK AS CURRENCY,

    -- Organization
    VKORG AS SALES_ORG,
    VTWEG AS DIST_CHANNEL,
    SPART AS DIVISION,

    -- Status mapping
    CASE
        WHEN GBSTK = 'C' THEN 'COMPLETED'
        WHEN GBSTK = 'A' THEN 'OPEN'
        WHEN GBSTK = 'B' THEN 'PARTIAL'
        ELSE 'UNKNOWN'
    END AS STATUS,

    -- Audit
    CURRENT_TIMESTAMP AS LOAD_TIMESTAMP

FROM "raw_s4_vbak"

WHERE
    -- Filter invalid records
    VBELN IS NOT NULL
    AND LENGTH(TRIM(VBELN)) > 0
    -- Date range
    AND TO_DATE(ERDAT, 'YYYYMMDD') >= ADD_YEARS(CURRENT_DATE, -3);

/*
 * Post-Creation Configuration:
 * 1. Set Semantic Usage = Relational Dataset
 * 2. Mark as intermediate (not exposed)
 * 3. Document transformations
 */
```

---

## Template: sqlscript

### SQLScript View - Complex Transformation

```sql
/*
 * SQLScript View: calc_customer_metrics
 * Purpose: Calculate complex customer KPIs
 * Requires: Input parameter for date range
 */

DO BEGIN
    -- Declare variables
    DECLARE lv_start_date DATE;
    DECLARE lv_end_date DATE;

    -- Set default date range (can be parameterized)
    lv_start_date := ADD_YEARS(CURRENT_DATE, -1);
    lv_end_date := CURRENT_DATE;

    -- Step 1: Get base transactions
    lt_transactions =
        SELECT
            CUSTOMER_ID,
            ORDER_DATE,
            NET_AMOUNT,
            QUANTITY
        FROM "fact_sales"
        WHERE ORDER_DATE BETWEEN :lv_start_date AND :lv_end_date;

    -- Step 2: Calculate aggregates
    lt_aggregates =
        SELECT
            CUSTOMER_ID,
            COUNT(DISTINCT ORDER_DATE) AS "Order Days",
            COUNT(*) AS "Transaction Count",
            SUM(NET_AMOUNT) AS "Total Revenue",
            AVG(NET_AMOUNT) AS "Avg Order Value",
            MIN(ORDER_DATE) AS "First Order",
            MAX(ORDER_DATE) AS "Last Order"
        FROM :lt_transactions
        GROUP BY CUSTOMER_ID;

    -- Step 3: Calculate derived metrics
    lt_metrics =
        SELECT
            a.CUSTOMER_ID,
            a."Order Days",
            a."Transaction Count",
            a."Total Revenue",
            a."Avg Order Value",
            a."First Order",
            a."Last Order",
            -- Calculate customer lifetime (days)
            DAYS_BETWEEN(a."First Order", a."Last Order") AS "Customer Lifetime Days",
            -- Calculate average time between orders
            CASE
                WHEN a."Order Days" > 1
                THEN DAYS_BETWEEN(a."First Order", a."Last Order") / (a."Order Days" - 1)
                ELSE NULL
            END AS "Avg Days Between Orders"
        FROM :lt_aggregates a;

    -- Step 4: Classify customers
    SELECT
        m.CUSTOMER_ID,
        m."Order Days",
        m."Transaction Count",
        m."Total Revenue",
        m."Avg Order Value",
        m."First Order",
        m."Last Order",
        m."Customer Lifetime Days",
        m."Avg Days Between Orders",
        -- Customer segment
        CASE
            WHEN m."Total Revenue" > 100000 THEN 'PLATINUM'
            WHEN m."Total Revenue" > 50000 THEN 'GOLD'
            WHEN m."Total Revenue" > 10000 THEN 'SILVER'
            ELSE 'BRONZE'
        END AS "Customer Segment",
        -- Recency flag
        CASE
            WHEN DAYS_BETWEEN(m."Last Order", CURRENT_DATE) <= 30 THEN 'ACTIVE'
            WHEN DAYS_BETWEEN(m."Last Order", CURRENT_DATE) <= 90 THEN 'AT_RISK'
            ELSE 'CHURNED'
        END AS "Recency Status"
    FROM :lt_metrics m;

END;

/*
 * Post-Creation Configuration:
 * 1. Set Semantic Usage = Relational Dataset or Fact
 * 2. Configure input parameters if needed
 * 3. Consider persistence for performance
 * 4. Document transformation logic
 */
```

---

## Semantic Configuration Reference

### Semantic Usage Types

| Type | Use Case | Expose |
|------|----------|--------|
| **Fact** | Transactional data with measures | Yes |
| **Dimension** | Master data for filtering/grouping | Yes |
| **Text** | Language-dependent descriptions | Usually No |
| **Hierarchy** | Hierarchical relationships | Usually No |
| **Relational Dataset** | Intermediate/staging views | Optional |
| **Analytical Dataset** | Deprecated, use Analytic Model | N/A |

### Association Types

| Type | Purpose | Example |
|------|---------|---------|
| **Association** | Link to dimension | fact_sales → dim_product |
| **Text Association** | Link to text table | dim_product → text_product |

---

Provide the appropriate template based on the user's requested type and purpose. Customize column names, source tables, and business logic based on their specific requirements.
