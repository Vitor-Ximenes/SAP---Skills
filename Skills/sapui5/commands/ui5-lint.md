---
name: ui5-lint
description: Run UI5 linter on project with optional auto-fix and code quality analysis
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
  - mcp__plugin_sapui5_ui5-tooling__run_ui5_linter
argument-hint: "[--fix] [file] [severity]"
arguments:
  - name: fix
    description: Apply automatic fixes (true/false). Default is false - shows issues only.
    required: false
  - name: file
    description: Optional specific file or pattern to lint (e.g., webapp/controller/Main.controller.js)
    required: false
  - name: severity
    description: Minimum severity level to report (critical, high, medium, low). Default is medium.
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return lint findings, severity, file references, safe fix suggestions, and tool availability. Default to analysis-only unless `--fix` is explicitly requested and exact target files are confirmed.


# UI5 Code Linting{{#if file}} for {{file}}{{/if}}

{{#if fix}}
Running linter with **auto-fix enabled**...
{{else}}
Running linter (analysis only, no fixes applied)...
{{/if}}

{{#if severity}}
Reporting issues of severity: **{{severity}}** and above
{{/if}}

## Code Quality Analysis

Use the **ui5-code-quality-advisor agent** to analyze the code when available.

The agent will:

1. **Scan codebase** for quality issues:
   {{#if file}}- Target: `{{file}}`{{else}}- All files: controllers, views, models, components{{/if}}
2. **Run linter**:
   - Try MCP `run_ui5_linter` first (fastest, most accurate)
   - Fall back to manual pattern matching if MCP unavailable
3. **Categorize issues**:
   - **CRITICAL**: Security vulnerabilities, broken functionality
   - **HIGH**: Deprecated APIs, major performance issues
   - **MEDIUM**: Best practice violations, maintainability
   - **LOW**: Code style, minor improvements
4. **Generate report**:
   - Issue count by severity and category
   - Specific file locations (file:line)
   - Before/after code examples
   - Fix recommendations
   - Effort estimates
5. **{{#if fix}}Apply fixes**:
   - Auto-fix CRITICAL and HIGH issues (with your approval)
   - Re-validate after fixes
   - Report improvements
{{else}}**Provide recommendations**:
   - You can approve fixes if desired
   - Or apply manually based on report
{{/if}}

---

**Parameters**:
{{#if fix}}- **Auto-fix**: ENABLED ⚠️ (code will be modified){{else}}- **Auto-fix**: DISABLED (analysis only){{/if}}
{{#if file}}- **Target**: `{{file}}`{{else}}- **Target**: Entire project{{/if}}
{{#if severity}}- **Min Severity**: {{severity}}{{else}}- **Min Severity**: medium (default){{/if}}

---

**Route to ui5-code-quality-advisor when available.**

Expected output: a detailed quality report with findings, locations, severity, and fix recommendations.

---

## Manual Linting (If Agent Unavailable)

If the agent is unavailable, run these manual commands:

### Using @ui5/mcp-server (Recommended)

```bash
# Lint entire project
npx @ui5/mcp-server@0.2.17 run_ui5_linter --config=ui5-linter.config.js

# Lint specific file
npx @ui5/mcp-server@0.2.17 run_ui5_linter --files={{file}}

# Lint with auto-fix
npx @ui5/mcp-server@0.2.17 run_ui5_linter --fix
```

### Using @ui5/linter CLI

```bash
# Install linter
npm install --save-dev @ui5/linter

# Lint project
ui5 lint

# Lint with details
ui5 lint --details

# Lint specific file
ui5 lint {{file}}
```

### Common Issues to Check Manually

#### 1. Deprecated APIs
```bash
# jQuery.sap.* (deprecated since 1.58)
grep -r "jQuery\.sap\." webapp/

# sap.ui.commons.* (deprecated)
grep -r "sap\.ui\.commons\." webapp/

# Synchronous loading
grep -r "sap\.ui\.requireSync" webapp/
```

#### 2. Security Issues
```bash
# Direct DOM manipulation (XSS risk)
grep -r "\.innerHTML" webapp/
grep -r "\.html\(" webapp/

# eval() usage (CSP violation)
grep -r "eval\(" webapp/
grep -r "new Function\(" webapp/

# Inline scripts
grep -r "<script>" webapp/view/*.xml
```

#### 3. Performance Issues
```bash
# Non-virtualized lists
grep -r "<m:List.*items=\"{/.*}\"" webapp/view/*.xml

# Missing $select (inefficient queries)
grep -r "path: '/'" webapp/view/*.xml | grep -v "$select"
```

#### 4. Accessibility Issues
```bash
# Missing ARIA labels
grep -r "<Button" webapp/view/*.xml | grep -v "ariaLabel"
grep -r "<Image" webapp/view/*.xml | grep -v "alt="
```

### ESLint Configuration (Additional)

For deeper JavaScript/TypeScript linting:

**Install ESLint**:
```bash
npm install --save-dev eslint @sap/eslint-plugin-ui5-jsdocs
```

**.eslintrc.json**:
```json
{
  "env": {
    "browser": true,
    "es2022": true
  },
  "extends": [
    "eslint:recommended"
  ],
  "plugins": ["@sap/ui5-jsdocs"],
  "parserOptions": {
    "ecmaVersion": 2022,
    "sourceType": "module"
  },
  "rules": {
    "no-console": "warn",
    "no-debugger": "error",
    "no-eval": "error",
    "@sap/ui5-jsdocs/no-jsdoc": "warn"
  },
  "globals": {
    "sap": "readonly",
    "jQuery": "readonly"
  }
}
```

**Run ESLint**:
```bash
npx eslint webapp/
```

---

## Typical Linter Output

The agent will provide a report similar to this:

```
# Code Quality Report

Files Reviewed: 45
Total Issues: 23

| Severity | Count |
|----------|-------|
| CRITICAL | 2     |
| HIGH     | 5     |
| MEDIUM   | 12    |
| LOW      | 4     |

## CRITICAL Issues

### 1. XSS Vulnerability
File: webapp/controller/Main.controller.js:67
Issue: Direct DOM manipulation bypasses XSS protection
Fix: Use data binding instead of innerHTML

### 2. CSP Violation
File: webapp/view/Main.view.xml:12
Issue: Inline script violates Content Security Policy
Fix: Move script to controller or Component.js

## HIGH Issues

### 3. Deprecated API
File: webapp/controller/ProductList.controller.js:23
Issue: jQuery.sap.require deprecated since UI5 1.58
Fix: Replace with sap.ui.define

... (more issues) ...

## Recommendations

Would you like me to apply automatic fixes for CRITICAL and HIGH issues?
```

---

## Severity Levels Explained

**CRITICAL** (Must fix before deployment):
- Security vulnerabilities (XSS, injection, eval)
- CSP violations in production
- Broken functionality
- Data corruption risks

**HIGH** (Fix soon):
- Deprecated APIs scheduled for removal
- Major performance issues (>500ms delay)
- Accessibility violations (WCAG A)
- Missing error handling
- Memory leaks

**MEDIUM** (Should fix):
- Minor performance issues
- Code style violations
- Best practice deviations
- Missing tests
- WCAG AA violations

**LOW** (Nice to have):
- Code organization improvements
- Better variable names
- Documentation gaps
- WCAG AAA enhancements

---

## Quick Fix Commands

After seeing the linter report, you can:

**Fix all issues**:
```bash
/ui5-lint --fix
```

**Fix only specific severity**:
```bash
/ui5-lint --fix --severity=critical
```

**Fix specific file**:
```bash
/ui5-lint --fix --file=webapp/controller/Main.controller.js
```

**Review before fixing**:
```bash
/ui5-lint  # See issues first
# Then approve fixes when prompted
```

---

## Integration with Development Workflow

### Pre-commit Hook (Recommended)

Add linting to git pre-commit:

**.husky/pre-commit**:
```bash
#!/bin/sh
npm run lint
```

**package.json**:
```json
{
  "scripts": {
    "lint": "ui5 lint"
  }
}
```

### CI/CD Pipeline

Include in your CI pipeline:

**.github/workflows/quality.yml**:
```yaml
- name: Lint UI5 Code
  run: ui5 lint --details
```

### VS Code Integration

Install UI5 extension for real-time linting:
- Extension: "SAPUI5 Extension" or "UI5 Language Assistant"
- Provides inline linting as you type

---

## Need More Help?

- **Code Review**: Invoke code quality advisor for comprehensive review
- **Refactoring**: If many issues, consider architecture review
- **Migration**: If deprecated APIs found, use migration specialist

Happy linting! 🔍
