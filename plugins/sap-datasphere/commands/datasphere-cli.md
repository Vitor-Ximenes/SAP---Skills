---
name: datasphere-cli
description: SAP Datasphere CLI command reference with examples
allowed-tools:
  - Read
  - Bash
argument-hint: "<auth|spaces|objects|tasks|users|marketplace>"
arguments:
  - name: command
    description: "Command category: auth, spaces, objects, tasks, users, marketplace"
    required: true
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return the relevant Datasphere CLI command set, authentication assumptions, safe read-only examples first, and tenant actions that require confirmation. Do not execute mutating CLI commands by default.


Generate CLI command reference for the specified category.

## Available Command Categories

### auth
Authentication and configuration setup

### spaces
Space management operations

### objects
Modeling object operations (views, tables, flows)

### tasks
Task and task chain management

### users
User and role management

### marketplace
Data Marketplace operations

---

## Reference: auth

### Authentication & Configuration

#### Install CLI

```bash
# Install globally via npm
npm install -g @sap/datasphere-cli

# Verify installation
datasphere --version
```

#### Initial Configuration

```bash
# Configure CLI with interactive prompts
datasphere configure

# Or set directly
datasphere configure set tenant-url https://your-tenant.eu10.hcs.cloud.sap
datasphere configure set oauth-client-id your-client-id
datasphere configure set oauth-client-secret your-client-secret
datasphere configure set oauth-token-url https://your-tenant.authentication.eu10.hana.ondemand.com/oauth/token
```

#### Login

```bash
# Interactive login
datasphere login

# Login with specific profile
datasphere login --profile production

# Check current session
datasphere login status
```

#### Configuration Management

```bash
# View all configuration
datasphere configure list

# View specific setting
datasphere configure get tenant-url

# Use environment variables (alternative)
export DATASPHERE_TENANT_URL=https://your-tenant.eu10.hcs.cloud.sap
export DATASPHERE_OAUTH_CLIENT_ID=your-client-id
export DATASPHERE_OAUTH_CLIENT_SECRET=your-client-secret
```

#### Profile Management

```bash
# Create named profile
datasphere configure --profile dev
datasphere configure --profile prod

# Switch profiles
export DATASPHERE_PROFILE=prod

# Or use per-command
datasphere spaces list --profile prod
```

---

## Reference: spaces

### Space Management Commands

#### List Spaces

```bash
# List all spaces (if admin)
datasphere spaces list

# List spaces with details
datasphere spaces list --output json

# Filter by name pattern
datasphere spaces list --filter "DEV*"
```

#### Get Space Details

```bash
# Get specific space
datasphere spaces read --space DEV_PROJECT

# Get space configuration
datasphere spaces read --space DEV_PROJECT --output json > space-config.json
```

#### Create Space

```bash
# Create new space
datasphere spaces create \
  --space-id "DEV_ANALYTICS" \
  --space-name "Development Analytics" \
  --disk-storage 100 \
  --memory 32

# Create with full options
datasphere spaces create \
  --space-id "PROD_SALES" \
  --space-name "Production Sales" \
  --disk-storage 500 \
  --memory 128 \
  --priority 2 \
  --allow-public-objects true
```

#### Update Space

```bash
# Update storage allocation
datasphere spaces update \
  --space DEV_PROJECT \
  --disk-storage 200

# Update priority
datasphere spaces update \
  --space DEV_PROJECT \
  --priority 3

# Update multiple settings
datasphere spaces update \
  --space DEV_PROJECT \
  --disk-storage 200 \
  --memory 64 \
  --priority 2
```

#### Space User Management

```bash
# List users in space
datasphere spaces users list --space DEV_PROJECT

# Add user to space
datasphere spaces users add \
  --space DEV_PROJECT \
  --user user@company.com \
  --role "DW Modeler"

# Remove user from space
datasphere spaces users remove \
  --space DEV_PROJECT \
  --user user@company.com

# Available roles:
# - DW Space Administrator
# - DW Modeler
# - DW Integrator
# - DW Viewer
```

#### Delete Space

```bash
# Delete space (requires confirmation)
datasphere spaces delete --space SANDBOX_TEST

# Force delete (careful!)
datasphere spaces delete --space SANDBOX_TEST --force
```

---

## Reference: objects

### Modeling Object Commands

#### List Objects

```bash
# List all objects in space
datasphere objects list --space DEV_PROJECT

# List by object type
datasphere objects list --space DEV_PROJECT --type view
datasphere objects list --space DEV_PROJECT --type local-table
datasphere objects list --space DEV_PROJECT --type data-flow

# List with filter
datasphere objects list --space DEV_PROJECT --filter "fact_*"

# Output as JSON
datasphere objects list --space DEV_PROJECT --output json
```

#### Read Object Definition

```bash
# Get object definition
datasphere objects read \
  --space DEV_PROJECT \
  --technical-name fact_sales

# Export to file (CSN/JSON format)
datasphere objects read \
  --space DEV_PROJECT \
  --technical-name fact_sales \
  --output json > fact_sales.json
```

#### Create/Update Objects

```bash
# Create object from definition file
datasphere objects create \
  --space DEV_PROJECT \
  --definition-file ./views/fact_sales.json

# Update existing object
datasphere objects update \
  --space DEV_PROJECT \
  --technical-name fact_sales \
  --definition-file ./views/fact_sales_v2.json
```

#### Deploy Objects

```bash
# Deploy single object
datasphere objects deploy \
  --space DEV_PROJECT \
  --technical-name fact_sales

# Deploy with dependencies
datasphere objects deploy \
  --space DEV_PROJECT \
  --technical-name fact_sales \
  --include-dependencies
```

#### Delete Objects

```bash
# Delete object
datasphere objects delete \
  --space DEV_PROJECT \
  --technical-name old_view

# Delete multiple objects
datasphere objects delete \
  --space DEV_PROJECT \
  --technical-name "temp_*" \
  --pattern
```

#### Export/Import Objects

```bash
# Export to package
datasphere objects export \
  --space DEV_PROJECT \
  --objects fact_sales,dim_product,dim_customer \
  --output-file ./export/analytics-package.zip

# Import package
datasphere objects import \
  --space PROD_ANALYTICS \
  --input-file ./export/analytics-package.zip \
  --overwrite
```

---

## Reference: tasks

### Task & Task Chain Management

#### List Tasks

```bash
# List all tasks
datasphere tasks list --space DEV_PROJECT

# List task chains
datasphere tasks list --space DEV_PROJECT --type task-chain

# List running tasks
datasphere tasks list --space DEV_PROJECT --status running
```

#### Run Tasks

```bash
# Run single task (replication, flow, etc.)
datasphere tasks run \
  --space DEV_PROJECT \
  --task-name replication_s4hana

# Run task chain
datasphere tasks run \
  --space DEV_PROJECT \
  --task-chain daily_etl

# Run with parameters
datasphere tasks run \
  --space DEV_PROJECT \
  --task-chain daily_etl \
  --parameters '{"date": "2025-01-01"}'
```

#### Monitor Tasks

```bash
# Get task status
datasphere tasks status \
  --space DEV_PROJECT \
  --task-id abc123-def456

# Watch task progress
datasphere tasks watch \
  --space DEV_PROJECT \
  --task-id abc123-def456

# Get task logs
datasphere tasks logs \
  --space DEV_PROJECT \
  --task-id abc123-def456
```

#### Cancel Tasks

```bash
# Cancel running task
datasphere tasks cancel \
  --space DEV_PROJECT \
  --task-id abc123-def456
```

#### Schedule Tasks

```bash
# Create schedule (cron)
datasphere tasks schedule create \
  --space DEV_PROJECT \
  --task-chain daily_etl \
  --cron "0 6 * * *" \
  --timezone "Europe/Berlin"

# List schedules
datasphere tasks schedule list --space DEV_PROJECT

# Delete schedule
datasphere tasks schedule delete \
  --space DEV_PROJECT \
  --schedule-id sched123
```

---

## Reference: users

### User & Role Management

#### List Users

```bash
# List all users (admin only)
datasphere users list

# List with details
datasphere users list --output json

# Filter by email
datasphere users list --filter "*@company.com"
```

#### Get User Details

```bash
# Get specific user
datasphere users read --user user@company.com

# Get user's roles
datasphere users roles --user user@company.com
```

#### Create User

```bash
# Create new user
datasphere users create \
  --user newuser@company.com \
  --first-name John \
  --last-name Doe

# Create with initial role
datasphere users create \
  --user newuser@company.com \
  --first-name John \
  --last-name Doe \
  --global-role "DW Modeler"
```

#### Update User

```bash
# Update user details
datasphere users update \
  --user user@company.com \
  --first-name Jonathan
```

#### Delete User

```bash
# Delete user
datasphere users delete --user olduser@company.com
```

#### Global Role Management

```bash
# List global roles
datasphere global-roles list

# Get role details
datasphere global-roles read --role "DW Administrator"

# Assign global role
datasphere global-roles assign \
  --role "DW Administrator" \
  --user admin@company.com

# Remove global role
datasphere global-roles unassign \
  --role "DW Administrator" \
  --user admin@company.com
```

#### Scoped Roles

```bash
# List scoped roles
datasphere scoped-roles list

# Create scoped role
datasphere scoped-roles create \
  --role-name "Analytics Viewer" \
  --permissions read \
  --spaces "PROD_ANALYTICS,PROD_FINANCE"

# Assign scoped role
datasphere scoped-roles assign \
  --role "Analytics Viewer" \
  --user viewer@company.com
```

---

## Reference: marketplace

### Data Marketplace Commands

#### Data Products

```bash
# List data products
datasphere marketplace products list

# Get product details
datasphere marketplace products read --product-id prod123

# Create data product
datasphere marketplace products create \
  --name "Sales Analytics Package" \
  --description "Complete sales analytics solution" \
  --space PROD_ANALYTICS \
  --objects fact_sales,dim_product,dim_customer

# Update product
datasphere marketplace products update \
  --product-id prod123 \
  --status published

# Delete product
datasphere marketplace products delete --product-id prod123
```

#### Data Providers

```bash
# List data providers
datasphere marketplace providers list

# Get provider details
datasphere marketplace providers read --provider-id prov123

# Update provider contact
datasphere marketplace providers update \
  --provider-id prov123 \
  --contact-email data-team@company.com
```

---

## Scripting Examples

### Batch Space Creation

```bash
#!/bin/bash
# create-spaces.sh

SPACES=("DEV_PROJECT1" "DEV_PROJECT2" "DEV_PROJECT3")
STORAGE=50
MEMORY=16

for SPACE in "${SPACES[@]}"; do
    echo "Creating space: $SPACE"
    datasphere spaces create \
        --space-id "$SPACE" \
        --space-name "Development $SPACE" \
        --disk-storage $STORAGE \
        --memory $MEMORY
done
```

### Export All Objects from Space

```bash
#!/bin/bash
# export-space.sh

SPACE=$1
OUTPUT_DIR="./exports/$SPACE"
mkdir -p "$OUTPUT_DIR"

# Get list of objects
datasphere objects list --space "$SPACE" --output json | \
    jq -r '.[] | .technicalName' | \
    while read OBJ; do
        echo "Exporting: $OBJ"
        datasphere objects read \
            --space "$SPACE" \
            --technical-name "$OBJ" \
            --output json > "$OUTPUT_DIR/$OBJ.json"
    done
```

### CI/CD Integration

```yaml
# .gitlab-ci.yml example
deploy-to-prod:
  stage: deploy
  script:
    - npm install -g @sap/datasphere-cli
    - datasphere configure set tenant-url $DATASPHERE_TENANT_URL
    - datasphere configure set oauth-client-id $DATASPHERE_CLIENT_ID
    - datasphere configure set oauth-client-secret $DATASPHERE_CLIENT_SECRET
    - datasphere objects import --space PROD_ANALYTICS --input-file ./export/package.zip --overwrite
  environment:
    name: production
```

---

## Common Options

| Option | Description |
|--------|-------------|
| `--output json` | Output as JSON |
| `--output table` | Output as table (default) |
| `--profile <name>` | Use named profile |
| `--help` | Show command help |
| `--version` | Show CLI version |
| `--verbose` | Enable verbose logging |
| `--quiet` | Suppress output |

---

Provide the appropriate command reference based on the user's requested category. Show practical examples for their specific use case.
