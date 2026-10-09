# SAP Cloud Integration — Architecture & Design Guidelines

This guide covers how to structure an iFlow so that it is maintainable, performant and operable: the
qualities that make an integration flow enterprise grade, the Enterprise Integration Patterns (EIP) and how
each one maps to a CPI step, and the anti-patterns that cause most production incidents.

---

## 1. What makes an integration flow "enterprise grade"

SAP describes an enterprise-grade integration flow through a set of qualities. Use them as the review lens,
because every one of them maps to a concrete design decision:

| Quality | What it means in practice | Typical design decision it forces |
| :--- | :--- | :--- |
| **High availability** | The flow never breaks the business process | Decoupling, retry, graceful error handling |
| **Resilience** | Failures are assumed, not avoided; the time between failure and recovery is minimised | Idempotency, DLQ, replay, alerting |
| **Resource management** | Tenant compute, storage and messaging resources are bounded | Streaming, chunking, parallelism limits, retention |
| **Loose coupling** | A failing external component does not cascade | Asynchronous decoupling, timeouts, circuit-breaking behaviour |
| **Handling failures gracefully** | Failures are caught, classified and recorded | Exception Subprocess, custom status, error contract |
| **Readability** | A new developer understands the flow without a walkthrough | Modularity via ProcessDirect, naming conventions, no monolith |
| **Reuse of prepackaged content** | SAP-delivered content and artifacts are reused instead of rebuilt | Mapping/script artifacts, script collections, Accelerator Hub packages |
| **Highest security standards** | Least privilege, no secrets in the flow, protected transport | Security artifacts, channel auth, externalized parameters |
| **Appropriate use of the Partner Directory** | B2B partner data lives in the Partner Directory, not in the flow | Generic B2B flows driven by partner data |
| **Appropriate use of scripting** | Scripts only where standard steps cannot do the job | Standard steps first, script as last resort |

> 💡 **Rule of thumb:** if you cannot explain where the flow stores its state, what happens when the
> receiver is down for 30 minutes, and how support finds a single failed business document, the design
> is not finished.

---

## 2. Layered flow architecture

A readable, testable iFlow separates concerns into layers. This is not bureaucracy — it is what makes the
flow reviewable and replaceable.

```text
┌──────────────────────────────────────────────────────────────────────┐
│ 1. INBOUND LAYER        sender adapter, authentication, size guard   │
├──────────────────────────────────────────────────────────────────────┤
│ 2. VALIDATION LAYER     schema/structure checks, mandatory fields,   │
│                         duplicate detection, business preconditions  │
├──────────────────────────────────────────────────────────────────────┤
│ 3. ENRICHMENT LAYER     lookups against master data / other systems  │
├──────────────────────────────────────────────────────────────────────┤
│ 4. TRANSFORMATION LAYER canonical model → receiver format            │
├──────────────────────────────────────────────────────────────────────┤
│ 5. DELIVERY LAYER       receiver adapter, retry, response mapping    │
├──────────────────────────────────────────────────────────────────────┤
│ CROSS-CUTTING           Exception Subprocess, MPL observability      │
└──────────────────────────────────────────────────────────────────────┘
```

Practical consequences:
- **Layers are candidates for sub-flows.** Validation and enrichment are usually reusable across several
  interfaces; delivery is interface-specific.
- **The canonical model lives at the boundary between layers 3 and 4.** Everything on the left speaks the
  canonical model, everything on the right speaks the receiver's contract.
- **The Exception Subprocess is not a layer of the happy path** — it is a parallel path that must exist for
  every one of them. See [error-handling-and-monitoring.md](error-handling-and-monitoring.md).

---

## 3. Enterprise Integration Pattern catalogue

SAP publishes example integration flows for the patterns below (package *Integration Flow Design
Guidelines – Enterprise Integration Patterns* on the SAP Business Accelerator Hub). The table adds the
CPI implementation and the trap to avoid.

| Pattern | CPI implementation | Avoid |
| :--- | :--- | :--- |
| **Content-Based Router** | Router step with expressions on XPath, headers or properties | Missing default branch |
| **Splitter** | Iterating Splitter (envelope not needed) / General Splitter (envelope preserved) / IDoc Splitter / PKCS#7 / ZIP-TAR / EDI | Splitting a huge payload without streaming; unbounded parallel processing |
| **Aggregator** | Aggregator step with correlation expression + completion condition | Using it as a queue; ignoring the storage backing |
| **Gather** | Gather step after Splitter or Multicast (*Combine*) | Gathering a huge array back into memory before a streaming target |
| **Scatter-Gather** | Multicast + Gather | Assuming parallel branches share exchange properties reliably |
| **Recipient List** | Multicast, or a JMS-based recipient list for dynamic/durable fan-out | Hardcoding recipient systems in the model |
| **Composed Message Processor** | Splitter → Router → per-branch processing → Gather | Doing everything in one branch with a giant mapping |
| **Content Enricher** | Content Enricher step (*Enrich* keeps streaming, *Combine* does not) | N+1 enrichment: one lookup per split item without batching |
| **Content Filter / Message Filter** | Filter step, or XPath-based removal in a Content Modifier | Filtering after the expensive transformation |
| **Resequencer** | Aggregator sorting by a sequence number, then Splitter again | Assuming message order is guaranteed without it |
| **Idempotent Consumer** | Data Store entry keyed by a business key, or an ID-based deduplication step | Deduplicating on a technical ID that changes on replay |
| **Claim Check** | Data Store Write + later Get, passing only the entry ID through the flow | Storing large payloads in headers or properties |
| **Message Translator** | Message Mapping, XSLT or Groovy | Logic duplicated across flows instead of a shared mapping artifact |
| **Process Manager / Orchestration** | Local integration processes, ProcessDirect sub-flows | Building a monolith in the main process |
| **Guaranteed delivery / retry** | JMS sender + JMS receiver with transaction rollback and dead-letter | Retrying a non-idempotent receiver call blindly |

Full details for the decoupling patterns are in
[persistence-and-decoupling.md](persistence-and-decoupling.md); for mapping patterns, see
[mapping-and-transformation.md](mapping-and-transformation.md).

---

## 4. Splitter patterns and their exception behaviour

SAP documents four combinations of Splitter behaviour, and the choice has a direct operational consequence:

| Variant | Gather | Stop on exception | Behaviour | Use when |
| :--- | :--- | :--- | :--- | :--- |
| **1** | No | Yes | The first failing item aborts the remaining items; the Exception Subprocess runs once | All-or-nothing semantics; the receiver rejects partial batches |
| **2** | No | No | Every item is attempted; failures are handled per item | Maximum throughput; each item is independent and individually retryable |
| **3** | Yes | Yes | Splits are recombined, but the first failure aborts the whole batch | You need one aggregate response and strict atomicity |
| **4** | Yes | No | Splits are recombined and failures are tolerated | Partial success must be reported back in a single response |

Decision rules:
1. **Ask what the business needs to see.** "Three of ten records failed" is a legitimate outcome that
   variant 2 or 4 can express; variant 1 cannot.
2. **`Stop on exception = true` plus a non-idempotent receiver** means the already-delivered items stay
   delivered while the flow reports failure. Plan the replay accordingly.
3. **With variant 2, error handling must be inside the split branch**, otherwise a single failure can
   terminate the whole flow depending on configuration.
4. **Enable streaming** on the splitter for anything above a few megabytes, and remember that
   `CamelSplitSize` may only be available on the last item when stream-based splitting is used.

---

## 5. Modularity with ProcessDirect

`ProcessDirect` routes a message to another iFlow **in memory, inside the same tenant JVM**, with no
serialisation and no network hop. It is the primary tool for reuse.

```
Main Flow (interface specific)
   │
   ├── ProcessDirect → [Sub-flow: Validate & Canonicalise]      (reused by many interfaces)
   ├── ProcessDirect → [Sub-flow: Enrich from Master Data]      (reused)
   ├── ProcessDirect → [Sub-flow: Deliver to S/4HANA]           (reused per receiver system)
   └── Exception Subprocess → ProcessDirect → [Sub-flow: Central Error Handler]
```

Rules that keep this healthy:
- **Sub-flows have their own contract**: document the expected incoming headers/properties and the
  expected outgoing ones. Treat it like an internal API.
- **Pass data in exchange properties**, not headers, for internal state.
- **Never use ProcessDirect to split a monolith into arbitrary pieces.** The criterion for a sub-flow is
  reuse or a genuinely different responsibility — not step count.
- **The called flow must exist and be deployed**, otherwise the call fails at runtime. Deployment order
  matters; see [transport-and-alm.md](transport-and-alm.md).
- **Central error handling** is the highest-value sub-flow: one handler, one alert, one runbook.

---

## 6. Routing: Router, Multicast and the default branch

### Content-Based Router
- Conditions are evaluated **top to bottom**; the first match wins. Put the most specific and highest-volume
  conditions first.
- **Always configure a default route.** An unmatched message with no default is a silent loss.
- Keep expressions on headers/properties where possible: XPath evaluation on a large XML payload forces
  parsing and breaks streaming.

### Multicast: sequential vs parallel
| | Sequential | Parallel |
| :--- | :--- | :--- |
| Execution | Branches run one after another in the same thread | Branches run concurrently on worker threads |
| Exchange properties | Changes in one branch are visible to the next | **Not reliably propagated back to the parent exchange** |
| Throughput | Bounded by the sum of branch durations | Bounded by the slowest branch, but consumes more threads |
| Use when | Branches share state or hit the same fragile receiver | Branches are independent and the receiver can take the load |

> ⚠️ **Anti-pattern:** parallel multicast against an SAP backend without a concurrency limit. You are
> competing with production users for the same work processes.

---

## 7. Decoupling: choose the mechanism deliberately

Summary only — the full comparison, limits and decision procedure are in
[persistence-and-decoupling.md](persistence-and-decoupling.md).

| Need | Mechanism |
| :--- | :--- |
| Absorb traffic spikes, retry automatically, protect the receiver | **JMS queue** |
| Poll periodically, park messages for manual replay, deduplicate on a business key | **Data Store** |
| Carry small values between steps or flows within an execution | **Variables / exchange properties** |
| Publish events to other applications on BTP | **Event Mesh / Advanced Event Mesh (AMQP)** |

> ⚠️ **Anti-pattern:** Data Store used as a high-frequency message queue. Database row locking and cleanup
> latency degrade the whole tenant, not just your flow.

---

## 8. Naming and structure

Correct naming is what makes a 40-step flow readable in a review. The full convention table is in
[security-and-governance.md](security-and-governance.md#4-standard-naming-conventions). Minimum rules:

- Every step has a type prefix (`CM_`, `SCR_`, `RTR_`, `SPL_`, `AGG_`, `ENC_`, `CAL_`, `FLT_`, `XSL_`, `MM_`)
  and a name that states its business intent in English, not its technical action (`CM_SetOrderDefaults`,
  not `CM_1`).
- Exchange properties are prefixed `p_`; headers use `h_` or the standard protocol name.
- One integration package per business domain or per source system; one iFlow per interface or per
  reusable responsibility.
- Every flow has a description in the artifact, and every interface has a specification document —
  see [templates/interface-specification.md](../templates/interface-specification.md).

---

## 9. Anti-pattern catalogue

| # | Anti-pattern | Symptom in production | Fix |
| :--- | :--- | :--- | :--- |
| 1 | Monolithic 40+ step flow with no sub-flows | Nobody dares to change it; changes break unrelated interfaces | Extract reusable logic into ProcessDirect sub-flows |
| 2 | Middleware as a backend | Business rules implemented and maintained in CPI only | Move domain rules to the system of record |
| 3 | Large payload loaded as String | `OutOfMemoryError`, worker restarts, tenant-wide impact | Stream; chunk if a non-streaming step is unavoidable |
| 4 | Router without default branch | Messages disappear without a trace | Add an explicit default route that reports the anomaly |
| 5 | Hardcoded endpoints and credentials | Transport requires manual editing; secrets in git | Externalized parameters and security artifacts |
| 6 | Synchronous chain of five systems | End-to-end availability equals the product of five availabilities | Introduce asynchronous decoupling at the weakest link |
| 7 | Retry without idempotency | Duplicate business documents after an incident | Business-key deduplication before the side effect |
| 8 | No Exception Subprocess | Support learns about failures from the business | Add the subprocess with a meaningful custom status |
| 9 | Payload dumps in headers or properties | HTTP 431, credential leakage, huge MPL entries | Use the claim-check pattern (Data Store) for payload handoff |
| 10 | Unbounded parallel splitter | Receiver throttling, timeouts, thread starvation | Bound concurrency; batch the calls |
| 11 | Logic duplicated in several iFlows | Fixes applied in two places out of three | Shared mapping/script artifacts, sub-flows |
| 12 | Everything logged at Debug permanently | MPL storage growth, noise, slower processing | Log levels controlled per environment |
| 13 | Never undeploying unused content | Storage quota pressure; unclear landscape | Governance duty: undeploy unused artifacts |
| 14 | Testing only the happy path | The first real incident is in production | Test failure, retry, duplicate and max-size paths |

---

## 10. Architecture review questions

Use these before the design is frozen — and see
[checklists/iflow-design-review.md](../checklists/iflow-design-review.md) for the full gate:

1. What is the maximum message size, and is the streaming chain intact end to end?
2. What happens to the message if the receiver is unavailable for 30 minutes?
3. If the same message is delivered twice, what does the receiver do — and what do we do?
4. Where is the state, and what happens to it on redeploy or restart?
5. Which resources does the flow consume at peak (threads, temporary storage, queue depth, MPL rows)?
6. How does support find this specific business document after a failure?
7. Which parts of this flow are specific to this interface and which are reusable?
8. What is the rollback plan, and what side effects would a rollback not undo?

---

## Further Reading

- [Integration Flow Design Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/integration-flow-design-guidelines)
- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Guidelines to Implement Specific Integration Patterns](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-implement-specific-integration-patterns)
- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/patterns/messaging/toc.html)
