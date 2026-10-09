# SAP Cloud Integration — Error Handling & Error Observability

A robust iFlow is defined by how it behaves when something goes wrong. This guide covers the error
taxonomy, the Exception Subprocess architecture, the end-event decision, retry and dead-letter patterns,
idempotency, and the error contract with the counterpart system.

Operational monitoring, alerting and incident triage are covered in
[monitoring-and-operations.md](monitoring-and-operations.md).

---

## 1. Principles

1. **Failures are normal.** A receiver will be down, a credential will expire, a payload will be malformed.
   Design for the failure path first, not as an afterthought.
2. **Classify before reacting.** Retrying a permanent business error three times wastes 30 minutes and
   creates three failure entries; not retrying a transient network error loses a business document.
3. **Never swallow an error silently.** A `try/catch` that logs nothing and continues is worse than a crash.
4. **The status must tell the truth.** A business rejection is not a technical success. Marking it as
   `Completed` to keep a dashboard green destroys trust in monitoring.
5. **Every error must be findable.** A support engineer must be able to locate the failed message by a
   business key and understand it without reading the payload.

---

## 2. Error taxonomy

Classify every error before deciding what to do with it.

| Class | Examples | Retry? | MPL status | Reaction |
| :--- | :--- | :--- | :--- | :--- |
| **Transient / technical** | Receiver timeout, connection reset, HTTP 503, temporary DNS failure | **Yes**, with backoff | `FAILED` then `RETRY`, or `ESCALATED` if parked | JMS rollback → automatic retry → DLQ |
| **Permanent / technical** | HTTP 404 on a wrong endpoint, TLS handshake failure, unsupported media type | No | `FAILED` | Alert immediately; fix the configuration |
| **Authentication / authorization** | HTTP 401/403, expired certificate, invalid token | No (a renewed credential fixes it) | `FAILED` | Alert with the credential alias; certificate expiry monitoring |
| **Business / data** | Missing mandatory field, unknown code value, validation failure | No | `FAILED` with a business custom status | Notify the business, park the message, provide a correction path |
| **Non-recoverable / poison message** | Structurally invalid payload the receiver always rejects | No | `FAILED` + parked | Dead-letter immediately; never retry-loop |
| **Resource** | Quota exceeded, temporary storage full, JMS queue full, thread starvation | Not usefully | `FAILED` | Alert on the resource, not on the message |

> 💡 **Design consequence:** the flow needs, at minimum, two distinct failure outcomes: *retryable*
> (rollback → retry → dead-letter) and *terminal* (park + notify). If everything ends the same way, either
> you retry poison messages forever or you lose transient failures immediately.

---

## 3. Exception Subprocess architecture

```text
[Main Integration Process]
   Start ──► Validate ──► Enrich ──► Transform ──► Call Receiver ──► End
                 │            │           │              │
                 └────────────┴───────────┴──────────────┘
                                    │  (exception)
                                    ▼
[Exception Subprocess]
   Error Start ──► SCR_CaptureException ──► CM_ClassifyError
                                              │
                        ┌─────────────────────┼──────────────────────┐
                        ▼                     ▼                      ▼
              [Retryable]            [Business error]        [Terminal]
              Error End              End Message +           Park in DLQ +
              (rollback, JMS         custom status           End Message +
               retry)               (200/4xx response)      alert
```

### Accessing exception details

In a Content Modifier:

| Value | Expression |
| :--- | :--- |
| Error message | `${exception.message}` |
| Stack trace | `${exception.stacktrace}` |

In a Groovy script, read the exception from the exchange:

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    Exception ex = message.getProperty("CamelExceptionCaught", Exception.class)

    if (ex != null) {
        message.setProperty("p_errorClass", ex.getClass().getName())
        String msg = ex.getMessage()
        // Keep the summary short and safe: never put a full stack trace in a header
        message.setProperty("p_errorSummary", msg ? msg.take(500) : "No message available")
    } else {
        message.setProperty("p_errorClass", "UNKNOWN")
        message.setProperty("p_errorSummary", "No exception object available in the exchange")
    }

    return message
}
```

A complete, production-ready version with classification and custom status is in
[examples/groovy-exception-details-capture.groovy](../examples/groovy-exception-details-capture.groovy).

> ⚠️ **Do not put stack traces into headers or exchange properties that are later sent to a receiver.**
> They are large, they leak internal structure, and they can break HTTP calls. Attach them to the MPL
> instead, and only when troubleshooting is enabled.

---

## 4. Choosing the end event — the decision that changes everything

SAP documents four Exception Subprocess variants (End event, End Message event, Error End event,
Escalation End event). The choice determines the MPL status, the sender's experience and the retry
behaviour.

| End event | MPL status | Behaviour toward the sender | JMS transaction | Use when |
| :--- | :--- | :--- | :--- | :--- |
| **End** | `COMPLETED` | Normal end of processing (no error signalling) | Committed | The exception was handled and the outcome is intentionally a success — use with extreme care |
| **End Message** | `COMPLETED` | The flow returns what it has produced (e.g. a controlled error response) | Committed, message removed from the queue | Handled business rejections, parking in DLQ, custom error payload returned to the caller |
| **Error End** | `FAILED` | HTTP sender receives **HTTP 500**; JMS sender rolls back and triggers a retry | Rolled back | System errors and unrecoverable failures where the sender must know, and where a retry may help |
| **Escalation End** | **`ESCALATED`** | Ends the subprocess and marks the message as *Escalated* | — | You want the failure visible as a distinct, searchable status (e.g. a dedicated "Escalated" operations tile) without an automatic retry |

Decision procedure:
1. **Is the failure retryable?** → `Error End`, so the JMS transaction rolls back and the retry mechanism
   takes over.
2. **Is the failure a business rejection the sender must see?** → `End Message` with a controlled response
   payload and a business custom status.
3. **Do you want the failure isolated for operational attention** without automatic retry? → `Escalation End`
   and a Monitor tile filtered on `Escalated`.
4. **Are you about to choose `End`?** Justify it in writing. In 9 cases out of 10 the intent was
   `End Message`.

> 💡 **If you use `End Message` in an Exception Subprocess, always set
> `SAP_MessageProcessingLogCustomStatus`** (max 40 alphanumeric characters) to a value such as
> `HANDLED_BUSINESS_ERROR`. Otherwise the failure disappears into a sea of green `Completed` messages, and
> the first symptom of a broken interface is a phone call from the business.

---

## 5. Outsource error handling into a dedicated flow

The same error handling logic is needed by every interface: capture the details, format a notification,
write to the dead-letter store, set the status. Duplicating it in 40 flows guarantees that 12 of them are
out of date.

```text
Main Flow ──Exception Subprocess──► Request-Reply (ProcessDirect) ──► [Central Error Handler Flow]
                                                                       ├─ Enrich with flow metadata
                                                                       ├─ Write to Data Store (DLQ)
                                                                       ├─ Set custom status
                                                                       └─ Notify (alert / mail)
```

Guidelines:
- The central handler receives the error context through **exchange properties** (`p_errorClass`,
  `p_errorSummary`, `p_originalPayloadRef`, correlation keys) — not through headers.
- Give the handler a documented contract: which properties it requires, which it sets, and what it does
  when they are missing.
- Keep the handler's own failure behaviour defined: if the handler itself fails, the main flow must still
  end in a state that produces an alert (never a silent success).
- Use `Request-Reply` when the main flow needs the handler's response (e.g. an error payload to return),
  plain `Request-Reply`/one-way depending on whether the caller needs to wait.

---

## 6. Retry patterns

### Pattern A — Automatic retry with a JMS queue (preferred for transient errors)

```text
Sender ──► [JMS queue] ──► Processing flow ──► Receiver
                                 │ failure
                                 ▼
                        Exception Subprocess
                                 │
                          Error End  → transaction rolled back
                                 │
                        JMS retries (until max retries)
                                 │
                        retries exhausted ──► dead-letter (queue or Data Store)
```

Why it works: the message is not lost on rollback; the broker owns the retry schedule; the sender was
already decoupled and is not blocked.

Rules:
- **Max retries:** configure a small number (typically 3) and make sure the total retry window matches how
  long the receiver is realistically unavailable.
- **Idempotency is mandatory.** A retry re-executes the whole flow from the queue. If the receiver call is
  not idempotent, a retry after a timeout can create a duplicate document.
- **Poison messages must not loop.** If the payload is structurally invalid, retrying cannot help: detect it
  and dead-letter immediately.

### Pattern B — Sender-side retry

The calling application retries on an HTTP 5xx response. Requires a documented, agreed error contract
(status codes, retry interval, maximum attempts) — see [templates/interface-specification.md](../templates/interface-specification.md).

### Pattern C — Flow-level retry with a counter

For a small number of controlled retries inside one flow execution, use an exchange property counter
(`p_retryCount`), a Router on the counter value, and a delay. Appropriate for short transient glitches.
Not a substitute for the JMS pattern: the message is still in flight, occupying a worker thread.

> ⚠️ **Never build an unbounded retry loop.** A receiver that is down for an hour plus an infinite retry
> loop equals a flow that never finishes and a queue that never drains.

---

## 7. Dead letter handling

The dead-letter destination is where messages go when automation has given up. It is a **business**
artefact, not a technical dump.

| Requirement | Why |
| :--- | :--- |
| Every failed message is retrievable with its original payload | Manual replay and root-cause analysis |
| Business keys are stored with the message | Support can find "the order 4711" without reading payloads |
| The failure reason is stored | Distinguish a receiver outage from a data error |
| The timestamp and the attempt count are stored | Understand the incident timeline |
| Retention and cleanup are defined | Uncleaned dead-letter storage becomes a tenant-wide quota problem |
| A replay procedure exists and is rehearsed | A DLQ nobody can replay is just a graveyard |
| Replay is duplicate-safe | Replay must not double-post side effects |

Implement the dead-letter destination as a Data Store (queryable, replayable, good for parking) or as a
dedicated JMS queue. The trade-offs are in
[persistence-and-decoupling.md](persistence-and-decoupling.md).

---

## 8. Idempotency and duplicate handling

Idempotency is not an optimisation — it is the prerequisite for retrying anything.

1. **Identify the business key** (order number, invoice number, document ID + version). Technical message
   IDs change on replay and are therefore useless for business deduplication.
2. **Decide the semantic:**
   - *Detect duplicate* → log it, skip processing, report "already processed" to the caller.
   - *Prevent duplicate* → reserve the key before performing the side effect.
3. **Where to store the key:** a Data Store entry keyed by the business key (with a retention policy), or
   the receiver's own idempotency mechanism when one exists. Prefer the receiver's mechanism when available.
4. **Order of operations:** reserve the key → perform the side effect → confirm. If the flow crashes between
   reservation and side effect, the key must not block a legitimate retry. Design the reservation state
   (in-progress / done) explicitly.
5. **Make the decision visible:** set a custom status such as `DUPLICATE_SKIPPED` so that duplicates are
   observable rather than mysterious.

See [examples/groovy-datastore-idempotency.groovy](../examples/groovy-datastore-idempotency.groovy) and
[persistence-and-decoupling.md](persistence-and-decoupling.md).

---

## 9. The error contract with the counterpart

Agree and document, for every synchronous interface:

| Aspect | Decision to document |
| :--- | :--- |
| Success response | HTTP status, payload/format, correlation ID |
| Business error response | HTTP status (typically a 4xx), payload structure, error code list |
| Technical error response | HTTP status the sender should expect when CPI itself fails (typically 5xx) |
| Retry expectation | Should the sender retry? On which status codes? How many times? With what interval? |
| Duplicate semantics | What happens if the sender re-sends a message it already sent |
| Timeout | The maximum time the caller should wait before assuming failure |
| Correlation | Which identifier links the caller's transaction to the CPI message and to the receiver's document |

For asynchronous interfaces, document instead: the acknowledgement semantics, the dead-letter volume the
sender may query, and the maximum processing latency expected.

---

## 10. Error-handling review checklist

- [ ] Every production flow contains an Exception Subprocess
- [ ] The end event is chosen deliberately and its consequence documented
- [ ] Errors are classified (transient / permanent / business / poison) and treated differently
- [ ] A retry strategy exists, with a bounded number of attempts and an idempotency guarantee
- [ ] A dead-letter destination exists, with business keys, reason and timestamp
- [ ] A replay procedure exists and has been rehearsed
- [ ] Custom status values are defined and set (including for handled business errors)
- [ ] The error contract with the sender is documented
- [ ] The error handler itself cannot fail silently
- [ ] Alerts exist for business-impacting failures, not for every error

---

## 11. Anti-patterns

| Anti-pattern | Consequence | Fix |
| :--- | :--- | :--- |
| No Exception Subprocess | Errors surface as opaque failures, discovered by the business | Add it; classify and record |
| `End Message` without a custom status | Handled errors look like successes; dashboards lie | Always set a business custom status |
| `Error End` on a business rejection | The message is retried forever for a data error | Classify first; park business errors |
| Retry without idempotency | Duplicate documents after every incident | Business-key deduplication |
| Infinite retry loop | The flow never completes; the queue never drains | Bound the attempts, escalate to DLQ |
| Full stack trace in a header | HTTP 431, payload leakage, unreadable MPL | Attach it to the MPL, gate behind a debug flag |
| Catching and ignoring exceptions | Silent data loss | Never swallow; classify and act |
| Dead-letter store without retention | Quota exhaustion across the tenant | Retention plus scheduled cleanup |
| One error handler per flow, copy-pasted | Twelve inconsistent handlers, none current | Central handler called via ProcessDirect |
| Alerting on every error | Alert fatigue; real incidents get ignored | Alert on business impact and accumulation |

---

## Further Reading

- [Define Exception Subprocess](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/define-exception-subprocess)
- [Apply the Retry Pattern with JMS Queue](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apply-the-retry-pattern-with-jms-queue)
- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Use Custom Header Properties to Search for Message Processing Logs](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/use-custom-header-properties-to-search-for-message-processing-logs)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
