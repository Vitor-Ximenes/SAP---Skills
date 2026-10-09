---
name: cap-deployment-checklist
description: Pre-deployment validation checklist for CAP applications to Cloud Foundry, Kyma, or local environments
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
argument-hint: "[cf|kyma|local]"
arguments:
  - name: target
    description: "Deployment target: cf (Cloud Foundry), kyma, or local"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a deployment readiness checklist, blockers, environment assumptions, safe validation commands, and pending tenant/system checks. Default to read-only guidance and do not deploy or modify services.


# CAP Deployment Checklist

Comprehensive pre-deployment validation checklist for SAP Cloud Application Programming Model applications.

## Universal Checklist (All Targets)

### 1. Code Quality
- [ ] All tests passing (`npm test`)
- [ ] No ESLint warnings
- [ ] No hardcoded credentials
- [ ] Environment variables configured

### 2. Database
- [ ] Schema migrations tested
- [ ] Indexes added for frequently queried fields
- [ ] Database backups configured

### 3. Security
- [ ] Authentication enabled (XSUAA)
- [ ] Authorization rules defined (@restrict)
- [ ] Input validation implemented
- [ ] Rate limiting configured

### 4. Performance
- [ ] Query optimization validated
- [ ] Pagination implemented for large datasets
- [ ] Response times validated

### 5. Monitoring
- [ ] Structured logging implemented
- [ ] Health check endpoint available
- [ ] Error tracking configured

## Cloud Foundry Checklist

### MTA Configuration
- [ ] mta.yaml syntax validated (`mbt validate`)
- [ ] All required resources defined
- [ ] Environment variables set

**Validate**:
```bash
mbt validate mta.yaml
```

### Service Bindings
- [ ] HANA Cloud service created
- [ ] XSUAA service created
- [ ] All services bound in mta.yaml

**Verify**:
```bash
cf services
```

### Build & Deployment
- [ ] MTA archive built successfully
- [ ] Archive size < 1GB

**Build**:
```bash
mbt build -t ./mta_archives
```

**Deploy**:
```bash
cf deploy mta_archives/my-app_1.0.0.mtar
```

## Local Deployment Checklist

### Environment Setup
- [ ] Node.js 18+ installed
- [ ] Dependencies installed (`npm install`)
- [ ] SQLite database deployed
- [ ] Environment variables set

### Testing
- [ ] `cds watch` starts without errors
- [ ] Service endpoints accessible (http://localhost:4004)
- [ ] Sample data loaded

## Common Issues & Solutions

### Issue: "Application failed to start"
**Check**:
1. Logs: `cf logs <app> --recent`
2. Environment: `cf env <app>`
3. Services: `cf services`

### Issue: "Database connection failed"
**Check**:
1. HDI container deployed
2. Service binding correct
3. Database service running

## Deployment Commands

### Cloud Foundry
```bash
# Login
cf login -a <api-endpoint>

# Build MTA
mbt build -t ./mta_archives

# Deploy
cf deploy mta_archives/my-app_1.0.0.mtar

# Check status
cf apps
```

### Local
```bash
# Deploy database
cds deploy --to sqlite:db/data.db

# Start server
cds watch
```

## References

- [Deployment Guide](../skills/sap-cap-capire/references/deployment-cf.md)
- [MTA Template](../skills/sap-cap-capire/templates/mta.yaml)
- [xs-security.json Template](../skills/sap-cap-capire/templates/xs-security.json)
