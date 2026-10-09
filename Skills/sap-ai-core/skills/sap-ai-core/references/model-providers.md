# Model Providers Reference

Complete reference for SAP AI Core model providers and available models.

**Documentation Source:** [SAP Help Portal - SAP AI Core](https://help.sap.com/docs/sap-ai-core)

**Latest Models:** SAP Note 3437766

---

## Overview

SAP AI Core provides access to models from multiple providers via the Generative AI Hub. All models are accessed through a unified API, allowing easy switching between providers.

**Catalog rule:** This reference describes provider families and common configuration shapes. Exact model IDs, versions, context windows, pricing, regions, and deprecation dates must be verified in the target tenant through SAP AI Launchpad Model Library, `GET /v2/lm/scenarios/foundation-models/models`, and SAP Note 3437766 before implementation.

---

## Provider Summary

| Provider | Executable ID | Access Type | Model Categories |
|----------|---------------|-------------|------------------|
| Azure OpenAI | `azure-openai` | Remote | Chat, Embeddings, Vision, Reasoning, Realtime |
| SAP Open Source | `aicore-opensource` | Local | Chat, Embeddings, Vision |
| Google Vertex AI | `gcp-vertexai` | Remote | Chat, Embeddings, Vision, Code, Image Gen |
| AWS Bedrock | `aws-bedrock` | Remote | Chat, Embeddings |
| Mistral AI | `aicore-mistralai` | Local | Chat, Code |
| IBM | `aicore-ibm` | Local | Chat, Code |
| Perplexity | `aicore-perplexity` | Remote | Chat with Citations, Deep Research |

---

## 1. Azure OpenAI

**Executable ID:** `azure-openai`
**Access Type:** Remote (Azure-hosted)

### Example Model Families

| Family | Typical Capabilities | Selection Guidance |
|--------|----------------------|--------------------|
| GPT chat/multimodal | Chat, vision, structured output | Verify exact model ID, context window, and region in tenant catalog. |
| Reasoning models | Complex reasoning chains | Use when quality justifies added latency/cost; verify quota. |
| Realtime models | Low-latency conversational API | Verify endpoint support and streaming/realtime quotas. |
| Embedding models | Vector embeddings | Match dimensions and language coverage to the target vector store. |

**Deprecated/retiring patterns:** older GPT-4, GPT-4 Turbo, GPT-4-32k, and GPT-3.5-era deployments should be checked against SAP Note 3437766 and migrated before retirement dates shown in the tenant catalog.

### Configuration Example

```json
{
  "name": "azure-gpt4o-config",
  "executableId": "azure-openai",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "gpt-4o"},
    {"key": "modelVersion", "value": "2024-05-13"}
  ]
}
```

---

## 2. SAP-Hosted Open Source

**Executable ID:** `aicore-opensource`
**Access Type:** Local (SAP-hosted)

### Example Model Families

| Family | Typical Capabilities | Selection Guidance |
|--------|----------------------|--------------------|
| Llama-family | Chat, reasoning, select vision variants | Verify enabled model IDs and context windows in tenant catalog. |
| Mistral/Mixtral-family | Instruction following, code, low-latency chat | Check tenant catalog before using exact IDs. |
| Falcon-family | General text generation | Use only where explicitly enabled. |

### Configuration Example

```json
{
  "name": "llama-config",
  "executableId": "aicore-opensource",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "meta--llama-3.1-70b-instruct"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## 3. Google Vertex AI

**Executable ID:** `gcp-vertexai`
**Access Type:** Remote (Google Cloud)

### Example Model Families

| Family | Typical Capabilities | Selection Guidance |
|--------|----------------------|--------------------|
| Gemini Pro-family | Chat, vision, code, long context | Verify catalog entry, preview/stable status, and token limits. |
| Gemini Flash-family | Fast multimodal responses | Use for lower-latency use cases when enabled. |
| Google embedding models | Vector embeddings | Match dimensions and language coverage to the target vector store. |

**Deprecated/retiring patterns:** older Gemini and PaLM-era deployments should be checked against SAP Note 3437766 and migrated before retirement dates shown in the tenant catalog.

### Configuration Example

```json
{
  "name": "gemini-config",
  "executableId": "gcp-vertexai",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "gemini-1.5-pro"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## 4. AWS Bedrock

**Executable ID:** `aws-bedrock`
**Access Type:** Remote (AWS)

### Example Model Families

| Family | Typical Capabilities | Selection Guidance |
|--------|----------------------|--------------------|
| Anthropic Claude-family | Chat, reasoning, coding, summarization | Verify exact model ID and Bedrock regional availability in SAP AI Core catalog. |
| Amazon Nova-family | General chat and multimodal tasks | Use only where enabled in the tenant catalog. |
| Amazon Titan-family | Text and embeddings | Verify dimensions and cost before vector workloads. |

### Configuration Example

```json
{
  "name": "claude-config",
  "executableId": "aws-bedrock",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "anthropic--claude-3-5-sonnet"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## 5. Mistral AI

**Executable ID:** `aicore-mistralai`
**Access Type:** Local (SAP-hosted)

### Models

| Model | Parameters | Context | Use Case |
|-------|------------|---------|----------|
| mistral-large | - | 32K | Advanced reasoning |
| mistral-medium | - | 32K | Balanced |
| mistral-small | - | 32K | Cost-efficient |
| codestral | - | 32K | Code generation |

### Configuration Example

```json
{
  "name": "mistral-config",
  "executableId": "aicore-mistralai",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "mistralai--mistral-large"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## 6. IBM

**Executable ID:** `aicore-ibm`
**Access Type:** Local (SAP-hosted)

### Granite Models

| Model | Parameters | Use Case |
|-------|------------|----------|
| granite-family entries | Various | Verify exact model ID and generation in the tenant catalog |
| granite-13b-chat | 13B | Conversational |
| granite-13b-instruct | 13B | Task completion |
| granite-code | - | Code generation |

### Configuration Example

```json
{
  "name": "granite-config",
  "executableId": "aicore-ibm",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "ibm--granite-13b-chat"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## 7. Perplexity

**Executable ID:** `aicore-perplexity`
**Access Type:** Remote (Perplexity-hosted)
**Availability:** tenant-dependent; verify in SAP AI Core catalog.

### Example Model Families

| Family | Use Case |
|--------|----------|
| Sonar-family | Web-grounded chat with citations |
| Deep-research family | Longer research flows with citations where enabled |

**Unique Features:**
- Returns citation URLs alongside responses
- Web-grounded responses for up-to-date information
- Supports output-with-citations in orchestration workflows

### Configuration Example

```json
{
  "name": "perplexity-config",
  "executableId": "aicore-perplexity",
  "scenarioId": "foundation-models",
  "parameterBindings": [
    {"key": "modelName", "value": "perplexity--sonar"},
    {"key": "modelVersion", "value": "latest"}
  ]
}
```

---

## Model Selection Guide

### By Use Case

| Use Case | Selection Guidance |
|----------|--------------------|
| General chat | Use the highest-quality tenant-approved chat model that meets cost and data-residency constraints. |
| Code generation | Use a code-capable catalog entry and validate output with project tests. |
| Long documents | Select catalog entries with sufficient context and explicit long-context support. |
| Vision/images | Choose multimodal catalog entries and verify image-input support. |
| Embeddings | Match embedding dimensions, language coverage, and vector-store requirements. |
| Cost-sensitive | Prefer smaller or mini/nano variants shown in the tenant catalog. |
| High throughput | Prefer lower-latency catalog entries and confirm quota. |
| Reasoning | Use reasoning-capable catalog entries when latency/cost tradeoffs are acceptable. |
| Web-grounded / citations | Use citation-capable entries and preserve returned source URLs. |
| Deep research | Use only where deep-research entries are enabled and validate citation quality. |
| Realtime | Verify realtime endpoint and quota support before implementation. |

### By Budget

| Budget | Tier | Guidance |
|--------|------|----------|
| Low | Economy | Use smaller variants and strict token limits. |
| Medium | Standard | Use balanced chat models with predictable latency. |
| High | Premium | Use top-tier catalog entries after confirming quota and cost. |

### By Capability

| Capability | Selection Guidance |
|------------|--------------------|
| Reasoning | Choose reasoning-capable entries and measure latency/cost. |
| Speed | Choose smaller, low-latency variants with sufficient quality. |
| Context length | Verify the context window reported by the tenant catalog. |
| Multimodal | Verify input media types and output modality support. |
| Code | Use code-capable entries and validate against project tests. |
| Citations | Use citation-capable entries and preserve returned source URLs. |

---

## Model Version Management

### Version Strategies

| Strategy | Configuration | Use Case |
|----------|---------------|----------|
| Latest | `"modelVersion": "latest"` | Development, auto-upgrade |
| Pinned | `"modelVersion": "2024-05-13"` | Production stability |

### Checking Available Versions

```bash
curl -X GET "$AI_API_URL/v2/lm/scenarios/foundation-models/models" \
  -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "AI-Resource-Group: default" | \
  jq '.resources[] | select(.model == "gpt-4o") | .versions'
```

### Handling Deprecation

1. Monitor `deprecationDate` in model metadata
2. Plan migration before `retirementDate`
3. Test new version in staging
4. Update configuration with new version
5. Patch existing deployments

---

## Pricing Considerations

Pricing varies by:
- Model complexity (larger = more expensive)
- Input vs output tokens (output often 2-3x input cost)
- Provider region
- Access type (Remote vs Local)

**Reference:** SAP Note 3437766 for current token rates.

### Cost Optimization

1. **Right-size models**: Use smaller models for simple tasks
2. **Batch requests**: Combine multiple queries when possible
3. **Cache responses**: Store and reuse common query results
4. **Limit tokens**: Set appropriate `max_tokens` limits
5. **Use streaming**: No additional cost, better UX

---

## Rate Limits

Rate limits vary by:
- Service plan tier
- Model provider
- Specific model

**Default limits** (vary by configuration):
- Requests per minute: 60-600
- Tokens per minute: 40K-400K

### Handling Rate Limits

```python
import time
from requests.exceptions import HTTPError

def call_with_retry(func, max_retries=3):
    for attempt in range(max_retries):
        try:
            return func()
        except HTTPError as e:
            if e.response.status_code == 429:
                wait_time = 2 ** attempt
                time.sleep(wait_time)
            else:
                raise
    raise Exception("Max retries exceeded")
```

---

## Documentation Links

- Supported Models: [https://help.sap.com/docs/sap-ai-core/generative-ai/supported-models](https://help.sap.com/docs/sap-ai-core/generative-ai/supported-models)
- Generative AI Hub: [https://help.sap.com/docs/sap-ai-core/generative-ai/generative-ai-hub](https://help.sap.com/docs/sap-ai-core/generative-ai/generative-ai-hub)
- SAP Note 3437766: Token rates, limits, deprecation dates
- SAP Discovery Center: [https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core](https://discovery-center.cloud.sap/serviceCatalog/sap-ai-core)
