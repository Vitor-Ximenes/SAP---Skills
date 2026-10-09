---
name: ui5-version
description: Display UI5 version information, support status, release notes, and upgrade paths
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[version]"
arguments:
  - name: version
    description: Optional UI5 version to query (e.g., 1.120.0, 1.108, latest). Defaults to project version.
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return detected or requested UI5 version details, support status, upgrade notes, local evidence, and unresolved package/source checks. Default to read-only reporting.


# UI5 Version Information{{#if version}} for {{version}}{{/if}}

{{#if version}}
Fetching information for UI5 version **{{version}}**...
{{else}}
Detecting project UI5 version and fetching information...
{{/if}}

## Version Detection

First, let me determine which UI5 version to query:

```bash
# Check project manifest.json
if [ -f "webapp/manifest.json" ]; then
  VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("webapp/manifest.json", "utf8"); const m = t.match(/"minUI5Version"\s*:\s*"([^"]+)"/); console.log(m ? m[1] : "Not found");')
  echo "Project UI5 Version (manifest.json): $VERSION"
fi

# Check ui5.yaml
if [ -f "ui5.yaml" ]; then
  YAML_VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("ui5.yaml", "utf8"); const m = t.match(/^\s*version:\s*"([^"]+)"/m); console.log(m ? m[1] : "Not found");')
  echo "UI5 Tooling Version (ui5.yaml): $YAML_VERSION"
fi

# Check package.json
if [ -f "package.json" ]; then
  PKG_VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("package.json", "utf8"); const m = t.match(/"@ui5\/cli"\s*:\s*"\^?([^"]+)"/); console.log(m ? m[1] : "Not found");')
  echo "UI5 CLI Version (package.json): $PKG_VERSION"
fi
```

{{#if version}}
Using specified version: **{{version}}**
{{else}}
Using detected project version (or latest if no project found)
{{/if}}

---

## Fetching Version Information

Use the **ui5-migration-specialist agent** or MCP tools to fetch:

1. **Version Details**:
   - Release date
   - Support status (active, maintenance, end-of-life)
   - Latest patch version
2. **Release Notes**:
   - New features
   - Breaking changes
   - Deprecations
   - Bug fixes
3. **Upgrade Path**:
   - Recommended upgrade route
   - Migration complexity
   - Required changes
4. **Compatibility**:
   - Browser support
   - Framework compatibility
   - TypeScript support

---

**Querying version information...**

Expected output: comprehensive version details from the agent or MCP tool.

---

## Manual Version Lookup (If Tools Unavailable)

If automated tools are unavailable, here's manual lookup information:

### Project Version Detection

```bash
# Detect from manifest.json
MANIFEST_VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("webapp/manifest.json", "utf8"); const m = t.match(/"minUI5Version"\s*:\s*"([^"]+)"/); console.log(m ? m[1] : "Not found");' 2>/dev/null || echo "Not found")

# Detect from ui5.yaml
YAML_VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("ui5.yaml", "utf8"); const m = t.match(/^\s*version:\s*"([^"]+)"/m); console.log(m ? m[1] : "Not found");' 2>/dev/null || echo "Not found")

# Detect from index.html (bootstrap script)
HTML_VERSION=$(node -e 'const fs = require("fs"); const t = fs.readFileSync("webapp/index.html", "utf8"); const m = t.match(/ui5\.sap\.com\/([0-9.]+)/); console.log(m ? m[1] : "Not found");' 2>/dev/null || echo "Not found")

echo "Detected Versions:"
echo "  manifest.json: $MANIFEST_VERSION"
echo "  ui5.yaml:      $YAML_VERSION"
echo "  index.html:    $HTML_VERSION"
```

### UI5 Version Resources

**Official Version Overview**:
```
https://ui5.sap.com/versionoverview.html
```

**Release Notes**:
```
https://ui5.sap.com/#/releasenotes
```

**Version-Specific Documentation**:
```
https://ui5.sap.com/{{version}}/
```

### UI5 Version Timeline

**Latest Versions** (as of 2025):
- **1.120.x** (Latest Stable) - Released 2024 Q4
  - Status: Active development
  - Support: Full support
  - TypeScript: Full support with @types/openui5
  - Recommended for new projects

- **1.108.x** (LTS - Long-Term Maintenance)
  - Status: Maintenance mode
  - Support: Extended until 2026
  - Recommended for enterprise projects
  - Stable, well-tested

- **1.84.x** (Older LTS)
  - Status: Limited support
  - Support: Ending soon
  - Upgrade recommended

- **1.71.x and earlier**
  - Status: End of life
  - Support: None
  - Upgrade required

### Support Status Explained

**Active Development**:
- New features added
- Regular updates
- Full support
- Recommended for new projects

**Maintenance**:
- Critical bug fixes only
- Security patches
- No new features
- Stable for production

**End of Life (EOL)**:
- No updates
- No support
- Security risks
- Upgrade immediately

### Breaking Changes by Version

**1.120.x** (from 1.108):
- Removed deprecated sap.ui.commons controls
- Changed default theme to Horizon
- Updated TypeScript definitions
- Modified OData batch handling

**1.108.x** (from 1.84):
- Removed jQuery.sap.* APIs
- Changed async loading requirements
- Updated Fiori Elements templates
- Modified manifest.json schema

**1.84.x** (from 1.71):
- Removed legacy controls
- Changed routing patterns
- Updated data binding syntax

### Upgrade Recommendations

**From 1.84 → 1.120** (Recommended):
```bash
# Estimated effort: 2-4 hours
# Complexity: Medium
# Breaking changes: ~7 API changes
# Use migration specialist for guided upgrade
```

**From 1.108 → 1.120** (Easy):
```bash
# Estimated effort: 1-2 hours
# Complexity: Low
# Breaking changes: ~2 API changes
# Mostly theme and minor API updates
```

**From 1.71 → 1.120** (Complex):
```bash
# Estimated effort: 8-16 hours
# Complexity: High
# Breaking changes: ~25 API changes
# Phased migration recommended: 1.71 → 1.84 → 1.108 → 1.120
```

---

## Version Comparison

### UI5 1.120.x (Latest)

**Pros**:
- Latest features (Horizon theme, improved performance)
- TypeScript first-class support
- OData v4 optimizations
- Active development and support
- Best documentation

**Cons**:
- Newer, less battle-tested
- May have occasional bugs
- Requires more frequent updates

**Best for**:
- New projects
- Projects needing latest features
- TypeScript projects
- Modern development

### UI5 1.108.x (LTS)

**Pros**:
- Long-term support (until 2026)
- Very stable
- Well-tested in production
- Fewer breaking changes
- Enterprise-friendly

**Cons**:
- Missing latest features
- Older theme (Quartz by default)
- Eventually needs migration

**Best for**:
- Enterprise projects
- Risk-averse organizations
- Long-term stability requirements
- Projects with infrequent updates

### UI5 1.84.x (Older LTS)

**Pros**:
- Familiar for existing developers
- Stable (but aging)

**Cons**:
- Support ending soon
- Missing many modern features
- Deprecations present
- Should upgrade

**Best for**:
- Legacy projects (temporarily)
- Planning migration to 1.108/1.120

---

## Version-Specific Features

### UI5 1.120+ Features
- **Horizon Theme**: Modern, accessible SAP Horizon design
- **TypeScript**: Enhanced type definitions
- **OData v4**: Improved batch operations
- **Performance**: 15-20% faster rendering
- **Accessibility**: WCAG 2.1 AAA improvements

### UI5 1.108+ Features
- **Fiori Elements v4**: Metadata-driven apps
- **Flexible Column Layout**: Responsive master-detail
- **Variants**: Save user UI preferences
- **Draft Handling**: OData draft support

### UI5 1.84+ Features
- **Modern Controls**: sap.m library matured
- **Responsive Layouts**: Grid, Splitter
- **Data Binding**: Two-way binding enhancements

---

## Check for Updates

To check if updates available:

```bash
# Check npm for latest @ui5/cli
npm outdated @ui5/cli

# Check UI5 CDN for latest version
curl -s https://ui5.sap.com/version.json

# Check OpenUI5 releases
curl -s https://openui5.org/releases/
```

---

## Upgrade Path Decision Tree

```
Current Version?
├─ 1.120.x → You're up to date! ✅
├─ 1.108.x → Consider upgrading to 1.120 (optional, low effort)
├─ 1.84.x  → Upgrade recommended: 1.84 → 1.108 → 1.120
└─ < 1.84  → Upgrade required: Phased migration to 1.120
```

**Need help upgrading?**
```bash
# For automatic migration assistance
# Invoke migration specialist agent

# Or use migration specialist directly
# It will analyze your project and create migration plan
```

---

## Quick Commands

**Check project version**:
```bash
node -e 'const fs = require("fs"); const t = fs.readFileSync("webapp/manifest.json", "utf8"); const m = t.match(/"minUI5Version"\s*:\s*"([^"]+)"/); console.log(m ? m[1] : "Not found");' 2>/dev/null || echo "Not found"
```

**List all UI5 versions**:
```
https://ui5.sap.com/versionoverview.html
```

**View release notes**:
```bash
# For specific version
/ui5-version --version=1.120.0

# Or visit
https://ui5.sap.com/#/releasenotes.html
```

**Start migration**:
```bash
# Invoke migration specialist for upgrade
# "Migrate to UI5 1.120"
```

---

## Need More Information?

- **Upgrade guidance**: Invoke migration specialist agent
- **Breaking changes**: See `/references/migration-patterns.md`
- **API changes**: Use `/ui5-api` to check new APIs
- **Code compatibility**: Use `/ui5-lint` to find issues

Stay up to date for best performance, security, and features! 🚀
