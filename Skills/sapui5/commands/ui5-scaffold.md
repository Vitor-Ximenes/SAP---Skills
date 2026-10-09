---
name: ui5-scaffold
description: Interactive project scaffolding for UI5 applications and Integration Cards
allowed-tools:
  - Read
  - Bash
  - AskUserQuestion
  - mcp__plugin_sapui5_ui5-tooling__create_ui5_app
  - mcp__plugin_sapui5_ui5-tooling__create_integration_card
  - mcp__plugin_sapui5_ui5-tooling__get_project_info
argument-hint: "[type] [lang] [backend] [name]"
arguments:
  - name: type
    description: Project type (freestyle, fiori-elements, integration-card, cap)
    required: false
  - name: lang
    description: Language (javascript, typescript)
    required: false
  - name: backend
    description: Backend type (standalone, odata-v2, odata-v4, cap)
    required: false
  - name: name
    description: Project name (lowercase, hyphenated)
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return project choices, files to create, package/version assumptions, and confirmation points before writes. Scaffold only when the user explicitly requests generation and confirms the target directory.


# Scaffold UI5 Project

{{#if type}}
Creating **{{type}}** project{{#if lang}} in **{{lang}}**{{/if}}{{#if backend}} with **{{backend}}** backend{{/if}}...
{{else}}
Starting interactive project scaffolding...
{{/if}}

## Project Scaffolding

Use the **ui5-app-scaffolder agent** to create the UI5 project when available.

The agent will:

1. **Read your preferences** from `sapui5.local.md` (if available)
2. **Ask clarifying questions** (if parameters not provided):
   - Project type (Freestyle, Fiori Elements, Integration Card)
   - Language (JavaScript or TypeScript)
   - Backend integration (Standalone, OData v2/v4, CAP)
   - Additional options (templates, features)
3. **Generate project structure** using:
   - MCP server (@ui5/mcp-server) if available
   - Reference templates as fallback
4. **Customize configuration**:
   - manifest.json with your settings
   - ui5.yaml for UI5 tooling
   - package.json with scripts and dependencies
5. **Validate project**:
   - Check file structure
   - Verify configuration
   - Test compilation
6. **Provide next steps**:
   - Installation instructions
   - Development server command
   - Testing and deployment guidance

---

{{#if type}}
**Parameters provided**:
- Type: `{{type}}`
{{#if lang}}- Language: `{{lang}}`{{/if}}
{{#if backend}}- Backend: `{{backend}}`{{/if}}
{{#if name}}- Name: `{{name}}`{{/if}}

The agent will use these as defaults and may ask for additional details.
{{else}}
**No parameters provided** - The agent will guide you through an interactive setup.
{{/if}}

---

**Route to ui5-app-scaffolder when available.**

Expected output: generated project files, validation notes, and complete setup instructions.

---

## Manual Scaffolding (If Agent Unavailable)

If the agent is unavailable, use these manual commands:

### Using @ui5/mcp-server (Recommended)

```bash
# Install MCP server globally
npm install -g @ui5/mcp-server@0.2.17

# Create freestyle JavaScript app
npx @ui5/mcp-server@0.2.17 create_ui5_app \
  --template="freestyle-js" \
  --projectName="my-ui5-app" \
  --namespace="com.mycompany.myapp"

# Create Fiori Elements TypeScript app
npx @ui5/mcp-server@0.2.17 create_ui5_app \
  --template="fiori-elements-list-report-ts" \
  --projectName="my-fiori-app" \
  --namespace="com.mycompany.fiori" \
  --enableCAP=true
```

### Using Yeoman (Alternative)

```bash
# Install Yeoman and Easy-UI5 generator
npm install -g yo generator-easy-ui5

# Interactive scaffolding
yo easy-ui5
```

### Using Fiori Tools (SAP Business Application Studio)

```bash
# Install Fiori Tools globally
npm install -g @sap/generator-fiori

# Create project
yo @sap/fiori
```

### Template Options

**Freestyle Applications**:
- `freestyle-js` - JavaScript freestyle app
- `freestyle-ts` - TypeScript freestyle app

**Fiori Elements**:
- `fiori-elements-list-report` - List Report (most common)
- `fiori-elements-object-page` - Object Page
- `fiori-elements-analytical-list-page` - Analytical List Page
- `fiori-elements-overview-page` - Overview Page
- `fiori-elements-worklist` - Worklist

**Integration Cards**:
- `integration-card-list` - List Card
- `integration-card-table` - Table Card
- `integration-card-object` - Object Card
- `integration-card-timeline` - Timeline Card
- `integration-card-analytical` - Analytical Card

**CAP Full-Stack**:
- `cap-full-stack-js` - CAP backend + UI5 frontend (JavaScript)
- `cap-full-stack-ts` - CAP backend + UI5 frontend (TypeScript)

---

## Quick Start Examples

### Example 1: Freestyle TypeScript App

```bash
/ui5-scaffold --type=freestyle --lang=typescript --name=product-catalog
```

Creates:
```
product-catalog/
├── webapp/
│   ├── controller/
│   │   └── App.controller.ts
│   ├── view/
│   │   └── App.view.xml
│   ├── Component.ts
│   ├── manifest.json
│   └── index.html
├── ui5.yaml
├── package.json
└── tsconfig.json
```

### Example 2: Fiori Elements with CAP

```bash
/ui5-scaffold --type=fiori-elements --backend=cap --name=orders-app
```

Creates full-stack application with:
- Frontend: Fiori Elements List Report
- Backend: CAP service with sample data

### Example 3: Integration Card

```bash
/ui5-scaffold --type=integration-card --name=sales-dashboard
```

Creates standalone Integration Card for Work Zone or launchpad.

---

## After Scaffolding

Once your project is created, follow these steps:

### 1. Install Dependencies
```bash
cd {{name}}
npm install
```

### 2. Start Development Server
```bash
npm start
# or
ui5 serve --port 8080
```

### 3. Open in Browser
```
http://localhost:8080
```

### 4. Start Developing
- Add views: `webapp/view/`
- Add controllers: `webapp/controller/`
- Add translations: `webapp/i18n/i18n.properties`
- Add styles: `webapp/css/style.css`

### 5. Build for Production
```bash
npm run build
# Output in dist/
```

---

## Need Help?

After scaffolding, use these commands:
- **API Reference**: `/ui5-api sap.m.Table`
- **Code Quality**: Invoke code quality advisor
- **Migration**: If upgrading later, use migration specialist

Happy coding! 🚀
