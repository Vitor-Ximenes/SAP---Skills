# Changelog

All notable changes to this knowledge package are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the package is versioned by
content, not by code.

## [2.0.0] — Expansion to a full integration lifecycle knowledge package

### Added
- `references/adapters-and-connectivity.md` — adapter selection matrix, HTTP/OData V2-V4 behaviour, file-based
  connectivity, SAP backends (IDoc, SOAP, RFC, Cloud Connector), event and messaging connectivity,
  channel-level security, resilience and retry at the adapter boundary.
- `references/mapping-and-transformation.md` — choosing between message mapping, XSLT, Groovy and the standard
  converters; XML↔JSON conversion pitfalls; canonical data models and mapping governance; CSV/EDI boundaries.
- `references/performance-and-sizing.md` — the three resource dimensions, verified Cloud Foundry tenant scope,
  the condensed streaming capability matrix, splitting/parallelism/backpressure, receiver-side performance,
  a sizing worksheet.
- `references/persistence-and-decoupling.md` — JMS vs Data Store vs Variables vs Event Mesh, JMS resource
  limits, the retry pattern with commit/rollback semantics, idempotency, retention, a decision procedure.
- `references/transport-and-alm.md` — tenant strategy, design-time vs runtime artifacts, the four transport
  options compared, externalization and configuration management, CI/CD, promotion, rollback and governance.
- `references/monitoring-and-operations.md` — MPL internals, log levels, custom status vocabulary, searchable
  business keys, correlation, alerting design, an incident triage runbook, routine operations.
- `references/testing-and-quality.md` — static analysis, unit-testing scripts outside the tenant, simulation
  and mocks, failure-path and volume testing, go-live and hypercare, a test plan template.
- `references/groovy-recipes.md` — a cookbook of idiomatic solutions to recurring CPI problems.
- `checklists/iflow-design-review.md`, `checklists/go-live-readiness.md`, `checklists/incident-triage.md`.
- `templates/iflow-design-document.md`, `templates/interface-specification.md`.
- New examples: streaming CSV reader, streaming JSON (Jackson), exception details capture, Data Store
  idempotency, OData `$batch` payload builder, MIME multipart builder, dynamic SFTP file name, payload size
  guard, value-mapping lookup — plus an index in `examples/README.md`.
- `CONTRIBUTING.md` and this changelog.
- `scripts/check-consistency.py` plus `.github/workflows/validate.yml`: automated validation of headings,
  code fences, relative links and Groovy consistency on every change.

### Changed
- `SKILL.md` rewritten as a real entry point: task→reference routing table, twelve golden rules with
  rationale, a six-step working procedure, an explicit output contract, an anti-pattern detection table,
  a glossary and a pre-answer self-check.
- `README.md` rewritten: full capability matrix, complete repository structure, installation, intended use
  by an AI assistant and by a human, scope and disclaimer.
- `references/design-guidelines.md` expanded: enterprise-grade qualities, layered flow architecture, an EIP
  catalogue mapped to CPI steps, the four documented splitter variants and their exception semantics,
  routing, modularity with ProcessDirect, an expanded anti-pattern catalogue and architecture review questions.
- `references/groovy-best-practices.md` expanded: verified script runtime facts (Groovy 2.4.21 / 4.0.29,
  Rhino 1.7.14, Java 8 libraries, CodeNarc 3.4.0), body-handling decision table, XXE guidance, MPL API usage,
  thread safety, SAP's general scripting guidelines as a do/don't table, script collections, a review
  checklist and a defect table.
- `references/error-handling-and-monitoring.md` expanded: error taxonomy with retry decisions, Exception
  Subprocess architecture, the End / End Message / Error End / **Escalation End** decision matrix, outsourcing
  error handling into a dedicated flow, three retry patterns, dead-letter requirements, idempotency design,
  the error contract, and an expanded anti-pattern table.
- `references/security-and-governance.md` expanded: inbound and outbound authentication options, security
  artifact lifecycle and rotation, transport security, standard naming conventions, parameter externalization
  rules, CSRF handling for SAP Gateway, data protection in flows, roles and separation of duties, a security
  review checklist.
- All existing example scripts rewritten with a problem/usage/assumption header, correct resource handling and
  verified API usage. The StAX example no longer buffers the result in a `ByteArrayOutputStream` (which
  defeated the purpose of streaming): it streams into a temporary file and hands the body over as a stream.

### Fixed
- `examples/groovy-stax-xml-stream.groovy` no longer defeats streaming by accumulating the transformed document
  in heap memory; namespace and attribute handling corrected (written after `writeStartElement`), the
  `parseText`-style patterns removed, XXE protection kept explicit.
- `examples/groovy-csrf-cookie-handler.groovy` now separates the capture and apply phases explicitly, normalises
  multiple `Set-Cookie` values correctly and drops cookie attributes that must not be sent back.
- `examples/groovy-mpl-logging-custom-status.groovy` now enforces the 40-alphanumeric-character limit of the
  custom status, uses typed MPL properties and gates attachments behind an externalized switch.

### Verified against SAP documentation
The following platform facts introduced in this version are taken from SAP Help for SAP Cloud Integration:
the streaming capability matrix per adapter/step; Cloud Foundry tenant scope (2 GB integration content,
9 GB / 150 transactions and up to 30 GB / 500 transactions for JMS, 35 GB MPL persistence, 35 GB runtime
database, 10 GB disk); JMS default and maximum resources and the per-queue capacity; public API rate limits;
the HTTP sender *Body Size* guard and its rejection message; the MPL `MessageLog` API surface; the
`SAP_MessageProcessingLogCustomStatus` property and its 40-alphanumeric-character limit; framework headers and
exchange properties (`SAP_*`, `Camel*`); the four documented Exception Subprocess variants; the general
scripting guidelines; the enterprise-grade design qualities; the documented integration patterns; and the four
content transport options.

## [1.0.0] — Initial release

### Added
- `SKILL.md`, `README.md`, `sap_cpi_skill_guide.md`.
- `references/design-guidelines.md`, `references/groovy-best-practices.md`,
  `references/error-handling-and-monitoring.md`, `references/security-and-governance.md`.
- `examples/groovy-stax-xml-stream.groovy`, `examples/groovy-mpl-logging-custom-status.groovy`,
  `examples/groovy-csrf-cookie-handler.groovy`, `examples/groovy-json-stream-transform.groovy`.
- MIT license.
