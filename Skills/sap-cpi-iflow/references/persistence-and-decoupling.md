# SAP Cloud Integration — Persistence & Decoupling Guide

Decoupling means that the availability, speed and failure of one system no longer dictate the behaviour of
the other. Cloud Integration offers four mechanisms for holding or deferring a message — JMS queues, Data
Stores, variables and SAP Event Mesh (AMQP) — and they are easy to confuse because they all "store
something". This guide explains what each one really is, how retries and transactions behave, and how to
choose between them for a new interface. For payload-size and concurrency limits see
[references/performance-and-sizing.md](performance-and-sizing.md); for error semantics see
[references/error-handling-and-monitoring.md](error-handling-and-monitoring.md).

---

## 1. Why decouple

A synchronous chain couples four independent risk profiles into one request: the sender's patience, the
CPI runtime, the receiver's availability, and the network between them.

| Failure mode in a synchronous chain | What the caller experiences | What decoupling changes |
| :--- | :--- | :--- |
| Receiver unavailable (planned maintenance, outage) | Error returned to the caller; the message is lost unless the caller retries | The message waits in a persistent store until the receiver returns |
| Traffic spike far above the receiver's capacity | Timeouts, 429/503 responses, partial processing | The store absorbs the burst; consumers drain it at a rate the receiver can survive |
| Receiver slow (seconds to minutes per call) | A worker thread is occupied for the whole call; latency propagates to the caller | Processing happens asynchronously, off the caller's critical path |
| Transient error mid-processing | Retry logic has to live in the caller | Rollback plus broker redelivery, or a scheduled re-poll, retries for you |
| Sender and receiver have different availability windows | The interface only works when both are up | The interface works when each side is up, independently |

The cost of decoupling is equally real: an asynchronous interface is harder to debug, needs correlation
keys to be traceable, must tolerate duplicate delivery, and consumes tenant storage. Decouple where the
availability or volume profile demands it — not by default.

> 💡 **Rule of thumb.** Synchronous when the caller must have the answer to continue. Asynchronous as soon
> as the answer is not needed immediately, the receiver is unstable, or the peak volume exceeds the
> receiver's documented capacity.

---

## 2. The four options compared

| Aspect | JMS Queue | Data Store | Variables | SAP Event Mesh / AMQP |
| :--- | :--- | :--- | :--- | :--- |
| **Purpose** | Decouple sender from processing, absorb bursts, retry automatically | Hold state or messages for polling, manual replay and deduplication | Carry small values between steps and flows | Publish events to subscribers across applications and tenants |
| **Persistence** | Durable messaging instance; survives restarts | Runtime database; survives restarts | Persisted by the tenant; lifetime depends on the scope chosen | Held by the Event Mesh service instance, outside the CPI JMS resources |
| **Throughput** | Highest of the four — the broker is built for messaging | Moderate: row locks and cleanup latency cap sustained rates | Very high for tiny values, but not designed for payloads | High for fan-out; subscribers process independently |
| **Retry behaviour** | Native: a rolled-back transaction makes the message available again; the adapter retries until the budget is exhausted | Manual: a failed entry stays in the store and the next timer run retries it | None — a variable is state, not work | Consumer-driven: every subscriber owns its own retry and error handling |
| **Cross-flow visibility** | Any flow consuming the same queue; queue depth is visible in monitoring | Any flow reading the same store, within the visibility the step is configured with | Global variables are visible tenant-wide | Subscribers outside the tenant; CPI is one participant among many |
| **Quota consumed** | The tenant JMS instance: 9 GB / 150 transactions default (30 queues), scalable to 30 GB / 500 transactions (100 queues); a single queue comes with ≈ 300 MB capacity and 5 transactions | Tenant runtime database (35 GB scope) plus monitoring storage | Tenant storage; not governed by the JMS resource limits | Quotas of the Event Mesh service instance, not of the CPI JMS instance |
| **When to use** | High-volume asynchronous ingestion, peak smoothing, guaranteed delivery with redelivery | State holding, manual replay, polling patterns, deduplication by business key | Small configuration values, correlation IDs and flags | Fan-out to many independent consumers, cross-application and cross-tenant eventing |
| **When it is an anti-pattern** | One queue per business case until the queue and transaction budget is exhausted; using JMS for request-reply | Using a Data Store as a high-frequency message queue | Storing payloads, business-critical state or anything that must survive for business reasons | Treating events as a substitute for guaranteed, ordered, replayable message processing |

> ⚠️ **JMS is an adapter resource, not a free tier of your own.** The JMS messaging instance is used by the
> JMS, AS2, AS4 **and** XI adapters. An XI interface configured for Exactly Once with JMS Queue temporary
> storage consumes the same shared budget as your own queues.

---

## 3. JMS queues

### Queue semantics

| Concept | Behaviour | Design consequence |
| :--- | :--- | :--- |
| Queue (point-to-point) | Each message is delivered to exactly one consumer | CPI JMS decoupling is built on queues; the same queue can be consumed by several flows or instances |
| Competing consumers | Several consumers on one queue increase throughput | Throughput up, ordering down: two messages of the same business object can be processed in parallel |
| Message ordering | Order is preserved as the broker delivers, which assumes a single consumer | If strict ordering matters, serialise consumption or re-sequence on a correlation key |
| Visibility during processing | While a consumer processes a message it is not available to the others; if processing exceeds the broker's window the message can become visible again | Processing must be idempotent: redelivery of an in-flight message is normal, not exceptional |
| Dead-letter | Messages that exhaust the retry budget must be parked and alerted | Never let a failing message loop forever; give it an owner and a replay runbook |

For publish/subscribe fan-out (every subscriber gets a copy), use SAP Event Mesh with the AMQP adapter
rather than trying to model topics with CPI JMS queues. Note that the AMQP adapter has **no streaming
support**, so event payloads must be sized accordingly.

### The retry pattern

1. **Write** the inbound message to a JMS queue as the first action of the ingestion flow, then end the
   sender's transaction cleanly. The sender is now decoupled from processing.
2. A **consumer flow** reads the queue and processes the message.
3. On failure, let the exception reach the exception subprocess and end it with an **`Error End`** event.
   The transaction is **rolled back**, so the broker makes the message available again and redelivery
   counts against the adapter's retry budget.
4. When the retry budget is exhausted, the message must be moved to a **dead-letter queue** or parked in
   a Data Store, together with the failure reason and the correlation key.
5. Only a **successful** completion commits the transaction and removes the message from the queue.

| Flow instance ends with | Transaction | Effect on the message |
| :--- | :--- | :--- |
| Normal **End** event | Commit | Message removed from the queue |
| **End Message** event in the exception subprocess (reported as Completed) | Commit | Message removed; use it deliberately, for example to park an unusable message and alert instead of retrying |
| **`Error End`** event in the exception subprocess | **Rollback** | Message available again, redelivered, retry budget consumed |
| **Escalation End** event | Documented variant; the instance is reported as escalated | Decide explicitly who reacts; never leave the outcome implicit |

> ⚠️ **`End Message` silently swallows errors.** An exception subprocess that ends with `End Message`
> commits the transaction and the message disappears from the queue — while the flow is reported as
> *Completed*. Use it only when the failure is handled on purpose (parked, alerted, replayed), never as a
> way to make red MPL entries disappear.

### Capacity planning

- Budget queues against the tenant JMS limits: 30 queues default, scalable to 100; 150 transactions
  default, scalable to 500. Each queue costs capacity (≈ 300 MB and 5 transactions per queue).
- Remember the shared users: JMS, AS2, AS4 and XI adapters. XI Exactly Once with JMS Queue temporary
  storage is a JMS consumer too.
- Size the queue for the burst that must survive while the receiver is down — not for the average, and
  not for the worst case you imagine. `burst size = peak rate × expected receiver downtime`.
- Consumers and providers are limited too (150 default, scalable to 500 for consumers; 159 default,
  scalable to 500 for providers): a fleet of parallel consumers can exhaust that budget on its own.

### Monitoring queue depth

1. Watch depth per queue, not just the tenant total; a single stuck queue is invisible in an aggregate.
2. Alert on sustained growth (depth rising across several consecutive checks), not on a single spike.
3. Distinguish "slow consumer" (depth rises and falls) from "broken consumer" (depth rises monotonically,
   dead-letter grows, MPL shows repeated failures).
4. Act: scale consumers, slow the producer at the edge with the sender's Body Size limit, or pause
   ingestion and fix the receiver.

---

## 4. Data Store

### Operations

| Operation | What it does | Typical use |
| :--- | :--- | :--- |
| **Write** | Stores one entry, addressed by an entry ID; existing entries can be overwritten depending on configuration | Persist state, park a message for later processing, record a deduplication key |
| **Get** | Reads a single entry | Retrieve a parked message or the current state for one business key |
| **Select** | Reads multiple entries in one call | Timer-driven batch processing and polling loops |
| **Delete** | Removes an entry | Release storage once the entry has been processed successfully |

Data Store Write and Persist do **not** support streaming on Cloud Foundry; Data Store Get and Data Store
Select do not support streaming either. A Data Store is therefore a place for bounded payloads and state,
not for multi-hundred-megabyte documents — chunk first (see
[references/performance-and-sizing.md](performance-and-sizing.md)).

### The polling model

The Data Store has no push notification. Processing a store means a **timer-triggered flow** that selects
entries and processes them:

```text
Timer (schedule) --> Data Store Select (entries, oldest first, page size N)
                       |
                       +-- for each entry: process --> Data Store Delete on success
                       |
                       +-- on failure: leave the entry in place, count the attempt in the entry
                                       (or a companion entry) and alert after the budget is spent
```

Consequences to design for:

- **At-least-once processing**: an entry that was processed but not yet deleted is processed again next
  time. Make the receiver call idempotent.
- **Overlapping runs**: if a run takes longer than the timer interval, two runs select the same entries.
  Bound the page size and the run duration, or serialise the flow.
- **Poison entries**: an entry that always fails blocks throughput. Track attempts and park it after a
  defined budget.

### Why a Data Store is not a message queue

| Property | JMS queue | Data Store |
| :--- | :--- | :--- |
| Delivery trigger | Broker pushes to a consumer as soon as a message arrives | A timer polls; latency starts at the timer interval |
| Throughput ceiling | Built for messaging | Limited by database row locks and cleanup latency |
| Concurrency | Competing consumers scale naturally | Concurrent selection of the same rows causes contention and duplicate work |
| Cleanup | Broker manages message lifecycle | You manage retention and deletion |
| Redelivery | Automatic with the retry budget | Manual, based on your polling logic |

Use a Data Store for state, replay and deduplication. Use JMS when the requirement is a message queue.

### Idempotency with entry IDs

Use a **business key as the entry ID** (order number, document number, message ID from the sender) so that
the store itself becomes the duplicate detector:

| Approach | What it gives you | What it does not give you |
| :--- | :--- | :--- |
| **Detect** a duplicate — read or write the entry and check whether it already existed | Visibility: you know a duplicate arrived and can log, alert or skip it | No guarantee under concurrency: two parallel branches can both read "not there" before either writes |
| **Prevent** a duplicate — design so that a repeated processing produces the same result | Real protection: repeated delivery is harmless because the receiving system rejects or ignores the second attempt | Cannot be achieved by the store alone; the receiver must support the idempotent operation (create-or-update, or its own uniqueness rule) |

> ⚠️ A "detect then act" check is racy by construction. If duplicates are a business defect, prevention
> must be enforced where the data lands — in the receiver — and the Data Store key is the supporting
> evidence, not the guarantee.

---

## 5. Variables

Variables are the lightest of the four mechanisms and the easiest to misuse.

| Aspect | Local variable | Global variable |
| :--- | :--- | :--- |
| Scope | The current message exchange | The whole tenant |
| Typical content | Flags, temporary keys, small computed values | Small values shared between flows |
| Visibility | Inside the flow execution | Readable by other flows |
| Streaming | **Write Variables does not support streaming** — in either scope | Same |
| Suitable as a payload carrier | No | No |

Rules:

- **Never store credentials or personal data in a variable.** Tracing can expose exchange content in
  clear text; use standard security artifacts and the credential store instead.
- **Never use a variable as a payload carrier.** A documented size limit applies, and a variable is
  resolved into the message, so a large value multiplies heap usage for every message that passes the
  step. Write Variables does not support streaming.
- **Never rely on a variable for business-critical state.** Variables have no queue semantics, no retry
  budget, no dead-letter and no replay runbook: if a value must survive a restart *for business reasons*,
  put it in a Data Store or a JMS queue, or better, in the system of record that owns it.
- Treat global variables as tenant-wide configuration with an owner. An unreviewed global variable is a
  hidden integration contract between flows.

---

## 6. Choosing: a decision procedure

Work through these steps for every new interface. The answer at step 7 is the recommendation.

1. **Can the caller wait?** If the caller needs the result to continue, the interface is synchronous —
   stop here and size the receiver instead (see [references/performance-and-sizing.md](performance-and-sizing.md)).
   If not, continue.
2. **What happens if a message is processed twice?** If a duplicate is harmless, at-least-once delivery is
   acceptable. If a duplicate is a defect, the design must include an idempotency key and a receiver that
   can reject or absorb the duplicate.
3. **What happens if a message is lost?** If loss is unacceptable, the message must be persisted before
   the sender gets its acknowledgement: a JMS queue or a Data Store entry, never a variable.
4. **How fast must the backlog drain, and how large can it get?** Volume and required drain rate point to
   JMS (high volume, automatic redelivery) rather than to a Data Store (state, replay, lower sustained rate).
5. **Does the work need to wait for a human or for a batch window?** Manual replay and controlled
   reprocessing are Data Store patterns, not queue patterns.
6. **Who else must see the message?** Fan-out to several independent consumers is an eventing requirement
   — SAP Event Mesh with the AMQP adapter — not a CPI JMS queue requirement.
7. **Recommendation.** State the chosen mechanism, the capacity it consumes, the retry behaviour, the
   correlation key and the cleanup owner. If two mechanisms seem necessary, split the interface into an
   ingestion step (queue) and a state or replay step (Data Store) instead of overloading one.

### Decisions by scenario

| Scenario | Recommendation | Key design point |
| :--- | :--- | :--- |
| High-volume burst, receiver temporarily slow | JMS queue | Size for `peak rate × downtime`; never let a spike hit the receiver directly |
| Guaranteed delivery, receiver may be down for hours | JMS queue plus dead-letter | Rollback via `Error End`; park and alert once the retry budget is spent |
| Manual replay of rejected messages | Data Store | Entry ID = business key; timer-driven replay flow with an attempt counter |
| Deduplication of inbound messages | Data Store with business key as entry ID | Detect centrally; prevent in the receiver |
| Request-reply with a slow backend | Asynchronous request plus a decoupled reply service | Correlation key in the reply; document the response contract and its latency |
| Cross-tenant or cross-application eventing | SAP Event Mesh (AMQP) | Subscribers own retry; remember the AMQP adapter does not stream |
| Small flag or correlation ID inside one flow | Exchange property or local variable | Never a global variable; never a payload |

---

## 7. Retention, cleanup and housekeeping

Uncleaned stores are a **tenant-wide** risk: the runtime database (35 GB scope), the JMS instance
(9 GB default) and monitoring storage are shared by every interface, so one forgotten Data Store or stuck
queue degrades flows that have nothing to do with it.

Strategy:

1. **Every entry gets an owner and a retention period**, agreed with the business before go-live.
2. **Delete on success** — the normal path must remove the entry, not leave it for a later cleanup.
3. **Retain failures deliberately**, with an attempt counter and a date, so the replay runbook has
   something to work with.
4. **Archive before deleting** if audit requires it. Move the body to the target system or an archive,
   keep only the key and the outcome in the store.
5. **Make cleanup schedulable.** A timer-triggered housekeeping flow that selects and deletes expired
   entries is testable, auditable and visible in the MPL — unlike a manual deletion in the monitor.
6. **Bound every store by construction**: a size or age limit per store, plus an alert when consumption
   grows faster than the message rate.
7. **Clean up the queue estate too**: unused queues still count against the 30-queue default budget, and
   undeployed artifacts keep their queues and stores alive.

| Asset | Cleanup trigger | Evidence to keep |
| :--- | :--- | :--- |
| Data Store entry (success) | Immediately after successful processing | Business key, timestamp, target system response |
| Data Store entry (failure) | After the retention period | Failures plus attempt history, then delete |
| JMS dead-letter queue | After the replay or the decision to discard | Message ID, correlation key, failure reason, decision maker |
| Global variables | Interface retirement | None — remove with the artifact |
| Unused queues and stores | On artifact undeployment review | A short note in the interface's documentation |

---

## 8. Anti-patterns

| Symptom | Root cause | Fix |
| :--- | :--- | :--- |
| Messages disappear although the flow shows failed steps | Exception subprocess ends with `End Message`, which commits the JMS transaction | Use `Error End` when redelivery is required; otherwise park the message and alert deliberately |
| Same business document processed twice, customer complains | At-least-once delivery with a non-idempotent receiver call | Introduce a business key as Data Store entry ID and make the receiver call idempotent |
| Queue depth grows without limit and never recovers | Consumers slower than producers, or a poison message blocking a serialised consumer | Scale consumers, park poison messages after a bounded retry budget, alert on sustained growth |
| Queue and transaction limits reached, new interfaces cannot be deployed | One queue per business case, plus XI Exactly Once flows using JMS temporary storage | Consolidate queues, reuse topics of decoupling, review the XI quality-of-service settings |
| Data Store acts as the tenant's bottleneck | Used as a high-frequency message queue; row locks and cleanup latency dominate | Move the high-volume path to JMS; keep the Data Store for state, replay and deduplication |
| Timer flow reprocesses the same entries | Run duration exceeds the timer interval and entries were not deleted | Serialise the flow, bound the page size, delete on success, shorten the run |
| Runtime database growth with no business growth | Entries never deleted, archiving never implemented, cleanup owned by nobody | Retention plus a schedulable cleanup flow with a named owner |
| Business state lost after a redeploy or restart | State kept in variables instead of a persistent store | Move business-critical state to a Data Store, JMS, or the system of record |
| Flow reported as Completed but nothing arrived at the receiver | `End Message` used to swallow an exception | Report failures with `Error End` or set an explicit custom status and alert |
| Nobody can trace a message across the decoupled hops | No correlation key propagated into the queue message and the MPL | Set a business correlation key and a searchable custom header property at ingestion |

> ⚠️ **Every anti-pattern above is cheaper to fix at design time than in production.** Each one has a
> one-line design rule: idempotency key, bounded retry, explicit end event, retention owner.

---

## 9. Operational checklist for a decoupled interface

- [ ] Queue or store capacity is sized from `peak rate × expected receiver downtime`, against the tenant
      JMS and runtime database limits.
- [ ] Queue depth is monitored and alerting is defined on **sustained** growth, per queue, not on the
      tenant total.
- [ ] Dead-letter handling exists: a target queue or store, an owner and a documented decision process.
- [ ] A replay runbook exists and has been executed at least once in a test tenant.
- [ ] Every message carries a business correlation key, and the key is also a searchable custom header
      property in the MPL.
- [ ] The exception subprocess uses `Error End` where redelivery is required, and `End Message` only where
      failure is intentionally handled.
- [ ] The processing step is idempotent: reprocessing the same message produces the same result.
- [ ] Retention is defined per store and per queue, with an owner and a scheduled cleanup flow.
- [ ] Cleanup is verified: consumption is checked after the first production week and after every peak.
- [ ] Queue and Data Store inventories are reviewed when artifacts are undeployed or retired.
- [ ] Concurrency of consumers is derived from the receiver's capacity, not from the queue depth.
- [ ] Test evidence covers the failure paths: receiver down, receiver slow, poison message, duplicate
      delivery, and restart during processing.

---

## Further Reading

- [Data Store, Variables, and JMS Queues: When to Use Which Option](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/data-store-variables-and-jms-queues-when-to-use-which-option)
- [Apply the Retry Pattern with JMS Queue](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apply-the-retry-pattern-with-jms-queue)
- [JMS Resource Limits and Optimizing their Usage](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/jms-resource-limits-and-optimizing-their-usage)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
