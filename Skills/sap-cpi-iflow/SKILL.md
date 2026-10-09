---
name: sap-cpi-iflow
description: >-
  Expert guidance for SAP Cloud Integration (SAP CPI / SAP Integration Suite) iFlow development,
  architecture, optimization, security, transport and operations. Use when designing, reviewing, building,
  optimizing, troubleshooting or operating Integration Flows (iFlows) and the surrounding artifacts:
  Enterprise Integration Patterns (Splitter, Aggregator, Gather, Scatter-Gather, Content Enricher,
  Recipient List, Resequencer, Composed Message Processor), modularity with ProcessDirect, decoupling with
  JMS queues, Data Store, Variables or Event Mesh, high-performance Groovy scripts (StAX, streaming,
  InputStream/Reader, OutOfMemoryError prevention, CodeNarc), Message Mapping, XSLT, XML/JSON/CSV conversion,
  adapter and connectivity configuration (HTTP, OData V2/V4, SOAP, SFTP, IDoc, JDBC, Mail, AS2/AS4, XI,
  Kafka), Exception Subprocess and error handling (Error End vs End Message vs Escalation End, Dead Letter
  Queue, retry patterns, idempotency), Message Processing Log observability (custom status, custom header
  properties, attachments, alerting), security (OAuth2 client credentials, SAML bearer assertion, keystore,
  mTLS, PGP, CSRF tokens for SAP Gateway, externalized parameters, credential handling), performance tuning
  and tenant sizing (worker threads, heap, temporary storage, JMS resource limits, quotas, rate limits),
  content transport and lifecycle (CTS+, Cloud Transport Management, mtar, CI/CD, promotion across
  DEV/TEST/PROD), testing and quality assurance, and incident triage for integration landscapes.
  Trigger this skill for any request mentioning SAP CPI, Cloud Integration, Integration Suite, iFlow,
  Integration Flow, Groovy script in CPI, message mapping, ProcessDirect, JMS queue in CPI, MPL custom
  status, CSRF token in CPI, or an SAP integration design/architecture review.
license: MIT
---

# SAP Cloud Integration (CPI / SAP Integration Suite) — iFlow Expert Skill

This skill turns SAP Cloud Integration knowledge into decisions and code. It guides the design,
implementation, optimization, security, transport and operation of Integration Flows (iFlows) in
**SAP Integration Suite / Cloud Integration**, aligned with the official **SAP Integration Flow Design
Guidelines** and Enterprise Integration Patterns (EIP).

The content is deliberately split into small, focused reference files (progressive disclosure): load only
what the task needs, and prefer the most specific file over the general one.

---

## 🧭 1. Route the task to the right reference

| The user is… | Load this |
| :--- | :--- |
| Designing or reviewing an iFlow architecture, choosing EIP patterns, splitting/modularity/anti-patterns | [references/design-guidelines.md](references/design-guidelines.md) |
| Writing, reviewing or debugging Groovy scripts; hitting `OutOfMemoryError`; memory/thread-safety rules | [references/groovy-best-practices.md](references/groovy-best-practices.md) |
| Needing a ready-made script for a concrete problem (dedupe, chunking, error capture, dynamic filename…) | [references/groovy-recipes.md](references/groovy-recipes.md) · [examples/](examples/) |
| Configuring a connection: HTTP, OData, SOAP, SFTP, IDoc, JDBC, Mail, JMS, Kafka, Cloud Connector | [references/adapters-and-connectivity.md](references/adapters-and-connectivity.md) |
| Mapping or converting a payload: message mapping, XSLT, XML↔JSON, CSV, EDI, canonical model | [references/mapping-and-transformation.md](references/mapping-and-transformation.md) |
| Handling failures: Exception Subprocess, retry, DLQ, error contract, custom status | [references/error-handling-and-monitoring.md](references/error-handling-and-monitoring.md) |
| Making a flow observable and supportable: MPL, log levels, search keys, alerts, triage | [references/monitoring-and-operations.md](references/monitoring-and-operations.md) |
| Dealing with security: authentication, credentials, keystore, CSRF, authorization, secrets governance | [references/security-and-governance.md](references/security-and-governance.md) |
| Fighting performance, sizing or quota problems; large payloads; parallelism | [references/performance-and-sizing.md](references/performance-and-sizing.md) |
| Deciding how to decouple: JMS vs Data Store vs Variables vs Event Mesh | [references/persistence-and-decoupling.md](references/persistence-and-decoupling.md) |
| Transporting content across tenants, CI/CD, promotion, rollback, governance | [references/transport-and-alm.md](references/transport-and-alm.md) |
| Testing, reviewing quality, running a design review or preparing go-live | [references/testing-and-quality.md](references/testing-and-quality.md) · [checklists/](checklists/) |

Supporting artifacts: [checklists/iflow-design-review.md](checklists/iflow-design-review.md) ·
[checklists/go-live-readiness.md](checklists/go-live-readiness.md) ·
[checklists/incident-triage.md](checklists/incident-triage.md) ·
[templates/iflow-design-document.md](templates/iflow-design-document.md) ·
[templates/interface-specification.md](templates/interface-specification.md)

---

## ⚡ 2. The Golden Rules

These are non-negotiable. In any review, check them first; in any design, apply them from the start.

1. **CPI is an orchestrator, not a backend.**
   Keep flows decoupled and stateless where possible, and delegate transactional and domain logic to the
   system of record. Middleware that "knows" business rules becomes a system nobody can replace.

2. **Never materialise a large payload as a `String`.**
   `message.getBody(java.lang.String)` puts the whole message in the JVM heap and can trigger
   `OutOfMemoryError` and a tenant-wide outage under concurrent load. Use `InputStream` / `Reader`,
   StAX for large XML, or a streaming JSON parser for large JSON.

3. **A streaming chain is only as strong as its weakest step.**
   One non-streaming step (message mapping, XML Schema Validation, CSV converter, Aggregator, Data Store
   Write on Cloud Foundry, the IDoc/JDBC/Mail/OData adapters…) breaks streaming for the entire flow.
   Verify the whole chain, not just the script. Where a non-streaming step is unavoidable, split the
   message into chunks first — see [references/performance-and-sizing.md](references/performance-and-sizing.md).

4. **Exchange Properties vs Headers — choose deliberately.**
   Properties (`setProperty`) stay inside the Camel exchange and never travel to the receiver: use them for
   routing keys, flags and state. Headers (`setHeader`) are propagated to outbound adapters by default: use
   them only for protocol metadata, never for large values or secrets (HTTP 431, credential leakage
   through tracing).

5. **Every router needs a default branch.**
   An unmatched condition without a default route silently drops or fails messages. Model the fallback
   explicitly, and decide what the business must see when it happens.

6. **Externalize everything environment-specific.**
   Hosts, ports, paths, credential aliases, schedules and feature flags are `{{parameters}}`. Routing logic
   and canonical mappings are not. Nothing else makes a zero-touch transport possible.

7. **Every production flow has an Exception Subprocess.**
   And the choice between `Error End`, `End Message` and `Escalation End` is a documented design decision:
   it decides whether a JMS transaction is rolled back and retried, whether the caller receives HTTP 500,
   and what the MPL status will be.

8. **Make failures searchable.**
   Set `SAP_MessageProcessingLogCustomStatus` (max 40 alphanumeric characters) and register business keys
   as MPL custom header properties. A failed message that cannot be found by order number is an incident
   that lasts hours instead of minutes.

9. **Never log or transport secrets.**
   No credentials in scripts, headers, properties or attachments — tracing makes headers visible in clear
   text. Use security artifacts and channel-level authentication.

10. **Decouple before you scale.**
    High-volume, bursty or backend-fragile interfaces go behind a JMS queue or an event. In a synchronous
    chain the availability is the product of its links.

11. **Bound your parallelism and your input.**
    Unbounded parallel processing against an SAP backend is a self-inflicted denial of service. Configure
    the inbound size guard on HTTP senders and a sane number of concurrent processes on splitters.

12. **The repository is the source of truth, not the tenant.**
    Integration content is versioned, reviewed and transported — never edited directly in production.

---

## 🛠️ 3. Working procedure

### Step 1 — Profile the requirement before proposing anything
Establish, explicitly and in writing, at least these five facts. If the user has not provided them, ask:
- **Message size** (average and maximum) — this drives the entire streaming decision.
- **Volume and peak rate** (messages/hour, peak messages/second, peak concurrency).
- **Interaction style** — synchronous request/response, asynchronous fire-and-forget, scheduled batch, event-driven.
- **Reliability expectation** — best effort, at-least-once with retry, exactly-once, must-support-manual-replay.
- **Counterparties and contract** — protocols, schemas, who owns the interface specification, error contract.

### Step 2 — Choose the architecture, then the patterns
- Reusable logic → separate iFlow called via **ProcessDirect** (in-memory, no network hop). Never build a
  50-step monolith, and never use ProcessDirect to hide a monolith in another artifact.
- Bulk payload → **Splitter** with streaming enabled (Iterating for line items, General when the envelope
  must be preserved), recombined with **Gather** when a single response is needed.
- Multiple receivers → **Multicast** (sequential unless parallelism is genuinely safe) or **Recipient List**.
- Slow, bursty or fragile receiver → **JMS queue** (retry and dead-letter) or **Data Store** (polling,
  manual replay) — see [references/persistence-and-decoupling.md](references/persistence-and-decoupling.md).
- Enrichment → **Content Enricher** (`Enrich` keeps streaming, `Combine` does not).

### Step 3 — Prefer the standard step over the script
Message mapping, XSLT, Content Modifier with XPath, the XML/JSON/CSV converters, splitters and routers are
reviewable, testable and supported. A Groovy script is the answer when the logic is genuinely procedural,
when no standard step expresses it, or when the standard step would break streaming. SAP guarantees only
the officially supported script APIs; every custom script is the project's own maintenance liability.

### Step 4 — Write code that survives concurrency
Follow the hard rules in [references/groovy-best-practices.md](references/groovy-best-practices.md):
locals not binding variables, no static mutable state, resources closed in `finally`, null-safe access,
logging switchable, no `Eval()`, no `TimeZone.setDefault`, no `parseText(String)`.

### Step 5 — Design the failure path before the happy path
Exception Subprocess, retry strategy with numbers, duplicate/replay handling, dead-letter destination,
custom status values, alert, runbook — see
[references/error-handling-and-monitoring.md](references/error-handling-and-monitoring.md).

### Step 6 — Close the loop
Run [checklists/iflow-design-review.md](checklists/iflow-design-review.md) before implementation and
[checklists/go-live-readiness.md](checklists/go-live-readiness.md) before promotion. Report open risks
explicitly instead of declaring success.

---

## 📤 4. Output contract

When answering a CPI request, produce answers a senior integration developer can act on without follow-up
questions. Unless the user asks for prose only:

1. **State the design decision and the trade-off** in one or two sentences before any detail.
2. **Give the concrete artifacts**: step-by-step iFlow configuration (using the step naming prefixes from
   [references/security-and-governance.md](references/security-and-governance.md)), complete Groovy scripts,
   XPath/predicate expressions, externalized parameter names.
3. **Give the operational side**: what to monitor, which custom status to expect, which alert makes sense,
   what the retry behaviour will be.
4. **Call out risks and assumptions** — especially message size assumptions, streaming breaks, concurrency
   limits, and anything you could not verify.
5. **Never invent SAP APIs, adapter parameters or limits.** If an exact parameter label, method signature or
   numeric limit is not known with confidence, describe the behaviour or the configuration area generically
   and say that the exact label must be verified in the tenant's release.
6. **Prefer verified guidance over plausible guidance.** The reference files in this skill contain
   fact-checked, SAP-documented behaviour; quoting it beats improvising.

---

## 🚫 5. Instant anti-pattern detection

If you see any of these, flag it immediately:

| Anti-pattern | Why it hurts |
| :--- | :--- |
| `message.getBody(String)` on a large payload | Heap exhaustion, `OutOfMemoryError`, tenant outage |
| Message mapping or XML Schema Validation in the middle of a large-payload flow | Breaks the streaming chain |
| Credentials in scripts, headers, properties or Content Modifiers | Plain-text exposure via tracing; secret sprawl |
| Hardcoded hostnames/URLs/paths | Promotion requires manual edits; drift and outages |
| Router without a default branch | Silent message loss |
| No Exception Subprocess | Errors surface as opaque failures hours later |
| `Error End` chosen by accident, or `End Message` chosen to "make it green" | Wrong retry behaviour, hidden failures |
| Unbounded parallel splitter processing | Receiver overload, thread starvation |
| Static mutable state in a Groovy script | Cross-message data leakage between worker threads |
| Payload attachments written unconditionally to the MPL | Storage quota exhaustion and noise |
| Data Store used as a high-frequency queue | Row locks and cleanup latency degrade the tenant |
| Editing flows directly in production | No review, no history, no rollback |
| Testing only the happy path | The failure paths are what break at 03:00 |

---

## 📚 6. Glossary (short form)

| Term | Meaning in this skill |
| :--- | :--- |
| **iFlow / Integration Flow** | The modeled integration process; the unit of design, deploy and monitor. |
| **MPL** | Message Processing Log — the per-message record in Monitor, including status, properties and attachments. |
| **ProcessDirect** | In-memory adapter connecting iFlows on the same tenant, used for modularity without network cost. |
| **Data Store** | Persistent key/value store on the tenant used for polling, parking and deduplication — not a queue. |
| **Exchange Property vs Header** | Internal state vs protocol metadata; see Golden Rule 4. |
| **Streaming** | Processing the payload incrementally through a temporary file instead of holding it in heap. |
| **Custom status** | `SAP_MessageProcessingLogCustomStatus` — a searchable business status replacing generic Completed/Failed. |
| **DLQ** | Dead Letter Queue — where messages go after retries are exhausted, for analysis and replay. |
| **Transport** | Moving integration content between tenants (design → test → production) with its configuration. |

---

## ✅ 7. Quick self-check before answering

- [ ] Did I ask for message size, volume, sync/async and reliability expectation if they were missing?
- [ ] Is the chosen pattern the simplest one that solves the problem?
- [ ] Does my design keep the streaming chain intact end to end?
- [ ] Are all environment specifics externalized parameters?
- [ ] Is there an explicit default branch in every router?
- [ ] Is there an Exception Subprocess, with a justified end event?
- [ ] Did I define the retry, duplicate and replay behaviour?
- [ ] Does the flow set a meaningful custom status and searchable business keys?
- [ ] Did I avoid inventing APIs, parameters or numbers?
- [ ] Did I state my assumptions and the remaining risks?
