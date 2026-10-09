# Examples — Production Groovy Scripts for SAP Cloud Integration

A curated set of Groovy scripts for SAP Cloud Integration (CPI / SAP Integration Suite). Each file is a
complete, self-contained script step: a header comment explains WHAT problem it solves, WHEN to use it and
HOW to wire it into an iFlow (which step, which properties to configure before and after it), followed by
the script itself.

Every recipe behind these scripts is explained, with alternatives and trade-offs, in
[references/groovy-recipes.md](../references/groovy-recipes.md). Memory rules and header/property
discipline are in [references/groovy-best-practices.md](../references/groovy-best-practices.md).

---

## 1. Index

| File | Problem it solves | Key API/feature used | When to use |
| :--- | :--- | :--- | :--- |
| [groovy-exception-details-capture.groovy](groovy-exception-details-capture.groovy) | Turns the raw `CamelExceptionCaught` property into a short summary, a category and a retry/monitor decision, keeping the full stack trace out of headers | `message.getProperty("CamelExceptionCaught")`, `instanceof Throwable`, cause-chain walk, `CamelHttpResponseCode`, `addCustomHeaderProperty`, `addAttachmentAsString`, `SAP_MessageProcessingLogCustomStatus` | First step of every Exception Subprocess, before the `End Message` / `Error End` / DLQ decision |
| [groovy-datastore-idempotency.groovy](groovy-datastore-idempotency.groovy) | Detects or prevents duplicate processing by building a stable, normalised, hashed business key and turning the Data Store outcome into a routing action | `java.security.MessageDigest` (SHA-256), property contract with the Data Store Select/Write steps, `SAP_MessageProcessingLogCustomStatus` | Any flow with a side effect (post/create/pay) that can be retried by JMS, a manual replay or a sender retry |
| [groovy-csv-stream-reader.groovy](groovy-csv-stream-reader.groovy) | Splits a very large CSV into chunks of N data lines with constant memory, handling header rows, quoted multi-line fields and the final partial chunk | `InputStream` + `BufferedReader` record-by-record read, `groovy.xml.XmlUtil.escapeXml`, `CamelSplitIndex`-free indexing, `p_chunkTruncated` guard | Flat-file ingestion where the receiver accepts at most N records per call, or before any non-streaming step |
| [groovy-jackson-json-stream.groovy](groovy-jackson-json-stream.groovy) | Filters, renames, adds and drops fields of a large JSON array without loading the document into memory | Jackson streaming API (`JsonFactory`, `JsonParser`, `JsonGenerator`, `copyCurrentStructure`, `skipChildren`) | Large JSON payloads with a flat array of records; verify the Jackson import is supported in your tenant's script runtime first |
| [groovy-odata-batch-payload-builder.groovy](groovy-odata-batch-payload-builder.groovy) | Assembles a valid OData `$batch` multipart request body (boundary, CRLF, per-part headers, byte-accurate `Content-Length`) without string-concatenation blow-ups | `StringBuilder`, `multipart/mixed` + `changeset` framing, `String.getBytes(UTF_8).length`, dynamic `Content-Type` header, `java.util.UUID` boundary | Bulk replication to an OData V2/V4 service, when the receiver supports `$batch` and round trips must be reduced |
| [groovy-multipart-attachment-builder.groovy](groovy-multipart-attachment-builder.groovy) | Builds a MIME multipart body with a generated boundary, text and binary parts, and RFC 2045 compliant Base64 wrapping | `java.util.Base64.getMimeEncoder` / `getMimeDecoder`, `Content-Disposition` and `Content-Transfer-Encoding` headers, boundary-collision check | HTTP form uploads or mail content assembled from dynamic parts (names, count or content from the payload) |
| [groovy-dynamic-sftp-filename.groovy](groovy-dynamic-sftp-filename.groovy) | Produces a compliant, sanitised target file name (prefix + business key + UTC timestamp + extension) that stays identical across retries | `CamelFileName` receiver header, `java.time` with explicit UTC (`TimeZone.setDefault` never used), extension whitelist, `CamelSplitIndex` | Before an SFTP/FTP receiver step whose file name must be business-meaningful, unique per part and reconcilable |
| [groovy-payload-size-guard.groovy](groovy-payload-size-guard.groovy) | Measures the body while reading it in bounded blocks, rejects oversized messages with a clear exception or a Router flag, and publishes a searchable status | Bounded `InputStream` read with early break, `ByteArrayOutputStream` capped by the limit, `Content-Length` as a hint only, `p_payloadTooLarge` | First step after the sender adapter of any flow that accepts files or bulk payloads; complements the HTTP sender *Body Size* parameter |
| [groovy-value-mapping-lookup.groovy](groovy-value-mapping-lookup.groovy) | Code-list / value-mapping lookup kept as maintainable data rows, forward and reverse, with a mandatory default branch and searchable unmapped-code evidence | `LinkedHashMap`, `containsKey` based hit detection (never truthiness), batch mode over a `List`, MPL custom header properties | Small code translations between two systems, and any mapping where unmapped codes must be visible in Monitor |
| [groovy-mpl-logging-custom-status.groovy](groovy-mpl-logging-custom-status.groovy) | Sets searchable custom header properties and a custom MPL status, and attaches the payload only when a debug flag is on | `messageLogFactory.getMessageLog`, `addCustomHeaderProperty`, `addAttachmentAsString`, `SAP_MessageProcessingLogCustomStatus`, null-safe `MessageLog` | End of a successful flow and inside the Exception Subprocess, to make messages searchable and statuses meaningful |
| [groovy-stax-xml-stream.groovy](groovy-stax-xml-stream.groovy) | Parses and rewrites multi-hundred-megabyte XML with constant memory instead of a DOM in heap, handing the result over as a stream backed by a temporary file that is deleted when the consumer closes it | StAX (`XMLInputFactory`, `XMLStreamReader`, `XMLStreamWriter`), XXE hardening via factory properties, file-backed body handoff | XML payloads above roughly 20 MB, mass IDoc/catalog feeds, any file where `XmlParser`/`XmlSlurper` would exhaust the heap; check the streaming capability notes in [references/performance-and-sizing.md](../references/performance-and-sizing.md) first |
| [groovy-json-stream-transform.groovy](groovy-json-stream-transform.groovy) | Reads and modifies a small/medium JSON payload through a Reader instead of a String, and extracts business keys | `message.getBody(Reader)`, `JsonSlurper`/`JsonOutput` on a Reader | JSON payloads of a few MB where a full tree is acceptable; for larger documents use the Jackson streaming example |
| [groovy-csrf-cookie-handler.groovy](groovy-csrf-cookie-handler.groovy) | Extracts the CSRF token and session cookie from a fetch call and prepares them for the following write request | Header handling for `x-csrf-token` and `Set-Cookie` (single value or list), exchange properties for the next call | SAP S/4HANA or SAP Gateway OData calls that require a CSRF token and a session cookie pair |

---

## 2. Common conventions

| Convention | Detail |
| :--- | :--- |
| Entry point | `def Message processData(Message message)` with `import com.sap.gateway.ip.core.customdev.util.Message`. Never rename it, never add parameters. |
| Input contract | Configuration and cross-step state travel through exchange properties named `p_*`, populated by a **Content Modifier** placed before the script. Externalized parameters (`{{param}}`) are bound in the Content Modifier, because a script cannot resolve placeholders itself. |
| Headers vs. properties | Headers are used only for protocol-bound values (for example `CamelFileName`, `Content-Type`). Business keys, flags and counts stay in exchange properties, so they are never forwarded to a receiver by accident. |
| Safety | Null checks before use, `try`/`finally` around every stream, no `static` mutable state, no Groovy binding variables, no `Eval()`, no `TimeZone.setDefault`, no `XmlSlurper.parseText(String)`. |
| Observability | Where a result matters operationally, the script sets `SAP_MessageProcessingLogCustomStatus` (max 40 characters) and adds short, searchable custom header properties. |
| Sizes | Every numeric threshold in the examples is an externalized local policy default, not a documented SAP limit. Review it against your tenant's quotas before production use. |

---

## 3. Disclaimer

All scripts follow the CPI script API signature and are provided **as-is**, to be **reviewed and adapted
before production use**. They were compiled and executed against a local harness that stands in for the CPI
`Message` and `MessageLog` interfaces (the JVM/Groovy toolchain used for that check is not part of this
repository). That proves the scripts are syntactically valid and that their logic behaves as documented — it
does **not** prove behaviour against real adapters, tenant quotas or script-runtime imports. Check each header
comment for the exact wiring, the size assumptions and the runtime-support notes (for example the Jackson
import), and adapt the property names to your naming standard.

> ⚠️ **Never hardcode credentials, hosts or tenant URLs in a script step.** Use externalized parameters and
> Security Material artifacts on the receiver adapter, and keep secrets out of headers and properties where
> tracing can expose them in clear text.
