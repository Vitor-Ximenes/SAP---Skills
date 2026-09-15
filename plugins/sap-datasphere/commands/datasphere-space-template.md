---
name: datasphere-space-template
description: Generate SAP Datasphere space configuration templates with best practices
allowed-tools:
  - Read
  - AskUserQuestion
argument-hint: "<development|analytics|integration|sandbox>"
arguments:
  - name: type
    description: "Space type: development, analytics, integration, sandbox"
    required: true
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a space design template, role/quota/data access assumptions, setup sequence, and confirmation points before tenant changes. Default to planning and read-only validation; do not create tenant spaces or local files unless the user explicitly confirms the target.


Generate a Datasphere space configuration template based on the requested type.

## Available Space Types

### development
Development space for modelers with full build permissions and moderate resources.

### analytics
Production analytics space with optimized settings for consumption and reporting.

### integration
Integration space focused on data loading, replication, and ETL processes.

### sandbox
Sandbox space with minimal resources for experimentation and learning.

---

## Template: development

### Space Configuration (Development)

```json
{
  "space": {
    "spaceId": "DEV_<PROJECT>",
    "businessName": "Development - <Project Name>",
    "description": "Development environment for data modeling and testing",
    "settings": {
      "storage": {
        "diskSize": "50 GB",
        "recommendation": "Start with 50GB, expand as needed based on data volume"
      },
      "memory": {
        "inMemory": "16 GB",
        "recommendation": "Sufficient for development workloads"
      },
      "priority": 4,
      "priorityNote": "Medium priority (1=highest, 8=lowest)"
    }
  }
}
```

### Recommended Roles

| Role | Users | Purpose |
|------|-------|---------|
| DW Space Administrator | 1-2 | Space configuration and management |
| DW Modeler | 3-5 | Create views, models, flows |
| DW Integrator | 1-2 | Configure connections, run flows |
| DW Viewer | As needed | View shared objects |

### Best Practices

1. **Naming Convention**: Use `DEV_` prefix for all development spaces
2. **Connections**: Create development-specific connections (don't share with production)
3. **Data Access Controls**: Implement early to test security
4. **Content Transport**: Export regularly for backup
5. **Documentation**: Maintain README in space for onboarding

### Space Properties to Configure

```yaml
Space Properties:
  Allow Public Objects: true        # Enable sharing to other spaces
  Enable Audit Logging: true        # Track changes for debugging
  Data Integration Monitor: Enabled # Monitor flows and tasks
  Database User Access: Optional    # Create if external tools needed
```

---

## Template: analytics

### Space Configuration (Analytics/Production)

```json
{
  "space": {
    "spaceId": "PROD_ANALYTICS",
    "businessName": "Analytics - Production",
    "description": "Production analytics space for SAC and external consumption",
    "settings": {
      "storage": {
        "diskSize": "200 GB",
        "recommendation": "Size based on analytic model data volumes"
      },
      "memory": {
        "inMemory": "64 GB",
        "recommendation": "Higher for concurrent query performance"
      },
      "priority": 2,
      "priorityNote": "High priority for production analytics"
    }
  }
}
```

### Recommended Roles

| Role | Users | Purpose |
|------|-------|---------|
| DW Space Administrator | 1-2 | Space management only |
| DW Modeler | 2-3 | Limited for maintenance |
| DW Viewer | Many | Consume analytics |

### Best Practices

1. **Naming Convention**: Use `PROD_` prefix for production spaces
2. **View Persistence**: Enable for frequently accessed views
3. **Statistics**: Keep table statistics up-to-date
4. **Monitoring**: Set up alerts for resource usage
5. **Access Controls**: Production data access controls required

### Analytic Model Optimization

```yaml
Optimization Settings:
  View Persistence:
    - Snapshot: For slowly changing data (refresh daily/weekly)
    - Real-time: For frequently changing data (use sparingly)

  Partition Strategy:
    - Time-based: For historical analysis (Year/Month)
    - Range: For large datasets with clear boundaries

  Statistics:
    - Enable automatic statistics collection
    - Schedule during off-peak hours
```

---

## Template: integration

### Space Configuration (Integration)

```json
{
  "space": {
    "spaceId": "INT_<SOURCE>",
    "businessName": "Integration - <Source System>",
    "description": "Data integration hub for <source> data ingestion",
    "settings": {
      "storage": {
        "diskSize": "500 GB",
        "recommendation": "Large storage for raw/staging data"
      },
      "memory": {
        "inMemory": "32 GB",
        "recommendation": "Moderate memory for transformation"
      },
      "priority": 3,
      "priorityNote": "Medium-high for ETL workloads"
    }
  }
}
```

### Recommended Roles

| Role | Users | Purpose |
|------|-------|---------|
| DW Space Administrator | 1-2 | Space management |
| DW Integrator | 3-5 | Primary role for integration work |
| DW Modeler | 2-3 | Staging layer modeling |
| DW Viewer | Limited | Troubleshooting only |

### Best Practices

1. **Naming Convention**: Use `INT_` prefix with source system name
2. **Layered Architecture**:
   - `raw_` prefix: Unchanged source data
   - `stg_` prefix: Cleansed staging data
   - `out_` prefix: Output to analytics spaces
3. **Task Chains**: Orchestrate complex ETL workflows
4. **Error Handling**: Configure email notifications
5. **Logging**: Enable detailed flow logging

### Integration Architecture

```yaml
Data Layers:
  Raw Layer:
    - Tables: raw_<source>_<entity>
    - Purpose: Land data unchanged
    - Retention: Keep for audit trail

  Staging Layer:
    - Views: stg_<source>_<entity>
    - Purpose: Cleanse, validate, transform
    - Pattern: Graphical views with filters

  Output Layer:
    - Views: out_<entity>
    - Purpose: Share to analytics spaces
    - Semantic: Set to Relational Dataset
```

---

## Template: sandbox

### Space Configuration (Sandbox)

```json
{
  "space": {
    "spaceId": "SANDBOX_<USER>",
    "businessName": "Sandbox - <User/Team>",
    "description": "Experimental sandbox for prototyping",
    "settings": {
      "storage": {
        "diskSize": "10 GB",
        "recommendation": "Minimal for experimentation"
      },
      "memory": {
        "inMemory": "8 GB",
        "recommendation": "Basic allocation"
      },
      "priority": 8,
      "priorityNote": "Lowest priority (won't impact production)"
    }
  }
}
```

### Recommended Roles

| Role | Users | Purpose |
|------|-------|---------|
| DW Space Administrator | 1 | Space owner |
| DW Modeler | 1-2 | Experiment with modeling |
| DW Integrator | 1 | Test integrations |

### Best Practices

1. **Naming Convention**: Use `SANDBOX_` prefix with owner identifier
2. **Isolation**: No connections to production systems
3. **Cleanup**: Regular cleanup policy (monthly)
4. **Sample Data**: Use sample datasets or anonymized data
5. **Documentation**: Track experiments and learnings

### Sandbox Policies

```yaml
Sandbox Policies:
  Data:
    - Use sample content package
    - Anonymize production data if needed
    - No real PII/sensitive data

  Resources:
    - Monitor for runaway processes
    - Auto-kill long-running queries (30 min)

  Lifecycle:
    - Review monthly
    - Archive unused sandboxes quarterly
```

---

## CLI Commands for Space Management

### Create Space
```bash
datasphere spaces create \
  --space-id "DEV_PROJECT" \
  --business-name "Development - Project" \
  --disk-storage 50 \
  --memory 16
```

### Allocate Storage
```bash
datasphere spaces update \
  --space "DEV_PROJECT" \
  --disk-storage 100
```

### Assign Users
```bash
datasphere spaces users add \
  --space "DEV_PROJECT" \
  --user "user@company.com" \
  --role "DW Modeler"
```

---

Provide the appropriate template based on the user's requested type. Customize the space ID, names, and sizes based on their specific project requirements.
