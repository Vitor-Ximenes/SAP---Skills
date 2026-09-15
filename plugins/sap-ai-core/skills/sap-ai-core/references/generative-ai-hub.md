# Generative AI Hub Reference

Complete reference for SAP AI Core Generative AI Hub.

**Documentation Source:** [SAP Help Portal - SAP AI Core](https://help.sap.com/docs/sap-ai-core)

---

## Overview

The Generative AI Hub integrates large language models (LLMs) into SAP AI Core and SAP AI Launchpad, providing unified access to models from multiple providers.

**Model availability note:** SAP AI Core model IDs, versions, regions, and deprecation dates change by tenant, service plan, entitlement, and SAP Note 3437766 updates. Treat model names in this reference as examples. Before creating deployments, verify the exact target catalog in SAP AI Launchpad Model Library or with `GET /v2/lm/scenarios/foundation-models/models`.

### Key Features

- Access to LLMs from multiple providers via unified API
- Harmonized API for model switching without code changes
- Prompt experimentation in AI Launchpad UI
- Prompt Registry for prompt template lifecycle management (available since Q1 2026)
- Orchestration workflows with filtering, masking, grounding, translation
- Token-based metering and billing

### Prerequisites

- SAP AI Core with **Extended** service plan
- Valid service key credentials
- Resource group created

---

## Global Scenarios

Two scenarios provide generative AI access:

| Scenario ID | Description | Use Case |
|-------------|-------------|----------|
| `foundation-models` | Direct model access | Single model deployment |
| `orchestration` | Unified multi-model access | Pipeline workflows |

---

## Model Providers

### 1. Azure OpenAI (`azure-openai`)

Access to OpenAI models via Azure's private instance.

**Example model families to verify in tenant catalog:**
- GPT-family chat and multimodal models
- Reasoning model families
- Realtime conversational models, where enabled
- Text embedding models

**Deprecated/retiring patterns:** older GPT-4, GPT-4 Turbo, GPT-4-32k, and GPT-3.5-era deployments should be checked against SAP Note 3437766 and migrated before retirement dates shown in the tenant catalog.

**Capabilities:** Chat, embeddings, vision, reasoning, realtime

### 2. SAP-Hosted Open Source (`aicore-opensource`)

SAP-hosted open source models via OpenAI-compatible API.

**Example model families to verify in tenant catalog:**
- Llama-family chat and vision models
- Mistral/Mixtral-family instruction models
- Falcon-family models

**Capabilities:** Chat, embeddings, vision (select models)

### 3. Google Vertex AI (`gcp-vertexai`)

Access to Google's AI models.

**Example model families to verify in tenant catalog:**
- Gemini-family chat, vision, code, and long-context models
- Gemini Flash-family lower-latency models
- Google embedding models

**Deprecated/retiring patterns:** older Gemini and PaLM-era deployments should be checked against SAP Note 3437766 and migrated before retirement dates shown in the tenant catalog.

**Capabilities:** Chat, embeddings, vision, code, image generation

### 4. AWS Bedrock (`aws-bedrock`)

Access to models via AWS Bedrock.

**Example model families to verify in tenant catalog:**
- Anthropic Claude-family chat models
- Amazon Nova-family models
- Amazon Titan text and embedding models

**Capabilities:** Chat, embeddings

### 5. Mistral AI (`aicore-mistralai`)

SAP-hosted Mistral models.

**Models:**
- Mistral Large
- Mistral Medium
- Mistral Small
- Mistral 7B Instruct
- Codestral

**Capabilities:** Chat, code

### 6. IBM (`aicore-ibm`)

SAP-hosted IBM models.

**Example model families to verify in tenant catalog:**
- Granite chat/instruct models
- Granite code models

**Capabilities:** Chat, code

### 7. Perplexity (`aicore-perplexity`)

Perplexity AI models accessed through SAP AI Core where enabled for the tenant.

**Example model families to verify in tenant catalog:**
- Sonar-family web-grounded chat models
- Deep-research models with citations, where enabled

**Capabilities:** Chat with citations, web-grounded responses

**Note:** Sonar and Sonar Pro models support an output-with-citations feature in orchestration, returning source URLs alongside responses.

---

## API: List Available Models

```bash
curl -X GET "$AI_API_URL/v2/lm/scenarios/foundation-models/models" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json"
```

### Response Structure

```json
{
  "count": 50,
  "resources": [
    {
      "model": "gpt-4o",
      "accessType": "Remote",
      "displayName": "GPT-4o",
      "provider": "azure-openai",
      "allowedScenarios": ["foundation-models"],
      "executableId": "azure-openai",
      "description": "OpenAI's most advanced model",
      "versions": [
        {
          "name": "2024-05-13",
          "isLatest": true,
          "capabilities": ["text-generation", "chat", "vision"],
          "contextLength": 128000,
          "inputCost": 5.0,
          "outputCost": 15.0,
          "deprecationDate": null,
          "retirementDate": null,
          "isStreamingSupported": true
        }
      ]
    }
  ]
}
```

### Model Metadata Fields

| Field | Description |
|-------|-------------|
| `model` | Model identifier for API calls |
| `accessType` | "Remote" (external) or "Local" (SAP-hosted) |
| `provider` | Provider identifier |
| `executableId` | Executable ID for deployments |
| `contextLength` | Maximum context window tokens |
| `inputCost` | Cost per 1K input tokens |
| `outputCost` | Cost per 1K output tokens |
| `deprecationDate` | Date version becomes deprecated |
| `retirementDate` | Date version is removed |
| `isStreamingSupported` | Streaming capability |

---

## Deploying a Model

### Step 1: Create Configuration

```bash
curl -X POST "$AI_API_URL/v2/lm/configurations" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "gpt4o-deployment-config",
    "executableId": "azure-openai",
    "scenarioId": "foundation-models",
    "parameterBindings": [
      {"key": "modelName", "value": "gpt-4o"},
      {"key": "modelVersion", "value": "latest"}
    ]
  }'
```

### Step 2: Create Deployment

```bash
curl -X POST "$AI_API_URL/v2/lm/deployments" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "configurationId": "<config-id-from-step-1>"
  }'
```

### Step 3: Check Status

```bash
curl -X GET "$AI_API_URL/v2/lm/deployments/<deployment-id>" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default"
```

Wait for status `RUNNING` and note the `deploymentUrl`.

---

## Using the Harmonized API

The harmonized API provides unified access without model-specific code.

### Chat Completion

```bash
curl -X POST "$DEPLOYMENT_URL/chat/completions" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "What is SAP AI Core?"}
    ],
    "max_tokens": 1000,
    "temperature": 0.7
  }'
```

### With Streaming

```bash
curl -X POST "$DEPLOYMENT_URL/chat/completions" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [{"role": "user", "content": "Tell me a story"}],
    "stream": true
  }'
```

### Embeddings

```bash
curl -X POST "$DEPLOYMENT_URL/embeddings" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-large",
    "input": ["Document chunk to embed"],
    "encoding_format": "float"
  }'
```

---

## Orchestration Deployment

For unified access to multiple models:

### Create Orchestration Deployment

```bash
# Get orchestration configuration ID
curl -X GET "$AI_API_URL/v2/lm/configurations?scenarioId=orchestration" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default"

# Create deployment
curl -X POST "$AI_API_URL/v2/lm/deployments" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "configurationId": "<orchestration-config-id>"
  }'
```

### Use Orchestration API

```bash
curl -X POST "$ORCHESTRATION_URL/v2/completion" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "config": {
      "module_configurations": {
        "llm_module_config": {
          "model_name": "gpt-4o",
          "model_version": "latest"
        },
        "templating_module_config": {
          "template": [
            {"role": "user", "content": "{{?prompt}}"}
          ]
        }
      }
    },
    "input_params": {
      "prompt": "What is machine learning?"
    }
  }'
```

---

## Model Version Management

### Auto-Upgrade Strategy

Set `modelVersion` to `"latest"` for automatic upgrades:

```json
{
  "parameterBindings": [
    {"key": "modelName", "value": "gpt-4o"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

### Pinned Version Strategy

Specify exact version for stability:

```json
{
  "parameterBindings": [
    {"key": "modelName", "value": "gpt-4o"},
    {"key": "modelVersion", "value": "2024-05-13"}
  ]
}
```

### Manual Version Upgrade

Patch deployment with new configuration:

```bash
curl -X PATCH "$AI_API_URL/v2/lm/deployments/<deployment-id>" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" \
  -H "Content-Type: application/json" \
  -d '{
    "configurationId": "<new-config-id>"
  }'
```

---

## SAP AI Launchpad UI

### Prompt Experimentation

Access: **Workspaces** → **Generative AI Hub** → **Prompt Editor**

Features:
- Interactive prompt testing
- Model selection and parameter tuning
- Variable placeholders
- Image inputs (select models)
- Streaming responses
- Save prompts (manager roles)

### Required Roles

| Role | Capabilities |
|------|--------------|
| `genai_manager` | Full access, save prompts |
| `genai_experimenter` | Test only, no save |
| `prompt_manager` | Manage saved prompts |
| `prompt_experimenter` | Use saved prompts |
| `prompt_media_executor` | Upload images |

### Prompt Types

- **Question Answering**: Q&A interactions
- **Summarization**: Extract key points
- **Inferencing**: Sentiment, entity extraction
- **Transformations**: Translation, format conversion
- **Expansions**: Content generation

---

## Model Library

View model specifications and benchmarks in AI Launchpad:

**Access:** Generative AI Hub → Model Library

Information available:
- Model capabilities
- Context window sizes
- Performance benchmarks (win rates, arena scores)
- Cost per token
- Deprecation schedules

---

## Rate Limits and Quotas

Refer to **SAP Note 3437766** for:
- Token conversion rates per model
- Rate limits (requests/minute, tokens/minute)
- Regional availability
- Deprecation dates

### Quota Increase Request

Submit support ticket:
- Component: `CA-ML-AIC`
- Include: tenant ID, current limits, requested limits, justification

---

## Best Practices

### Model Selection

| Use Case | Selection Guidance |
|----------|--------------------|
| General chat | Use the tenant-approved flagship chat model with enterprise data handling enabled. |
| Cost-sensitive | Prefer smaller or mini/nano variants shown in the tenant catalog. |
| Long context | Choose catalog entries with the required context window and verify token cost. |
| Embeddings | Use the approved embedding model for the target vector store and language coverage. |
| Code | Prefer code-capable catalog entries and validate output with project tests. |
| Vision | Choose multimodal catalog entries and verify image-input support. |
| Reasoning | Use reasoning-capable catalog entries only when latency/cost tradeoffs are acceptable. |
| Citations / web-grounded | Use citation-capable models where enabled and preserve returned source URLs. |
| Deep research | Use deep-research models where enabled and validate citation quality. |
| Realtime | Use realtime catalog entries only after confirming endpoint and quota support. |

### Cost Optimization

1. Use smaller models for simple tasks
2. Implement caching for repeated queries
3. Set appropriate `max_tokens` limits
4. Use streaming for better UX without extra cost
5. Monitor token usage via AI Launchpad analytics

### Reliability

1. Implement fallback configurations
2. Pin model versions in production
3. Monitor deprecation dates
4. Test before upgrading versions

---

## Documentation Links

- Generative AI Hub: [https://help.sap.com/docs/sap-ai-core/generative-ai/generative-ai-hub](https://help.sap.com/docs/sap-ai-core/generative-ai/generative-ai-hub)
- Supported Models: [https://help.sap.com/docs/sap-ai-core/generative-ai/supported-models](https://help.sap.com/docs/sap-ai-core/generative-ai/supported-models)
- SAP Note 3437766: Token rates, limits, deprecation
- SAP Discovery Center: [https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core](https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core)
