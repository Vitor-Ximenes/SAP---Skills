# SAP Cloud Integration — Performance & Sizing Guide

Cloud Integration executes every message on **shared tenant resources**: JVM heap on the worker nodes,
the tenant file system used for streaming temporary files, the runtime database and the JMS messaging
instance. None of them is per-flow, so one badly sized interface degrades every other interface on the
tenant. This guide explains how those resources are consumed, how to design flows that stay inside them,
and how to size an interface before it is built. Companion guides:
[references/groovy-best-practices.md](groovy-best-practices.md),
[references/design-guidelines.md](design-guidelines.md),
[references/persistence-and-decoupling.md](persistence-and-decoupling.md).

---

## 1. The three resource dimensions

| Dimension | What it covers | Exhaustion symptom | Primary lever |
| :--- | :--- | :--- | :--- |
| **Compute** | Worker threads and JVM heap on the nodes running the flows | `java.lang.OutOfMemoryError`, worker restarts, flows slow but not failing | Streaming instead of `String` materialisation; bounded parallelism |
| **Tenant storage** | Disk for streaming temporary files, runtime database, monitoring storage | Writes fail, monitoring storage alerts, large transfers abort | Chunking, retention, cleanup of Data Stores and queues |
| **Messaging** | The JMS messaging instance, shared by the JMS, AS2, AS4 and XI adapters | Senders blocked, retries pending, queue depth growing, dead-letter growth | Queue capacity planning, consumer parallelism, monitoring |

A CPI performance problem is almost always one of four things: an **oversized message in heap** (payload
materialised as a `String` or a full DOM tree); a **non-streaming step in the middle of a large-payload
flow** (one Message Mapping, XML Schema Validation or CSV conversion forces the whole chain into memory);
**unbounded parallelism** (a splitter, multicast or consumer count that scales with message volume
instead of with receiver capacity); or a **chatty synchronous call pattern** (hundreds of small blocking
calls per message, each holding a worker thread).

### Cloud Foundry system scope

| Resource | Default scope | What happens when it is exhausted |
| :--- | :--- | :--- |
| Integration content | 2 GB | Deployment of new or changed artifacts fails until content is removed |
| JMS queues | 9 GB / 150 transactions default (30 queues), scalable to 30 GB / 500 transactions (100 queues) | Producing flows fail or block; retries cannot be scheduled; messages accumulate until the queue is drained |
| Message processing log persistence | 35 GB | Monitoring history is truncated by retention; MPL writes and searches degrade |
| Runtime database | 35 GB | Data Store writes, aggregator state and variable persistence degrade or fail |
| Disk space | 10 GB | Streaming temporary files cannot be created, so large-message flows fail with I/O errors |

> ⚠️ **Rate limits are not load protection.** The documented rate limits (for example 1000 requests/second
> per tenant on MPL public APIs) protect the platform against unexpectedly high *request rates*. They do
> **not** protect against memory-, CPU-, storage- or parallelism-driven load. A flow can destroy a tenant
> while staying far below every rate limit.

For what to do once a flow has failed, see
[references/error-handling-and-monitoring.md](error-handling-and-monitoring.md).

---

## 2. Streaming design

**Core rule: never materialise a large payload as a `String`.** Work with `InputStream` or `Reader` so the
framework moves bytes through a temporary file. A `String` costs heap proportional to the payload, per
concurrent message, and that heap is shared by every flow on the node.

### Streaming capability matrix

Streaming works **only** if every step in the chain supports it. The lists below are the complete
documented matrix — anything not listed as streaming-capable must be treated as non-streaming.

**Adapters with streaming support**

| Adapter | Condition |
| :--- | :--- |
| FTP, SFTP | Only if **Change Directories Stepwise** is disabled |
| HTTP, JMS | Always |
| SOAP (SOAP 1.x) | General case; **not** when WS-Security is enabled |
| XI | Best Effort; Exactly Once with **JMS Queue** temporary storage. Exactly Once with **Data Store** temporary storage is not supported on Cloud Foundry (supported on Neo) |

**Adapters without streaming support:** `AMQP`, `Ariba`, `AS2`, `AS4`, `ELSTER`, `Facebook`, `IDoc`,
`JDBC`, `LDAP`, `Mail`, `OData V2`, `OData V4`, `ODC`, `RFC`, `SuccessFactors OData V4`,
`SuccessFactors REST`, `SuccessFactors SOAP`, `Twitter`.

| Group | Streaming supported | Streaming not supported |
| :--- | :--- | :--- |
| Routing | Gather (Aggregation Algorithm = *Combine*), General Splitter, Iterating Splitter, ZIP/TAR Splitter | Aggregator, Gather (*Combine at XPath*), Router, PKCS#7/CMS Splitter, IDoc Splitter, EDI Splitter |
| Transformers | JSON to XML Converter, XML to JSON Converter (configurable), Base64 Encoder, Base64 Decoder, GZIP Encoder, ZIP Encoder | CSV to XML, XML to CSV, EDI to XML, XML to EDI, Filter, Message Digest, Message Mapping, GZIP Decoder, ZIP Decoder, MIME Multipart Encoder, MIME Multipart Decoder |
| Security | PKCS#7 Decryptor/Encryptor/Signer/Signature Verifier, Simple Signer, PGP Decryptor, PGP Encryptor | XML Digital Signer, XML Signature Verifier |
| External call | Content Enricher (*Enrich*) | Content Enricher (*Combine*) |
| Validation | — | XML Schema Validation |
| Persistence | *Persist* and *Data Store Write* on Neo only | *Persist* and *Data Store Write* on Cloud Foundry, Data Store Get, Data Store Select, Write Variables |

> ⚠️ **One non-streaming step breaks the whole chain.** If a 300 MB message passes through a Message
> Mapping or an XML Schema Validation step, the runtime must hold the payload (or its object graph) in
> memory regardless of how streaming-capable the adapters around it are. There is no partial credit.
> Where such a step cannot be avoided, **split the message into chunks** so each exchange stays small.

### The temporary-file mechanism and its limit

Streaming is implemented with a **temporary file in the tenant file system**, which has two consequences:

- The **temporary storage size limits the maximum file size that can be transferred**. Check current
  consumption in the monitor page **Inspect Temporary Storage** before designing a new payload class.
- Temporary files from *concurrent* messages add up: disk space is a tenant-wide budget (10 GB in the
  default Cloud Foundry scope) shared with everything else the tenant stores.

### How large is too large: sizing decision table

| Payload class | Recommended architecture |
| :--- | :--- |
| **< 5 MB** | In-memory processing is acceptable: Message Mapping, XML Schema Validation, `XmlSlurper`/`XmlParser`, `getBody(String)` for genuinely small documents. Still avoid storing the payload in headers or variables. |
| **5–40 MB** | Streaming mandatory: stream-capable sender and receiver adapters, Iterating or General Splitter with the streaming option enabled, no Message Mapping, no XML Schema Validation, no CSV conversion. Replace mappings with a streaming script. |
| **40–200 MB** | Streaming plus chunking: read the document as a stream, split it into records with a streaming splitter, and never re-aggregate the full document into one exchange. Budget temporary storage for peak concurrency, not for one message. |
| **> 200 MB** | Chunking is the architecture, not an optimisation: process the payload as independently deliverable records and write each chunk to the receiver. Confirm the receiver's own file or payload limit before promising the interface. |

### Worked example: a 300 MB XML feed to SFTP

Input: a 300 MB XML feed on an SFTP server; output: transformed records on a second SFTP server.

```text
SFTP sender (Change Directories Stepwise = disabled, polling schedule)
   |
   +-- Iterating Splitter (streaming enabled, XPath to the record element)
   |      |
   |      +-- Groovy script: StAX read of the record (constant memory)
   |      |
   |      +-- SFTP receiver (Change Directories Stepwise = disabled,
   |                        CamelFileName set per split message)
   |
   +-- no Gather, no Aggregator at the end of the flow
```

Rules that make this work:

1. **Sender**: SFTP/FTP with *Change Directories Stepwise* disabled — otherwise the sender itself is non-streaming.
2. **Splitter**: streaming option enabled, so records are produced from a stream and not from a parsed DOM.
3. **Receiver**: a streaming-capable adapter — here SFTP with *Change Directories Stepwise* disabled, with `CamelFileName` setting the target file name (if neither a file name nor the header is set, the Exchange ID is used).
4. **No Message Mapping, no XML Schema Validation, no CSV conversion** — transform in a StAX script.
5. **No Gather or Aggregator** that rebuilds the 300 MB document — aggregation belongs in the receiver.
6. **Concurrency is the sizing variable**: two concurrent 300 MB streams consume temporary storage that a single-file test never shows. Buffer at most one record, never the document.

> 💡 **Test with concurrency, not with a single file.** A flow that handles one 300 MB file is not proven;
> a flow that handles the documented peak number of *simultaneous* 300 MB files is.

---

## 3. Splitting, parallelism and throttling

| Splitter | Use when | Streaming option |
| :--- | :--- | :--- |
| **Iterating Splitter** | Envelope tags are not needed in the split messages (typical line-item processing) | Supported — enable it for large payloads |
| **General Splitter** | Split messages must keep the original root/envelope | Supported |
| **ZIP/TAR Splitter** | Archive members are the units of work | Supported |
| **IDoc / EDI / PKCS#7-CMS Splitter** | Protocol- or format-specific splitting | Not supported — treat the input as non-streaming |

Splitter headers and properties:

| Name | Meaning |
| :--- | :--- |
| `CamelSplitIndex` | Counter of the current split part, starting at 0 |
| `CamelSplitSize` | Total number of parts; for **stream-based** splitting provided only on the last item |
| `CamelSplitComplete` | Marks the last part of the split |

> ⚠️ Do not compute progress as `CamelSplitIndex / CamelSplitSize` in a streaming split: `CamelSplitSize`
> is not available on the early parts, so the expression divides by null or zero.

### Parallel or sequential

- **Sequential** processing runs parts one after another in the same thread: predictable, low resource
  footprint, correct when the receiver is the bottleneck.
- **Parallel** processing runs parts on multiple threads: faster only if the receiver can absorb the
  concurrency, and it multiplies heap, temporary storage and outbound connections at the same time.
- Cap the number of concurrent processes explicitly, deriving the cap from the **receiver's** capacity
  (work processes, rate limits, connection pools) and never from the message size.

**Unbounded parallelism is a self-inflicted denial of service.** A parallel splitter in front of a
receiver that allows N concurrent sessions produces failures for *all* callers, including the other flows
on the tenant that share the same receiver and worker nodes. Prefer lower concurrency with idempotent
retry over high concurrency with errors.

### Backpressure: what CPI does and does not do

| CPI does | CPI does not |
| :--- | :--- |
| Reject messages above a configured sender body size | Slow down a producer when a flow is behind |
| Enforce tenant rate limits on public APIs | Queue or throttle splitter parts automatically |
| Roll back a JMS transaction so the broker can redeliver | Reduce parallelism when heap or disk runs low |
| Report storage and queue consumption in monitoring | Guarantee that a burst is absorbed by the receiver |

Design backpressure explicitly: bound splitter concurrency, decouple ingestion through a JMS queue
([references/persistence-and-decoupling.md](persistence-and-decoupling.md)), and reject over-sized input
at the edge instead of failing deep inside the flow.

### Guarding inbound size

HTTP-based sender adapters expose a **Body Size** parameter (tab *Conditions*). Messages above the limit
are rejected with `Message body exceeds configured size limit (…)` and subsequent steps are not executed:

- Set the limit from the interface's payload contract, not from the largest file ever seen in a test.
- Rejecting at the sender protects the whole downstream chain from a heap problem.
- The caller sees the message above; document it in the interface's error contract so consumers can react
  (typically: split the request at the source).

---

## 4. Groovy-level performance

Every object created in a script lives until the exchange is released, and all exchanges share the node's
heap. Therefore: never build a large intermediate collection (a `List` of records, a `Map` of the whole
document) unless the payload class is small by contract; never accumulate the whole payload in a
`StringBuilder` — use it only for bounded values such as business keys, error summaries or small
generated fragments; and prefer a single pass over the stream to "parse, then transform, then serialise".

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

class XmlSupport {
    // Reused immutable factory configuration: treat it as read-only, never mutate it at runtime.
    static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance()
}

def Message processData(Message message) {
    Reader reader = message.getBody(java.io.Reader)   // stream: never getBody(String) for large payloads
    StringBuilder keys = new StringBuilder()          // bounded: business keys only
    XMLStreamReader xml = null
    try {
        xml = XmlSupport.INPUT_FACTORY.createXMLStreamReader(reader)
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && xml.getLocalName() == 'OrderId') {
                if (keys.length() > 0) { keys.append(',') }
                keys.append(xml.getElementText())
            }
        }
    } finally {
        if (xml != null) { xml.close() }
    }
    message.setProperty("p_orderKeys", keys.toString())
    return message
}
```

- Use `StringBuilder` (faster, not thread-safe) or `StringBuffer` (thread-safe, synchronized) instead of
  repeated `+` concatenation inside loops, and emit compact rather than pretty-printed output.
- Do not use `XmlSlurper.parseText(String)`; parse from the `Reader`. Use `XmlSlurper` for read-only
  parsing and `XmlParser` for in-place manipulation, and move to StAX as soon as payloads stop being small.
- Reuse only **immutable** static factories — never a static mutable cache: script state is shared between
  executions and between threads.
- Never use binding variables (`body = '123'`): they live until undeploy/redeploy, are shared between
  executions, are not thread-safe and can cause `OutOfMemoryError`. Use typed locals or `def`.
- `Eval()` is forbidden — generated classes are never unloaded (SAP Note 3246624).
- `TimeZone.setDefault` is forbidden — it changes the JVM default time zone for everything on the node
  (SAP Note 3289679).
- Use SLF4J for logging and make logging switchable by log level or externalized parameter.

### The cost of MPL logging

Message Processing Log entries are **persisted monitoring data** (35 GB MPL persistence scope), not free
diagnostics:

- `addAttachmentAsString(name, text, mediaType)` stores the full text; calling it unconditionally on a
  high-volume flow converts payload volume into monitoring storage volume.
- Properties added from a Script step are visible in the MPL only at log level **Debug** or **Trace**, so
  raising the level multiplies MPL volume for every message of every flow at that level.
- `messageLogFactory.getMessageLog(message)` can return `null` (for example in local simulation) — always
  null-check before use.
- Use `addCustomHeaderProperty` for short searchable business keys, and keep
  `SAP_MessageProcessingLogCustomStatus` within its 40-alphanumeric-character limit.

### Static analysis as a performance gate

Run **CodeNarc 3.4.0** over script collections in CI: it catches string concatenation in loops, unused
variables and missing null checks mechanically. Treat a CodeNarc regression like a failing test.

---

## 5. Receiver-side performance

The most common "CPI is slow" cause is a call pattern, not a resource limit:

| Anti-pattern | Why it hurts | Fix |
| :--- | :--- | :--- |
| N+1 calls: one lookup per line item | Every call costs a connection, a worker thread slot and latency; the receiver sees a flood | Batch into one call per chunk, or use the receiver's bulk/batch API |
| One OData call per record | As above, plus OData overhead per request | Use the receiver's batch capability or a bulk service |
| Read-modify-write per record | Doubles the call count and creates backend contention | Send the complete record in one call |

### Pagination

"Give me everything" is an anti-pattern on both sides: the receiver spends memory and work processes
building one huge response, and CPI must hold that response — a non-streaming adapter such as OData will
materialise it. Page through large result sets with the receiver's documented paging parameters, process
each page, and persist or forward it before requesting the next one. Cap the pages per run so a runaway
query cannot exhaust the flow.

### Timeouts and thread occupancy

A synchronous call occupies a worker thread for its whole duration, so `peak concurrency × worst-case
receiver latency` is the thread budget the tenant must fund — not the average. Configure explicit
timeouts on every outbound call so a hung receiver fails fast enough for the flow's error handling to
act, and prefer asynchronous decoupling over long synchronous chains
([references/persistence-and-decoupling.md](persistence-and-decoupling.md)).

### Caching lookup data

| Cache it | Do not cache it |
| :--- | :--- |
| Small, genuinely static reference data (country codes, unit codes) that changes on a release cycle | Prices, stock, credit limits or master data a business user can change |
| Data that is identical for every message of a run | Anything whose staleness would be a business or compliance defect |

Cache in the receiver or in a governed store, never in a static script variable: static mutable state is
shared across threads and executions — it is a defect, not a cache.

---

## 6. Monitoring and tuning the tenant

| Metric | Question it answers | Action when it grows |
| :--- | :--- | :--- |
| MPL count per flow | Which flows do the work and where failures concentrate | Use **Inspect Top Integration Flows** to find and optimise the heaviest flows |
| Temporary storage usage | Are streaming temporary files accumulating | **Inspect Temporary Storage**; reduce peak concurrency or payload class |
| MPL persistence growth | Is monitoring data the storage problem | Lower log levels, remove unconditional attachments, shorten retention |
| JMS queue depth | Are producers faster than consumers | Add consumers or throughput within the JMS resource limits |
| Runtime database usage | Are Data Stores and aggregator state growing | **Inspect Monitoring Storage Usage**; clean Data Stores, expire entries |
| Disk space | Is the tenant close to the file-system limit | Clean up artifacts and temporary files; revisit concurrency |

Tuning checklist:

1. **Log levels** — Debug/Trace only while troubleshooting, never permanently.
2. **Attachments** — off by default, switched by an externalized parameter.
3. **Retention** — define and enforce MPL and Data Store retention that matches the business need.
4. **Queue sizes** — size for the burst you must absorb, not for the burst you hope never happens.
5. **Parallelism** — cap splitter and consumer concurrency from receiver capacity.
6. **Undeploy unused artifacts** — idle flows still consume content, queue, Data Store and schedule quotas.
7. **Re-test after tuning** — one flow's concurrency changes the budget available to all other flows.

---

## 7. Performance anti-patterns

| Symptom | Root cause | Fix | How to detect it |
| :--- | :--- | :--- | :--- |
| `OutOfMemoryError`, worker restarts under load | Payload materialised as `String` or DOM | Stream with `InputStream`/`Reader` or StAX | Failures cluster on one flow; heap alerts correlate with large-payload runs |
| Flow succeeds with one file, fails with several | Concurrent streaming temporary files exceed the disk budget | Serialise, lower concurrency, or chunk | Temporary storage usage grows in steps with concurrency |
| Large flow silently slow, CPU low | A non-streaming step (Message Mapping, XML Schema Validation, CSV conversion) forces buffering | Remove or replace the step; split into chunks | Temporary storage high while the flow spends time in Java, not in the network |
| `Message body exceeds configured size limit` on valid business messages | Body Size set below the real payload contract | Align the limit with the contract; reject and document at the edge | HTTP sender responses on that endpoint |
| Queue depth grows monotonically | Consumers slower than producers | Increase consumer throughput or capacity within JMS limits | JMS queue depth monitor |
| Receiver errors (429/503) exactly at peak | Unbounded parallel splitter | Cap concurrent processes; add bounded retry | Receiver error rate correlates with splitter parallelism |
| Runtime database grows without business growth | Uncleaned Data Store entries and aggregator state | Retention plus scheduled cleanup | **Inspect Monitoring Storage Usage** |
| Monitoring storage grows faster than message volume | Unconditional MPL attachments, permanent Trace level | Switch logging off by default; lower levels | MPL persistence consumption versus MPL count |
| End-to-end latency high, throughput low, receivers idle | Chatty N+1 synchronous calls | Batch per chunk; use bulk APIs | Outbound call count per MPL |
| Worker threads exhausted, flows queue up | No explicit timeouts; a slow receiver holds threads | Set timeouts; decouple asynchronously | Thread occupancy ≈ concurrency × latency |
| The same load is slower every month | Tenant shared with undeployed artifacts, uncleaned stores and queues | Undeploy unused artifacts, clean queues and Data Stores | Quota reports and storage monitors |
| Scheduled flow overlaps its own previous run | Runtime longer than the schedule interval | Lengthen the interval or decouple through a queue | Overlapping MPL entries for the same artifact |

---

## 8. Sizing worksheet

Collect these numbers **before** designing; leave a cell empty rather than guessing.

| Question | How to collect it | Design decision it drives |
| :--- | :--- | :--- |
| Typical and peak message size | Payload samples plus sender logs or file statistics | Streaming versus in-memory processing; whether chunking is mandatory (see §2) |
| Peak messages per second and per day | Sender-side counters over the real peak window, not the daily average | Splitter concurrency, queue depth, consumer count |
| Peak concurrent executions | Sender contract (how many callers may be in flight) plus schedule overlap | Temporary storage and disk budget; cap on parallel processing |
| Largest payload you must *retain* | Business and audit requirements | Data Store versus JMS versus external storage; retention period |
| Whether a payload must survive a tenant restart | Business requirement, not convenience | Persistent store (Data Store or JMS) versus exchange properties |
| Synchronous or asynchronous contract | Whether the caller can wait, and for how long | Sync call versus queue decoupling; timeout budget |
| Receiver capacity | Receiver's documented concurrency, rate limits and batch APIs | Parallelism cap; batch size; retry strategy |
| Receiver worst-case latency | Receiver metrics or a load test | Thread occupancy budget; whether to decouple |
| Tolerance for duplicate processing | Business rule for the interface | Idempotency design (business key as entry ID) |
| Monitoring and audit needs | Support and compliance requirements | Log levels, custom header properties, attachment policy |

Sizing rules of thumb:

- Size temporary storage for `peak concurrency × peak payload size`, with headroom.
- Size queues for the burst that must be absorbed while the receiver is unavailable, not for the average.
- Size parallelism from receiver capacity; shrink it whenever the receiver, not the tenant, is the bottleneck.
- Re-run the worksheet whenever payload class, concurrency, receiver or retention requirement changes.

---

## Further Reading

- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [Limit Size of Incoming Messages](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/limit-size-of-incoming-messages)
- [System Scope in the Cloud Foundry Environment](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/system-scope-in-the-cloud-foundry-environment)
- [General Scripting Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/general-scripting-guidelines)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
