---
name: cap-setup-wizard
description: Interactive guide for setting up new CAP projects with framework and database selection
allowed-tools:
  - Read
  - Bash
  - AskUserQuestion
argument-hint: "[nodejs|java|typescript] [sqlite|hana|postgres]"
arguments:
  - name: framework
    description: "Target framework: nodejs, java, or typescript"
    required: false
  - name: database
    description: "Database: sqlite, hana, or postgres"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a setup plan, selected stack/runtime assumptions, generated-file checklist, safe commands, and confirmation points before writes. Default to planning unless the user explicitly requests project generation and provides or confirms a target directory.


# CAP Project Setup Wizard

Interactive guide for initializing new SAP Cloud Application Programming Model projects.

## Quick Start

### Node.js + SQLite (Development)

```bash
cds init my-bookshop --add tiny-sample
cd my-bookshop
npm install
cds watch
```

**Access**: http://localhost:4004

### Node.js + HANA (Production-Ready)

```bash
cds init my-bookshop --add hana,mta
cd my-bookshop
npm install
cds add hana
```

### TypeScript + SQLite

```bash
cds init my-bookshop --add typescript,tiny-sample
cd my-bookshop
npm install
cds watch
```

### Java + HANA

```bash
cds init my-bookshop --add java,hana,mta
cd my-bookshop
mvn spring-boot:run
```

## Framework Selection

### Node.js (JavaScript)

**Best for**: Rapid development, prototyping, lightweight services

**Runtime**: Node.js 18+ LTS

**Key packages**:
- `@sap/cds` - Core CAP framework
- `express` - HTTP server
- `sqlite3` - Development database

**Project Structure**:
```
my-bookshop/
├── db/
│   └── schema.cds
├── srv/
│   ├── catalog-service.cds
│   └── catalog-service.js
└── package.json
```

**Advantages**:
- Fastest development cycle
- Smallest learning curve
- Largest CAP community
- Best for prototypes and MVPs

### TypeScript

**Best for**: Type safety, large teams, enterprise applications

**Runtime**: Node.js 18+ LTS + TypeScript 5+

**Key packages**:
- `@sap/cds` - Core CAP framework
- `typescript` - Type system
- `@types/node` - Node.js type definitions

**Additional setup**:
```bash
npm install --save-dev typescript @types/node
npx tsc --init
```

**Advantages**:
- Compile-time type checking
- Better IDE support
- Refactoring safety
- Enterprise-grade code quality

### Java (Spring Boot)

**Best for**: Enterprise applications, existing Java ecosystems

**Runtime**: Java 17+ LTS

**Key dependencies**:
- `com.sap.cds:cds-starter-spring-boot`
- `org.springframework.boot:spring-boot-starter-web`

**Build tool**: Maven or Gradle

**Advantages**:
- Strong typing
- Mature ecosystem
- Enterprise patterns
- Integration with Java systems

## Database Selection

### SQLite (Development Only)

**Best for**: Local development, quick testing

**Setup**: Auto-configured with Node.js projects

**Deploy**: `cds deploy --to sqlite:db/data.db`

**Advantages**:
- Zero configuration
- Fast startup
- No external dependencies
- Perfect for development

**Limitations**:
- Single-user only
- Not for production
- Limited concurrency

### SAP HANA Cloud (Production)

**Best for**: Production SAP BTP applications

**Setup**:
```bash
cds add hana
cds add mta
```

**Deploy**: Via MTA build and Cloud Foundry CLI

**Advantages**:
- Production-grade performance
- Multi-tenancy support
- Advanced HANA features
- Native SAP BTP integration

**Requirements**:
- SAP BTP account
- HANA Cloud instance
- Cloud Foundry CLI

### PostgreSQL (Alternative Production)

**Best for**: Non-SAP cloud environments

**Setup**:
```bash
npm add @cap-js/postgres
```

**Configuration**: Add postgres binding in package.json

**Advantages**:
- Open source
- Wide cloud support
- Cost-effective
- Standard SQL features

## Additional Features

### Add Multitenancy

```bash
cds add multitenancy
```

**When to use**: Building SaaS applications

**Enables**:
- Tenant isolation
- Subscription management
- Tenant-specific extensions

### Add UI (Fiori Elements)

```bash
cds add fiori
```

**When to use**: Building Fiori applications

**Generates**:
- UI5 application structure
- Navigation
- Annotations

### Add Authentication

```bash
cds add xsuaa
```

**When to use**: Production applications

**Configures**:
- XSUAA service binding
- xs-security.json
- Role-based access control

### Add Extensibility

```bash
cds add extensibility
```

**When to use**: SaaS applications with customer extensions

**Enables**:
- Field extensions
- Custom logic
- UI extensions

## Post-Setup Checklist

- [ ] Run `npm install` to install dependencies
- [ ] Verify `cds watch` starts successfully
- [ ] Access service at http://localhost:4004
- [ ] Check Fiori preview at http://localhost:4004/fiori-preview.html
- [ ] Review generated package.json configuration
- [ ] Set up version control (git init)
- [ ] Configure .gitignore (automatically included)
- [ ] Plan deployment strategy (Cloud Foundry, Kyma, etc.)

## Common Project Configurations

### Configuration 1: Local Development

```bash
cds init my-project --add tiny-sample
cd my-project
npm install
cds watch
```

**Use case**: Learning CAP, rapid prototyping

### Configuration 2: Full-Stack with UI

```bash
cds init my-project --add hana,mta,fiori,xsuaa
cd my-project
npm install
```

**Use case**: Production Fiori application

### Configuration 3: Multi-Tenant SaaS

```bash
cds init my-project --add hana,mta,xsuaa,multitenancy,extensibility
cd my-project
npm install
```

**Use case**: Enterprise SaaS application

### Configuration 4: Microservice API

```bash
cds init my-service --add hana,mta,xsuaa
cd my-service
npm install
# Remove app/ folder if no UI needed
```

**Use case**: Backend API service

## References

- [CAP Setup Guide](../skills/sap-cap-capire/references/tools-complete.md)
- [Deployment Configuration](../skills/sap-cap-capire/references/deployment-cf.md)
- [Package.json Template](../skills/sap-cap-capire/templates/package.json)
- [MTA Descriptor Template](../skills/sap-cap-capire/templates/mta.yaml)
