# SAP Datasphere Skill

Portable SAP Datasphere skill for AI coding assistants, packaged with 3
specialized agent role guides, 5 command templates, validation hooks, and 17
reference documents covering data warehouse creation, analytic modeling, data
integration, CLI automation, data marketplace, governance, and SAP Business
Data Cloud.

## Capability Index

| Capability | Status |
|------------|--------|
| Commands | 5: `/datasphere-cli`, `/datasphere-connection-guide`, `/datasphere-mcp-tools`, `/datasphere-space-template`, `/datasphere-view-template` |
| Agents | 3: `datasphere-admin-helper`, `datasphere-integration-advisor`, `datasphere-modeler` |
| Hooks | Yes: `hooks/hooks.json` |
| MCP | Yes: `.mcp.json` |
| LSP | No |
| Source Freshness | `last_verified`: 2026-06-11; MCP package upgrade candidate and tenant checks tracked separately. |
| Verification | `npm run validate`; live Datasphere tenant checks pending unless evidence is provided. |

## Overview

SAP Datasphere is SAP's cloud-native data warehouse solution on SAP Business Technology Platform (BTP) and the data foundation of SAP Business Data Cloud (BDC). This plugin provides comprehensive guidance for building enterprise data warehouses with SAP Datasphere, including 2026 features such as task chain ports, replication flow enhancements, semantic onboarding from HANA Cloud calculation views, SAP Snowflake integration, and BDC data products.

## When to Use

This skill activates when working with:

- SAP Datasphere tenant setup and configuration
- Data Builder (graphical views, SQL views, tables, flows)
- Business Builder (business entities, consumption models)
- Analytic models for SAP Analytics Cloud
- Data integration (replication, transformation, task chains)
- Connection configuration (40+ connection types)
- Space and user administration
- Data access controls and security
- Content transport between tenants
- CLI automation and CI/CD integration
- Data products and marketplace
- Catalog and governance

## Plugin Components

**Agents:**
- `datasphere-modeler` - Data Builder tasks, views, flows, analytic models
- `datasphere-integration-advisor` - Connectivity, replication, data integration
- `datasphere-admin-helper` - Space management, security, monitoring

**Commands:**
- `/datasphere-space-template` - Generate space configurations
- `/datasphere-view-template` - Generate view templates
- `/datasphere-connection-guide` - Step-by-step connection setup
- `/datasphere-cli` - CLI command reference
- `/datasphere-mcp-tools` - SAP Datasphere MCP tool reference and usage guidance

**Hooks:**
- PreToolUse validation for SQL/SQLScript quality
- PostToolUse suggestions for optimization

## MCP Integration

This plugin includes a Claude-compatible MCP connection recipe for the SAP
Datasphere MCP Server. MCP use is optional and live-tenant behavior remains
pending until the user configures credentials and verifies tool availability in
their harness.

| Item | Value |
|------|-------|
| Command | `npx` |
| Args | `-y @mariodefe/sap-datasphere-mcp@1.5.2` |
| Approved pin | `1.5.2` |
| Tool profile | Defaults to `lean` since v1.3.0; set `DATASPHERE_TOOL_PROFILE=full` for the complete tool set |
| Operation safety | Read-only, mutating tenant, and destructive tools; require confirmation before create/update/delete/reset/deploy/publish/trigger actions |
| Fallback | Use CLI/reference guidance and mark live tenant checks pending |

### Setup

1. **Install MCP Server:**
   ```bash
   npm install -g @mariodefe/sap-datasphere-mcp@1.5.2
   ```

2. **Configure Environment Variables:**
   Create `.env` file in your project:
   ```bash
   DATASPHERE_BASE_URL=https://your-tenant.eu10.hcs.cloud.sap
   DATASPHERE_CLIENT_ID=your-oauth-client-id
   DATASPHERE_CLIENT_SECRET=your-oauth-client-secret
   DATASPHERE_TOKEN_URL=https://your-tenant.authentication.eu10.hana.ondemand.com/oauth/token
   ```

3. **Get OAuth Credentials:**
   - Go to SAP BTP Cockpit → Subscriptions → SAP Datasphere
   - Create OAuth2 Client (grant type: Client Credentials)
   - Required scopes: READ, WRITE (based on needs)

### Available Tools

When the approved MCP server is installed and verified, the documented server
version is expected to expose 45 MCP tools across 8 categories. Confirm the
actual tool list in your harness before relying on a specific count:
- **Foundation:** Connection testing, tenant info, space discovery
- **Catalog:** Asset search, column analysis
- **Analytics:** Query analytic models, smart queries
- **ETL:** Relational queries, bulk extract
- **User Management:** CRUD operations for DB users
- **Metadata:** Table/view/model structure inspection
- **Search:** Semantic and quick find
- **Space Management:** Space details and permissions

Use `/datasphere-mcp-tools` command to see all available tools.

### Usage in Agents

In Claude-compatible clients, all three agents can be granted relevant MCP
tools. In other harnesses, use the same agent files as role guidance and run MCP
tools only if your client has been configured for them:
- **datasphere-modeler:** Queries, metadata, data preview
- **datasphere-integration-advisor:** Connection testing, space discovery
- **datasphere-admin-helper:** User management, permissions

### Commands Using MCP

- `/datasphere-mcp-tools` - List all MCP tools

### Illustrative Use Cases

See [mcp-use-cases.md](references/mcp-use-cases.md) for 8 illustrative source-material use cases. Time and ROI examples are planning assumptions, not repository-verified productivity outcomes:
- **The Monday Morning Health Check**: 45 min/day saved for Data Operations Managers
- **Data Lineage Inspection**: 3 hours saved per investigation for Data Engineers
- **Pre-Analytics Data Quality Audit**: 2 hours saved per project for Data Analysts
- **The Onboarding Speedrun**: 2 days saved for New Data Engineers
- **The Marketplace Shopping Spree**: 1 hour saved per evaluation for Analytics Team Leads
- **The Security Audit**: 4 hours saved per audit for Data Governance Managers
- **The Performance Troubleshooter**: 1.5 hours saved per incident for Data Platform Engineers
- **Cross-Functional Collaboration**: 3 hours saved per issue for Business Analysts + Data Engineers

**Illustrative ROI model**: $159,100+/year for mid-sized teams. Treat this as
planning material, not validated repository evidence.

## Keywords

**Product Terms**: sap datasphere, data warehouse cloud, dwc, sap btp data warehouse, datasphere tenant, datasphere space, sap business data cloud

**MCP Integration**: mcp, model context protocol, oauth, live tenant, real-time data, direct queries, mcp tools, mcp server, datasphere mcp, tenant interaction

**Data Builder**: data builder, graphical view, sql view, sqlscript, local table, remote table, data flow, replication flow, transformation flow, task chain, e-r model, intelligent lookup, rest api task

**Business Builder**: business builder, business entity, fact model, consumption model, authorization scenario

**Analytic Modeling**: analytic model, dimension, fact, measure, hierarchy, calculated measure, restricted measure, currency conversion, unit conversion, time dimension, fiscal calendar, secondary structure

**Connectivity**: datasphere connection, cloud connector, data provisioning agent, sap s4hana connection, bw4hana connection, hana cloud connection, aws connection, azure connection, gcp connection, kafka connection, odata connection, jdbc connection, generic http connection, microsoft fabric, databricks, snowflake, mongodb

**Administration**: datasphere administration, space management, user management, role management, elastic compute node, monitoring, audit log, workload management, scim api

**CLI**: datasphere cli, datasphere command line, datasphere config, datasphere spaces, datasphere objects, datasphere tasks, datasphere marketplace, ci cd automation, service key authentication

**Integration**: data integration, real-time replication, delta replication, cdc, data persistence, view analyzer, scheduling, view persistence, partition strategy

**Security**: data access control, row-level security, dac, single values dac, hierarchy dac, column level security, dynamic masking

**Marketplace**: data products, data marketplace, data provider, data consumer, data sharing, data monetization

**Catalog & Governance**: data catalog, glossary, business terms, data quality, data lineage, impact analysis, data classification, data steward, data owner

**Transport**: content transport, export package, import package, csn json, cloud transport management

**2025–2026 Features**: generic http connection, rest api task, sap business data cloud, bdc, analytic model enhancements, elastic compute auto-scaling, task chain ports, auto-retry, technical user scheduling, sap databricks, sap snowflake, data composer

**Errors**: datasphere deployment failed, connection timeout, replication error, out of memory, permission denied, circular dependency

## File Structure

```
plugins/sap-datasphere/
├── .claude-plugin/
│   └── plugin.json
├── .mcp.json                         # MCP server configuration
├── agents/
│   ├── datasphere-modeler.md
│   ├── datasphere-integration-advisor.md
│   └── datasphere-admin-helper.md
├── commands/
│   ├── datasphere-space-template.md
│   ├── datasphere-view-template.md
│   ├── datasphere-connection-guide.md
│   ├── datasphere-cli.md
│   └── datasphere-mcp-tools.md       # MCP tools reference
├── hooks/
│   └── hooks.json
└── skills/
    └── sap-datasphere/
        ├── .claude-plugin/
        │   └── plugin.json
        ├── SKILL.md
        ├── README.md
        └── references/
            ├── data-acquisition-preparation.md
            ├── data-modeling.md
            ├── graphical-sql-views.md
            ├── connectivity.md
            ├── administration.md
            ├── data-integration-monitor.md
            ├── data-access-security.md
            ├── content-transport.md
            ├── cli-commands.md
            ├── data-products-marketplace.md
            ├── catalog-governance.md
            ├── best-practices-patterns.md
            ├── whats-new-2025.md
            ├── whats-new-2026.md
            ├── business-data-cloud.md
            ├── mcp-tools-reference.md    # MCP technical reference
            └── mcp-use-cases.md          # MCP real-world use cases
```

## Documentation Sources

- **SAP Help Portal**: [https://help.sap.com/docs/SAP_DATASPHERE](https://help.sap.com/docs/SAP_DATASPHERE)
- **GitHub Repository**: [https://github.com/SAP-docs/sap-datasphere](https://github.com/SAP-docs/sap-datasphere)
- **SAP Community**: [https://community.sap.com/topics/datasphere](https://community.sap.com/topics/datasphere)
- **API Reference**: [https://api.sap.com/package/sapdatasphere](https://api.sap.com/package/sapdatasphere)
- **CLI Documentation**: [https://help.sap.com/docs/SAP_DATASPHERE/d0ecd6f297ac40249072a44df0549c1a](https://help.sap.com/docs/SAP_DATASPHERE/d0ecd6f297ac40249072a44df0549c1a)
- **Best Practices**: [https://pages.community.sap.com/topics/datasphere/best-practices-troubleshooting](https://pages.community.sap.com/topics/datasphere/best-practices-troubleshooting)

## Coverage

This plugin provides comprehensive coverage of SAP Datasphere with:

| Component | Count | Description |
|-----------|-------|-------------|
| Reference Files | 17 | Core documentation covering all major topics + MCP integration + BDC + 2026 updates |
| Agents | 3 | Specialized agents for modeling, integration, admin |
| Commands | 4 | Template generators and CLI reference |
| Hooks | 2 | Validation and optimization suggestions |

## Version

- **Plugin Version**: 3.0.0
- **Skill Version**: 2.2.0
- **Last Verified**: 2026-06-11
- **SAP Datasphere Version**: 2026.12 (June 2026)

## License

GPL-3.0
