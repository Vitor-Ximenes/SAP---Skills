---
name: cloud-sdk-ai-python-chat-template
description: Generate or review a Python chat template for SAP Cloud SDK for AI with environment-based configuration
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[langchain|native|orchestration] [target-file]"
arguments:
  - name: mode
    description: Optional Python client mode, such as langchain, native, or orchestration
    required: false
  - name: target_file
    description: Optional file path to create or update when generation is explicitly requested
    required: false
---

# Cloud SDK AI Python Chat Template

Prepare a Python chat client template for SAP Cloud SDK for AI. Default to a snippet and write files only when the user explicitly requests generation and provides or confirms the target file path.

## Workflow

1. Inspect `pyproject.toml`, `requirements.txt`, `.env.example`, and existing client code.
2. Select LangChain, native client, or orchestration usage based on the project shape.
3. Use environment variables for credentials, model IDs, resource groups, and base URLs.
4. Add compact error handling for authentication, deployment lookup, and quota/rate errors.
5. Provide an isolated smoke test that avoids logging prompts with sensitive data.

## Output Contract

Return:
- Recommended Python client pattern.
- Code snippet or generated file path.
- Dependency and environment variable list.
- Smoke-test command.
- Security and deployment verification notes.
