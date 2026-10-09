# SAP Cloud Integration — Mapping & Transformation Guide

This guide is the reference for converting and mapping message content inside an integration flow: which transformation
technology to use for a given rule, how that choice affects performance, memory and reviewability, and which converter
and parser pitfalls destroy payloads in production. For scripting mechanics see
[references/groovy-best-practices.md](groovy-best-practices.md), for splitter/aggregator routing around transformations
see [references/design-guidelines.md](design-guidelines.md), and for handling a mapping failure see
[references/error-handling-and-monitoring.md](error-handling-and-monitoring.md).

---

## 1. Choosing the right transformation technology

| Technology | Best for | Avoid when | Maintainability | Performance | Reviewability | Testability |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Message Mapping (graphical)** | Field-to-field maps between two XML structures: 1:1 copies, simple conditions, a small number of value lookups. Handover to functional consultants. | Rules are algorithmic (regex, loops, date arithmetic), the same rule repeats across many interfaces, or the target has deep recursive structures. | Good while the rule set is flat; degrades quickly with many context changes and cross-node dependencies. Graphical artifacts diff badly in Git. | Not a streaming step: source and target trees are held in memory. Fine for small/medium payloads, wrong for large ones. | Excellent for business reviewers, weak for logic: a reviewer cannot see *why* a value was produced. | Manual inspection and sample payloads are easy; automated regression requires an external harness. |
| **XSLT mapping** | Structural rewrites: grouping, flattening, recursion, named-template reuse, sorting/grouping keys, very large field counts. | A trivial 1:1 copy (graphical is faster to build and review) or the team cannot maintain XSLT. Never for orchestration or external calls. | Excellent when the team owns XSLT: plain text, diffable, reviewable in a pull request. | Better than graphical mapping for structural work; still a whole-document operation, not a streaming step for the transform itself. | Good for an engineer reading the stylesheet; requires XSLT literacy. | Strong: stylesheets can be unit-tested outside CPI with sample payloads. |
| **Groovy script** | Algorithmic logic the declarative tools express badly: custom parsing, loops, aggregation decisions, and transformations that need the current header/exchange property values. | A standard step, converter or mapping already expresses the rule. SAP documents standard steps as the preferred option; custom scripts are your own maintenance responsibility and only the officially supported script APIs are guaranteed. | Owned entirely by the integration team; readability depends on discipline. Highest long-term cost of all options. | Best possible for streaming (event-based XML and JSON parsers) and worst possible when written carelessly — see §4. | Good if short and pure; bad if it becomes an application. | Strong: runnable in an external IDE with fixtures. See [references/groovy-best-practices.md](groovy-best-practices.md). |
| **Standard converters** (CSV↔XML, XML↔JSON) | Pure syntax bridging: the payload is already semantically correct and only its serialization must change. | Any semantic change is required (renaming, code mapping, filtering, enrichment). Chain a mapping *after* the converter instead of scripting it. | High: configuration, not code. | JSON↔XML can stream; CSV↔XML and EDI↔XML cannot — see §5 and §7. | High: the step name states exactly what happens. | High, provided the contract tests cover the constructs listed in §5. |
| **Content Modifier + XPath** | Reading or writing a single value: a routing key, a correlation ID, one element or attribute into a header/property, a constant. | Reshaping the document. It produces no target structure, no validation, and moves mapping logic into step configuration where it is invisible to a reviewer of the interface contract. | Deceptively cheap for one field, expensive once five steps each extract a different field. | XPath is evaluated against the message in the pipeline; keep it for small/medium payloads, not as a substitute for a streaming parse. | Low: the rule is buried in step configuration. | Low: nothing to test except the whole flow. |

**Default preference:** use the simplest tool that expresses the rule, and escalate only when the simpler tool demonstrably
cannot. In practice that means: Content Modifier for a single value → standard converter for syntax → Message Mapping for
a readable field map → XSLT for structural work → Groovy only for real algorithms or streaming. SAP's own design guidance
prefers standard integration flow steps (splitting, encoding, conversions, mappings, XPath in a Content Modifier) over
scripts; choosing a script is a deliberate acceptance of maintenance and support responsibility.

---

## 2. Message Mapping in depth

### 2.1 When graphical mapping is the right choice

Graphical mapping is right when a functional reviewer should be able to read the rule set from the picture, and when the
target structure is a per-field projection of the source:

- target field depends on one source field, or on a small boolean decision over a few fields;
- one context level, no joins across sibling parent nodes;
- fewer than roughly a hundred target fields, so the canvas stays readable.

It becomes unmaintainable when: the same rule is drawn again in every interface; conditional logic nests more than two
levels deep; string handling (padding, splitting, reformatting) dominates; joins multiply the target; or the mapping has
so many context changes that no reviewer can reconstruct the cardinality. At that point the honest options are XSLT (for
structure) or Groovy (for algorithms) — not a bigger canvas.

### 2.2 Standard functions, node functions, and the queue model

Two categories of function matter, and they are not interchangeable:

| Category | What it operates on | Effect |
| :--- | :--- | :--- |
| **Standard functions** | Input values | Produce an output value from one or more inputs: equality checks, concatenation, substring, trimming, date and arithmetic conversion. They never change how many nodes exist. |
| **Node functions** | The queue and structure | Decide how many output nodes are produced and how input values are grouped, ordered or collapsed. Documented examples include `splitByValue`, `removeContexts` and `useOneAsMany`; `createIf` produces a target node conditionally and `mapWithDefault` guarantees a value when the source is empty. |

Function inventories differ between releases and tenants. Confirm a function exists in the palette of your release before
naming it in a design document or a handover; where you cannot confirm it, describe the required behaviour in prose.

**Context handling vs. queue handling.** The mapping runtime keeps, for every node, a queue of the values produced for it.
*Context handling* is the explicit statement that all values below a given node belong to one instance of the parent;
*queue handling* is what node functions do to those values. The single most important rule follows from this:

> 💡 **One queue per parent node.** If you read several child nodes without introducing a context change per parent
> instance, all values of that child land in *one* queue regardless of which parent they came from. The runtime then
> combines them positionally or repeats them, and the symptom is duplicated or cross-mixed target nodes — data that still
> looks syntactically valid, which is why it survives testing. Introduce one context per parent occurrence, and keep the
> queues you create to the minimum the rule needs.

### 2.3 Headers and exchange properties inside mappings

A mapping is defined over the message payload; framework headers and exchange properties are not part of the source
structure. Do not assume they are reachable from a mapping expression or from a user-defined function — a user-defined
function receives only the argument values passed into it, not the message context.

Two portable patterns:

1. **Inject before mapping.** Use a Content Modifier before the mapping step to write the required header/property value
   into the payload (for example as an element of a small envelope), so the mapping consumes it like any other field.
   This keeps the mapping a pure function of its input and makes the value visible in a trace.
2. **Enrich before mapping.** If the value comes from another system, resolve it with a Content Enricher step first, so
   the lookup is a visible step with its own error handling rather than a hidden call inside the transformation.

Some releases expose header/property values to the mapping editor through expression-based references. Treat this as
release-dependent configuration: verify it in your tenant, document it in the interface specification, and keep the two
patterns above as the fallback that works everywhere.

### 2.4 User-defined functions (Groovy/Java)

User-defined functions are the extension point of the mapping editor, and the usual source of mapping defects. Rules:

- **Pure** — no I/O: no HTTP call, no file access, no JMS, no data store, no clock-dependent branch. A lookup inside a
  UDF hides an external dependency in a field mapping and cannot be simulated or retried meaningfully. Use a Content
  Enricher step instead.
- **Deterministic** — same input, same output. Anything else makes the mapping untestable.
- **Small** — one responsibility per function. A UDF is invoked once per value; a heavyweight function inside a
  1..unbounded multiplication multiplies its cost by the number of combinations.
- **Stateless** — no static mutable fields. Mapping execution is multi-threaded and script state is shared; static caches
  leak data across messages and can end in `OutOfMemoryError`.
- **Null-safe** — inputs may be empty strings or present-but-empty nodes; decide explicitly what an empty input produces.

### 2.5 Common defects

| Defect | Symptom | Fix |
| :--- | :--- | :--- |
| Missing context change | Values from different parent instances mixed or cross-joined in the output. | Add one context change per parent instance and verify cardinality with a two-parent test payload (§2.2). |
| Duplicated target nodes after a join / `useOneAsMany`-style multiplication | Target has *n×m* entries where *n* was expected, often only with multi-line input. | Model the expected cardinality first, then check it against a payload with at least two parent nodes. |
| Unbounded 1..unbounded multiplication | Runtime and memory explode with growing input; regression appears as a suddenly slow interface. | Bound the structure: split the message before mapping, or move the multiplication into an explicit split/join with a documented maximum. |
| Silently unmapped mandatory target field | Target element missing or empty; the receiver rejects the message days later. | Map every mandatory target node or set a default explicitly; validate the *result* against the target schema before sending (§9). |
| Expensive work inside a UDF | Throughput collapses under load; timeouts at the receiver. | Move lookups to a Content Enricher, keep UDFs to value-level arithmetic and string work. |
| Mapping used as a validator | The mapping "cleans" bad input by defaulting everything, so real data errors are never surfaced. | Validate upstream (schema validation, mandatory-field checks) and let the mapping fail on genuinely invalid input. |

---

## 3. XSLT in CPI

### 3.1 When XSLT beats graphical mapping

| Task | Prefer XSLT | Why |
| :--- | :--- | :--- |
| Reordering, grouping, flattening a hierarchy | Yes | Expressed in a few templates; in the graphical editor the same work becomes a web of context changes. |
| Recursion over an arbitrary-depth structure | Yes | Graphical mapping cannot express recursion. |
| Reusable transformation fragments across interfaces | Yes | Named templates and includes are reused as text; graphical mappings are copied. |
| Sorting, deduplication, key-based grouping within the target | Yes | Declarative and reviewable. |
| Hundreds of near-identical field copies | Yes | Text scales; a canvas does not. |
| Two-field rename inside an otherwise trivial flow | No | Graphical mapping is faster to build and to review. |

### 3.2 Headers and exchange properties from XSLT

The XSLT mapping step transforms the message body. Headers and exchange properties are not automatically part of the
source document. Where the step exposes them to the stylesheet (typically as parameters), bind them explicitly and treat
the parameter names as release-dependent configuration: declare them in the stylesheet, document them in the interface
specification, and test the binding — an unbound parameter is silently an empty value in XSLT. When in doubt, inject the
value into the payload with a Content Modifier before the XSLT step, exactly as described in §2.3.

### 3.3 Stylesheet rules

- **No environment-specific values.** A stylesheet must be a pure function of its input: no URLs, host names, tenant
  identifiers, credential aliases, party identifiers or fixed file paths inside the XSLT. Environment-specific values
  belong to step configuration, externalized parameters and adapters. A stylesheet with a hardcoded endpoint cannot be
  transported between tenants and silently points at the wrong system after a copy.
- **No I/O and no side effects.** A stylesheet that needs another system's data is the wrong design; enrich first.
- **Compact output for large payloads.** Do not generate pretty-printed output. Set the output method to XML with
  `indent="no"` and avoid `xsl:output` settings that reformat the whole document:

  ```xml
  <xsl:output method="xml" indent="no" omit-xml-declaration="no" encoding="UTF-8"/>
  ```

  Indentation on a large document inflates it with whitespace text nodes, increases heap and network cost, and changes
  the payload the receiver compares against. If a human needs readability, log a sample in the message processing log
  instead of shipping pretty output.
- **Check the XSLT version your tenant supports** before using 2.0/3.0-only constructs (multiple `xsl:result-document`,
  `xsl:function`, higher-order constructs). Version support is release-dependent; a stylesheet that only works on one
  release is a transport risk.
- **Namespace discipline.** Declare namespaces once at the stylesheet root and reference them consistently. Namespace
  mismatches are the most common cause of a stylesheet that "returns nothing".

---

## 4. Groovy-based transformations

### 4.1 Parser choice

SAP documents `XmlSlurper` for read-only parsing and `XmlParser` for parsing with in-place manipulation.

| Approach | Use for | Notes |
| :--- | :--- | :--- |
| `XmlSlurper` | Read-only traversal, value extraction, building new output | Lazy, lowest overhead of the tree-based parsers. Parse from a `Reader`. |
| `XmlParser` | In-place node addition/removal on a small document | Builds the full document tree; only for small payloads. |
| StAX (`XMLStreamReader`/`XMLStreamWriter`) | Large documents, any size | Event-by-event, constant memory. Production template: [examples/groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy). |

### 4.2 Rules

1. **Never `XmlSlurper.parseText(String)`** on a payload. The string conversion costs additional memory before parsing
   even starts. Parse from the message body as a `Reader`.
2. **Never load a large payload into a `String`.** `message.getBody(java.lang.String)` on a multi-megabyte payload
   becomes a UTF-16 string in heap and is a direct route to `OutOfMemoryError` under concurrent load.
3. **Stream the large ones.** For very large XML use StAX; for very large JSON use a streaming JSON parser. See
   [examples/groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy) and
   [examples/groovy-json-stream-transform.groovy](../examples/groovy-json-stream-transform.groovy).
4. **Compact output.** Do not pretty-print large XML or JSON; compact payloads are smaller to serialize, transmit and
   persist.
5. **Compose with `StringBuilder`/`StringBuffer`**, never with repeated `String` concatenation in a loop
   (`StringBuilder` is faster and not thread-safe; `StringBuffer` is synchronized).
6. **No binding variables.** `body = '…'` lives until undeploy/redeploy, is shared between executions, is not thread-safe
   and can cause `OutOfMemoryError`. Use typed locals or `def` locals inside the method.
7. **Treat input XML as untrusted.** Keep DTD and external-entity resolution disabled unless the interface genuinely
   requires a DTD; entity expansion is an availability risk on a shared tenant.

### 4.3 Reference snippet: read a business key, emit a compact body

Correct, copy-pasteable pattern for a small or medium XML payload (a few megabytes at most).

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    // Small/medium payload only: XmlSlurper builds a tree. For large payloads use StAX.
    Reader reader = message.getBody(java.io.Reader)
    if (reader == null) {
        return message
    }

    // Read-only parse from the Reader. Never XmlSlurper.parseText(String).
    // XmlSlurper lives in groovy.util and is default-imported - no import needed.
    def input = new XmlSlurper().parse(reader)

    // text() returns an empty String when the element is absent - no null check needed.
    String orderId = input.Header.OrderId.text()
    if (!orderId) {
        orderId = input.Header.MessageId.text()
    }
    if (orderId) {
        message.setProperty("p_orderId", orderId)
    }

    // MarkupBuilder escapes text correctly and does not indent by default => compact output.
    // Quoted element names keep the calls unambiguous inside the builder closure.
    StringWriter writer = new StringWriter()
    def xml = new MarkupBuilder(writer)
    xml.OrderConfirmation {
        'OrderId'(orderId)
        'Status'(input.Body.Status.text())
    }

    message.setBody(writer.toString())
    return message
}
```

> ⚠️ **Size assumption.** This snippet is for payloads that fit in heap comfortably. If the interface can receive tens or
> hundreds of megabytes, replace the whole transformation with StAX and never materialise the document.

---

## 5. XML/JSON conversion rules and pitfalls

The JSON to XML and XML to JSON converters are configuration steps for *syntax* conversion. They are not mapping steps:
they will not rename a field, translate a code, drop a node or enforce a mandatory element.

| Construct | What conversion does | Consequence for the contract |
| :--- | :--- | :--- |
| **XML attributes** | An attribute has no JSON equivalent, so it must be projected into a JSON property. The projection uses a prefix/naming convention. | The prefix is implementation- and configuration-dependent, and it is visible to the receiver. Pin the exact expectation in the interface specification and assert it in a contract test — never let the receiver "discover" it in production. |
| **JSON arrays** | An array has no XML equivalent; repeated elements represent it. | A JSON array with exactly one element can come back as an object rather than an array (or vice versa) on the return trip. Normalise arrays explicitly in the target contract, or agree with the receiver that a single-element array is always an array. |
| **XML namespaces** | JSON has no namespace concept, so namespace information must be encoded in names or handled by configuration. | Namespace prefixes can be lost, changed or made part of the field name. Do not rely on a prefix surviving a round trip; agree on the wire format with the receiver. |
| **Mixed content** (text plus child elements in one node) | There is no faithful JSON representation of interleaved text and elements. | Mixed content is effectively unsupported: restructure the source document before converting, or convert with a mapping that defines an explicit order. |
| **Root element** | XML requires exactly one root; JSON needs a designated root name when converting to XML. | The root name the receiver expects must be configured deliberately, not left to a default. |
| **Numbers, booleans, null** | JSON scalars become text in XML; XML text has no type. | Type information is lost in both directions. Do not let a receiver depend on numeric type inference across an XML hop. |
| **Ordering** | JSON object member order is not semantically meaningful; XML element order is. | Do not build a receiver contract that depends on object member order. |

### 5.1 Streaming support of the converters

| Converter | Streaming | Practical consequence |
| :--- | :--- | :--- |
| JSON to XML | Yes (configurable) | Can be used on large payloads if streaming is enabled; the temporary file in the tenant file system bounds the practical maximum size. |
| XML to JSON | Yes (configurable) | Same as above. |
| CSV to XML / XML to CSV | No | Large flat files must be chunked *before* the converter step — see §7. |
| EDI to XML / XML to EDI | No | Keep EDI interchange sizes bounded at the source or split upstream. |
| Message Mapping, XML Schema Validation, Filter, Message Digest | No | Split a large payload before these steps; keep them off the hot path of a large message. |

Where a non-streaming step cannot be avoided, split the message into chunks before that step. Streaming steps use a
temporary file in the tenant file system, and temporary storage size limits the maximum file size that can be
transferred — check the temporary storage inspection in the operations area before designing for very large files.

> ⚠️ **Never round-trip a payload through XML→JSON→XML as a "normalisation" step.** It is lossy by construction: attribute
> prefixes, namespace prefixes, single-element arrays, mixed content, typing and ordering do not survive. A round trip
> produces a payload that looks normalised, passes a smoke test, and differs from the original in exactly the details your
> receiver depends on. If normalisation is required, define one canonical XML structure and map *into* it once.

---

## 6. Canonical data models and mapping governance

### 6.1 When a canonical model pays off

| Situation | Canonical model | Verdict |
| :--- | :--- | :--- |
| Hub architecture: *n* senders × *m* receivers, several of them sharing a business object | Yes | *n + m* mappings instead of *n × m*; the model is the contract that lets systems change independently. |
| One sender, one receiver, one direction | No | A canonical layer adds a schema, a governance process and a second transformation for nothing. Map directly. |
| Two systems exchanging a stable, well-documented format over a long life | Usually no | The external standard already *is* the canonical model. |
| Party/partner onboarding with many similar partners | Yes, but partner-specific | Keep the canonical model thin and push partner specifics into the partner layer, not into the core model. |

Over-engineering shows up as a canonical schema nobody owns, with fields added for one interface and never reused.

### 6.2 Where mappings should live

| Option | Use when | Trade-off |
| :--- | :--- | :--- |
| **Mapping artifact referenced by the flow step** (message mapping or XSLT resource) | The mapping is reused by more than one flow, or is owned by a different team. | One place to change; requires transport discipline so the artifact version and the flow version travel together. |
| **Mapping embedded as a flow step** | One flow, one use, no reuse expected. | Fast and local; guarantees duplication the moment a second flow needs the same rule. |
| **Script collection** | Reusable Groovy logic (parsing helpers, format utilities) shared across flows. | Excellent reuse, but the collection becomes a shared runtime dependency: version it and treat changes as breaking. |
| **Child flow via the ProcessDirect adapter** | A whole transformation/enrichment step, including its error handling, is reused. | Reuse at the level of behaviour, not of field rules; keeps the caller readable. See [references/design-guidelines.md](design-guidelines.md). |

Governance rules that prevent the classic mess: one owner per interface schema; the mapping artifact and the schema it
depends on are versioned together; a change to a canonical model requires an impact analysis over all flows referencing
it; and reusable mappings are never edited in place for a single consumer's special case — create a variant explicitly.

### 6.3 Versioning and ownership of interface schemas

- Every interface schema (source and target) has a named owner and an explicit version in its name or metadata.
- Breaking changes (removed or renamed mandatory fields, tightened cardinality, changed code lists) get a new version and
  a migration window; additive optional fields do not.
- The mapping artifact is part of the interface contract: version it with the flow, transport both together, and record
  which release/tenant the schema was verified against. A mapping validated only by inspection is not validated.

---

## 7. CSV, flat files and EDI

### 7.1 Flat-file pitfalls

| Pitfall | Symptom | Rule |
| :--- | :--- | :--- |
| Delimiter inside a quoted field | Columns shift by one from that row onward; the error appears far from the cause. | Confirm the quote character and the escape convention (doubled quote vs. backslash) with the producer; never infer them from a sample. |
| No header row, or a header row that changes | Column mapping breaks silently when the producer adds a column. | Prefer position-based parsing with a documented column contract, or validate the header row explicitly and fail fast. |
| Charset ambiguity | Accented characters corrupted (`Ã©` instead of `é`); payloads that look fine in one system and not in another. | Agree on the encoding in the interface contract, handle the byte-order mark explicitly, and use the documented `CamelCharsetName` property to override the character encoding where the sender does not preserve it. |
| Line endings and the trailing separator | A field value contains `LF` while rows end with `CRLF`; parsers disagree on the row count, or one extra empty record appears at the end. | Normalise line endings explicitly, reject embedded newlines inside unquoted fields, and test with a file that ends with a newline. |
| Locale-dependent numbers and dates | `1.234,56` parsed as `1.234`; dates interpreted in the wrong format. | Define number and date formats as part of the schema, never as a locale setting. |

### 7.2 Chunking very large flat files

CSV converters do not stream. A multi-hundred-megabyte CSV pushed through CSV to XML will hold the payload and its XML
representation in memory at the same time — the most common self-inflicted `OutOfMemoryError` in flat-file interfaces.
Split *before* converting:

1. **Split at the source** where possible (the sender writes files of a bounded size). Cheapest and most robust.
2. **Split on read** with a Groovy step that streams the file line by line from a `Reader`, forwards bounded chunks to a
   JMS queue (or a child flow via ProcessDirect) and never materialises the whole file. Note that the splitter steps in
   CPI operate on XML structure, so they cannot chunk a raw flat file.
3. **Convert inside the chunk**, so each CSV to XML conversion sees a small input, then map and send per chunk.

Chunk size is a trade-off between receiver round-trips and per-chunk memory. Fix it from the receiver's documented limit,
not from the file you happen to have in test.

### 7.3 EDI and IDoc conversion boundaries

- The EDI to XML and XML to EDI converters are the syntax layer: they turn an interchange into structured XML and back.
  They do not perform the semantic mapping, so a mapping step still sits behind them.
- Neither the EDI converters nor the IDoc adapter stream, and the IDoc splitter does not stream either. Large IDoc
  packages and large interchanges are therefore memory-bound: bound them at the source, or split and decouple through a
  JMS queue before processing. Recheck the streaming matrix for the exact adapters and steps in your flow before sizing.
- Interchange-level concerns — partner agreement, envelopes, control numbers, acknowledgements, test/production
  indicator — are handled in the trading-partner layer of Integration Suite, not inside the iFlow. The iFlow's boundary
  starts where the interchange has already been identified for a partner and a document type. Validate inbound EDI
  against the agreed guideline before mapping: a syntactically valid interchange with a semantically wrong qualifier is a
  business error, and it must surface as one.
- Machine-readable interface descriptions and mapping specifications (type systems, message implementation guidelines,
  mapping guidelines) belong to SAP's Integration Advisor capability, which generates mapping artifacts that are then
  deployed and used from the iFlow. Keep that generated content as the source of truth for partner-specific structures;
  do not re-implement it as hand-drawn graphical mappings that will drift the moment the guideline changes.

---

## 8. Anti-patterns

| # | Symptom | Root cause | Fix |
| :--- | :--- | :--- | :--- |
| 1 | The same field rules exist in three iFlows and diverge over time. | Mapping logic copied per interface instead of extracted. | Extract the mapping into one mapping artifact (or a child flow via ProcessDirect) and reference it from all three. |
| 2 | A Content Modifier contains a dozen XPath expressions that together rebuild the payload. | Using value extraction as a mapping tool. | Replace with a Message Mapping or XSLT that produces an explicit target structure; keep the Content Modifier for single values. |
| 3 | `OutOfMemoryError` on a 200 MB XML interface written in Groovy. | `XmlParser`/`XmlSlurper` (or `getBody(String)`) on a large payload. | Rewrite with StAX, or split the message before transforming: [examples/groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy). |
| 4 | A large CSV file kills the tenant during conversion. | CSV to XML does not stream. | Bound the file size at the source, or chunk on read and convert per chunk (§7.2). |
| 5 | A mandatory target field arrives empty in production; the receiver rejects the message. | The field was never mapped, and nothing validated the result. | Map or default every mandatory target node, and validate the transformed output against the target schema before sending. |
| 6 | A payload is "normalised" through XML→JSON→XML and the receiver reports differences. | Round-tripping is lossy (attributes, namespaces, arrays, typing). | Define one canonical XML structure and map into it once. |
| 7 | Throughput drops and timeouts appear under load only. | A user-defined function performs an external lookup per value, or an unbounded 1..unbounded multiplication expands the structure. | Move lookups into a Content Enricher step; bound the cardinality and split the message before mapping. |
| 8 | The payload is valid but twice as large as necessary on the wire. | Pretty-printed XML/JSON output on a large message. | Emit compact output (`indent="no"` in XSLT, compact writers in Groovy, no pretty-print option enabled). |
| 9 | The same stylesheet works in DEV and silently writes to the wrong endpoint in PROD. | Hardcoded environment values inside the XSLT. | Remove all environment values from stylesheets; keep them in step configuration and externalized parameters. |
| 10 | A one-element JSON array breaks the receiver after a converter step. | XML/JSON array semantics: a single repeated element is indistinguishable from a single value. | Pin array semantics in the interface contract and cover it with a one-element test case. |
| 11 | Incident analysis cannot tell why a value was produced. | Logic hidden inside a Groovy script or a UDF with no logging and no reviewable artifact. | Keep scripts short and pure, log key decisions through the message processing log, and prefer a reviewable mapping artifact for rule-based logic. |
| 12 | Every change to a mapping requires a full regression of all interfaces. | Graphical mapping with many context changes and no documented cardinality. | Document the expected cardinality per node, add it to the interface specification, and cover it with a multi-parent test payload. |

---

## 9. Mapping review checklist

Run this against every new or changed mapping before transport:

- [ ] Is the simplest technology used that expresses the rule (Content Modifier for a single value, converter for syntax, mapping for a field map, XSLT for structure, script only for real algorithms)?
- [ ] Is the expected cardinality of every target node documented, and verified with at least one payload containing **two** parent instances?
- [ ] Are all context changes deliberate, with one queue per parent node — not accidental side effects of a shared source node?
- [ ] Is every mandatory target field either mapped or given an explicit default, and is the transformed output validated against the target schema?
- [ ] Are source and target schemas versioned, and is the owner recorded in the interface specification?
- [ ] Does the transformation avoid all mandatory-field assumptions that depend on sample data only?
- [ ] Is the payload size of the interface known, and is the chosen technology compatible with it (no CSV converter, no graphical mapping, no `getBody(String)` on a large payload)?
- [ ] Is output compact for large payloads (no pretty-printing, no unnecessary indentation)?
- [ ] Is the mapping free of environment-specific values (hosts, tenant identifiers, aliases, paths) and of I/O inside user-defined functions?
- [ ] Are strings composed with `StringBuilder`/`StringBuffer`, and is internal state local (no binding variables, no static mutable state)?
- [ ] Are the converter-specific constructs (attributes, single-element arrays, namespaces, mixed content) covered by an explicit contract test where a converter is used?
- [ ] Can a reviewer understand the rule from the artifact alone, and would a change to it be visible in a diff and in a code review?

---

## Further Reading

- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [General Scripting Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/general-scripting-guidelines)
- [Headers and Exchange Properties Provided by the Integration Framework](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/headers-and-exchange-properties-provided-by-the-integration-framework)
- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Integration Flow Design Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/integration-flow-design-guidelines)
