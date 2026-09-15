---
name: datasphere-mcp-tools
description: List all available SAP Datasphere MCP tools with descriptions and usage examples
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return the relevant MCP tool category, configuration checks, local fallback approach, and unresolved tenant/MCP availability items. Default to read-only guidance and avoid changing MCP config.


Display available MCP tools for SAP Datasphere integration.

## Operation Safety

- **Classification**: `read-only tenant`, `mutating tenant`, `destructive`
- The Datasphere MCP server uses tenant OAuth credentials from environment variables and can expose tenant-affecting operations.
- Treat read/list/search tools as read-only tenant operations. Require explicit user approval before create, update, delete, reset, deploy, publish, trigger, or other destructive actions.
- Do not claim live tenant verification unless matching evidence is recorded in the source verification ledger.

## SAP Datasphere MCP Tools (45 total)

The SAP Datasphere MCP Server (@mariodefe/sap-datasphere-mcp) provides 45 tools for direct tenant interaction.

**GitHub:** https://github.com/MarioDeFelipe/sap-datasphere-mcp
**Package:** npm install -g @mariodefe/sap-datasphere-mcp@1.5.2

---

## Foundation Tools (5)

### test_connection
**Purpose:** Verify connectivity to SAP Datasphere tenant
**Returns:** Connection status, latency, tenant URL
**Use when:** Setting up MCP integration, troubleshooting connectivity

### get_current_user
**Purpose:** Get authenticated user details
**Returns:** User ID, email, permissions, authentication status
**Use when:** Verifying authentication, checking user context

### get_tenant_info
**Purpose:** Retrieve tenant configuration and capabilities
**Returns:** Tenant URL, version, features, region
**Use when:** Discovering tenant features, checking version compatibility

### get_available_scopes
**Purpose:** List available permission scopes
**Returns:** Scopes array with descriptions
**Use when:** Understanding permission model, configuring OAuth clients

### list_spaces
**Purpose:** Discover all spaces in tenant
**Returns:** Space names, IDs, storage info, owners
**Use when:** Finding available spaces, discovering data domains

---

## Catalog & Asset Tools (4)

### search_catalog
**Purpose:** Search business catalog for objects
**Returns:** Matching objects with metadata
**Use when:** Finding existing views, tables, models by name or description

### find_assets_by_column
**Purpose:** Find data assets by column name
**Returns:** Objects containing specified column
**Use when:** Discovering which tables/views contain specific columns

### analyze_column_distribution
**Purpose:** Profile data distribution in columns
**Returns:** Statistics, distinct values, nulls, distribution
**Use when:** Data quality analysis, understanding data patterns

### get_asset_metadata
**Purpose:** Retrieve detailed asset metadata
**Returns:** Complete object definition, lineage, usage
**Use when:** Deep dive into object structure and dependencies

---

## Space Discovery (3)

### get_space_details
**Purpose:** Get space configuration and settings
**Returns:** Storage allocation, members, permissions, priority
**Use when:** Understanding space setup, auditing configuration

### list_space_objects
**Purpose:** List all objects in a space
**Returns:** Tables, views, models, flows by type
**Use when:** Discovering space content, cataloging objects

### get_space_permissions
**Purpose:** Review space access controls
**Returns:** User permissions, role assignments
**Use when:** Auditing security, understanding access levels

---

## Database User Management (5)

### create_database_user
**Purpose:** Create new database user
**Parameters:** Username, password, permissions
**Use when:** Setting up technical users, ETL accounts

### update_database_user
**Purpose:** Modify database user properties
**Parameters:** Username, new permissions
**Use when:** Changing user permissions, updating configuration

### delete_database_user
**Purpose:** Remove database user
**Parameters:** Username
**Use when:** Deprovisioning users, cleanup

### reset_database_user_password
**Purpose:** Reset database user password
**Parameters:** Username
**Returns:** New password (encrypted)
**Use when:** Password recovery, security incidents

### list_database_users
**Purpose:** List all database users
**Returns:** Usernames, permissions, status
**Use when:** Auditing users, permission review

---

## Metadata Tools (4)

### get_table_metadata
**Purpose:** Get table structure and properties
**Returns:** Columns, data types, keys, partitions
**Use when:** Designing integrations, understanding table structure

### get_view_metadata
**Purpose:** Get view definition and dependencies
**Returns:** SQL definition, source tables, columns
**Use when:** Understanding view logic, tracing lineage

### get_model_metadata
**Purpose:** Get analytic model structure
**Returns:** Dimensions, measures, associations, variables
**Use when:** Building SAC stories, understanding model capabilities

### describe_entity
**Purpose:** Get comprehensive entity information
**Returns:** Full metadata, relationships, lineage
**Use when:** Detailed analysis, documentation generation

---

## Analytical Consumption (4)

### query_analytical_data
**Purpose:** Query analytic models for consumption
**Parameters:** Model name, filters, dimensions, measures
**Returns:** Aggregated data
**Use when:** Testing analytic models, validating calculations

### get_analytic_model_structure
**Purpose:** Get analytic model definition
**Returns:** Model structure, available dimensions/measures
**Use when:** Building queries, understanding model capabilities

### execute_smart_query
**Purpose:** Run SQL queries with aggregation support
**Parameters:** SQL statement
**Returns:** Query results with automatic aggregation
**Use when:** Ad-hoc analysis, testing transformations

### preview_data
**Purpose:** Sample data from tables/views
**Parameters:** Object name, row limit
**Returns:** Sample rows
**Use when:** Quick data inspection, validation

---

## ETL-Optimized Relational (4)

### query_relational_entity
**Purpose:** Query relational data efficiently
**Parameters:** Entity name, filters
**Returns:** Relational data without aggregation
**Use when:** ETL operations, data extraction

### execute_query
**Purpose:** Run SQL queries via OData
**Parameters:** SQL statement
**Returns:** Query results
**Use when:** Standard SQL operations, data retrieval

### bulk_extract
**Purpose:** Extract large datasets
**Parameters:** Source, filters, batch size
**Returns:** Data in batches
**Use when:** Migrating data, large-scale exports

### stream_data
**Purpose:** Stream data for real-time processing
**Parameters:** Source, streaming configuration
**Returns:** Data stream
**Use when:** Real-time pipelines, continuous processing

---

## Search Tools (2)

### semantic_search
**Purpose:** AI-powered semantic search across catalog
**Parameters:** Natural language query
**Returns:** Relevant objects ranked by relevance
**Use when:** Finding objects by description, fuzzy search

### quick_find
**Purpose:** Fast object lookup by name
**Parameters:** Object name (exact or partial)
**Returns:** Matching objects
**Use when:** Quick navigation, autocomplete scenarios

---

## Setup Requirements

Configure environment variables (create `.env` file):

```bash
DATASPHERE_BASE_URL=https://your-tenant.eu10.hcs.cloud.sap
DATASPHERE_CLIENT_ID=your-oauth-client-id
DATASPHERE_CLIENT_SECRET=your-oauth-client-secret
DATASPHERE_TOKEN_URL=https://your-tenant.authentication.eu10.hana.ondemand.com/oauth/token
```

## Getting OAuth Credentials

1. Go to **SAP BTP Cockpit** → Subscriptions → SAP Datasphere
2. Navigate to **Security** → **OAuth Clients**
3. Click **Create OAuth Client**
4. Configure:
   - Grant Type: **Client Credentials**
   - Scopes: **READ**, **WRITE** (based on needs)
5. Save **Client ID** and **Client Secret**
6. Use token URL from subscription details

## Authentication

**Type:** OAuth 2.0 Client Credentials
**Token Refresh:** Automatic (60 seconds before expiration)
**Storage:** Encrypted with Fernet encryption
**Permissions:** Based on OAuth scope (READ, WRITE, ADMIN, SENSITIVE)

## Usage Examples

### Test Connection
```
Use MCP tool test_connection to verify Datasphere tenant connectivity
```
Result: Connection OK, latency 45ms, tenant version 2025.24

### Query Data
```
Use MCP tool execute_smart_query with SQL: SELECT REGION, SUM(REVENUE) FROM fact_sales GROUP BY REGION
```
Result: Aggregated sales by region

### Create Database User
```
Use MCP tool create_database_user to create ETL_READER with READ permissions
```
Result: User created, credentials returned

### Find Column
```
Use MCP tool find_assets_by_column to find all objects containing CUSTOMER_ID
```
Result: List of tables/views with CUSTOMER_ID column

### Preview Data
```
Use MCP tool preview_data from table dim_product with limit 10
```
Result: First 10 rows of product dimension

## Performance

| Operation Type | Response Time | Notes |
|----------------|---------------|-------|
| Metadata queries | < 100ms | Cached |
| Catalog queries | 100-500ms | Indexed search |
| OData queries | 500-2,000ms | Depends on data volume |
| Batch operations | Varies | Up to 50,000 records |

## Security Features

- ✅ **Encrypted Token Storage** - Fernet encryption
- ✅ **Permission Levels** - READ, WRITE, ADMIN, SENSITIVE
- ✅ **User Consent** - Prompts for high-risk operations
- ✅ **Input Validation** - SQL injection prevention
- ✅ **PII Redaction** - Automatic credential/PII masking

## Error Handling

Common errors and solutions:

**Authentication Failed:**
- Verify OAuth credentials in .env
- Check token URL matches tenant region
- Ensure scopes are correct

**Connection Timeout:**
- Check DATASPHERE_BASE_URL is correct
- Verify network connectivity
- Check firewall/proxy settings

**Permission Denied:**
- Verify OAuth scope includes required permissions
- Check user has access to requested space
- Review object-level permissions

## Integration with Agents

**datasphere-modeler:**
- Uses: execute_smart_query, query_analytical_data, preview_data, get_*_metadata
- For: Validating designs, testing queries, discovering assets

**datasphere-integration-advisor:**
- Uses: test_connection, list_spaces, get_tenant_info, search_catalog
- For: Verifying connectivity, discovering spaces, finding objects

**datasphere-admin-helper:**
- Uses: create/update/delete_database_user, get_current_user, list_spaces
- For: Managing users, auditing permissions, space management

## Additional Resources

- **GitHub:** https://github.com/MarioDeFelipe/sap-datasphere-mcp
- **NPM Package:** @mariodefe/sap-datasphere-mcp
- **Blog Post:** https://pub.towardsai.net/sap-datasphere-mcp-server-release-blog-0e82ea33db16
- **SAP Datasphere Help:** https://help.sap.com/docs/SAP_DATASPHERE

## Troubleshooting

Run diagnostics:
```bash
npx @mariodefe/sap-datasphere-mcp@1.5.2 --test
```

Enable debug mode (add to .env):
```bash
LOG_LEVEL=debug
```

Check MCP server status:
```bash
# From the Code CLI, use:
/mcp
```

## See Also

**Illustrative Use Cases:**
For source-material examples of how these MCP tools can be used in practice, see:
- **[mcp-use-cases.md](../skills/sap-datasphere/references/mcp-use-cases.md)** - 8 detailed use cases demonstrating:
  1. **The Monday Morning Health Check** - Operations monitoring (45 min/day saved)
  2. **Data Lineage Inspection** - Impact analysis (3 hours saved)
  3. **Pre-Analytics Data Quality Audit** - Data validation (2 hours saved)
  4. **The Onboarding Speedrun** - New hire onboarding (2 days saved)
  5. **The Marketplace Shopping Spree** - Content discovery (1 hour saved)
  6. **The Security Audit** - Compliance review (4 hours saved)
  7. **The Performance Troubleshooter** - Performance debugging (1.5 hours saved)
  8. **Cross-Functional Collaboration** - Team collaboration (3 hours saved)

**Impact estimates:** Illustrative planning assumptions only; not repository-verified productivity outcomes.

Each use case includes:
- Persona and role
- Quantified time savings
- Complete conversational workflow
- MCP tools used
- Expected results
- ROI analysis
