---
name: cap-mcp-tools
description: Complete reference for CAP MCP server tools (search_model, search_docs) with usage examples
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

Return the relevant CAP MCP tool path, configuration checks, fallback local commands, and unresolved MCP availability questions. Default to read-only guidance and do not change MCP config unless explicitly requested.


# CAP MCP Tools Reference

## Operation Safety

- **Classification**: `local-only`
- The bundled CAP MCP server operates on local CAP project model/docs and does not require tenant credentials.
- Keep examples read-only by default; explicit user approval is required before changing MCP config or running commands that deploy, publish, or affect remote systems.

Complete reference for the published @cap-js/mcp-server tools that provide AI agents with live access to your CAP project.

## Overview

The CAP MCP (Model Context Protocol) server exposes two powerful tools:
1. **search_model** - Search compiled CDS model (entities, services, associations)
2. **search_docs** - Semantic search through CAP documentation

**Package**: `@cap-js/mcp-server`
**Installation**: `npx -y @cap-js/mcp-server@0.0.5`
**License**: Apache-2.0

---

## Tool 1: search_model

### Purpose

Fuzzy search for CDS definitions in your project's compiled Core Schema Notation (CSN) model.

### What It Searches

- Entity definitions and their fields
- Service definitions and exposed entities
- Actions and functions (bound and unbound)
- Associations and compositions
- Annotations and metadata
- HTTP endpoints (auto-generated OData routes)

### Returns

- Definition details (type, properties, constraints)
- Relationships (associations, compositions)
- Annotations (UI, validation, authorization)
- HTTP endpoints with methods

### Use Cases

**Use Case 1: Entity Discovery**
- **Question**: "What entities exist in my project related to orders?"
- **Tool Call**: `search_model(query="orders", type="entity")`
- **Returns**: Orders entity with fields, associations, annotations

**Use Case 2: Service Endpoint Discovery**
- **Question**: "What HTTP endpoints does my CatalogService expose?"
- **Tool Call**: `search_model(query="CatalogService", type="service")`
- **Returns**: Service definition with all exposed entities and their OData routes

**Use Case 3: Association Validation**
- **Question**: "What associations does the Books entity have?"
- **Tool Call**: `search_model(query="Books.associations")`
- **Returns**: All associations (author, reviews, etc.) with cardinality and target entities

**Use Case 4: Action Signature Lookup**
- **Question**: "What parameters does the submitOrder action expect?"
- **Tool Call**: `search_model(query="submitOrder", type="action")`
- **Returns**: Action signature with input/output parameters

### Example Workflow

```
User: "Add a bound action to Books entity to mark as bestseller"
Agent: Let me check the Books entity structure first.
[Calls: search_model(query="Books", type="entity")]
Result: Books entity has ID, title, author_ID, stock fields
Agent: Next, check how to define bound actions.
[Calls: search_docs(query="define bound action in CDS")]
Result: Bound action syntax documentation
Agent: Here's how to add the action...
```

---

## Tool 2: search_docs

### Purpose

Semantic search through preprocessed SAP CAP documentation using vector embeddings.

### What It Searches

- CDS syntax and language features
- CAP Node.js/Java API references
- Service handler patterns
- Deployment guides
- Best practices and examples

### Search Technology

- **Vector embeddings**: Converts your query to a semantic vector
- **Similarity matching**: Finds documentation chunks with highest semantic similarity
- **Locally cached**: No external API calls, fully offline-capable
- **Semantic understanding**: Finds relevant docs even without exact keyword matches

### Use Cases

**Use Case 1: Syntax Lookup**
- **Question**: "How do I define a composition in CDS?"
- **Tool Call**: `search_docs(query="composition syntax CDS")`
- **Returns**: CDS composition syntax with examples

**Use Case 2: API Pattern Discovery**
- **Question**: "How do I register a BEFORE CREATE handler in Node.js?"
- **Tool Call**: `search_docs(query="register before create handler nodejs")`
- **Returns**: Event handler registration API with code examples

**Use Case 3: Deployment Configuration**
- **Question**: "How do I configure multitenancy for HANA?"
- **Tool Call**: `search_docs(query="multitenancy HANA configuration")`
- **Returns**: Multitenancy setup guide for HANA

**Use Case 4: Best Practice Lookup**
- **Question**: "What's the recommended way to handle pagination in CAP?"
- **Tool Call**: `search_docs(query="pagination best practices CAP")`
- **Returns**: Pagination patterns with LIMIT/OFFSET examples

### Example Workflow

```
User: "Implement input validation for Books.stock field"
Agent: Let me find the validation patterns.
[Calls: search_docs(query="input validation CDS annotations")]
Result: @assert.range annotation documentation
Agent: [Calls: search_docs(query="validation in event handlers nodejs")]
Result: req.error() and srv.before() patterns
Agent: Here are two approaches for validation...
```

---

## Integration with Agents

### cap-cds-modeler Agent

**Uses**:
- `search_model` - Find existing entities, check associations
- `search_docs` - Look up CDS syntax, annotation patterns

**Example**: "Create entity Products with category association"
1. Calls `search_model(query="Categories", type="entity")` to find target entity
2. Calls `search_docs(query="association syntax CDS")` for association syntax
3. Generates CDS code with proper association

### cap-service-developer Agent

**Uses**:
- `search_model` - Find service definitions, action signatures
- `search_docs` - Look up event handler API, CQL syntax

**Example**: "Implement custom CREATE handler for Orders"
1. Calls `search_model(query="Orders", type="entity")` to understand entity
2. Calls `search_docs(query="before create handler registration nodejs")` for API
3. Generates handler code

### cap-project-architect Agent

**Uses**:
- `search_model` - Check project structure, service bindings
- `search_docs` - Look up deployment config, multitenancy setup

**Example**: "Configure Cloud Foundry deployment"
1. Calls `search_docs(query="cloud foundry deployment mta")` for MTA config
2. Calls `search_docs(query="hana service binding")` for database setup
3. Generates mta.yaml configuration

### cap-performance-debugger Agent

**Uses**:
- `search_model` - Analyze entity relationships for query optimization
- `search_docs` - Look up performance patterns, debugging techniques

**Example**: "Why is this query slow?"
1. Calls `search_model` to understand entity associations
2. Calls `search_docs(query="query optimization N+1 problem CAP")` for patterns
3. Suggests optimized query with expand

---

## Setup & Configuration

### 1. Create .mcp.json

```json
{
  "sap-cap-capire": {
    "command": "npx",
    "args": ["-y", "@cap-js/mcp-server@0.0.5"],
    "env": {}
  }
}
```

### 2. Configure Code CLI (VS Code - Cline)

```json
{
  "mcpServers": {
    "cap-mcp": {
      "command": "npx",
      "args": ["-y", "@cap-js/mcp-server@0.0.5"],
      "env": {}
    }
  }
}
```

### 3. Configure opencode

```json
{
  "mcp": {
    "cap-mcp": {
      "type": "local",
      "command": ["npx", "-y", "@cap-js/mcp-server@0.0.5"],
      "enabled": true
    }
  }
}
```

### 4. Configure GitHub Copilot (mcp.json)

```json
{
  "servers": {
    "cap-mcp": {
      "command": "npx",
      "args": ["-y", "@cap-js/mcp-server@0.0.5"],
      "env": {},
      "type": "stdio"
    }
  }
}
```

---

## Rules for LLM Usage

**CRITICAL RULES** (from official documentation):

1. **Always search model first**: You MUST search for CDS definitions with `search_model` first. Only read .cds files if `search_model` fails.

2. **Always search docs before coding**: You MUST search for CAP docs with `search_docs` EVERY TIME you create or modify CDS models or use CAP APIs. Do NOT propose changes without checking documentation first.

3. **Trust MCP tools over file reads**: MCP tools search the compiled model (CSN), which is the source of truth. File reads may not reflect the final compiled model.

---

## Troubleshooting

### Issue: MCP server not found

**Solution**:
```bash
# Install globally for faster access
npm install -g @cap-js/mcp-server@0.0.5

# Or use npx (auto-installs)
npx -y @cap-js/mcp-server@0.0.5
```

### Issue: search_model returns no results

**Possible Causes**:
- Project not compiled yet (run `cds build`)
- Query too specific (try broader search)
- Entity/service name mismatch (check case sensitivity)

**Solution**: Run `cds compile srv/ --to csn` to verify model compilation

### Issue: search_docs returns irrelevant results

**Possible Causes**:
- Query too broad or too narrow
- Semantic mismatch (embeddings don't understand query intent)

**Solution**: Rephrase query with more specific technical terms

---

## CLI Usage

You can also use MCP tools directly from command line:

```bash
# Install globally
npm i -g @cap-js/mcp-server@0.0.5

# Search compiled model
cds-mcp search_model . Books entity

# Search CAP documentation
cds-mcp search_docs "how to add columns to a select statement in CAP Node.js" 1
```

---

## Performance

### search_model
- **Speed**: Instant (local file read + fuzzy search)
- **No network required**: Searches compiled CSN locally

### search_docs
- **Speed**: Sub-second (vector embedding lookup)
- **No network required**: Documentation pre-embedded and cached locally
- **First run**: Slower (downloads and caches embeddings ~50MB)
- **Subsequent runs**: Instant (uses cached embeddings)

---

## References

- **Official GitHub**: https://github.com/cap-js/mcp-server
- **npm Package**: https://npmjs.com/package/@cap-js/mcp-server
- **Community Blog**: https://community.sap.com/t5/technology-blog-posts-by-sap/boost-your-cap-development-with-ai-introducing-the-mcp-server-for-cap/ba-p/14202849
- **MCP Integration Guide**: [MCP Integration](../skills/sap-cap-capire/references/mcp-integration.md)
- **MCP Use Cases**: [MCP Use Cases](../skills/sap-cap-capire/references/mcp-use-cases.md)
