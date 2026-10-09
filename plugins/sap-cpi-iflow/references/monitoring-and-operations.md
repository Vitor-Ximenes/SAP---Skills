# SAP Cloud Integration — Monitoring, Observability & Operations Guide

An integration flow is only as good as the answers it can give at 03:12, when nobody remembers the
interface any more and the business asks where one specific document is. This guide defines what
"observable" means for a CPI iFlow, how to use the Message Processing Log (MPL) deliberately instead of
logging everything, how to search and diagnose a failure, what deserves an alert, how to run an incident,
and which routine checks keep the landscape supportable. It complements
[error-handling-and-monitoring.md](error-handling-and-monitoring.md) (Exception Subprocess design) and
[design-guidelines.md](design-guidelines.md) (runtime resource limits).

---

## 1. What "observable" means for an iFlow

An iFlow is observable when a support engineer can answer these five questions from the monitoring
tools alone, without reading the flow model and without asking the developer.

| Operational question | Mechanism that must answer it | Where the answer lives |
| :--- | :--- | :--- |
| Did the message arrive? | An MPL entry exists at all; the entry is created by the sender adapter and by the correlation identifier set at the edge | Monitor → message processing logs; `SAP_ApplicationID` |
| Where is the message now? | MPL status plus explicit milestone custom statuses; queue depth and Data Store entries for decoupled steps | MPL status, custom status, JMS queue monitoring |
| Did it reach the receiver? | A custom status set **after** the receiver step returned success — never before | Custom status (`SENT`, `DELIVERED`), receiver-side application log |
| If it failed, why? | MPL status, exception message and stack trace from the Exception Subprocess, verbose log level for that message, DLQ entry | MPL detail view, DLQ |
| How do I find this one business document again? | Custom header properties holding the business keys plus the correlation identifier | MPL search by custom header property |

Two design consequences follow, and both are cheap: **set a correlation identifier at the entry point** (the
business key, or the technical request ID if no business key exists yet) and propagate it through every
chained iFlow; and **write a custom status at every milestone a support engineer would otherwise guess** —
received, validated, transformed, delivered, parked, rejected.

> ⚠️ **An iFlow whose only observable state is "Completed" is not observable.** If the receiver silently
> accepted a wrong payload, the flow still ends green. Observability is about semantic milestones, not
> about HTTP status codes.

---

## 2. The Message Processing Log

The MPL is the primary operational record. Every design decision here is a trade-off between evidence and
storage: the MPL is a persisted, bounded resource, not an unlimited log file.

### 2.1 Log levels and their cost

| Level | What it is for | Typical use | Cost profile |
| :--- | :--- | :--- | :--- |
| Trace | Maximum verbosity: step-level detail, script-written properties and log entries | Short, targeted diagnosis of one message | Highest: many entries per step, large MPL volume |
| Debug | Detailed diagnostic information, still below Trace | Investigating one interface during an incident window | High |
| Info | Default production level: lifecycle events without step internals | Normal operation | Baseline |
| Warn | Recoverable anomalies | Normal operation with a low-noise signal | Baseline |
| Error | Failures only | Always on | Low |

Points that matter in practice:

- **Script-step properties and script log output are only visible in the MPL at Debug or Trace.** A script
  that "logs" a value with a `MessageLog` call produces nothing visible at Info — do not design alerting or
  reporting on top of Debug-only data.
- **Raise the level for a reason and lower it again.** Leaving Trace enabled on a high-volume interface is
  the single most common cause of MPL storage pressure.
- **Set the level per message** by passing the `SAP_MessageProcessingLogLevel` header on the input message.
  This forces the log level used to record the processing of that message, which is exactly what you want
  when a specific business document must be traced without turning on Debug for the whole interface.
- **Read it at runtime** to make expensive diagnostic output conditional: the value is an ordinary header
  on the input message, so `message.getHeader('SAP_MessageProcessingLogLevel', String.class)` gives the
  effective level inside a script.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Effective level for this message: forced per message by the inbound header, else the tenant default.
    String level = message.getHeader('SAP_MessageProcessingLogLevel', String.class) ?: 'INFO'
    boolean verbose = level.equalsIgnoreCase('DEBUG') || level.equalsIgnoreCase('TRACE')

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null && verbose) {
        // Size assumption: diagnostic evidence is an exception case < 1 MB, never a bulk payload.
        String body = message.getBody(String.class)
        if (body != null) {
            messageLog.addAttachmentAsString('DiagnosticPayload', body, 'text/plain')
        }
    }
    return message
}
```

### 2.2 Statuses and what produces them

The status shown in Monitor tells the operator what the runtime did with the message. The exact set rendered
in your tenant follows your tenant version, so verify before building an alert rule on a specific label.

| Status | Semantics | What typically produces it |
| :--- | :--- | :--- |
| Completed | Processing ended successfully | Normal `End` event; an Exception Subprocess that ends with `End Message` is also reported as Completed |
| Failed | Processing ended with an unhandled error | `Error End` event in the Exception Subprocess, or an exception that no handler caught |
| Escalated | Processing ended without success but without a hard failure; needs a human | `Escalation End` event in the Exception Subprocess |
| Retry | The message will be processed again by the runtime | Queue-based delivery where the transaction was rolled back so the message stays available for redelivery |
| Discarded | The message is no longer processed automatically | Exhausted retries or explicit dead-letter handling; the message must be found in the DLQ and replayed manually |

> 💡 **Design rule:** choose the end event of the Exception Subprocess by asking what the *operator* needs
> to see, not only what the sender needs to receive. `End Message` produces a green Completed message — pair
> it with a custom status such as `BUSINESS_REJECTED`, otherwise the failure disappears from every
> operations report.

### 2.3 Custom status: `SAP_MessageProcessingLogCustomStatus`

The exchange property `SAP_MessageProcessingLogCustomStatus` is the cheapest semantic signal available. It
is transferred as the `CustomStatus` attribute of the MPL root and stored in the MPL header table, which
makes it filterable in Monitor.

Constraints and conventions:

- The documented limit is **at most 40 alphanumeric characters**. Keep separators such as `_` inside that
  budget and verify the rendered value in Monitor.
- Use a **closed vocabulary**: uppercase, no free text, no dynamic content, no IDs (IDs belong in custom
  header properties).
- Set it **after** the milestone actually succeeded. A status written before the receiver call produces a
  green-looking message with a missing document.

| Custom status | Meaning | Required action |
| :--- | :--- | :--- |
| `RECEIVED` | Message accepted at the entry point, processing started | None |
| `VALIDATED` | Structural and business validation passed | None |
| `MAPPED` | Transformation to the target structure completed | None |
| `SENT` | Receiver call returned success | None |
| `QUEUED` | Parked for asynchronous processing | None |
| `NO_RECIPIENT` | Router found no target for this message | None unless the volume pattern changes |
| `DUPLICATE_IGNORED` | Idempotency check rejected a re-delivery | None; investigate if the rate rises |
| `BUSINESS_REJECTED` | Receiver rejected the document on business grounds | None; the business process owns it |
| `RETRY_PENDING` | Redelivery scheduled by the runtime | Monitor; escalate if it persists |
| `DLQ` | Message moved to the dead letter queue | Operator: inspect, fix, replay |
| `RECEIVER_UNAVAILABLE` | Receiver unreachable or returned a server-side error | L2: check receiver health and retry |
| `MAPPING_FAILED` | Transformation or mapping failure | L2: fix mapping or source data quality |

### 2.4 Custom header properties for search

`messageLogFactory.getMessageLog(message)` returns the `MessageLog` used to enrich the MPL.
`addCustomHeaderProperty(String name, String value)` persists a name/value pair that is searchable in
Monitor (Properties tab). The factory **can return null** (for example during local simulation) — always
null-check before using it.

Log the keys an operator would search with, not everything that is technically available:

| Domain | Property names worth logging | Why it is searched |
| :--- | :--- | :--- |
| Generic | `CorrelationId`, `InterfaceName`, `SourceSystem`, `MessageType`, `BatchId` | Any "find this message again" request |
| Order | `OrderId`, `SalesOrderNumber`, `OrderType` | Order status complaints, missing order investigations |
| Invoice | `InvoiceNumber`, `CompanyCode`, `FiscalYear` | Finance reconciliation, tax reporting questions |
| Customer | `CustomerId`, `BusinessPartnerId`, `Country` | Master-data driven failures |
| Employee | `EmployeeId`, `PersonnelNumber`, `CostCenter` | HR interface questions |
| Shipment / Delivery | `DeliveryNumber`, `ShipmentId`, `Carrier` | Logistics escalations |
| Payment | `PaymentId`, `PaymentReference`, `BankAccountId` | Treasury and reconciliation issues |

Rules that keep this useful:

- Keep the vocabulary stable: same meaning, same name in every flow — `OrderId` in one interface and
  `SalesOrder` in another cannot be searched with one query.
- Never log credentials, tokens, bank details, or personal data beyond what the business case justifies.
- Prefer `addAttachmentAsString` over `addCustomHeaderProperty` for anything long or structured, and keep
  composite values out of the search vocabulary.

### 2.5 Attachments and other payload evidence

`addAttachmentAsString(String name, String text, String mediaType)` stores payload evidence with the MPL
(if `mediaType` is passed as `"null"`, `text/plain` is assumed). Attachments appear in Monitor under the
message's Attachments.

| Legitimate use | Not a legitimate use |
| :--- | :--- |
| Storing the failing request/response of a rejected message | Storing every payload of every message "just in case" |
| Storing the payload that could not be parsed by the receiver | Archiving business documents (that is the backend's job) |
| Storing a compact audit extract (IDs, totals, hash) | Storing bulk files to "check later" |

The cost argument is quantitative: in the Cloud Foundry environment, message processing log persistence is
35 GB for the whole tenant, so unconditional payload logging shortens the retention window for *all*
messages. Gate payload logging on the effective log level or an externalized parameter — never on nothing.

> ⚠️ **Remember the visibility rule:** properties and log entries written by a Script step appear in the MPL
> only at Debug or Trace. If a support process depends on such data, it must be set as a custom header
> property or a custom status instead — those are persisted regardless of the log level.

---

## 3. Searching and diagnosing

### 3.1 Finding the message

| Search axis | Use it when | Practical note |
| :--- | :--- | :--- |
| Custom header property | A business document must be found (`OrderId`, `InvoiceNumber`) | Requires that the flow logged the key (§2.4) |
| Correlation identifier | Following one message across chained iFlows | Requires propagation at every hop |
| Time window | Establishing the blast radius of an incident | Always combine with an interface or status filter |
| Status | Finding everything that did not succeed | Completed-with-`End Message` failures must be caught by custom status, not by status alone |
| MPL public APIs | Bulk extraction, automated reporting, long-term evidence | Subject to rate limits per tenant and per user; batch and cache, do not poll per message |

### 3.2 Correlation

The framework provides `SAP_ApplicationID`, `SAP_Sender` and `SAP_Receiver` headers (plus
`SAP_ReceiverOverwrite` to override the recorded receiver). Use them deliberately:

- **`SAP_ApplicationID`** is the end-to-end correlation key. Set it at the edge from the incoming message
  (a business key if one exists, otherwise the technical request ID) and copy it forward on every hop.
- **`SAP_Sender` / `SAP_Receiver`** make the MPL readable: an operator sees *which* system and endpoint was
  involved without opening the flow model.
- **When chaining iFlows** (for example via ProcessDirect to a reusable child flow), pass the correlation key
  explicitly: a child flow that generates its own ID fragments the trace and doubles the MTTR.

> 💡 **Correlation is a design decision, not a monitoring feature.** If the key is not set in the first
> step of the first flow, no search in Monitor will ever reconstruct it.

### 3.3 Adapter tracing and verbose MPL entries

Adapter-level tracing and verbose step logs answer "what exactly was sent and returned". Rules of use:

| Turn it on when | Turn it off when |
| :--- | :--- |
| A specific message must be proven wrong (payload or receiver answer) | The reproduction is captured — immediately |
| The failure is not reproducible and needs a sample | The interface is healthy again |
| A receiver dispute must be settled with evidence | The support window closes |

Prefer the per-message header `SAP_MessageProcessingLogLevel` over a tenant-wide level change: it gives you
the same evidence for one message and leaves the volume of every other message untouched.

### 3.4 Worked diagnosis narrative

A message is reported failed at 03:12. The following works with only the mechanisms above:

1. **Confirm the claim.** Search the MPL for the interface and the 03:05–03:20 window; filter on the failing
   status. The count tells you whether this is one message or a batch — the blast radius decides the
   severity, not the tone of the report.
2. **Identify the receiver.** Read `SAP_Receiver` (and `SAP_Sender`) on the failing entry. That immediately
   separates "our mapping" from "their endpoint".
3. **Locate the milestone.** Read the custom status: `RECEIVED` only ⇒ failure before validation;
   `MAPPED` ⇒ failure at or after the receiver call; `DLQ` ⇒ already handed to operations.
4. **Read the failure.** Open the entry: the exception message and stack trace captured by the Exception
   Subprocess describe the failing step. If they are not enough, re-drive one message with
   `SAP_MessageProcessingLogLevel` set to Debug or Trace and capture the evidence at full verbosity.
5. **Prove the payload was wrong (or right).** Retrieve the attachment written by the flow, or the receiver
   response recorded at verbose level, and compare it with the contract. This is the artefact the business
   will accept as evidence; a screenshot of a red status is not.
6. **Close the loop with the business.** Report: business key, interface, timestamp, receiver, root cause,
   mitigation applied, and the re-delivery status of the affected documents. Business keys come from the
   custom header properties — this is the reason §2.4 exists.

---

## 4. Alerting and incident management

### 4.1 What deserves an alert

| Alert | Do not alert |
| :--- | :--- |
| A business-impacting interface failed and did not recover | Every single message that ends in an error, including expected business rejections |
| Accumulation: DLQ grew, queue depth above threshold, Data Store entries not being consumed | A single retry that succeeded on the second attempt |
| A scheduled flow did not run at all in its expected window | A slow-but-successful flow inside its SLA |
| Credential or certificate approaching expiry | Routine volume changes inside the expected band |
| Receiver permanently unavailable (repeated failures over a window) | Known maintenance windows already suppressed |

Everything else belongs in a **report**: daily counts per interface and status, DLQ age, top error classes.
Reports are read; alerts interrupt people, and alert fatigue produces missed outages.

### 4.2 What an actionable alert must contain

1. Flow / interface name and environment.
2. MPL message ID of a representative failure (so the operator opens the right record).
3. Correlation key or business key, if known.
4. One-line error summary from the exception message.
5. Occurrence count and first/last occurrence in the affected window.
6. Runbook link and current owner (L2/L3).

An alert missing items 2 and 6 forces the operator to search from scratch — which is exactly the work the
alert was supposed to save.

### 4.3 Avoiding alert fatigue

| Technique | How to apply it |
| :--- | :--- |
| Thresholds on rates | Alert on N failures in M minutes, not on the first failure |
| Aggregation | One alert per interface per window, with a count, instead of one per message |
| Suppression windows | Silence alarms during announced receiver maintenance |
| Recovery notification | Send a single "recovered" event so the incident can be closed without polling |
| Ownership routing | Route to the team that can fix it; an alert nobody owns is noise |
| Regular pruning | Delete or re-baseline alerts that fired without action in the last quarter |

### 4.4 Escalation path and ownership

| Level | Owns | Time to act | Escalates when |
| :--- | :--- | :--- | :--- |
| L1 (operations) | Acknowledge, classify, replay from DLQ, restart scheduled flows | Minutes | The failure is not a known pattern, or a credential/tenant-wide problem is suspected |
| L2 (integration) | Flow logic, mappings, credentials, adapter configuration, deployment | Hours | The defect is in a backend system or in the receiver's behaviour |
| L3 (platform / architecture) | Tenant resources, capacity, networking, receiver-side root cause with the owning team | Day | — |

Document for each interface: owner, receiver contact, replay procedure, and whether replay is idempotent.

---

## 5. The incident triage runbook

For the on-call checklist version, with severity and communication steps, see [checklists/incident-triage.md](../checklists/incident-triage.md).

### 5.1 The procedure

1. **Confirm scope and impact.** Which interfaces, which business documents, since when, how many. Verify
   that the report is real before touching anything.
2. **Locate the messages.** Use the search axes of §3.1; capture MPL message IDs as evidence before any redeploy or replay.
3. **Classify the failure.** Use the classes in §5.2 — the class determines the fix and the owner.
4. **Mitigate.** Replay from the DLQ, restart the schedule, redeploy the last known-good artifact version,
   or fail over to the backup receiver path. Mitigation restores service; it does not require the root cause
   to be known — but it must be recorded.
5. **Communicate.** One message to the business and one to the operations channel, both with the same facts:
   impact, workaround, next update time. No estimates you cannot hold.
6. **Root-cause and prevent.** Fix the defect, add the missing test or alert, and write down what would have
   detected it earlier. If nothing would have, that is the action item.

### 5.2 Failure classes

| Class | Typical symptoms | First three checks | Usual fix |
| :--- | :--- | :--- | :--- |
| Sender-side | Message never reaches the flow, or fails at the first step | Sender-side authentication; **Body Size** limit of the HTTP-based sender adapter; payload shape | Fix the caller, relax or align the size limit, and return a clear error to the sender |
| Flow logic | Wrong routing, duplicated or missing items after a split | Router default branch; splitter/aggregator configuration; loop termination | Correct the model, add the missing default branch, redeploy |
| Mapping | Message fails in the mapping step or produces an invalid structure | Mandatory source fields; namespace and structure of the source payload; mapping version deployed | Fix the mapping or reject bad source data at the entry point |
| Receiver-side | Receiver returns a server error or times out; failures cluster on one endpoint | Receiver availability and its own logs; request payload as sent; whether a retry already succeeded | Coordinate with the receiver owner; replay once healthy |
| Credential / certificate | Failures start abruptly and affect every message of one interface | Expiry date of the certificate or keystore entry; whether a secret was rotated; the security artefact deployed | Renew/rotate the artefact, redeploy, replay |
| Quota / resource | Throughput collapses or messages are rejected as volume grows | Temporary storage usage; JMS queue capacity and transactions; rate limits on public APIs | Decouple with queues, split payloads, slow the producer, raise self-service limits |

### 5.3 Symptom-to-action quick table

| Symptom | Likely cause | Action |
| :--- | :--- | :--- |
| `Message body exceeds configured size limit (…)` | Payload above the sender adapter Body Size parameter; subsequent steps do not execute | Coordinate with the sender on size, or raise the limit, or split the message |
| MPL green but no document at the receiver | Exception handled with `End Message`, or custom status set before the receiver call | Inspect the custom status; move the status write after the receiver step |
| Queue depth grows monotonically | Receiver slower than producer, or consumers failing | Check receiver health and consumer errors; throttle the producer; scale consumers within limits |
| Same message appears several times at the receiver | Redelivery without receiver-side idempotency | Agree an idempotency key with the receiver, log `DUPLICATE_IGNORED` |
| Failures start at a fixed time of day | Scheduled flow colliding with receiver batch window or maintenance | Reschedule or add suppression |
| A flow that always worked now fails on login | Credential rotation or expired certificate | Check the security artefact and redeploy |
| Everything slows down progressively and then recovers | Temporary storage pressure from streaming, or MPL/DB growth | Inspect temporary storage, MPL retention and volume; move bulk work off-peak |

> ⚠️ **Do not build alerting on exact error text.** Message wording varies by runtime version, adapter and
> language. Alert on status, custom status, counts and durations; use the text only for humans.

---

## 6. Routine operations

| Task | Cadence | Owner | Evidence produced |
| :--- | :--- | :--- | :--- |
| Check DLQ and failed messages; replay or close | Daily | L1 | DLQ age and count trend |
| Review MPL growth and retention behaviour | Monthly | L2 | Retention window actually available |
| Inspect temporary storage usage | Weekly (daily for bulk interfaces) | L2 | Peak usage vs. limit |
| Review JMS queue depths and consumer health | Daily (automated threshold) | L1 | Queue depth trend |
| Review one-off scheduled jobs and stuck Data Store entries | Weekly | L2 | List of parked entries with age |
| Review unused artifacts and undeploy them | Quarterly | L2 | Content inventory vs. used interfaces |
| Review expiring certificates, keystores and credentials | Monthly | L2 | Expiry calendar with lead times |
| Review externalized parameter drift between environments | With every transport | L2 | Configuration comparison report |
| Re-baseline alert thresholds and remove dead alerts | Quarterly | Operations lead | Alert catalogue |
| Re-test replay procedures (restore confidence in the runbook) | Half-yearly | L2 | Rehearsal record |

---

## 7. Observability anti-patterns

| Symptom | Root cause | Fix |
| :--- | :--- | :--- |
| Nobody can find a specific business document | No custom header property for business keys | Log 2–4 stable business keys per interface (§2.4) |
| Support cannot tell where a message stopped | Only `Completed`/`Failed` are used | Write milestone custom statuses (§2.3) |
| A green message produced no document | Failure handled with `End Message`, no custom status | Set a distinct custom status before ending the subprocess |
| Alert storm on every business rejection | Thresholds alert on the status, not on business semantics | Separate technical failure from `BUSINESS_REJECTED`; aggregate and threshold |
| MPL retention collapsed to a few days | Trace or Debug left enabled, or unconditional payload attachments | Gate verbosity on `SAP_MessageProcessingLogLevel` and an externalized parameter |
| "We log everything" — but nothing is searchable | Log level Info, so Script-step properties never reach the MPL | Use custom header properties for anything support must search |
| Root cause unknown after a week of retries | No correlation key propagated across chained flows | Set `SAP_ApplicationID` at the edge and pass it on every hop |
| Temporary storage exhausted during peak | Streaming plus bulk payloads, reviewed only after the incident | Monitor temporary storage weekly; split large payloads |
| Same incident repeats every month | Triage stopped at mitigation; no prevention action | Require a prevention item in every post-incident write-up |
| Alerts nobody reacts to | Unclear owner and no runbook link | Attach owner and runbook to every alert definition |

---

## 8. Pre go-live observability checklist

- [ ] Custom status vocabulary agreed and set at every milestone of the flow
- [ ] Business keys logged as custom header properties, with landscape-wide naming
- [ ] `SAP_ApplicationID` (or the agreed correlation key) set at the entry point and propagated to child flows
- [ ] Exception Subprocess sets a status and writes the failing payload as an attachment where useful
- [ ] Payload logging is switchable (log level or externalized parameter) and off by default
- [ ] Alert configured with threshold, owner, and runbook link — and tested by inducing a failure
- [ ] DLQ / retry behaviour verified and a replay procedure written down
- [ ] MPL retention expectation agreed with operations, and the log level of the artifact reviewed
- [ ] Interface owner, receiver contact, and escalation path recorded in the interface documentation
- [ ] Routine operational task for this interface added to the operations calendar
- [ ] Volume baseline captured in the test environment and compared with expectations for production
- [ ] Post-go-live review scheduled (hypercare exit criteria defined)

---

## Further Reading

- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
- [Add Information to the Message Processing Log](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/add-information-to-the-message-processing-log)
- [Use Custom Header Properties to Search for Message Processing Logs](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/use-custom-header-properties-to-search-for-message-processing-logs)
- [JMS Resource Limits and Optimizing their Usage](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/jms-resource-limits-and-optimizing-their-usage)
- [System Scope in the Cloud Foundry Environment](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/system-scope-in-the-cloud-foundry-environment)
