# SAP AI Core & AI Launchpad Skill

A portable AI coding assistant skill for SAP AI Core and SAP AI Launchpad development on SAP Business Technology Platform (BTP). Claude-specific command metadata is packaging support only; the skill content is intended to remain useful in Codex, OpenCode, and other Markdown-capable harnesses.

## Capability Index

| Capability | Status |
|------------|--------|
| Commands | 1: `/ai-core-deployment-check` |
| Agents | 0 |
| Hooks | No |
| MCP | No |
| LSP | No |
| Source Freshness | `last_verified`: 2026-06-12; 2026-06-16 pass corrected portability and evidence wording. |
| Verification | `npm run validate`; live deployment behavior and model availability remain tenant-verified only. |

## Overview

This skill provides guidance for:
- Deploying and consuming generative AI models
- Building orchestration workflows with templating, filtering, and grounding
- Implementing RAG (Retrieval-Augmented Generation) with vector databases
- Managing ML training pipelines with Argo Workflows
- Configuring content filtering and data masking for PII protection
- Using the Generative AI Hub for prompt experimentation

## When to Use This Skill

This skill is triggered when working with:

### SAP AI Core
- SAP AI Core setup and configuration
- SAP AI Core deployments and executions
- SAP AI Core service plans (Free, Standard, Extended)
- SAP AI Core API endpoints
- AI Core generative AI hub
- AI Core orchestration service
- AI Core foundation models
- AI Core grounding and RAG

### SAP AI Launchpad
- SAP AI Launchpad setup
- AI Launchpad generative AI hub
- AI Launchpad prompt experimentation
- AI Launchpad ML operations
- AI Launchpad orchestration workflows

### Model Providers
- Azure OpenAI on SAP
- GPT-4o, GPT-4 Turbo, GPT-3.5 on SAP
- AWS Bedrock on SAP
- Claude on SAP BTP
- Anthropic Claude via AI Core
- Google Vertex AI on SAP
- Gemini on SAP
- Mistral AI on SAP
- IBM Granite on SAP
- Llama models on SAP

### Features
- LLM deployment on SAP
- Generative AI on SAP BTP
- AI model orchestration
- Prompt templating
- Content filtering for AI
- Data masking for AI
- PII protection in AI
- Vector database integration
- Document grounding
- RAG implementation SAP
- Embeddings generation
- Tool calling with LLMs
- Function calling AI Core
- Structured output AI
- JSON schema responses
- Streaming responses

### ML Operations
- ML model training SAP
- Argo Workflows SAP
- Training pipelines
- Batch inference SAP
- Model deployment SAP
- Training schedules
- Execution management
- Artifact management

### API & Integration
- AI API SAP
- Harmonized API
- Chat completion API
- Embeddings API
- Orchestration API
- REST API AI Core

### Advanced Features
- Multi-turn chat conversations
- Git repository sync applications
- Prompt templates declarative
- Prompt optimization SAP
- AI content as a service
- AI content security
- Data protection privacy GDPR
- Auditing logging SAP AI
- KServe serving templates
- Metadata vector search
- Content packages DataRobot

## Keywords

```
sap ai core, sap ai launchpad, generative ai hub, foundation models,
llm deployment, model orchestration, prompt templating, content filtering,
data masking, pii protection, vector database, document grounding, rag,
embeddings, tool calling, function calling, structured output, streaming,
azure openai sap, gpt-4 sap, claude sap, gemini sap, mistral sap,
llama sap, aws bedrock sap, google vertex ai sap, ibm granite sap,
ml operations, argo workflows, training pipeline, batch inference,
model deployment, ai api, harmonized api, orchestration api,
sap btp ai, enterprise ai, sap machine learning, ai core extended,
prompt experimentation, ai launchpad workspaces, resource groups,
configurations, executions, deployments, artifacts, scenarios,
chat conversations, messages history, git sync applications,
prompt templates, prompt optimization, ai content security,
data protection, gdpr compliance, auditing logging, kserve,
serving templates, metadata retrieval, content packages
```

## File Structure

```
sap-ai-core/
├── SKILL.md                        # Main skill file
├── README.md                       # This file
├── references/
│   ├── orchestration-modules.md    # Detailed orchestration module docs
│   ├── generative-ai-hub.md        # Generative AI Hub reference
│   ├── api-reference.md            # Complete API reference
│   ├── grounding-rag.md            # Grounding and RAG implementation
│   ├── ml-operations.md            # ML training and operations
│   ├── model-providers.md          # Model providers and configurations
│   ├── advanced-features.md        # Chat, security, auditing, templates
│   └── ai-launchpad-guide.md       # Complete AI Launchpad UI guide
└── templates/
    ├── deployment-config.json      # Deployment configuration template
    ├── orchestration-workflow.json # Orchestration workflow template
    └── tool-definition.json        # Tool calling definition template
```

## Prerequisites

- SAP BTP enterprise account
- SAP AI Core service instance
- Extended service plan (for Generative AI Hub)
- Service key with credentials

## Quick Start

1. Set up authentication:
```bash
export AI_API_URL="<your-ai-api-url>"
export AUTH_TOKEN="<your-oauth-token>"
```

2. List available models:
```bash
curl -X GET "$AI_API_URL/v2/lm/scenarios/foundation-models/models" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default"
```

3. Create orchestration deployment and start using models

Before using any model ID from examples or references, list the target tenant catalog:

```bash
curl -X GET "$AI_API_URL/v2/lm/scenarios/foundation-models/models" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default"
```

## Documentation Sources

| Resource | URL |
|----------|-----|
| SAP AI Core Guide | [https://help.sap.com/docs/sap-ai-core](https://help.sap.com/docs/sap-ai-core) |
| SAP AI Launchpad Guide | [https://help.sap.com/docs/sap-ai-launchpad](https://help.sap.com/docs/sap-ai-launchpad) |
| GitHub Docs Source | [https://github.com/SAP-docs/sap-artificial-intelligence](https://github.com/SAP-docs/sap-artificial-intelligence) |
| SAP Note (Models) | SAP Note 3437766 |
| SAP Discovery Center | [https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core](https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core) |

## License

GPL-3.0

## Version

**Current**: 2.3.0 (2026-06-16)
- Documentation-audited AI Core guidance with tenant/runtime verification still pending.
- Model names are examples and must be checked against the target tenant catalog.
- Skill content is portable across Claude, Codex, OpenCode, and similar Markdown-capable harnesses.

## Last Updated

2026-06-16

## Next Review

Next source refresh and tenant verification remain pending until SAP Help/package evidence or live tenant evidence is available.
