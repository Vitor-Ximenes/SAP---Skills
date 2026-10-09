# SAP Cloud Integration — Groovy Recipe Book

A cookbook of idiomatic, copy-pasteable Groovy solutions to the problems that recur in every SAP Cloud
Integration project: safe payload access, exception enrichment, MPL observability, duplicate detection,
dynamic file names, encoding, chunking, message composition, value mapping, retry sanity and
configuration hygiene. Every recipe is a complete `processData` script that compiles as-is and that
states the exchange properties the surrounding iFlow must provide.

---

## 1. How to use this book

Read the recipe, copy the whole fenced block into a **Script** step, then wire the properties the recipe
lists in its header comment through a **Content Modifier** placed *before* the script. Nothing in the
scripts reads the tenant configuration directly — see [R14](#r14-read-externalized-configuration-inside-a-script).

The rules the recipes follow (memory discipline, headers vs. properties, static state) are stated once in
[groovy-best-practices.md](groovy-best-practices.md); the failure-handling context for R2 is in
[error-handling-and-monitoring.md](error-handling-and-monitoring.md). Downloadable, ready-to-upload
versions of most recipes live in [../examples/](../examples/README.md).

| Convention | Meaning |
| :--- | :--- |
| `def Message processData(Message message)` | The only CPI script entry point. Never rename it, never add parameters. |
| `p_*` exchange properties | Internal flow state and the script's input/output contract. Not sent over the network. |
| `Camel*` / `SAP_*` names | Framework-provided headers and properties (see the table below). |
| Helper methods below `processData` | Allowed and recommended; keep them `def`-typed and side-effect free. |
| No `static` mutable state, no `@Field`, no binding variables | Scripts run concurrently on shared worker threads; per-tenant state must not live in the script. |
| Sizes and limits | Any numeric threshold in a recipe is a **local policy default**, not a documented SAP limit. Externalize it. |

**Framework names used by these recipes** (all documented in
[Headers and Exchange Properties Provided by the Integration Framework](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/headers-and-exchange-properties-provided-by-the-integration-framework)):

| Name | Position | Used for |
| :--- | :--- | :--- |
| `CamelExceptionCaught` | exchange property | The exception object inside an Exception Subprocess. |
| `SAP_MessageProcessingLogCustomStatus` | exchange property | Custom MPL status, max 40 alphanumeric characters. |
| `CamelFileName` | header | Dynamic target file name for the SFTP/FTP receiver. |
| `CamelCharsetName` | property | Overrides the character encoding used for the message. |
| `CamelSplitIndex` / `CamelSplitSize` | property | Position of a split message (0-based) / total count. |
| `CamelHttpResponseCode` | header | HTTP status the receiver adapter returned, or the code to return. |
| `SAP_Sender` / `SAP_Receiver` | header | Sender and receiver identifiers of the flow. |

> ⚠️ **Before writing a script, check whether a standard step does the job.** A script is your own
> maintenance liability; SAP only guarantees the officially supported script APIs.

| Task | Use this instead of a script |
| :--- | :--- |
| Copy/rename a field, set headers, concatenate text | Content Modifier |
| Split a large payload by element or line break | General Splitter / Iterating Splitter (Streaming enabled) |
| Recombine split messages | Gather (Combine) |
| XML ⇄ JSON, Base64, GZIP, ZIP | Message transformers |
| Conditional routing on payload or header values | Router with an explicit default branch |
| Reject messages above a size limit at the door | HTTP sender adapter *Body Size* parameter (tab *Conditions*) |
| Store/read an entry by key | Data Store steps (Write / Select) |

**Recipes that also exist as ready-to-upload files** in [../examples/](../examples/README.md):

| Recipe | File | Extra content compared to the recipe |
| :--- | :--- | :--- |
| R2 | [groovy-exception-details-capture.groovy](../examples/groovy-exception-details-capture.groovy) | Secret redaction, retryable/not-retryable decision, context attachment |
| R3, R4 | [groovy-mpl-logging-custom-status.groovy](../examples/groovy-mpl-logging-custom-status.groovy) | Searchable status and keys with a payload-logging switch |
| R5, R13 | [groovy-datastore-idempotency.groovy](../examples/groovy-datastore-idempotency.groovy) | Key sources (parts, business key, named header) and the full Data Store wiring |
| R6 | [groovy-dynamic-sftp-filename.groovy](../examples/groovy-dynamic-sftp-filename.groovy) | Reserved name handling, split-index suffix, stability flag |
| R7 | [groovy-payload-size-guard.groovy](../examples/groovy-payload-size-guard.groovy) | Soft warning threshold, `Content-Length` hint comparison, size report attachment |
| R10 | [groovy-csv-stream-reader.groovy](../examples/groovy-csv-stream-reader.groovy) | Column-count validation, malformed-line accounting per chunk |
| R11 | [groovy-odata-batch-payload-builder.groovy](../examples/groovy-odata-batch-payload-builder.groovy) | OData change set handling, GET vs. write parts, payload-to-descriptor fallback |
| R12 | [groovy-value-mapping-lookup.groovy](../examples/groovy-value-mapping-lookup.groovy) | Batch mode over a list of codes, unmapped-code evidence |
| R9 | [groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy) | The large-payload counterpart of R9 (streaming, not `XmlSlurper`) |

---

## 2. Payload access and observability

### R1. Read a payload safely without materialising it as a String

**When to use it:** In every step that has to *inspect* a payload (log a preview, count bytes, decide a
branch) before a later step transforms it. **Watch out for:** the body is normally a **consume-once**
stream — after you read it, downstream steps see an exhausted payload unless you hand something back.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets

def Message processData(Message message) {
    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        message.setProperty("p_bodyEmpty", "true")
        return message
    }

    // Peek at most 512 bytes. This buffer is the ONLY heap copy of the payload in this step.
    int peekLimit = 512
    byte[] head = new byte[peekLimit]
    int filled = 0
    while (filled < peekLimit) {
        int read = body.read(head, filled, peekLimit - filled)
        if (read <= 0) {
            break
        }
        filled += read
    }

    if (filled > 0) {
        // Safe because it is bounded by peekLimit. A multi-byte character may be cut -> use it for
        // decisions and log lines only, never for parsing or for the outbound payload.
        String preview = new String(head, 0, filled, StandardCharsets.UTF_8)
        message.setProperty("p_headPreview", preview.replaceAll("\\s+", " ").trim())
    }

    // Hand the FULL payload downstream: buffered head + untouched remainder of the original stream.
    // SequenceInputStream is plain JDK, so the body never becomes a String in heap.
    message.setBody(new SequenceInputStream(new ByteArrayInputStream(head, 0, filled), body))
    return message
}
```

**Why it is written this way**
- `message.getBody(String)` on a 30 MB body creates a ~60 MB UTF-16 String plus the original stream:
  that is the classic path to `OutOfMemoryError` under concurrent load.
- Buffering a bounded head and re-attaching the untouched remainder (`SequenceInputStream`) is the only
  way to peek at a stream *and* keep the payload usable for the following steps.
- `getBody(String)` is acceptable only when the size is known and small: a few hundred KB of business
  payload, an MPL attachment, or a header/property value. State that assumption in a comment — as done
  above — so the next developer does not copy the pattern onto a 200 MB file.
- If a downstream step needs text, prefer `message.getBody(java.io.Reader)` inside the step that
  actually needs it instead of converting the whole payload up front.

---

### R2. Extract and classify exception details in an Exception Subprocess

**When to use it:** In the first step of the Exception Subprocess, before the flow decides between
`Error End`, `End Message` or a DLQ write. **Watch out for:** dumping a full stack trace into headers or
properties — they are size-bound, they travel to receivers, and they flood the MPL.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.io.PrintWriter
import java.io.StringWriter

def Message processData(Message message) {
    // Set by the framework inside the Exception Subprocess; read dynamically and verified with
    // instanceof, so no tenant-specific class has to be imported or assumed.
    Object caught = message.getProperty("CamelExceptionCaught")

    String errorClass = "java.lang.Exception"
    String errorSummary = "Unclassified error, inspect the MPL attachment"
    String category = "UNKNOWN"

    if (caught instanceof Throwable) {
        Throwable root = rootCause((Throwable) caught)
        errorClass = root.getClass().getName()
        errorSummary = truncate(oneLine((Throwable) caught), 500)
        category = classify(message, (Throwable) caught)
    }

    // Short, machine-readable values go to exchange properties: the Router and the alerting step read them.
    message.setProperty("p_errorCategory", category)
    message.setProperty("p_errorClass", errorClass)
    message.setProperty("p_errorSummary", errorSummary)
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate("ERROR_" + category, 40))

    def messageLog = messageLogFactory.getMessageLog(message)   // can be null (local simulation)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("ErrorCategory", category)
        messageLog.addCustomHeaderProperty("ErrorClass", errorClass)
        String correlationId = str(message.getProperty("p_correlationId"))
        if (correlationId) {
            messageLog.addCustomHeaderProperty("CorrelationId", truncate(correlationId, 120))
        }
        if (caught instanceof Throwable) {
            // The stack trace goes to an ATTACHMENT only. Attachments are stored with the MPL and are
            // not copied into outbound requests.
            messageLog.addAttachmentAsString("Error_Stacktrace", stackTrace((Throwable) caught), "text/plain")
        }
    }

    // The payload is deliberately left untouched: the subprocess can still park it in a DLQ.
    return message
}

Throwable rootCause(Throwable t) {
    Throwable current = t
    int guard = 0
    while (current.getCause() != null && current.getCause() != current && guard < 10) {
        current = current.getCause()
        guard++
    }
    return current
}

String classify(Message message, Throwable top) {
    // 1) Protocol outcome first: a receiver that answered tells you more than the exception type does.
    String httpCode = str(message.getHeader("CamelHttpResponseCode", String.class))
    if (httpCode) {
        if (httpCode.startsWith("401") || httpCode.startsWith("403")) return "AUTHENTICATION"
        if (httpCode.startsWith("404")) return "NOT_FOUND"
        if (httpCode.startsWith("429")) return "THROTTLED"
        if (httpCode.startsWith("5")) return "BACKEND_SERVER_ERROR"
        if (httpCode.startsWith("4")) return "REQUEST_REJECTED"
    }
    // 2) Walk the cause chain from the OUTERMOST exception: the most specific signal wins. A
    //    SocketTimeoutException wrapping an IOException must classify as TIMEOUT, not as IO.
    Throwable current = top
    int guard = 0
    while (current != null && guard < 10) {
        String category = categoryOf(current)
        if (category != null) {
            return category
        }
        if (current.getCause() == current) {
            break
        }
        current = current.getCause()
        guard++
    }
    return (top instanceof java.io.IOException) ? "IO" : "UNKNOWN"
}

String categoryOf(Throwable t) {
    // Name fragments instead of imports: works for JDK and framework types alike and never requires
    // a class that may be absent from the tenant's script runtime.
    String name = t.getClass().getName()
    if (name.contains("SocketTimeout") || name.contains("Timeout")) return "TIMEOUT"
    if (name.contains("UnknownHost") || name.contains("Connect")) return "CONNECTIVITY"
    if (name.contains("SSL") || name.contains("Certificate")) return "TLS"
    if (name.contains("SAXParse") || name.contains("Json") || name.contains("XMLStream")) return "PAYLOAD_FORMAT"
    if (name.contains("NumberFormat") || name.contains("IllegalArgument")) return "VALIDATION"
    return null
}

String oneLine(Throwable t) {
    StringBuilder sb = new StringBuilder()
    Throwable current = t
    int depth = 0
    while (current != null && depth < 3) {
        sb.append(current.getClass().getSimpleName())
        String msg = str(current.getMessage())
        if (msg) {
            sb.append(": ").append(msg.replaceAll("\\s+", " ").trim())
        }
        current = current.getCause()
        if (current != null) {
            sb.append(" <- ")
        }
        depth++
    }
    return sb.toString()
}

String stackTrace(Throwable t) {
    StringWriter sw = new StringWriter()
    PrintWriter pw = new PrintWriter(sw)
    try {
        t.printStackTrace(pw)
        pw.flush()
        return truncate(sw.toString(), 20000)   // local policy cap, tune together with the MPL quota
    } finally {
        pw.close()
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- A category (not a raw stack trace) is what a Router, an alert rule or a support query can act on; the
  raw detail stays in the MPL attachment where size does not matter.
- Walking to the root cause is essential because adapter and mapping layers wrap the real failure several
  levels deep; the depth guard protects against self-referencing cause chains. Classification, however,
  runs **outermost-first**: a `SocketTimeoutException` wrapping an `IOException` must be reported as
  `TIMEOUT`, and only the *message* comes from the deepest cause.
- Classification by *name fragment* keeps the script independent of classes that may not exist in your
  tenant's runtime, and it degrades gracefully to `UNKNOWN` instead of failing inside the error handler.
- `SAP_MessageProcessingLogCustomStatus` is truncated to 40 characters because that is the documented
  maximum — an over-long value is otherwise silently dropped.

---

### R3. Set the MPL custom status and searchable business keys, with logging switchable by parameter

**When to use it:** At the end of a successful flow, and again in the Exception Subprocess with a failure
status. **Watch out for:** long or duplicated keys, and for treating a *property* as if it were searchable.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Contract - populate these with a Content Modifier BEFORE this script:
    //   p_businessObject -> 'SalesOrder'                    ({{business_object}})
    //   p_businessKey    -> ${header.OrderId}               ({{business_key_expression}})
    //   p_customStatus   -> 'ORDER_DELIVERED'
    //   p_interfaceName  -> 'IF_SalesOrder_Out'
    //   p_detailedLog    -> '{{enable_detailed_logging}}'   ('true' / 'false')
    String businessObject = str(message.getProperty("p_businessObject")) ?: "UnknownObject"
    String businessKey = str(message.getProperty("p_businessKey"))
    String customStatus = str(message.getProperty("p_customStatus")) ?: "PROCESSED"

    // Custom status: short, stable, alphanumeric; it is written into the MPL header table.
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncate(alnum(customStatus), 40))

    boolean detailedLog = "true".equalsIgnoreCase(str(message.getProperty("p_detailedLog")) ?: "false")

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog == null) {
        return message
    }

    // addCustomHeaderProperty -> persisted with the MPL and searchable in Monitor.
    addKey(messageLog, "BusinessObject", businessObject)
    addKey(messageLog, "BusinessKey", businessKey)
    addKey(messageLog, "Interface", str(message.getProperty("p_interfaceName")))
    addKey(messageLog, "Sender", str(message.getHeader("SAP_Sender", String.class)))

    // Expensive or high-cardinality keys only when the parameter says so: every key costs storage.
    if (detailedLog) {
        addKey(messageLog, "PayloadBytes", str(message.getProperty("p_payloadBytes")))
        addKey(messageLog, "Step", str(message.getProperty("p_stepName")))
    }
    return message
}

void addKey(def messageLog, String name, String value) {
    if (messageLog != null && value != null && !value.isEmpty()) {
        messageLog.addCustomHeaderProperty(name, truncate(value, 120))
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
String alnum(String value) { value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "_") }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- Searchable keys are the difference between "the interface failed" and "order 4711 failed" during an
  incident: they turn Monitor into a query tool. Keep them short and business-meaningful.
- Per SAP Help, properties added by a Script step are only visible in the MPL when the log level is
  **Debug** or **Trace**; custom header properties are the persisted, searchable channel. Do not rely on
  a script property as an audit trail.
- The logging switches are read from exchange properties that a Content Modifier fills from externalized
  parameters, so PROD can run quiet while TEST logs everything — without a redeploy.
- `messageLog` is null-checked because `getMessageLog` can return null (for example in local simulation);
  a null-check keeps the script runnable outside the tenant.

---

### R4. Attach a debug payload to the MPL only when a debug flag is on

**When to use it:** During go-live and incident analysis, behind a switch that is off in PROD.
**Watch out for:** attaching unconditionally — it floods the MPL database and can exhaust the storage quota.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Contract (Content Modifier before this script):
    //   p_enablePayloadLog = '{{enable_payload_logging}}'   ('true' / 'false', default 'false' in PROD)
    //   p_stepName         = literal label, e.g. 'AfterMapping'
    //   p_payloadBytes     = size measured by the size-guard step (see R7); optional but recommended
    boolean enabled = "true".equalsIgnoreCase(str(message.getProperty("p_enablePayloadLog")) ?: "false")
    if (!enabled) {
        return message
    }

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog == null) {
        return message
    }

    // Local policy cap for a single attachment; tune per tenant, it is not a documented SAP limit.
    long maxAttachmentChars = 200000L

    // Refuse to materialise the body when the size guard already told us it is too big.
    long knownSize = parseLong(str(message.getProperty("p_payloadBytes")), -1L)
    if (knownSize > maxAttachmentChars) {
        message.setProperty("p_payloadLogSkipped", "true")
        messageLog.addCustomHeaderProperty("PayloadLogSkipped", "true")
        return message
    }

    String payload = message.getBody(java.lang.String)
    if (payload == null) {
        return message
    }

    if (payload.length() > maxAttachmentChars) {
        // Size was unknown or the text form is larger than the byte form (multi-byte characters).
        message.setBody(payload)   // the body is now a String; that is acceptable at this size
        messageLog.addAttachmentAsString(attachmentName(message, "truncated"),
                truncate(payload, (int) maxAttachmentChars), mediaType(message))
        message.setProperty("p_payloadLogTruncated", "true")
    } else {
        message.setBody(payload)
        messageLog.addAttachmentAsString(attachmentName(message, null), payload, mediaType(message))
    }
    return message
}

String attachmentName(Message message, String suffix) {
    String step = str(message.getProperty("p_stepName")) ?: "payload"
    String index = str(message.getHeader("CamelSplitIndex", String.class))
    String name = step + (index ? "_" + index : "") + (suffix ? "_" + suffix : "")
    return name.replaceAll("[^A-Za-z0-9_.-]", "_")
}

String mediaType(Message message) {
    String contentType = str(message.getHeader("Content-Type", String.class))
    // A "null" media type is treated as text/plain by the logging API.
    return (contentType == null || contentType.isEmpty()) ? "text/plain" : contentType
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
long parseLong(String value, long fallback) {
    if (value == null || value.isEmpty()) {
        return fallback
    }
    try {
        return Long.parseLong(value)
    } catch (NumberFormatException ignored) {
        return fallback
    }
}
```

**Why it is written this way**
- The flag, not the script, decides whether payloads are logged: operations can turn diagnostics on for
  one flow without touching code, and PROD stays quiet by default.
- The size check happens *before* `getBody(String)`, so a debug switch can never itself cause an
  `OutOfMemoryError`; when the size is unknown the attachment is still capped after the fact.
- The attachment name carries the step label and `CamelSplitIndex`, which is what makes a split-based
  flow debuggable: you can tell which of the 4000 split messages produced the bad payload.
- Setting the body back as a String is safe only inside the documented size cap — that is exactly the
  trade-off the cap encodes.

---

## 3. Delivery, duplicates and guards

### R5. Idempotency: detect or prevent duplicates with a Data Store keyed by a business key

**When to use it:** Any flow that creates, posts or pays something on the receiver side and can be
retried (JMS redelivery, manual replay, retried HTTP call). **Watch out for:** using a key that changes
between two runs of the *same* message — then no duplicate will ever be found.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

def Message processData(Message message) {
    // FLOW CONTRACT - this script calls NO Data Store API, it only prepares and interprets:
    //   1. A Content Modifier fills the business key parts: p_keyPart1 ... p_keyPartN (contiguous!).
    //   2. The iFlow performs a Data Store SELECT with Entry ID = p_idempotencyKey and writes the
    //      outcome into p_dsDuplicateFound ('true' / 'false').
    //   3. The iFlow performs a Data Store WRITE with Entry ID = p_idempotencyKey BEFORE the side effect
    //      (prevent) or AFTER it (detect only). Keep the stored entry small: Data Store steps do not
    //      support streaming, so store the key and metadata, not a large payload.
    String key = buildKey(message)
    if (key == null) {
        // Fail fast: without a stable business key there is no duplicate detection at all.
        message.setProperty("p_duplicateAction", "REVIEW")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "IDEMPOTENCY_KEY_MISSING")
        return message
    }
    message.setProperty("p_idempotencyKey", key)

    boolean duplicate = "true".equalsIgnoreCase(str(message.getProperty("p_dsDuplicateFound")) ?: "false")
    String mode = (str(message.getProperty("p_idempotencyMode")) ?: "PREVENT").toUpperCase()

    // DETECT  : report the duplicate and continue. Use it for alerting, replay analysis and audits.
    // PREVENT : skip the side effect. Requires the Data Store write BEFORE the receiver call.
    String action = !duplicate ? "PROCESS" : (mode == "DETECT" ? "PROCESS_DUPLICATE" : "SKIP_DUPLICATE")

    message.setProperty("p_duplicateFound", String.valueOf(duplicate))
    message.setProperty("p_duplicateAction", action)
    message.setProperty("SAP_MessageProcessingLogCustomStatus",
            duplicate ? (action == "SKIP_DUPLICATE" ? "DUPLICATE_SKIPPED" : "DUPLICATE_DETECTED") : "NEW_MESSAGE")

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("IdempotencyKey", truncate(key, 120))
        messageLog.addCustomHeaderProperty("DuplicateAction", action)
    }
    return message
}

String buildKey(Message message) {
    StringBuilder raw = new StringBuilder()
    int index = 1
    while (index <= 10) {
        String part = str(message.getProperty("p_keyPart" + index))
        if (part == null) {
            break
        }
        raw.append(normalise(part)).append('|')
        index++
    }
    if (raw.length() == 0) {
        return null
    }
    // Hashing gives a fixed-length, safe Entry ID and hides business values from the Data Store key.
    return sha256Hex(raw.toString())
}

String normalise(String value) {
    // Case and whitespace differences between runs must not produce different keys.
    return value.trim().replaceAll("\\s+", " ").toUpperCase()
}

String sha256Hex(String value) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8))
    StringBuilder hex = new StringBuilder(hash.length * 2)
    for (byte b : hash) {
        int v = b & 0xFF
        if (v < 16) {
            hex.append('0')
        }
        hex.append(Integer.toHexString(v))
    }
    return hex.toString()
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- **Detect vs prevent** is a design decision, not a code detail. *Detect* compares after the fact and is
  safe to add to a running flow; *prevent* only works if the key is written before the side effect, which
  makes the Data Store entry part of the transaction boundary — pair it with a JMS sender and an
  `Error End` so a failed attempt rolls back instead of leaving a "seen" marker for work that never happened.
- Normalising and hashing the key makes duplicate detection independent of formatting noise and keeps the
  Data Store key short; the Data Store itself is the only component that must be stateful.
- The script deliberately does not guess a Data Store script API: the storage step stays in the flow,
  where it is visible in the iFlow model, transactional, and supported by SAP.
- The key parts are read through a documented property contract (`p_keyPart1..N`), so the script stays
  reusable across interfaces without a code change.

---

### R6. Build a dynamic SFTP target file name (timestamp + business key + extension)

**When to use it:** Before the SFTP/FTP receiver step, when the file name must be derived from the payload
and the run time. **Watch out for:** characters the target filesystem rejects, missing business keys, and
file names that change on every retry of the same message.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

def Message processData(Message message) {
    // Contract (Content Modifier before this script):
    //   p_fileNamePrefix   -> 'ORDER'                       ({{file_name_prefix}})
    //   p_businessKey      -> ${property.p_orderId}
    //   p_fileExtension    -> 'csv'                         (whitelisted below)
    //   p_requestTimestamp -> set ONCE for the whole message (optional but required for retry safety)
    String prefix = sanitise(str(message.getProperty("p_fileNamePrefix")) ?: "FILE", 20)
    String key = sanitise(str(message.getProperty("p_businessKey")), 60)
    String extension = allowedExtension(str(message.getProperty("p_fileExtension")))

    // A stable timestamp is reused if the flow already provided one: a JMS retry must produce the
    // identical file name, otherwise the same file is written twice under two names.
    String stamp = str(message.getProperty("p_requestTimestamp"))
    if (stamp == null) {
        // Always format UTC explicitly. Never call TimeZone.setDefault - it changes the JVM default
        // time zone for every flow on the worker node.
        stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        message.setProperty("p_requestTimestamp", stamp)
    }

    String uniqueness = str(message.getHeader("CamelSplitIndex", String.class))
    if (uniqueness == null) {
        uniqueness = UUID.randomUUID().toString().substring(0, 8)
    }

    // Fallback keeps the flow running when the business key is missing; the MPL key makes it visible.
    String safeKey = key ?: "NO_KEY"
    String fileName = truncate(prefix + "_" + safeKey + "_" + stamp + "_" + uniqueness + "." + extension, 200)

    // CamelFileName is the documented receiver header: it overrides the file name configured on the
    // adapter. The target DIRECTORY stays an adapter parameter - do not try to smuggle a path in here.
    message.setHeader("CamelFileName", fileName)

    message.setProperty("p_targetFileName", fileName)
    if (key == null) {
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "FILE_NAME_FALLBACK")
    }

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("TargetFileName", truncate(fileName, 120))
    }
    return message
}

String sanitise(String value, int max) {
    if (value == null) {
        return null
    }
    String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_")
    // Single-quoted patterns: '$' is literal there and cannot be mistaken for GString interpolation.
    cleaned = cleaned.replaceAll('^[_.]+', '').replaceAll('[_.]+$', '')
    if (cleaned.isEmpty()) {
        return null
    }
    return truncate(cleaned, max)
}

String allowedExtension(String value) {
    List<String> allowed = ['csv', 'txt', 'xml', 'json', 'dat', 'zip']
    String candidate = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]", "")
    return allowed.contains(candidate) ? candidate : "dat"
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- `CamelFileName` is the documented way to override the target file name on the SFTP/FTP receiver; when
  neither the adapter parameter nor the header is set, the framework falls back to the Exchange ID, which
  is useless for downstream reconciliation.
- Sanitising and whitelisting the extension protects the receiver from names it cannot store; the length
  cap keeps the name inside common filesystem limits.
- A missing business key must not fail the flow silently: the name falls back to `NO_KEY` and the custom
  status makes the fallback searchable in Monitor.
- Retry safety comes from reusing `p_requestTimestamp` if it exists; generating `now()` inside the script
  would rename the file on every redelivery.

---

### R7. Guard the payload size before expensive work, and reject oversize explicitly

**When to use it:** Immediately after the sender adapter, before mapping, conversion or an external call.
**Watch out for:** `available()` — it is a hint, not a size — and for buffering a 500 MB file while
"checking" it.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.io.ByteArrayOutputStream
import java.io.InputStream

def Message processData(Message message) {
    // Contract (Content Modifier before this script):
    //   p_maxPayloadBytes = '{{max_payload_bytes}}'   local policy, e.g. 10485760
    //   p_failOnOversize  = 'true' | 'false'          true -> exception, false -> flag + Router branch
    long maxBytes = parseLong(str(message.getProperty("p_maxPayloadBytes")), 10485760L)
    boolean failOnOversize = !"false".equalsIgnoreCase(str(message.getProperty("p_failOnOversize")) ?: "true")

    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        message.setProperty("p_payloadBytes", "0")
        message.setProperty("p_payloadTooLarge", "false")
        return message
    }

    // A Content-Length header, when present, is a hint only: not every sender sets it, and it can lie.
    long hint = parseLong(str(message.getHeader("Content-Length", String.class)), -1L)

    ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(maxBytes, 1048576L))
    long total = 0L
    boolean oversize = false
    byte[] chunk = new byte[8192]
    try {
        int read
        while ((read = body.read(chunk)) != -1) {
            total += read
            if (total > maxBytes) {
                // Stop reading at the first byte above the limit: heap never grows past maxBytes + 8 KB.
                oversize = true
                break
            }
            buffer.write(chunk, 0, read)
        }
    } finally {
        body.close()
    }

    message.setProperty("p_payloadBytes", String.valueOf(oversize ? total : buffer.size()))

    if (oversize) {
        message.setProperty("p_payloadTooLarge", "true")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "PAYLOAD_TOO_LARGE")
        if (failOnOversize) {
            // A plain JDK exception is enough: the Exception Subprocess (R2) classifies and logs it.
            throw new IllegalArgumentException(
                    "Payload exceeds the configured limit of " + maxBytes + " bytes (at least " + total + " bytes were read)")
        }
        // The body is intentionally NOT re-set: the Router must send this message to the reject branch.
        return message
    }

    message.setProperty("p_payloadTooLarge", "false")
    message.setProperty("p_payloadBytes", String.valueOf(buffer.size()))
    message.setBody(buffer.toByteArray())
    if (hint > 0 && hint != buffer.size()) {
        message.setProperty("p_contentLengthMismatch", "true")
    }
    return message
}

String str(Object value) { value == null ? null : value.toString().trim() }
long parseLong(String value, long fallback) {
    if (value == null || value.isEmpty()) {
        return fallback
    }
    try {
        return Long.parseLong(value)
    } catch (NumberFormatException ignored) {
        return fallback
    }
}
```

**Why it is written this way**
- The first line of defence is the sender adapter's *Body Size* parameter (tab *Conditions* for HTTP-based
  senders): messages above it are rejected before any step runs. This recipe is the second line, for
  adapters that have no such parameter and for limits that depend on business context.
- Reading in 8 KB blocks with an early break keeps memory bounded by the limit instead of by the payload;
  buffering is unavoidable only because the body must be handed on, and it is capped by design.
- The limit comes from an externalized parameter (via a Content Modifier), so DEV and PROD can differ
  without a code change; the default in the script is a safe fallback, not a SAP limit.
- Because every downstream step now knows `p_payloadBytes`, later recipes (R4) can decide whether text
  conversion is affordable *before* materialising anything.

---

### R8. Parse the payload encoding defensively (`CamelCharsetName`, explicit charsets)

**When to use it:** Whenever a file arrives from a system that does not guarantee UTF-8 (legacy flat
files, Windows exports, IDoc-adjacent text) or when a byte-order mark breaks the first field.
**Watch out for:** relying on the JVM default charset and for "fixing" encoding by re-encoding bytes blindly.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.io.InputStream
import java.io.PushbackInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

def Message processData(Message message) {
    InputStream raw = message.getBody(java.io.InputStream)
    if (raw == null) {
        return message
    }

    // 1) Declared encoding: explicit flow parameter wins, then the documented framework property.
    String declared = str(message.getProperty("p_charset")) ?: str(message.getProperty("CamelCharsetName"))
    Charset fallback = resolveCharset(declared)

    // 2) A byte-order mark is stronger evidence than a declaration and must not reach the payload.
    PushbackInputStream stream = new PushbackInputStream(raw, 8192)
    byte[] head = new byte[3]
    int filled = readInto(stream, head)
    String bomCharset = bomCharset(head, filled)
    if (bomCharset == null && filled > 0) {
        stream.unread(head, 0, filled)   // no BOM: these bytes are payload, put them back
    }
    Charset charset = bomCharset != null ? Charset.forName(bomCharset) : (fallback ?: StandardCharsets.UTF_8)

    // 3) Bounded sample for the MPL, pushed back so the payload stays complete and unconsumed.
    byte[] sample = new byte[1024]
    int sampleLength = readInto(stream, sample)
    if (sampleLength > 0) {
        stream.unread(sample, 0, sampleLength)
        String preview = new String(sample, 0, sampleLength, charset).replaceAll("\\s+", " ").trim()
        message.setProperty("p_encodingSample", truncate(preview, 200))
    }

    // 4) Publish the decision so every following step and adapter decodes identically.
    message.setProperty("p_detectedCharset", charset.name())
    message.setProperty("CamelCharsetName", charset.name())
    message.setBody(stream)   // same payload, BOM removed, position preserved

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("Charset", charset.name())
        messageLog.addCustomHeaderProperty("CharsetSource", bomCharset != null ? "BOM" : (fallback != null ? "DECLARED" : "DEFAULT"))
    }
    return message
}

Charset resolveCharset(String name) {
    if (name == null || name.isEmpty()) {
        return null
    }
    try {
        // Throws for unknown or illegal names: fall back instead of failing the whole flow.
        return Charset.forName(name)
    } catch (Exception ignored) {
        return null
    }
}

int readInto(InputStream stream, byte[] buffer) {
    int filled = 0
    while (filled < buffer.length) {
        int read = stream.read(buffer, filled, buffer.length - filled)
        if (read <= 0) {
            break
        }
        filled += read
    }
    return filled
}

String bomCharset(byte[] head, int length) {
    if (length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
        return "UTF-8"
    }
    if (length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
        return "UTF-16LE"
    }
    if (length >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
        return "UTF-16BE"
    }
    return null
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- `PushbackInputStream` gives byte-exact "look and put back" semantics: the payload is never copied, and
  the peek cannot corrupt it — the same trick as R1, one level lower.
- A BOM outranks a declared charset because the BOM is written by the producing system itself; consuming
  it prevents the classic "ï»¿" appearing in the first CSV header or XML declaration.
- `Charset.forName` throws on unknown names, so the lookup is wrapped: a typo in an externalized parameter
  degrades to UTF-8 with a searchable `CharsetSource=DEFAULT` key instead of failing the flow.
- Publishing `CamelCharsetName` makes every downstream step and adapter agree on one decoding; the sample
  in the MPL is bounded so encoding incidents can be diagnosed without dumping the payload.

---

## 4. Transformation, chunking and composition

### R9. Convert a small/medium XML payload with `XmlSlurper` from a Reader

**When to use it:** Read-only extraction or a small reshape of an XML payload of a few MB.
**Watch out for:** `XmlSlurper.parseText(String)` — it costs an extra full copy in heap and is explicitly
discouraged by SAP.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import java.io.Reader

def Message processData(Message message) {
    // Size assumption: small/medium payload (a few MB). Above ~20 MB switch to StAX, see
    // ../examples/groovy-stax-xml-stream.groovy
    Reader reader = message.getBody(java.io.Reader)
    if (reader == null) {
        return message
    }

    def root
    try {
        // No validation, no namespace awareness: cheaper and sufficient for a value extraction.
        def slurper = new XmlSlurper(false, false)
        // Harden against XXE (external entity) attacks. Wrapped: parser support varies, and a flow must
        // keep running if the feature is not accepted by the tenant's parser.
        try {
            slurper.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (Exception ignored) {
            // Rely on the tenant-level XML hardening policy in this case.
        }
        root = slurper.parse(reader)   // parse(Reader) - never parseText(String)
    } finally {
        reader.close()
    }

    StringBuilder out = new StringBuilder(256)
    out.append("<Orders>")
    root.Order.each { order ->
        String id = order.@Id.text()
        if (id) {
            out.append("<Order id=\"").append(XmlUtil.escapeXml(id)).append("\">")
            out.append("<Customer>").append(XmlUtil.escapeXml(order.Customer.text())).append("</Customer>")
            out.append("<Total>").append(XmlUtil.escapeXml(order.Total.text())).append("</Total>")
            out.append("</Order>")
        }
    }
    out.append("</Orders>")

    message.setBody(out.toString())   // compact output: pretty-printing inflates the payload for no gain
    message.setHeader("Content-Type", "application/xml")
    message.setProperty("p_orderCount", String.valueOf(root.Order.size()))
    return message
}
```

**Why it is written this way**
- `parse(Reader)` streams the document into the parser; `parseText(String)` would first build a full String
  copy, which is exactly the memory behaviour the payload rules forbid.
- Compacting the output with a `StringBuilder` and escaping every value with `XmlUtil.escapeXml` avoids an
  XML injection when a source value contains `<`, `&` or a quote — a very common production defect.
- The XXE hardening is attempted, not assumed: `setFeature` is parser-dependent, and swallowing the failure
  keeps a working flow working while the tenant-level policy is the real guarantee.
- `XmlSlurper` is documented as the read-only parser (use `XmlParser` only when in-place manipulation is
  genuinely required, and only for small documents).

---

### R10. Split a very large CSV file into chunks with constant memory

**When to use it:** When a downstream system accepts at most N records per call and the source file is far
larger than the heap you are willing to spend. **Watch out for:** the header row, quoted fields containing
line breaks, the final partial chunk, and the fact that the *output* — not the input — is what costs memory.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlUtil
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

def Message processData(Message message) {
    // Contract (Content Modifier before this script):
    //   p_charset       -> 'UTF-8'      ({{source_charset}})
    //   p_hasHeader     -> 'true' | 'false'
    //   p_linesPerChunk -> '1000'
    //   p_maxChunks     -> '10'         hard bound: output heap ~ maxChunks * linesPerChunk * avgLineLength
    // The INPUT size is irrelevant: only the current line and one chunk are held in heap.
    // For an unbounded number of chunks prefer a Splitter step (Streaming enabled) downstream of this script.
    Charset charset = resolveCharset(str(message.getProperty("p_charset")))
    boolean hasHeader = !"false".equalsIgnoreCase(str(message.getProperty("p_hasHeader")) ?: "true")
    int linesPerChunk = (int) parseLong(str(message.getProperty("p_linesPerChunk")), 1000L)
    int maxChunks = (int) parseLong(str(message.getProperty("p_maxChunks")), 10L)
    if (linesPerChunk < 1) {
        linesPerChunk = 1
    }

    InputStream body = message.getBody(java.io.InputStream)
    if (body == null) {
        return message
    }

    BufferedReader reader = new BufferedReader(new InputStreamReader(body, charset))
    StringBuilder out = new StringBuilder(4096)
    out.append("<Chunks>")
    StringBuilder chunk = new StringBuilder()
    String header = null
    int linesInChunk = 0
    int chunkCount = 0
    long dataLines = 0L
    long physicalLines = 0L
    boolean truncated = false

    try {
        String line = reader.readLine()
        while (line != null) {
            physicalLines++
            // Basic quoted-field handling: an odd number of quotes means the record continues on the
            // next physical line. Appending only the current record keeps memory per-record, not per-file.
            while (countQuotes(line) % 2 == 1) {
                String continuation = reader.readLine()
                if (continuation == null) {
                    break
                }
                physicalLines++
                line = line + "\n" + continuation
            }

            if (hasHeader && header == null) {
                header = line
            } else {
                if (linesInChunk == 0 && hasHeader && header != null) {
                    chunk.append(header).append('\n')   // every chunk is self-describing for the receiver
                }
                chunk.append(line).append('\n')
                linesInChunk++
                dataLines++
                if (linesInChunk >= linesPerChunk) {
                    appendChunk(out, chunk, chunkCount, dataLines - linesInChunk + 1, linesInChunk)
                    chunkCount++
                    linesInChunk = 0
                    if (chunkCount >= maxChunks) {
                        truncated = true
                        break
                    }
                }
            }
            line = reader.readLine()
        }
        // Final partial chunk: forgetting it is the classic off-by-one of every chunking implementation.
        if (!truncated && linesInChunk > 0) {
            appendChunk(out, chunk, chunkCount, dataLines - linesInChunk + 1, linesInChunk)
            chunkCount++
        }
    } finally {
        reader.close()   // closes the underlying body stream as well
    }

    out.append("</Chunks>")
    message.setBody(out.toString())
    message.setProperty("p_chunkCount", String.valueOf(chunkCount))
    message.setProperty("p_dataLineCount", String.valueOf(dataLines))
    message.setProperty("p_physicalLineCount", String.valueOf(physicalLines))
    message.setProperty("p_chunkTruncated", String.valueOf(truncated))
    message.setProperty("SAP_MessageProcessingLogCustomStatus", truncated ? "CHUNKS_TRUNCATED" : "CHUNKS_BUILT")
    message.setHeader("Content-Type", "application/xml")
    return message
}

void appendChunk(StringBuilder out, StringBuilder chunk, int index, long firstDataLine, int lineCount) {
    out.append("<Chunk index=\"").append(index)
       .append("\" firstDataLine=\"").append(firstDataLine)
       .append("\" lineCount=\"").append(lineCount).append("\">")
       .append(XmlUtil.escapeXml(chunk.toString()))
       .append("</Chunk>")
    chunk.setLength(0)   // bounded memory: the chunk is released as soon as it is embedded
}

int countQuotes(String value) {
    char quote = (char) '"'
    int count = 0
    for (int i = 0; i < value.length(); i++) {
        if (value.charAt(i) == quote) {
            count++
        }
    }
    return count
}

Charset resolveCharset(String name) {
    if (name == null || name.isEmpty()) {
        return StandardCharsets.UTF_8
    }
    try {
        return Charset.forName(name)
    } catch (Exception ignored) {
        return StandardCharsets.UTF_8
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
long parseLong(String value, long fallback) {
    if (value == null || value.isEmpty()) {
        return fallback
    }
    try {
        return Long.parseLong(value)
    } catch (NumberFormatException ignored) {
        return fallback
    }
}
```

**Why it is written this way**
- The reader walks the file record by record, so heap usage is one line plus one chunk: the input file can be
  gigabytes while the step stays inside a few MB.
- `p_maxChunks` makes the output bound explicit. When the bound is hit the script stops, sets
  `p_chunkTruncated` and a custom status, so nothing is silently dropped and the rest can be handled by a
  second run or by a streaming Splitter.
- The header row is captured once and repeated in every chunk, and `firstDataLine` records where the chunk
  starts in the source, which is what makes a failed chunk traceable back to the file.
- Quote counting is a deliberately simple, documented heuristic: exact RFC 4180 parsing would need a
  dedicated library, and open source classes are not a supported dependency in the script runtime.

---

### R11. Compose a multipart / `$batch` request body without string concatenation blow-ups

**When to use it:** When a receiver expects one multipart payload for many operations (OData `$batch`,
bulk upload) and the individual parts must be byte-accurate. **Watch out for:** line endings — multipart
requires CRLF — `Content-Length` per part in **bytes** rather than characters, and boundaries that appear
inside the payload.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.nio.charset.StandardCharsets
import java.util.UUID

def Message processData(Message message) {
    // Contract: an upstream Script step provides the ordered request descriptors.
    //   p_authToken   -> OAuth2 bearer token ({{oauth_token}}); never hardcoded, never logged
    //   p_requests    -> List<Map> with keys: method, path, body (String, optional), contentType (optional)
    // Example entry: [method:'POST', path:'$/EntitySet', body:'{"a":1}']
    // The resulting body is a multipart/mixed request body; the boundary is published for the header.
    Object raw = message.getProperty("p_requests")
    if (!(raw instanceof List)) {
        throw new IllegalArgumentException("p_requests must be a List of request descriptors (method/path/body)")
    }
    List requests = (List) raw

    String boundary = "batch_" + UUID.randomUUID().toString().replace("-", "")
    String crlf = "\r\n"
    StringBuilder out = new StringBuilder(4096)

    for (int i = 0; i < requests.size(); i++) {
        Map request = (Map) requests.get(i)
        String method = upper(str(request.get("method")) ?: "POST")
        String path = str(request.get("path"))
        if (path == null) {
            throw new IllegalArgumentException("Request descriptor " + i + " has no path")
        }
        String partBody = str(request.get("body"))
        String partType = (partBody == null) ? null : (str(request.get("contentType")) ?: "application/json")

        out.append("--").append(boundary).append(crlf)
        out.append("Content-Type: application/http").append(crlf)
        out.append("Content-Transfer-Encoding: binary").append(crlf)
        out.append("Content-ID: ").append(i + 1).append(crlf)
        out.append(crlf)                                   // blank line closes the part headers
        out.append(method).append(' ').append(path).append(" HTTP/1.1").append(crlf)
        if (partType != null) {
            out.append("Content-Type: ").append(partType).append(crlf)
            // Content-Length counts BYTES of the embedded body, not characters: UTF-8 umlauts differ.
            out.append("Content-Length: ")
               .append(partBody.getBytes(StandardCharsets.UTF_8).length).append(crlf)
        }
        out.append(crlf)                                   // blank line closes the embedded headers
        if (partBody != null) {
            out.append(partBody).append(crlf)
        }
    }
    out.append("--").append(boundary).append("--").append(crlf)   // closing delimiter

    String body = out.toString()
    message.setBody(body)
    // The receiver adapter sends this header; the boundary in it MUST match the body.
    message.setHeader("Content-Type", "multipart/mixed; boundary=" + boundary)
    message.setProperty("p_batchBoundary", boundary)
    message.setProperty("p_batchPartCount", String.valueOf(requests.size()))
    message.setProperty("p_batchBytes", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length))
    message.setProperty("SAP_MessageProcessingLogCustomStatus", "BATCH_BUILT")
    return message
}

String upper(String value) { value == null ? null : value.trim().toUpperCase() }
String str(Object value) { value == null ? null : value.toString() }
```

**Why it is written this way**
- One `StringBuilder` with a single `toString()` at the end avoids the quadratic copying of repeated
  `+` concatenation — the classic reason a "small" bulk builder times out under load.
- CRLF is used everywhere because HTTP multipart framing is defined that way; a `\n`-only body is one of
  the most common reasons a `$batch` call is rejected.
- `Content-Length` is computed on the UTF-8 bytes of the embedded body; character count is wrong as soon
  as a non-ASCII character appears, and the receiver then truncates or hangs.
- The boundary is generated, published as `p_batchBoundary` and used for the `Content-Type` header, so the
  header and the body can never drift apart. A generated boundary is safe for retries because the whole
  batch is one atomic HTTP request; if your receiver needs a *stable* boundary, seed it from
  `p_requestTimestamp` instead of a UUID.

---

### R12. Maintainable value-mapping (code list) lookup with a mandatory default branch

**When to use it:** Translating codes between two systems (document types, units, status codes) where a
full message mapping would be overkill. **Watch out for:** `map.get(key) ?: fallback` — it also replaces a
legitimately mapped empty value — and for silently swallowing unmapped codes.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Contract (Content Modifier before this script):
    //   p_sourceValue    -> the code to translate, e.g. ${header.DocType}
    //   p_defaultTarget  -> what to use when the code is not in the list, e.g. 'UNKNOWN'
    //   p_direction      -> 'FORWARD' (default) or 'REVERSE'
    //   p_mappingName    -> label used in the MPL, e.g. 'DocType_CRM_to_S4'
    String sourceValue = str(message.getProperty("p_sourceValue"))
    String defaultTarget = str(message.getProperty("p_defaultTarget"))
    if (defaultTarget == null) {
        // No default = no safe behaviour. Fail fast instead of inventing a value.
        throw new IllegalArgumentException("p_defaultTarget is mandatory: an unmapped code must have a defined outcome")
    }
    String direction = (str(message.getProperty("p_direction")) ?: "FORWARD").toUpperCase()

    // The code list stays in the script as data, one row per line: reviewable in a diff, testable as-is.
    // It can be overridden at runtime through p_codeListOverride (rows separated by newline or ';').
    List<String> rows = parseRows(str(message.getProperty("p_codeListOverride")))
    if (rows.isEmpty()) {
        rows = [
                '01|INVOICE',        // CRM document category -> S/4 billing type
                '02|CREDIT_MEMO',
                '03|DEBIT_MEMO',
                '04|#SKIP#'          // deliberately mapped to a technical marker, not to a business value
        ]
    }
    Map<String, String> mapping = buildMapping(rows)
    Map<String, String> effective = (direction == "REVERSE") ? invert(mapping) : mapping

    boolean mapped = effective.containsKey(sourceValue)
    String target = mapped ? effective.get(sourceValue) : defaultTarget

    message.setProperty("p_targetValue", target)
    message.setProperty("p_mappingHit", String.valueOf(mapped))

    if (!mapped) {
        // Searchable trace of every unmapped code: this is the input for the next code-list review.
        message.setProperty("p_unmappedValue", sourceValue ?: "")
        if (str(message.getProperty("p_failOnUnmapped")) == "true") {
            throw new IllegalArgumentException("No mapping for value '" + sourceValue + "' in " + direction + " direction")
        }
        def messageLog = messageLogFactory.getMessageLog(message)
        if (messageLog != null) {
            messageLog.addCustomHeaderProperty("UnmappedCode", truncate(sourceValue ?: "<empty>", 60))
            messageLog.addCustomHeaderProperty("MappingName", truncate(str(message.getProperty("p_mappingName")) ?: "unnamed", 60))
            messageLog.addAttachmentAsString("UnmappedCodeDetail",
                    "mapping=" + (str(message.getProperty("p_mappingName")) ?: "unnamed") +
                            "\ndirection=" + direction + "\nsourceValue=" + (sourceValue ?: "<empty>") +
                            "\ndefaultApplied=" + defaultTarget, "text/plain")
        }
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "UNMAPPED_CODE")
    } else {
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "MAPPED")
    }
    return message
}

List<String> parseRows(String override) {
    if (override == null || override.trim().isEmpty()) {
        return []
    }
    return override.split("[\\n;]").toList()
}

Map<String, String> buildMapping(List<String> rows) {
    Map<String, String> mapping = new LinkedHashMap<String, String>()
    for (String row : rows) {
        String line = row == null ? "" : row.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            continue   // comments and blank lines keep the list readable
        }
        int separator = line.indexOf('|')
        if (separator < 1 || separator == line.length() - 1) {
            throw new IllegalArgumentException("Malformed code list row (expected SOURCE|TARGET): " + line)
        }
        mapping.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
    }
    return mapping
}

Map<String, String> invert(Map<String, String> mapping) {
    Map<String, String> inverted = new LinkedHashMap<String, String>()
    mapping.each { key, value ->
        // On collisions the first mapping wins: the list order is the priority order.
        if (!inverted.containsKey(value)) {
            inverted.put(value, key)
        }
    }
    return inverted
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- `containsKey` decides the hit, not the truthiness of the value: a mapping whose target is an empty string
  is still a mapping, and `?:` would silently replace it with the default.
- The default branch is mandatory (`p_defaultTarget` is validated), because an unmapped code that becomes
  `null` travels into the receiver and fails there — far from the cause.
- Every miss is logged as a searchable MPL property plus an attachment with the full context, which turns
  the code list into a self-improving artifact instead of a permanent source of "unknown value" tickets.
- The list is data (rows of `SOURCE|TARGET`), so extending the mapping is a one-line change that survives a
  transport, and the optional `p_codeListOverride` covers urgent hotfixes without a redeploy.

---

## 5. Retries and configuration

### R13. Make a script re-entrant so a JMS retry does not double-post side effects

**When to use it:** Any step that has an external effect (post, pay, send, write) in a flow that may be
retried by a JMS queue or replayed manually. **Watch out for:** any per-attempt value — timestamps, UUIDs,
sequence numbers — because it makes the second attempt look like a new business event.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

def Message processData(Message message) {
    // Contract (Content Modifier + Data Store steps around this script):
    //   p_businessKey           stable business identity of the message
    //   p_payloadChecksumInput  canonical business fields, e.g. ${property.p_orderId}|${property.p_amount}
    //   p_stepName              name of the effect, e.g. 'POST_INVOICE'
    //   p_stepAlreadyExecuted   'true' when the Data Store SELECT found an entry for p_idempotencyToken
    String businessKey = str(message.getProperty("p_businessKey"))
    String stepName = str(message.getProperty("p_stepName")) ?: "UNNAMED_STEP"
    String checksumInput = str(message.getProperty("p_payloadChecksumInput")) ?: businessKey
    if (businessKey == null || checksumInput == null) {
        message.setProperty("p_skipSideEffect", "true")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "REENTRANCY_KEY_MISSING")
        return message
    }

    // The token is derived ONLY from stable inputs. It is identical on attempt 1, 2 and 3, which is
    // exactly what makes the retry recognisable as the same business event.
    String token = sha256Hex(businessKey + "|" + stepName + "|" + checksumInput)
    message.setProperty("p_idempotencyToken", token)
    message.setProperty("p_effectStep", stepName)

    boolean alreadyExecuted = "true".equalsIgnoreCase(str(message.getProperty("p_stepAlreadyExecuted")) ?: "false")

    // The outbound timestamp must be stable too: derive it once and reuse it on every attempt.
    String outboundTimestamp = str(message.getProperty("p_outboundTimestamp"))
    if (outboundTimestamp == null) {
        outboundTimestamp = str(message.getProperty("p_requestTimestamp"))
        if (outboundTimestamp != null) {
            message.setProperty("p_outboundTimestamp", outboundTimestamp)
        }
    }

    if (alreadyExecuted) {
        // Re-entrant path: do not repeat the side effect, but do repeat the observable bookkeeping.
        message.setProperty("p_skipSideEffect", "true")
        message.setProperty("p_reentrantSkip", "true")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "RETRY_ALREADY_EXECUTED")
    } else {
        message.setProperty("p_skipSideEffect", "false")
        message.setProperty("SAP_MessageProcessingLogCustomStatus", "EFFECT_PENDING")
    }
    if (outboundTimestamp != null) {
        message.setProperty("p_outboundTimestamp", outboundTimestamp)
    }

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addCustomHeaderProperty("IdempotencyToken", truncate(token, 120))
        messageLog.addCustomHeaderProperty("EffectStep", stepName)
        messageLog.addCustomHeaderProperty("ReentrantSkip", String.valueOf(alreadyExecuted))
    }
    return message
}

String sha256Hex(String value) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256")
    byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8))
    StringBuilder hex = new StringBuilder(hash.length * 2)
    for (byte b : hash) {
        int v = b & 0xFF
        if (v < 16) {
            hex.append('0')
        }
        hex.append(Integer.toHexString(v))
    }
    return hex.toString()
}

String str(Object value) { value == null ? null : value.toString().trim() }
String truncate(String value, int max) { value != null && value.length() > max ? value.substring(0, max) : value }
```

**Why it is written this way**
- Re-entrancy is a property of the *inputs*, not of the code: a token built from stable values is the same
  on every attempt, so the Data Store can answer "already done" and the flow can skip the second post.
- `p_outboundTimestamp` is written once and reused. Generating `new Date()` inside the script changes the
  payload on every attempt, which defeats downstream deduplication and produces two different documents.
- The skip is expressed as `p_skipSideEffect` + a custom status rather than an exception: the retry ends
  *successfully* (the queue message is consumed) but is still identifiable in Monitor as a retry.
- Bookkeeping stays unconditional: skipping the effect must not skip the MPL keys, or a retried message
  becomes invisible exactly when support is looking for it.

---

### R14. Read externalized configuration inside a script — and never leak credentials

**When to use it:** When a value genuinely cannot be supplied by the channel configuration (a policy
threshold, a switch, a business-code list). **Watch out for:** `{{placeholders}}` are resolved by the
*configuration* of steps and adapters — a script never sees them — and for writing secrets into headers or
properties, where tracing exposes them in clear text.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // A script cannot resolve {{parameters}} itself. The documented pattern is:
    //   Content Modifier step  ->  property  p_maxRetries  =  {{max_retries}}
    // This script then validates what the flow handed over, applies safe defaults, and refuses to
    // promote secrets into protocol headers or into properties.
    String maxRetries = config(message, "p_maxRetries", "3")
    String timeoutMs = config(message, "p_timeoutMs", "30000")
    String targetAlias = config(message, "p_credentialAlias", null)   // alias NAME only, never the secret

    message.setProperty("p_maxRetries", maxRetries)
    message.setProperty("p_timeoutMs", timeoutMs)

    // Credentials belong to a Security Material artifact used by the receiver adapter. A script must not
    // read them, must not store them, and must not forward them.
    if (targetAlias != null) {
        message.setProperty("p_credentialAliasName", targetAlias)
        setOutboundHeader(message, "X-Credential-Alias", targetAlias)   // alias name, safe to expose
    }

    // Guard rail: fail the configuration early instead of calling the receiver with an empty URL.
    String endpoint = config(message, "p_endpoint", null)
    if (endpoint == null) {
        throw new IllegalArgumentException("Configuration property p_endpoint is missing (bind it to {{target_endpoint}})")
    }
    message.setProperty("p_endpoint", endpoint)

    message.setProperty("SAP_MessageProcessingLogCustomStatus", "CONFIG_OK")
    return message
}

String config(Message message, String name, String fallback) {
    String value = str(message.getProperty(name))
    return (value == null || value.isEmpty()) ? fallback : value
}

void setOutboundHeader(Message message, String name, String value) {
    // Deny-list of header names that must never carry a secret: headers are copied into outbound
    // requests and can appear in traces, logs and error reports.
    List<String> forbidden = ['authorization', 'cookie', 'set-cookie', 'x-api-key', 'apikey', 'password',
                              'client_secret', 'clientsecret', 'token', 'x-csrf-token']
    if (forbidden.contains(name.toLowerCase())) {
        throw new IllegalArgumentException("Refusing to set header '" + name +
                "': credentials must be configured as a Security Material on the receiver adapter, not by a script")
    }
    if (value != null) {
        message.setHeader(name, value)
    }
}

String str(Object value) { value == null ? null : value.toString().trim() }
```

**Why it is written this way**
- The `{{parameter}}` placeholder is resolved when the step or adapter configuration is read, so the only
  supported way to give a script a tenant-specific value is a Content Modifier that maps the parameter to a
  property. Every recipe in this book follows that contract.
- Defaults are applied in the script *and* validated: a missing critical value (endpoint) throws early with
  a message a support engineer can act on, instead of failing later inside an adapter call.
- The deny-list makes credential leakage a hard error rather than a review finding — storing a secret in a
  header or a property exposes it to tracing, and headers travel to the receiver by default.
- Only the *alias name* of a Security Material is passed around; the secret itself never enters the
  exchange, which is also why the receiver step, not the script, performs authentication.

---

## Further Reading

- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [General Scripting Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/general-scripting-guidelines)
- [Add Information to the Message Processing Log](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/add-information-to-the-message-processing-log)
- [Use Custom Header Properties to Search for Message Processing Logs](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/use-custom-header-properties-to-search-for-message-processing-logs)
- [Headers and Exchange Properties Provided by the Integration Framework](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/headers-and-exchange-properties-provided-by-the-integration-framework)
