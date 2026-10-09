---
name: cap-troubleshooter
description: Diagnostic guide for common CAP errors and issues with solutions
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[syntax|deployment|database|auth|performance]"
arguments:
  - name: error_category
    description: "Error category: syntax, deployment, database, auth, or performance"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return the likely root cause, evidence, diagnostic steps, safe local commands, and pending runtime checks. Default to read-only troubleshooting and ask before changing project or tenant state.


# CAP Troubleshooter

Diagnostic guide for common SAP Cloud Application Programming Model errors and issues.

## Syntax Errors

### Error: "Expected ';' but found..."
**Cause**: Missing semicolon in CDS definition

**Solution**:
```cds
// ✓ CORRECT
entity Books {
  title : String(100);
  price : Decimal(10,2);
}
```

### Error: "Association target not found"
**Cause**: Referenced entity doesn't exist or isn't imported

**Solution**:
```cds
using { Authors } from './schema';
entity Books {
  author : Association to Authors;
}
```

## Deployment Errors

### Error: "Application failed to start"
**Diagnostic Steps**:
1. Check Cloud Foundry logs: `cf logs <app-name> --recent`
2. Verify environment variables: `cf env <app-name>`
3. Check service bindings: `cf services`

### Error: "Could not build MTA project"
**Solution**:
```bash
# Validate MTA descriptor
mbt validate mta.yaml

# Clean build
mkdir -p .cap-cleanup
mv mta_archives .mta.* gen .cap-cleanup/ 2>/dev/null || true
mbt build
```

Only delete `.cap-cleanup/` after confirming it contains generated build artifacts and no hand-written files.

## Database Errors

### Error: "ER_NO_SUCH_TABLE: Table 'Books' doesn't exist"
**Cause**: Database not deployed

**Solution**:
```bash
# Deploy database schema
cds deploy --to sqlite:db/data.db

# For HANA
cds deploy --to hana
```

## Authentication Errors

### Error: "401 Unauthorized"
**Cause**: Missing or invalid JWT token

**Solution**:
```json
// package.json - Enable authentication
{
  "cds": {
    "requires": {
      "auth": { "kind": "xsuaa" }
    }
  }
}
```

## Performance Issues

### Issue: Slow Query Execution
**Solution**:
```javascript
// ✓ FAST: Single query with expand
const books = await SELECT.from(Books).columns(b => {
  b.*, b.author(a => a.*)
});
```

## Getting Help

1. **Check CAP Documentation**: https://cap.cloud.sap/docs/
2. **Use MCP Tools**: Ask cap-performance-debugger agent
3. **Enable Debug Logging**: `export DEBUG=*`
