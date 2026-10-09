# SAP Cloud Integration (CPI / Integration Suite) — iFlow Expert Skill

[![Agent Skill](https://img.shields.io/badge/Agent-Skill-blue.svg)](https://antigravity.google)
[![SAP Integration Suite](https://img.shields.io/badge/SAP-Integration%20Suite-0070F2.svg)](https://help.sap.com/docs/cloud-integration)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An **enterprise-grade skill package and knowledge base for SAP Cloud Integration (SAP CPI / SAP Integration
Suite)**, designed for two audiences:

- **AI coding assistants** (Claude, Antigravity, Cursor, Copilot and other agent harnesses) that need
  precise, verified, actionable guidance on iFlow design, Groovy scripting, error handling, security,
  performance and operations;
- **Integration architects and developers** who need the same knowledge as a readable, reviewable handbook
  with checklists and templates they can use on a real project.

Every factual claim about the platform (limits, streaming capabilities, API names, header names, documented
behaviour) is grounded in the official SAP Help documentation for SAP Cloud Integration — the sources are
linked at the end of each guide.

---

## 🎯 What it covers

| Domain | Content |
| :--- | :--- |
| **Architecture & EIP** | Enterprise-grade design qualities, layered flow architecture, EIP catalogue mapped to CPI steps, ProcessDirect modularity, splitter variants and their exception semantics, routing, anti-pattern catalogue |
| **Groovy & performance** | Script runtime facts, heap vs stream access, XML/JSON parsing strategy, StAX constant-memory processing, MPL logging API, thread safety, SAP's general scripting guidelines, CodeNarc static analysis |
| **Recipes** | Ready-to-use scripts for duplicate detection, chunked flat-file reading, MPL instrumentation, exception capture, dynamic file names, payload guards, value mappings |
| **Adapters & connectivity** | Adapter selection matrix, HTTP/OData V2-V4 behaviour, file-based connectivity, SAP backends (IDoc, SOAP, RFC, Cloud Connector), event/messaging connectivity, channel security, retry at the adapter boundary |
| **Mapping & transformation** | Choosing between message mapping, XSLT, Groovy and standard converters, XML↔JSON conversion pitfalls, canonical data models, CSV/EDI boundaries |
| **Error handling** | Error taxonomy, Exception Subprocess architecture, the End / End Message / Error End / Escalation End decision, retry patterns, dead-letter handling, idempotency, the error contract |
| **Observability & operations** | MPL internals, custom status vocabulary, searchable business keys, correlation, alerting design, incident triage runbook, routine operations |
| **Security & governance** | Inbound/outbound authentication options, credential and key lifecycle, TLS/mTLS, CSRF handling for SAP Gateway, parameter externalization, naming conventions, roles and separation of duties |
| **Performance & sizing** | The three resource dimensions, verified tenant quotas, streaming capability matrix, splitting/parallelism/backpressure, receiver-side performance, sizing worksheet |
| **Persistence & decoupling** | JMS vs Data Store vs Variables vs Event Mesh, queue semantics and limits, the retry pattern, idempotency, retention, a decision procedure |
| **Transport & ALM** | Tenant strategy, design-time vs runtime artifacts, the four transport options compared, CI/CD expectations, promotion and rollback, governance and audit |
| **Testing & quality** | Static analysis, unit testing scripts outside the tenant, simulation and mocks, failure-path testing, volume testing, go-live and hypercare |

---

## 📂 Repository structure

```text
sap-cpi-iflow/
├── SKILL.md                                  # Entry point: routing table, golden rules, working procedure
├── README.md                                 # This file
├── CHANGELOG.md                              # Version history of the knowledge package
├── CONTRIBUTING.md                           # How to extend the package consistently
│
├── references/                               # Deep-dive guides (load only what the task needs)
│   ├── design-guidelines.md                  # Qualities, layers, EIP catalogue, splitter variants, anti-patterns
│   ├── groovy-best-practices.md              # Runtime facts, memory rules, MPL API, thread safety, style rules
│   ├── groovy-recipes.md                     # Cookbook of idiomatic solutions to recurring problems
│   ├── adapters-and-connectivity.md          # Adapter selection, HTTP/OData, files, SAP backends, channel security
│   ├── mapping-and-transformation.md         # Message mapping, XSLT, Groovy, converters, canonical models
│   ├── error-handling-and-monitoring.md      # Error taxonomy, Exception Subprocess, retry, DLQ, idempotency
│   ├── monitoring-and-operations.md          # MPL, log levels, search, alerting, incident triage, routine ops
│   ├── security-and-governance.md            # Auth, credentials, TLS, CSRF, externalization, naming, roles
│   ├── performance-and-sizing.md             # Quotas, streaming matrix, parallelism, sizing worksheet
│   ├── persistence-and-decoupling.md         # JMS vs Data Store vs Variables vs Event Mesh, decision procedure
│   ├── transport-and-alm.md                  # Tenant strategy, transport options, CI/CD, promotion, rollback
│   └── testing-and-quality.md                # Static analysis, unit tests, simulation, failure-path testing
│
├── examples/                                 # Production-oriented Groovy scripts
│   ├── README.md                             # Index: problem solved, API used, when to use
│   ├── groovy-stax-xml-stream.groovy         # Constant-memory StAX transformation of huge XML
│   ├── groovy-csv-stream-reader.groovy       # Chunked, streaming read of a very large flat file
│   ├── groovy-jackson-json-stream.groovy     # Streaming JSON for large documents
│   ├── groovy-json-stream-transform.groovy   # Reader-based JSON enrichment for small/medium payloads
│   ├── groovy-mpl-logging-custom-status.groovy # Custom status + searchable keys + gated attachment
│   ├── groovy-exception-details-capture.groovy # Exception Subprocess: classify and record failures
│   ├── groovy-datastore-idempotency.groovy   # Duplicate detection on a business key
│   ├── groovy-csrf-cookie-handler.groovy     # CSRF token + session cookie handling for SAP Gateway
│   ├── groovy-odata-batch-payload-builder.groovy # OData $batch multipart request builder
│   ├── groovy-multipart-attachment-builder.groovy # MIME multipart body builder
│   ├── groovy-dynamic-sftp-filename.groovy   # Dynamic target file name via CamelFileName
│   ├── groovy-payload-size-guard.groovy      # Reject or route oversized payloads explicitly
│   └── groovy-value-mapping-lookup.groovy    # Code-list lookup with a mandatory default branch
│
├── checklists/                               # Gates you run, not documents you read
│   ├── iflow-design-review.md                # Architect review before implementation/promotion
│   ├── go-live-readiness.md                  # Readiness gate before cut-over
│   └── incident-triage.md                    # On-call runbook for a broken interface
│
├── templates/                                # Fill-in documents for real projects
│   ├── iflow-design-document.md              # Complete design document with externalized parameter table
│   └── interface-specification.md            # Contract to share with the counterpart system team
│
├── scripts/
│   └── check-consistency.py                  # Validates headings, code fences, links and Groovy syntax
└── .github/workflows/validate.yml            # Runs the consistency checks on every push and pull request
```

---

## ⚡ The Golden Rules (short version)

1. CPI is an orchestrator, not a backend — keep business logic in the system of record.
2. Never materialise a large payload as a `String`.
3. A streaming chain is only as strong as its weakest step — verify the whole chain.
4. Exchange properties for internal state, headers only for protocol metadata.
5. Every router needs a default branch.
6. Externalize everything environment-specific as `{{parameters}}`.
7. Every production flow has an Exception Subprocess with a deliberately chosen end event.
8. Make failures searchable: custom status + business keys in the MPL.
9. Never log or transport secrets.
10. Decouple before you scale.
11. Bound your parallelism and your input size.
12. The repository is the source of truth, not the tenant.

The full rules, with rationale and consequences, are in [SKILL.md](SKILL.md).

---

## 🚀 Installation

### As an agent skill

**Workspace-specific** — place the folder inside the project that the agent works on:

```bash
mkdir -p .agents/skills
cp -r /path/to/sap-cpi-iflow .agents/skills/
```

**Global** — install it for every project of the current user:

```bash
mkdir -p ~/.agents/skills
cp -r /path/to/sap-cpi-iflow ~/.agents/skills/
```

For Gemini/Antigravity-style harnesses that keep a skill registry, register the parent directory:

```json
{
  "entries": [
    { "path": "~/.agents/skills" }
  ]
}
```

### As documentation

Read [SKILL.md](SKILL.md) first, then open the reference that matches your problem from the routing table in
section 1. The checklists and templates are designed to be copied into a project and filled in.

### Validating the package after a change

```bash
python3 scripts/check-consistency.py
```

The same check runs in CI on every push and pull request (`.github/workflows/validate.yml`). It verifies
headings, balanced code fences, resolvable relative links and basic Groovy consistency.

---

## 🧩 How the package is meant to be used

**For an AI assistant:** the routing table in [SKILL.md](SKILL.md) maps a request to the single most
relevant reference. Load that file rather than the whole package, then answer using the output contract in
section 4 of `SKILL.md`: state the design decision, give the concrete artifacts, cover the operational side,
and declare assumptions and risks. Never invent SAP APIs, adapter parameters or limits.

**For a human:** use `references/` to make a decision, `examples/` to avoid writing boilerplate,
`checklists/` as a gate before implementation, promotion and during an incident, and `templates/` to produce
the documentation the interface needs anyway.

---

## ⚠️ Scope and disclaimer

- This is a **knowledge package, not an executable artifact**. It does not deploy anything and contains no
  tenant-specific configuration, credentials or customer data.
- Sample scripts are provided **as-is**: review, test and adapt them before production use. Pay particular
  attention to the size assumptions stated at the top of each script.
- Platform behaviour, parameter labels and limits change with releases. Where a detail is
  release-dependent, the guides say so instead of guessing; always confirm against the SAP Help
  documentation for your tenant's version.
- SAP, SAP Integration Suite, SAP S/4HANA and SAP BTP are trademarks of SAP SE. This project is not
  affiliated with or endorsed by SAP.

---

## 📄 License

MIT — see [LICENSE](LICENSE).
