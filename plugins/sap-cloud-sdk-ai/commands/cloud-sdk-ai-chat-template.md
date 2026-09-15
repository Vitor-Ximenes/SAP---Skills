---
name: cloud-sdk-ai-chat-template
description: Generate or review a JavaScript or TypeScript chat template for SAP Cloud SDK for AI with safe configuration boundaries
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[typescript|javascript] [orchestration|native] [target-file]"
arguments:
  - name: language
    description: Optional output language, either typescript or javascript
    required: false
  - name: mode
    description: Optional client mode, such as orchestration, langchain, or native
    required: false
  - name: target_file
    description: Optional file path to create or update when generation is explicitly requested
    required: false
---

# Cloud SDK AI Chat Template

Prepare a minimal chat client template for SAP Cloud SDK for AI. Default to a snippet and generate files only when the user explicitly asks for workspace output and provides or confirms the target file path.

## Workflow

1. Inspect existing package manager, TypeScript setup, environment variable conventions, and SDK usage.
2. Choose the smallest client pattern that fits the requested mode.
3. Keep credentials in environment variables or service bindings.
4. Include model/deployment configuration as placeholders, not real secrets.
5. Provide a smoke-test command and error-handling notes.

## Output Contract

Return:
- Template approach and assumptions.
- Code snippet or generated file path.
- Required environment variables.
- Local validation steps.
- Security notes for model IDs, destinations, and secrets.
