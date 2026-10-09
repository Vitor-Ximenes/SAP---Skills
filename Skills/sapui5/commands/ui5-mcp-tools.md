---
name: ui5-mcp-tools
description: List all available UI5 MCP server tools and their usage
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

Return the relevant UI5 MCP tool, expected inputs, fallback local commands, and unresolved MCP availability checks. Default to read-only guidance.


# UI5 MCP Server Tools

## Operation Safety

- **Classification**: `local-only`
- The bundled UI5 MCP server supports local documentation, scaffolding, project inspection, and linting workflows without tenant credentials.
- Require explicit user approval before creating project files, changing MCP config, or running commands that deploy, publish, or affect remote systems.

This command lists all available tools provided by the **@ui5/mcp-server** (Model Context Protocol server for UI5 development).

## What is MCP?

The **Model Context Protocol (MCP)** is an open standard that allows AI assistants like Claude to connect with external tools and services. The **@ui5/mcp-server** provides 9 specialized tools for SAPUI5/OpenUI5 development.

---

## Available Tools (9 total)

### 📋 Scaffolding Tools (2)

#### 1. create_ui5_app
**Purpose**: Scaffold new UI5 applications with various templates

**Templates**:
- Freestyle Applications (JavaScript, TypeScript)
- Fiori Elements (List Report, Object Page, Analytical List Page, Overview Page, Worklist)
- CAP Integration (Full-stack with backend)

**Usage**:
```javascript
create_ui5_app({
  template: "freestyle-ts",
  projectName: "my-ui5-app",
  namespace: "com.mycompany.myapp",
  ui5Version: "1.120.0",
  odataVersion: "v4",
  enableCAP: false
})
```

**Agent**: Used by `ui5-app-scaffolder`
**Command**: `/ui5-scaffold`

---

#### 2. create_integration_card
**Purpose**: Scaffold Integration Cards for SAP Work Zone, launchpad, or standalone widgets

**Card Types**:
- List Card: Vertical list of items
- Table Card: Tabular data display
- Object Card: Single object with header and content
- Timeline Card: Chronological events
- Analytical Card: Charts and visualizations

**Usage**:
```javascript
create_integration_card({
  cardType: "List",
  cardName: "sales-dashboard",
  namespace: "com.mycompany.cards"
})
```

**Agent**: Used by `ui5-app-scaffolder`
**Command**: `/ui5-scaffold --type=integration-card`

---

### 🔍 Code Analysis Tools (2)

#### 3. run_ui5_linter
**Purpose**: Static code analysis for UI5 projects

**Checks For**:
- Deprecated API usage (jQuery.sap.*, old libraries)
- Security issues (XSS, CSP violations, eval)
- Performance problems (non-virtualized lists, inefficient bindings)
- Best practice violations (MVC patterns, async loading)
- Accessibility issues (missing ARIA, keyboard navigation)

**Usage**:
```javascript
run_ui5_linter({
  files: ["webapp/**/*.js", "webapp/**/*.xml"],
  fix: false,  // Set to true for auto-fix
  config: {
    rules: {
      "no-deprecated-api": "error",
      "no-globals": "error",
      "async-module-loading": "error"
    }
  }
})
```

**Agent**: Used by `ui5-code-quality-advisor`
**Command**: `/ui5-lint`

---

#### 4. get_project_info
**Purpose**: Extract metadata from UI5 projects

**Returns**:
- UI5 version (from manifest.json, ui5.yaml)
- Project structure (controllers, views, models count)
- Dependencies (libraries, components)
- OData version
- Build configuration
- Namespace information

**Usage**:
```javascript
get_project_info({
  projectPath: "./webapp"
})
```

**Agent**: Used by all agents for context awareness
**Command**: None (internal tool)

---

### 📚 Reference & Guidance Tools (5)

#### 5. get_api_reference
**Purpose**: Fetch UI5 API documentation for controls, methods, events, and properties

**Features**:
- Version-aware lookups
- Method signatures with parameters
- Event details
- Property types and defaults
- Code examples
- Deprecation warnings

**Usage**:
```javascript
get_api_reference({
  control: "sap.m.Table",
  version: "1.120.0",
  includeExamples: true,
  includeDeprecated: false
})
```

**Agent**: Used by `ui5-api-explorer`
**Command**: `/ui5-api sap.m.Table`

---

#### 6. get_guidelines
**Purpose**: Retrieve SAP UI5 best practice guidelines

**Topics**:
- Controller patterns (MVC architecture)
- Performance optimization (virtualization, caching)
- Security best practices (XSS prevention, CSP)
- Accessibility guidelines (WCAG 2.1 AA)
- Data binding patterns
- Component structure
- Testing strategies

**Usage**:
```javascript
get_guidelines({
  topic: "controller-patterns",
  version: "1.120.0"
})
```

**Agent**: Used by `ui5-code-quality-advisor`
**Command**: None (used internally for code reviews)

---

#### 7. get_version_info
**Purpose**: UI5 version information, release notes, and migration paths

**Returns**:
- Release date
- Support status (active, maintenance, EOL)
- Breaking changes
- Deprecated APIs with alternatives
- New features
- Upgrade recommendations
- Browser compatibility

**Usage**:
```javascript
get_version_info({
  version: "1.120.0",
  compareWith: "1.108.0"  // Optional: show differences
})
```

**Agent**: Used by `ui5-migration-specialist`, `ui5-code-quality-advisor`
**Command**: `/ui5-version 1.120.0`

---

#### 8. get_typescript_conversion_guidelines
**Purpose**: Guidelines for converting JavaScript UI5 projects to TypeScript

**Provides**:
- Step-by-step conversion process
- Type definition usage (@types/openui5)
- Controller conversion examples
- Component.ts patterns
- Formatter typing
- ui5-tooling-transpile configuration
- Common pitfalls and solutions

**Usage**:
```javascript
get_typescript_conversion_guidelines({
  projectPath: "./webapp"
})
```

**Agent**: Used by `ui5-migration-specialist`
**Command**: None (used for TypeScript migration)

---

#### 9. get_integration_cards_guidelines
**Purpose**: Best practices for developing SAP Integration Cards

**Topics**:
- Card types and when to use them
- Manifest configuration
- Data binding in cards
- Actions and navigation
- Parameters and filters
- Card extensions
- Work Zone integration
- Testing cards

**Usage**:
```javascript
get_integration_cards_guidelines({
  cardType: "List"
})
```

**Agent**: Used by `ui5-app-scaffolder`
**Command**: None (used during Integration Card scaffolding)

---

## MCP Server Installation

### Install Globally (Recommended)
```bash
npm install -g @ui5/mcp-server@0.2.17
```

### Use with npx (No Installation)
```bash
npx -y @ui5/mcp-server@0.2.17 <tool-name> <arguments>
```

### Configure for Code CLI

The sapui5 plugin automatically configures MCP via `.mcp.json`:

```json
{
  "ui5-tooling": {
    "command": "npx",
    "args": ["-y", "@ui5/mcp-server@0.2.17"],
    "env": {
      "UI5_PROJECT_DIR": "${cwd}",
      "UI5_VERSION": "1.120.0",
      "UI5_MCP_SERVER_RESPONSE_NO_RESOURCES": "true"
    }
  }
}
```

This configuration is already set up in the sapui5 plugin - no manual setup needed!

---

## How Agents Use MCP Tools

### ui5-app-scaffolder
Uses:
- `create_ui5_app` - Create projects
- `create_integration_card` - Create cards
- `get_project_info` - Validate scaffolding

### ui5-api-explorer
Uses:
- `get_api_reference` - Fetch API docs
- `get_version_info` - Check version compatibility

### ui5-code-quality-advisor
Uses:
- `run_ui5_linter` - Static analysis
- `get_guidelines` - Best practices
- `get_version_info` - Deprecation checks

### ui5-migration-specialist
Uses:
- `get_version_info` - Migration paths
- `get_typescript_conversion_guidelines` - TypeScript conversion
- `run_ui5_linter` - Post-migration validation
- `get_api_reference` - New API documentation

---

## Fallback Behavior

All agents have **graceful fallback** when MCP tools are unavailable:

**If MCP unavailable**, agents use:
- Reference files in `references/`
- Web scraping from ui5.sap.com
- Template files in `templates/`
- Manual pattern matching

This ensures **100% functionality** even without MCP server.

---

## MCP Tool Benefits

**With MCP** (Recommended):
- ✅ 5x faster API lookups
- ✅ Real-time linting
- ✅ Accurate version information
- ✅ Official SAP templates
- ✅ Up-to-date best practices

**Without MCP** (Fallback):
- ⚠️ Slower lookups (web scraping)
- ⚠️ Manual linting patterns
- ⚠️ Reference file based (may be slightly outdated)
- ✅ Still fully functional

---

## Testing MCP Availability

To check if MCP tools are available:

```bash
# Test MCP server
npx -y @ui5/mcp-server@0.2.17 --help

# Expected output: List of available tools
```

If this works, all agents will automatically use MCP tools for faster, more accurate results.

---

## Troubleshooting

### MCP Server Not Found
```bash
# Install globally
npm install -g @ui5/mcp-server@0.2.17

# Or use npx (automatic download)
npx -y @ui5/mcp-server@0.2.17 --version
```

### Permission Errors
```bash
# Prefer a user-local npm global directory instead of changing ownership
mkdir -p ~/.npm-global
npm config set prefix ~/.npm-global
```

Avoid recursive ownership changes unless an administrator has confirmed the affected directory and impact.

### Network Issues
```bash
# Check npm registry
npm config get registry

# Try different registry
npm config set registry https://registry.npmjs.org/
```

---

## More Information

**MCP Server Documentation**:
```
https://github.com/UI5/mcp-server
```

**SAP Community Blog**:
```
https://community.sap.com/t5/technology-blog-posts-by-sap/give-your-ai-agent-some-tools-introducing-the-ui5-mcp-server/ba-p/14200825
```

**Detailed MCP Integration Guide**:
See `plugins/sapui5/skills/sapui5/references/mcp-integration.md`

---

## Summary

The UI5 MCP server provides 9 powerful tools for:
- **Scaffolding**: create_ui5_app, create_integration_card
- **Code Analysis**: run_ui5_linter, get_project_info
- **Reference**: get_api_reference, get_guidelines, get_version_info, get_typescript_conversion_guidelines, get_integration_cards_guidelines

All tools are automatically available to the sapui5 plugin agents and commands!

Happy developing with MCP! 🚀
