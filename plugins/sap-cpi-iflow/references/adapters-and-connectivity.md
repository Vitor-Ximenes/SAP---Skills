# SAP Cloud Integration — Adapters & Connectivity Guide

This guide is the reference for choosing, configuring and hardening the connectivity between an integration flow and the outside world. It answers three questions in order: which adapter fits a given integration problem, how that adapter must be configured so the flow behaves predictably under load and failure, and which connectivity choices are architectural mistakes regardless of how well they are configured. Adapter names are only the entry point — the decisions that matter are payload size and streaming, synchrony, quality of service, authentication model and who owns failure handling.

---

## 1. Adapter selection matrix

| Use case | Recommended adapter (sender → receiver) | Why | Main caveat |
| :--- | :--- | :--- | :--- |
| SAP backend pushes/consumes IDocs (ALE, master data, EDI outbound) | IDoc sender; IDoc receiver | Native SAP document format, no mapping of the envelope required, transport-level status handling | **No streaming** — an IDoc package is materialised in memory. Split large packages before processing |
| Standardised web service to/from a non-SAP system | SOAP 1.x sender; SOAP 1.x receiver | WSDL-driven, WS-Security support, widely understood by partners | Streaming is lost as soon as WS-Security is enabled; streaming is available in the general (plain) case |
| Reliable asynchronous exchange with an SAP backend | SOAP SAP RM receiver | Reliable-messaging protocol designed for SAP-to-SAP exchange; supports asynchronous, EO-capable quality of service | The receiver must actually support SAP RM; not a substitute for business-level idempotency |
| Read/write SAP business objects (S/4HANA, SAP Gateway) | OData V2 receiver | Service catalogue, CSRF handling, `$batch`, filters and paging built into the protocol | **No streaming**; `$batch` inflates memory further because the whole batch is assembled before sending |
| Modern SAP APIs published as OData V4 | OData V4 receiver | Current SAP API generation, JSON-first, `@odata` annotations | **No streaming**; batch and delta semantics differ from V2 — do not copy V2 patterns blindly |
| Generic REST integration, webhooks, custom APIs | HTTP sender; HTTP receiver | Universal, streaming enabled, full control of headers, methods and status codes | Nothing is handled for you: auth, CSRF, paging, retry and error mapping are your configuration |
| File exchange with a partner or on-premise system over SSH | SFTP sender; SFTP receiver | Encrypted transport, key-based auth, directory semantics | Streaming only while **Change Directories Stepwise** is disabled |
| File exchange where only plain FTP/FTPS is offered | FTP sender; FTP receiver | Same file semantics as SFTP | Same streaming restriction; credentials travel in clear text on plain FTP — insist on FTPS or SFTP |
| Human-facing notifications and attachment distribution | Mail sender; Mail receiver | Reaches people where they work, no partner-side development | **No streaming**, attachment sizes are bounded in practice, delivery is not transactional — never use Mail as a transport for business-critical data |
| Direct database read or write | JDBC sender (poll); JDBC receiver | Lowest-friction access to a legacy schema or a staging table | **No streaming**, blocking I/O, schema coupling. Prefer an API or event when one exists |
| Decouple steps *inside* the tenant, buffer peaks, retry with persistence | JMS sender; JMS receiver | Durable queue inside the tenant, at-least-once delivery, redelivery and dead-letter handling, streaming enabled | Consumes the shared messaging instance (see §5); a single queue has limited capacity and transactions |
| Cross-application events on SAP BTP | AMQP sender; AMQP receiver | Standard broker protocol; connects to SAP Event Mesh / Advanced Event Mesh | **No streaming**; topic vs queue semantics must be understood before use |
| Legacy SAP PI/PO style connectivity, EO with temporary storage | XI sender; XI receiver | XI message protocol with quality-of-service control and EO when a JMS queue is used as temporary storage | EO with **Data Store** temporary storage is not supported on Cloud Foundry; shares the JMS instance |
| B2B EDI over HTTP with signed receipt | AS2 sender; AS2 receiver | Standardised partner protocol with MDN acknowledgement, signing and encryption | **No streaming**; shares the JMS instance; partner-specific configuration must be versioned |
| B2B EDI with a modern, WS-based reliable profile | AS4 sender; AS4 receiver | Payload-agnostic, supports receipt and signing, increasingly the preferred B2B profile | **No streaming**; shares the JMS instance |
| SuccessFactors integration | SuccessFactors OData V2 / OData V4 / REST / SOAP adapters | Built-in handling of the SuccessFactors API specifics (paging, auth, payload conventions) | **No streaming** in any SuccessFactors variant; paging must be designed explicitly |
| Reuse between flows inside one tenant | ProcessDirect sender; ProcessDirect receiver | In-memory handover, no network hop, no serialisation, lowest latency | Only valid inside the same tenant; a chain of ProcessDirect calls is not a modular architecture by itself |
| Parking, deduplication, state and manual replay | Data Store write / select / get | Persists the payload for later inspection, replay or dedup checks | *Write* is not streaming on Cloud Foundry; *Get* and *Select* are never streaming |
| High-volume event backbone, replay, ordered streams | Kafka sender; Kafka receiver | Durable partitioned log, consumer offsets, replay, horizontal scale | **No streaming**; ordering is guaranteed only within a partition |
| Direct ABAP function call to an on-premise system | RFC receiver (through Cloud Connector) | Direct BAPI/RFC invocation without building an OData service | **No streaming**, requires Cloud Connector and an explicitly exposed resource; couples CPI to internal function signatures |
| One flow serving many B2B partners | Generic adapter (HTTP / SOAP / XI) parameterised from the Partner Directory | Partner-specific endpoints and credentials resolved at runtime instead of one flow per partner | Partner Directory APIs are rate limited; parameter governance and testing discipline are mandatory |

**When an adapter is the wrong tool.** An adapter moves bytes; it does not create an integration contract. It is the wrong tool when the real problem is mediation (use mapping and routing steps), when two backends must be updated atomically (no CPI adapter provides a distributed transaction — design compensation instead), when the receiver already publishes events (subscribe instead of polling), when CPI is used as a file server, database or long-term archive, or when a synchronous request-reply chain is stretched across systems that are only loosely available. In those cases the connectivity works and the architecture still fails.

---

## 2. HTTP and OData connectivity

### 2.1 Timeout, connection pool and retry semantics

- **Set both a connection timeout and a response (read) timeout explicitly.** A connection timeout protects you from an unreachable host; a response timeout protects you from a reachable but unresponsive one. Leaving the response timeout open means a stuck receiver holds a worker thread until the platform intervenes.
- **Respect the timeout budget.** The sender's own timeout must be larger than the sum of the timeouts on every hop the request passes through. If CPI waits longer than the caller does, the caller gives up first and the flow continues processing a request nobody is listening to.
- **Connection pooling is per receiver channel.** Reusing connections avoids a TLS handshake per message, but pool size is a concurrency ceiling: parallel branches that exceed it queue up and appear as latency, not as errors.
- **Receiver slow** → latency rises, threads accumulate, and eventually timeouts and pool exhaustion appear as failures on *unrelated* steps. Bound the parallelism of the flow before tuning the timeout.
- **Receiver down** → an unreachable host usually fails fast (connection refused), a black-holed host fails slowly (timeout). Both must be treated as transient and retried away from the caller thread, ideally after decoupling through a JMS queue.
- **Retry at most at the boundary you can control.** If the sender already retries, an adapter-level retry multiplies the load. Cap attempts, add backoff, and finish with a dead-letter destination rather than an unbounded loop.

### 2.2 HTTP adapter vs OData adapters

| Aspect | HTTP adapter | OData adapters (V2 / V4) |
| :--- | :--- | :--- |
| Payload handling | Body passed through untouched; content type is whatever you set | Payload is interpreted as OData; entity and feed structures matter |
| CSRF | You fetch the token and forward the session cookie yourself (see [security-and-governance.md](security-and-governance.md)) | Token handling is part of the adapter when the operation is configured as CSRF-protected |
| Error mapping | Raw status code and body; the flow must interpret them | Service error responses are surfaced in the adapter's own error form and must still be mapped to your canonical error |
| `$batch` | You must construct the multipart batch body yourself | Batch requests are built and sent by the adapter |
| Streaming | Enabled | Not enabled for OData V2 or V4 |

### 2.3 OData V2 vs OData V4

| Topic | OData V2 | OData V4 |
| :--- | :--- | :--- |
| Batch payload | Multipart MIME with embedded HTTP requests | OData 4.0 batch format (JSON-based) |
| Optimistic locking | `ETag` exchanged through `If-Match` | Same `If-Match` mechanism; the ETag is also carried inline in the JSON representation |
| Server-driven paging | Next-link / skip-token delivered in the feed | `@odata.nextLink` in the JSON payload |
| Client-driven paging | `$top` / `$skip` where the service supports them | Same, but service support varies — check the service metadata |
| Delta / change tracking | Track-changes preference returning a delta token | `@odata.deltaLink` that must be persisted and replayed |
| Practical rule | Read the next-link rather than computing offsets; never assume `$skip` is supported | Treat V4 as a different protocol: do not reuse V2 batch or delta logic |

**Paging is a loop, not a parameter.** Whatever the version, a correct implementation follows the service-provided continuation until it disappears, persists the token if the flow is incremental, and has a termination guard so a misbehaving service cannot loop forever.

### 2.4 Dynamic endpoint control

| Header | Effect | Engineering note |
| :--- | :--- | :--- |
| `CamelHttpUri` | Overrides the configured URI | Replaces the whole target including host — the most dangerous of the dynamic options |
| `CamelHttpPath` | Sets the dynamic part of the path | Preferred: the host and base path stay externalised and validated |
| `CamelHttpQuery` | Sets the query string | Keep it built from validated values, not from raw user input |
| `CamelHttpMethod` | Sets the HTTP method | Useful for pattern-based flows; document which methods the flow accepts |
| `CamelHttpResponseCode` | Sets the response status code returned to the sender | The correct way to return a controlled code instead of letting an exception decide |
| `CamelHttpUrl` | Read-only, the resolved URL | Log it for supportability, never rebuild it from parts |

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def properties = message.getProperties()
    // Only the path is overridden here: host and base path stay externalized in the channel.
    String path = properties.get("p_targetPath")
    if (path) {
        message.setHeader("CamelHttpPath", path)
    }
    String query = properties.get("p_targetQuery")
    if (query) {
        message.setHeader("CamelHttpQuery", query)
    }
    return message
}
```

> ⚠️ **Never build a URL by concatenating a hardcoded host.** Hosts, ports and base paths must come from `{{externalized_parameters}}` so the artifact can be promoted from DEV to TEST to PROD without editing — and so that a single point of change exists when a receiver is migrated. Dynamic segments belong in `CamelHttpPath`, not in a string that re-creates the endpoint.

### 2.5 Error mapping: 4xx vs 5xx

| Receiver response | Meaning | Correct treatment |
| :--- | :--- | :--- |
| 400 / 422 | Payload is wrong for this receiver | Do not retry. Fail fast, record a custom status and park the message for analysis |
| 401 / 403 | Credentials or authorisation are wrong | Do not retry — repeated attempts can lock technical users. Alert; this is a configuration incident |
| 404 | Endpoint or object key does not exist | Do not retry. Usually a routing or master-data problem |
| 409 | Conflict, typically an optimistic-locking failure | Re-read the current state and retry once with a fresh ETag |
| 429 | Throttling | Back off, respect the receiver's retry hint, reduce flow parallelism |
| 500 / 502 / 503 / 504 | Transient receiver or infrastructure failure | Retry with backoff behind a persistent queue; end in a dead-letter destination |

**Returning a controlled code to the sender.** Decide deliberately what the caller sees: a validation failure is a client error, while an unreachable backend is a server-side or unavailability condition. Set `CamelHttpResponseCode` and return a stable error body containing a correlation identifier, and keep the receiver's raw error in the message processing log rather than in the response. Never let the sender receive the receiver's own status code unfiltered — it leaks internal topology and lets the caller draw wrong conclusions about who failed.

---

## 3. File-based connectivity (SFTP / FTP)

### 3.1 Polling model and lifecycle

The sender adapter polls on a schedule, selects files matching the configured pattern, and creates one message per file. Consequences to design for:

- **No global ordering.** Files are picked up in the order the adapter's selection produces; never build a flow that depends on file A being processed before file B unless the payload itself carries the sequence.
- **Configure the after-processing action explicitly.** Delete after read is efficient but destroys the only copy before the receiver has confirmed anything. Moving the file to an archive or processed directory keeps an audit trail and allows replay.
- **Polling intervals and file patterns are a contract with the producer.** A pattern that also matches the producer's temporary files will ingest half-written payloads.

**Change Directories Stepwise** allows the adapter to traverse directory trees and change directories during processing. It is convenient and it is exactly what disables streaming for the SFTP and FTP adapters. If a large file must be streamed, keep stepwise directory changes disabled and address directories through fully qualified paths or through separate channels per directory.

### 3.2 Target file names

On the receiver side, the `CamelFileName` header overrides the configured name and is the supported way to set the target file name dynamically. `CamelFileNameOnly` exposes the source file name and `CamelFileParent` the parent directory when the name must be derived from the input file. If neither a configured name nor the header is set, the Exchange ID is used as the file name — technically valid, operationally poor, because nobody can correlate the file back to a business object. Always set an explicit name or naming pattern.

```text
Good target name pattern:  ORDERS_${property.p_orderId}_${date:now:yyyyMMddHHmmss}.xml
Anti-pattern:              <random exchange id> with no business meaning
```

### 3.3 Large files, partial files and idempotency

- **Large files:** streaming is only available for SFTP/FTP while stepwise directory changes are disabled, and streamed processing relies on temporary storage in the tenant, which also bounds the maximum transferable size. Where a non-streaming step cannot be avoided, split the message into chunks rather than raising the limit.
- **Partial-file detection:** CPI has no universal "file is complete" signal. The producer must stage the file under a temporary name or extension and rename it on completion so that the poll pattern only sees finished files. Do not solve this with retries or delays.
- **Idempotency:** the same file can be delivered twice by a partner, replayed after a manual recovery, or re-read if archiving is misconfigured. Derive a stable deduplication key (file name plus size plus modification timestamp, or a business key inside the payload) and check it in a Data Store before writing to the receiver. The check must be committed *after* a successful receiver call, otherwise a failure loses the message.

### 3.4 Charset and line endings

Mismatched encoding does not fail loudly — it corrupts umlauts, currency signs and non-Latin characters, and it usually surfaces as a mapping error far from the cause. Set the encoding explicitly with the `CamelCharsetName` property when the partner's files are not in the platform default, and agree the encoding with the partner in writing. Line endings matter for the same reason: a CRLF/LF difference changes checksums, breaks fixed-length or line-count validations, and can make a perfectly valid file look non-compliant. Never rely on the platform default for either.

---

## 4. SAP backends: IDoc, SOAP (SAP RM), RFC and Cloud Connector

### 4.1 Synchronous vs asynchronous semantics

| Connectivity | Calling semantics | What CPI can guarantee | What it cannot |
| :--- | :--- | :--- | :--- |
| HTTP / OData (sync) | Caller waits for the response | That it received a response, or that it timed out | That the receiver did not process the request before timing out |
| SOAP with SAP RM | Reliable asynchronous exchange with SAP backends | Reliable delivery at the protocol level, EO-capable quality of service when both sides cooperate | Business-level idempotency of the receiver's function |
| IDoc over tRFC/qRFC | Asynchronous submission to ABAP | That the IDoc was handed over; qRFC preserves order within its queue | That the IDoc was posted successfully — errors appear later and must be monitored in SAP |
| XI | Message-protocol based, quality of service configurable | EO when a JMS queue is used as temporary storage | EO with Data Store temporary storage on Cloud Foundry (not supported) |
| JMS (internal) | Asynchronous, persisted | At-least-once delivery, redelivery, dead-letter handling | Exactly-once delivery — duplicates are possible and must be absorbed by the consumer |

### 4.2 Exactly-once, message IDs and the XI adapter

Exactly-once is a property of a *chain*, not of an adapter. It requires a persistent intermediate store, a stable message identifier and a receiver that can detect and discard a repeat. In CPI the pieces are: the XI adapter with a JMS queue as temporary storage for EO quality of service, a stable correlation identifier carried end to end (the framework's `SAP_ApplicationID` header is the natural carrier, and `addCustomHeaderProperty` makes the same identifier searchable in the MPL), and an idempotent receiver operation. If the receiver cannot reject a duplicate, no adapter setting will produce exactly-once behaviour — design deduplication on the receiver side or accept at-least-once and make the operation idempotent.

### 4.3 Cloud Connector

Cloud Connector is an outbound tunnel from the on-premise network to SAP BTP. It exposes only the resources that are explicitly configured as accessible systems and resources; nothing else becomes reachable, and it is not a general-purpose proxy to the internet, nor does it replace authorisation on the on-premise system.

| Symptom | Typical root cause | Fix |
| :--- | :--- | :--- |
| Connection refused although the tunnel is connected | Resource not in the allowlist, or wrong port | Expose the exact host, port and path prefix that the flow calls |
| HTTP 404 or "wrong host" from the backend | Flow uses the internal host name while the connector expects the virtual host | Use the configured virtual host consistently in the channel's externalised parameter |
| TLS handshake failure | Missing root or intermediate certificate on one side | Import the correct chain into the tenant keystore and trust the issuing CA in the connector |
| All requests arrive as the technical user | Principal propagation not configured end to end | Configure propagation on the channel, in the connector and on the backend; verify with a test call |
| Intermittent connectivity under load | Tunnel or backend concurrency limits, not CPI | Throttle flow parallelism; do not compensate with retries |

---

## 5. Event and messaging connectivity

| Option | Where it belongs | Strengths | Limits |
| :--- | :--- | :--- | :--- |
| JMS (tenant-internal) | Decoupling steps and flows inside one tenant; peak buffering; retry with persistence | Durable, at-least-once, redelivery and dead-letter handling, streaming enabled | Inside the tenant only; consumes the shared messaging instance; bounded queue capacity |
| AMQP / SAP Event Mesh / Advanced Event Mesh | Cross-application events, notifications, event-driven integration on BTP | Standard broker protocol, publish/subscribe, decoupling across applications and tenants | No streaming; event contracts and topic naming need governance |
| Kafka | Enterprise event backbone, high throughput, replay, ordered streams | Durable log, consumer offsets, replay, partition-level ordering, horizontal scale | No streaming; ordering only within a partition; requires its own operational ownership |

**Choosing between them.** Use JMS when the goal is to make a *single* integration robust — buffering a burst, retrying a flaky receiver, isolating a slow step. Use Event Mesh / Advanced Event Mesh when *multiple* applications must react to the same business event. Use Kafka when the event stream itself is a product: it must be replayable, ordered per key, and consumed independently by several systems with their own offsets.

**Kafka ordering.** Ordering is guaranteed within a partition, never across partitions. Messages that must be processed in sequence have to share a message key, so the partitioning strategy is an architectural decision, not a tuning detail. Changing the partition count can remap keys and therefore break ordering for in-flight sequences — plan partition counts up front.

> ⚠️ **The JMS messaging instance is shared by the JMS, AS2, AS4 and XI adapters.** B2B traffic and internal decoupling compete for the same queues and transactions: a retry storm in a JMS flow can starve AS2/AS4 partner traffic and XI EO processing. Monitor capacity, keep partner and internal queues separate, and treat JMS resource consumption as a tenant-wide budget — default limits are moderate and can only be raised within the platform's self-service maximum.

---

## 6. Security at the channel level

| Mechanism | Appropriate when | Avoid when |
| :--- | :--- | :--- |
| Basic authentication | Legacy internal systems that support nothing else | Anything crossing the internet; partner-facing endpoints |
| OAuth2 client credentials | System-to-system API calls to SAP BTP, S/4HANA and SaaS APIs | Scenarios requiring the end user's identity or authorisation scope |
| OAuth2 SAML bearer assertion | Propagating a user context to a system that trusts an assertion from the tenant | Background or scheduled flows — there is no interactive user |
| mTLS (client certificate from the keystore) | Partner APIs, B2B endpoints and APIs that mandate certificate-based client auth | As a substitute for authorisation — the certificate proves identity, not entitlement |
| Principal propagation | End-to-end user identity to an on-premise ABAP system through Cloud Connector | Bulk or scheduled interfaces that legitimately run as a technical user |
| API key / token in a header | SaaS endpoints that offer nothing better | As the primary control for a high-value interface; keys do not identify a user |

> ⚠️ **Never read a credential artifact from Groovy and copy it into an HTTP header.** Credentials placed in headers or exchange properties are exposed in clear text by tracing, and they end up in the message processing log. Configure authentication in the channel with a security artifact (user credentials, OAuth2 credentials, keystore) — see [security-and-governance.md](security-and-governance.md).

**TLS.** Outbound trust is anchored in the tenant keystore: a receiver presenting a certificate chain the tenant does not trust fails the handshake, and the fix is importing the correct root or intermediate certificate — never disabling hostname verification. Hostname verification must stay enabled; turning it off converts a certificate error into an undetected man-in-the-middle exposure. Treat certificate expiry on both sides as an operational item with an owner and a reminder, and validate partner certificates on inbound channels as strictly as you expect them to validate yours.

---

## 7. Timeouts, retries and resilience at the adapter boundary

| Failure mode | Where to handle it | Trade-off |
| :--- | :--- | :--- |
| Receiver returns 5xx or times out once | Adapter/JMS redelivery with backoff | Fast recovery, but a duplicate is possible if the first attempt was actually processed |
| Receiver is throttling (429) | Backoff honouring the receiver's hint, plus reduced parallelism | Slower throughput is the point; tight retries deepen the outage |
| Receiver is down for hours | JMS queue in front of the receiver, dead-letter destination after the attempt cap | Survives the outage, but queue capacity is finite and the backlog delays fresh messages |
| Payload rejected as invalid (4xx) | No retry — dead-letter plus alert | Needs a human; automatic retry only wastes resources |
| Receiver is merely slow | Decouple with an asynchronous channel and answer the caller immediately | Caller no longer gets the business result synchronously |
| Sender retries the same message | Idempotency on the receiver, or a deduplication key in a Data Store | Requires a stable business key across systems |
| Flow defect discovered after the receiver wrote data | Compensating action in a dedicated flow | A retry cannot undo a completed write |
| An exception subprocess must still return a controlled response | Flow-level handling with `CamelHttpResponseCode` and a custom status | Must be implemented per flow; see [error-handling-and-monitoring.md](error-handling-and-monitoring.md) |

**Idempotent vs non-idempotent receiver calls.** Retry freely when the call is idempotent — a query, a read, an upsert keyed by business object, or a full replacement of a known record. For a non-idempotent call (creating a document, posting a movement, invoking a function that increments state) a retry after a timeout may create a duplicate, because a timeout tells you nothing about whether the receiver processed the request. Retry a non-idempotent call only when you can prove the request never reached the receiver, otherwise first check the receiver's state or send an explicit idempotency key that the receiver honours. This distinction is the single most common cause of duplicated business documents in CPI landscapes.

---

## 8. Anti-patterns

| Symptom | Root cause | Fix |
| :--- | :--- | :--- |
| Flow works in DEV, fails immediately after transport | Hardcoded host, port or credential alias | Externalise every environment-specific value as `{{parameter}}` |
| Out-of-memory errors and tenant-wide instability on a large flat file | OData or SuccessFactors adapter used for a huge payload although those adapters do not stream | Move file ingestion to SFTP/HTTP with streaming, or chunk the payload before the non-streaming step |
| The same file is processed repeatedly, producing duplicate documents | SFTP polling without archiving, delete or deduplication | Configure an explicit after-processing action and a deduplication key in a Data Store |
| Backend slows down and then fails completely | Retry storm from unbounded immediate retries | Exponential backoff, attempt cap, dead-letter destination, respect throttling hints |
| Sender times out although the flow eventually succeeds | Synchronous chain longer than the caller's patience | Insert an asynchronous hop, answer the caller early, report the outcome separately |
| A "monolith split into flows" that cannot be deployed or tested independently | ProcessDirect used to chain steps rather than to expose a reusable component | Define real component boundaries with stable interfaces; keep flows independently deployable |
| AS2/AS4 partner traffic stalls while an internal flow is retrying | All of them share the same JMS messaging instance, whose capacity is finite | Separate partner and internal queues, monitor capacity, scale JMS resources deliberately |
| Credentials appear in clear text in the message processing log | A script read a credential artifact and wrote it into a header or property | Configure authentication on the channel; never let credentials reach headers, properties or logs |
| Streaming silently disabled on a multi-gigabyte transfer | Change Directories Stepwise enabled on the SFTP/FTP channel | Disable stepwise directory changes and address directories through explicit paths |
| Target files are named with random identifiers nobody can trace | Neither a configured file name nor `CamelFileName` was set, so the Exchange ID was used | Always set an explicit target name or naming pattern |
| TLS errors "solved" by disabling hostname verification | Missing or wrong certificate chain in the tenant keystore | Import the correct root/intermediate certificate and keep verification enabled |
| Unnecessary load on an SAP backend | Polling for changes that the backend could publish as events | Publish or subscribe to events instead of polling; keep polling only where no event exists |

---

## Further Reading

- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [Headers and Exchange Properties Provided by the Integration Framework](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/headers-and-exchange-properties-provided-by-the-integration-framework)
- [JMS Resource Limits and Optimizing their Usage](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/jms-resource-limits-and-optimizing-their-usage)
- [Apply the Retry Pattern with JMS Queue](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apply-the-retry-pattern-with-jms-queue)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
