# SAP MCP Security Policy

Use this reference when configuring, updating, or reviewing SAP MCP servers. MCP servers are executable dependencies. Some only read local project metadata, while others use SAP tenant credentials and can call live APIs.

## Operation Safety Classes

- `local-only`: Operates on local files, local package metadata, local project models, or cached docs.
- `read-only tenant`: Reads tenant/system metadata or data and must not mutate tenant state.
- `mutating tenant`: Creates, updates, deploys, publishes, triggers, or otherwise changes tenant/system state.
- `destructive`: Deletes, drops, revokes, removes, disables, resets, or risks irreversible tenant/system impact.

Require explicit user approval before any `mutating tenant` or `destructive` MCP operation. Keep credentials, tokens, service keys, destinations, and tenant URLs in environment variables or local secret managers.

## Policy

- Use exact npm package pins in `.mcp.json`; never use `@latest` or a bare package name.
- Use the SAP MCP inventory (`sap-mcp-inventory.json`) as the source of approved package/source pins.
- Use a 14-day SAP cooldown by default. A newer version can be approved only when the inventory records the exception.
- For local-source MCPs, pin the trusted repository and exact commit SHA. Do not trust a moving branch.
- Keep tenant credentials in environment variables or local secret managers. Do not commit `.env`, service keys, or generated local install records.
- Run `npm run validate:mcp-security` after changing any plugin `.mcp.json`.

## Current Approved MCP Pins

| Plugin | MCP Package / Source | Approved Pin |
|--------|----------------------|--------------|
| `sap-cap-capire` | `@cap-js/mcp-server` | `0.0.5` |
| `sapui5` | `@ui5/mcp-server` | `0.2.17` |
| `sap-fiori-tools` | `@sap-ux/fiori-mcp-server` | `1.11.7` |
| `sap-hana-cli` | `hana-mcp-server` | `0.3.4` |
| `sap-datasphere` | `@mariodefe/sap-datasphere-mcp` | `1.5.2` |
| `sap-sac-scripting` | `secondsky/sap_analytics_cloud_mcp` | `2020235505d98111c2889598ab2217c1619b6943` |

## SAC Source MCP

The SAC MCP server is source-installed because the trusted `secondsky/sap_analytics_cloud_mcp` fork is not published as a versioned npm package in this repo. Treat it like a package with a source commit pin:

```bash
git clone https://github.com/secondsky/sap_analytics_cloud_mcp
cd sap_analytics_cloud_mcp
git checkout 2020235505d98111c2889598ab2217c1619b6943
npm ci --ignore-scripts
npm run build
```

Record the installation in `.claude/sac-mcp.local.md`:

```markdown
# SAC MCP Installation Record
- Repository: https://github.com/secondsky/sap_analytics_cloud_mcp
- Commit: 2020235505d98111c2889598ab2217c1619b6943
- Path: /absolute/path/to/sap_analytics_cloud_mcp/build/index.js
- Build command: npm ci --ignore-scripts && npm run build
- Env vars configured: SAC_MCP_PATH, SAC_MCP_COMMIT, SAC_BASE_URL, SAC_TOKEN_URL, SAC_CLIENT_ID, SAC_CLIENT_SECRET
```

## Update Workflow

1. Check the current package or source release metadata.
2. Apply the 14-day cooldown unless a maintainer records an exception.
3. Scan the package/source using the command in `sap-mcp-inventory.json`.
4. Update `.mcp.json` manually with the exact version or source commit marker.
5. Update `sap-mcp-inventory.json` in the same change.
6. Run `npm run validate:mcp-security` and the affected SAP plugin checks.
