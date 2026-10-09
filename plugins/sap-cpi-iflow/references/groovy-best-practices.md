# SAP Cloud Integration — Groovy Scripting Best Practices

Groovy is the most powerful extension point in SAP Cloud Integration and the most common cause of
`java.lang.OutOfMemoryError`, tenant instability and unmaintainable flows. This guide defines when to write
a script, how to write one that survives concurrency and large payloads, and how to review one.

---

## 1. When to use a script — and when not to

SAP's position is explicit: **prefer standard integration flow steps over scripts**. Standard steps
(splitting, encoding, conversions, mappings, XPath in a Content Modifier) are reviewable, supported and
maintained by SAP. SAP guarantees only the officially supported script APIs; every custom script is a
maintenance liability owned by the project.

| Use a script when… | Use a standard step when… |
| :--- | :--- |
| The logic is genuinely procedural (loops, conditional assembly, lookups in maps) | A Message Mapping or XSLT expresses the rule declaratively |
| You need behaviour the modeler cannot express (custom validation, checksum, dynamic filename rules) | A Content Modifier with XPath can set the same value |
| You must stream a very large payload that no standard step can handle | The CSV/XML/JSON converters or splitters do the job |
| You need controlled MPL instrumentation (custom status, search keys) | The value is already a header or property |
| You need to influence the flow technically (set `CamelFileName`, adjust the HTTP response code) | The adapter can be configured to do it |

> ⚠️ **Anti-pattern:** a script that "just checks one field" when a Router or a Filter step would be
> visible in the diagram. Scripts are invisible logic: a reviewer looking at the model sees a box called
> `SCR_Check` and learns nothing.

---

## 2. Script runtime facts

| Item | Value |
| :--- | :--- |
| Script languages | Groovy and JavaScript |
| Groovy runtime | 2.4.21 for Groovy script step version 1.1; **4.0.29** for version 2.0 |
| JavaScript engine | Rhino 1.7.14 (ECMAScript standard support) |
| Java libraries available | Java 8 standard libraries |
| Supported APIs | SDK Groovy/Script APIs and native Groovy APIs. Direct use of open source classes is **not supported** and may break on runtime upgrades |
| Default entry function | `processData` — you can specify another function name without arguments in the *Script Function* field |
| Static analysis | CodeNarc **3.4.0** |

Practical consequences:
- **Upgrade your Groovy script steps to version 2.0** and use the editor's *Problems* view / "Fix Script
  Incompatibilities" support to be ready for runtime upgrades.
- Avoid depending on a specific version of an open source library. If you need XML/JSON parsing, prefer the
  Script APIs or native Groovy classes (`XmlSlurper`, `XmlParser`, `JsonSlurper`, `JsonOutput`).
- Do not use `Eval()` — generated classes are never unloaded and will eventually exhaust memory
  (see SAP Note 3246624). Never call `TimeZone.setDefault` — it changes the JVM default time zone and can
  break database connectivity (see SAP Note 3289679).

---

## 3. Message body handling and memory

### The rule
Never materialise a large payload as a `String`. `message.getBody(java.lang.String)` forces the entire
payload into the JVM heap; under concurrent load this is the fastest route to a tenant-wide outage.

### The three access modes

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // ✅ Stream-based access — no full copy in heap
    InputStream inputStream = message.getBody(java.io.InputStream)
    Reader      reader      = message.getBody(java.io.Reader)

    // ⚠️ Heap-based access — acceptable ONLY for small, known-bounded payloads
    //    (e.g. a few KB of XML, or building a short MPL attachment)
    // String body = message.getBody(java.lang.String)

    return message
}
```

### Decision table

| Payload size | Recommended handling |
| :--- | :--- |
| < 1 MB | `String` access is acceptable; still prefer `Reader` if it costs nothing |
| 1–20 MB | `Reader` for parsing, `InputStream` for copying; never build a second full copy |
| 20–200 MB | StAX / streaming parsers, streaming adapters, no XML Schema Validation, no message mapping |
| > 200 MB | Split/iterate first; then process each chunk. Verify the temporary storage limit of the tenant |

> 💡 A single 30 MB payload can become a 60 MB UTF-16 `String`, plus the copy produced by the transformation,
> plus the copy held by the outbound adapter — all per concurrent message. That multiplication, not the
> single message, is what kills the node.

---

## 4. Headers vs exchange properties

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // PROPERTIES — internal to the Camel exchange, never sent to the receiver
    //   use for: routing keys, flags, state, business identifiers
    message.setProperty("p_orderId", "100234")
    def orderId = message.getProperty("p_orderId")

    // HEADERS — propagated to outbound adapters by default
    //   use for: protocol metadata only (Content-Type, dynamic file name, HTTP overrides)
    message.setHeader("Content-Type", "application/json")

    return message
}
```

| | Exchange property | Header |
| :--- | :--- | :--- |
| Visibility | Inside the flow | Propagated to the receiver |
| Risk | None to the receiver contract | HTTP 431, protocol pollution, leakage via tracing |
| Use for | Internal state and business keys | Protocol metadata |
| Large values | Never | Never — use the claim-check pattern (Data Store) instead |

> ⚠️ **Never store credentials in headers or properties.** Tracing and MPL properties can expose them in
> clear text. Use security artifacts and channel-level authentication.

---

## 5. XML processing strategy

| Strategy | Payload size | Mutability | Memory | Use for |
| :--- | :--- | :--- | :--- | :--- |
| `XmlSlurper` | small / medium | read-only | low | Reading values, read-only validation. **Parse from a `Reader`** |
| `XmlParser` | small | read/write | high (full DOM) | In-place node manipulation |
| **StAX** (`XMLStreamReader` / `XMLStreamWriter`) | **any** | streamed | **constant** | Large XML, mass feeds, IDoc-scale payloads |

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlSlurper

def Message processData(Message message) {
    Reader reader = message.getBody(java.io.Reader)

    // ✅ parse(Object) — never parseText(String), which allocates an extra copy
    def root = new XmlSlurper().parse(reader)

    String orderId = root.Header.OrderNumber.text()
    if (orderId) {
        message.setProperty("p_orderId", orderId)
    }

    return message
}
```

> ⚠️ **XXE:** any script that parses XML is exposed to XML External Entity attacks. Either do not use XML
> parsing at all, or disable external entity and DTD processing on the parser factory (see the OWASP XXE
> Prevention Cheat Sheet). The StAX template in
> [examples/groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy) shows the factory
> configuration.

For the full large-payload template see
[examples/groovy-stax-xml-stream.groovy](../examples/groovy-stax-xml-stream.groovy), and for chunking a
huge flat file see [examples/groovy-csv-stream-reader.groovy](../examples/groovy-csv-stream-reader.groovy).

---

## 6. MPL logging from a script

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def messageLog = messageLogFactory.getMessageLog(message)

    // ALWAYS null-check: the log is not available in every context (e.g. local simulation)
    if (messageLog != null) {
        // 1. Searchable business keys (persisted, used by Monitor message search)
        String orderId = message.getProperty("p_orderId")
        if (orderId) {
            messageLog.addCustomHeaderProperty("OrderNumber", orderId)
        }

        // 2. Typed MPL property. Note: script-step properties are only visible
        //    in the MPL when the log level is Debug or Trace.
        messageLog.setStringProperty("Step", "VALIDATED")

        // 3. Attachments only when a debug flag is on — never unconditionally
        if (message.getProperty("p_enablePayloadLog") == "true") {
            messageLog.addAttachmentAsString("Debug_Payload", message.getBody(String), "text/xml")
        }
    }

    // 4. Custom status — the ONE property that is always worth setting.
    //    Max 40 alphanumeric characters; surfaced as the CustomStatus attribute of the MPL.
    message.setProperty("SAP_MessageProcessingLogCustomStatus", "ORDER_VALIDATED")

    return message
}
```

Rules:
- **Guard against null.** `getMessageLog` may return `null`.
- **Attachments cost storage.** Unconditional payload logging fills the MPL persistence quota and slows
  processing; drive it from an externalized parameter that is `false` in production by default.
- **Keep `setStringProperty` for short strings.** For structured or long content use
  `addAttachmentAsString(name, text, mediaType)`.
- **The custom status is limited to 40 alphanumeric characters** — define a status vocabulary per interface
  instead of improvising strings. See
  [monitoring-and-operations.md](monitoring-and-operations.md) for a recommended vocabulary.

---

## 7. Thread safety and resource management

Each script can run concurrently on multiple worker threads. Treat every field as shared.

```groovy
// ❌ CRITICAL: static mutable state is shared across messages and threads
class GlobalCache {
    static List<String> cachedIds = []
}

// ✅ SAFE: static immutable factories only
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLOutputFactory

class XmlHelper {
    static final XMLInputFactory  INPUT_FACTORY  = XMLInputFactory.newInstance()
    static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance()
}
```

Never use Groovy **binding variables** (assigning without `def` or a type, e.g. `body = '123'`). They live
until the flow is undeployed or redeployed, are shared between script executions, are not thread-safe, and
can hold large payloads in memory indefinitely. Always declare a local:

```groovy
def body = '123'        // ✅ local
String body = '123'     // ✅ local, typed
body = '123'            // ❌ binding variable — shared, long-lived, not thread-safe
```

Resource discipline:
- Close `InputStream`, `OutputStream`, `Reader`, `Writer`, `XMLStreamReader`, `XMLStreamWriter` in `finally`
  or with `withReader` / `withWriter` / `withStream`.
- Do not hold references to streams, connections or large collections in fields.
- Prefer `StringBuilder` over repeated string concatenation; use `StringBuffer` only when the object is
  genuinely shared between threads.

---

## 8. Code style rules (SAP general scripting guidelines)

| ✅ Do | ❌ Don't |
| :--- | :--- |
| Use `new XmlSlurper().parse(reader)` | `parseText(String)` — allocates an additional copy |
| Write compact output | Generate pretty-printed XML/JSON for large payloads |
| Use `StringBuilder` / `StringBuffer` | Concatenate strings in a loop |
| Declare locals (`def x`, `String x`) | Use binding variables (`x = …`) |
| Use SLF4J for logging | Build your own logging framework |
| Make logging switchable via log level or externalized parameters | Leave permanent Debug logging in production |
| Write explanatory comments | Leave the reviewer to reverse-engineer the intent |
| Use Script APIs and native Groovy | Import arbitrary open source classes (unsupported) |
| Keep simple transformations in the editor | Develop complex logic in the built-in editor instead of an IDE |
| Use simulation tools / an IDE to test | Deploy an iFlow just to test a script |
| Null-check everything that can be null | Assume the message, body, header or log is present |

---

## 9. Reuse: script collections

Instead of copying a script into ten flows, put it in a **Script Collection** and reference it from the
script steps of the same integration package. Documented benefits: reuse, reduced maintenance effort,
reduced deployed content size and memory usage, no duplicates.

Note that mapping user-defined functions (UDFs) cannot yet be provided through a script collection, so a UDF
still lives locally in the mapping.

---

## 10. Static analysis and review

- Run **CodeNarc 3.4.0** on your Groovy sources to catch defects, bad practices and inconsistencies before
  the code reaches the tenant. It is cheap and it catches the classic mistakes in section 8.
- Review every script against this checklist:
  - [ ] No `getBody(String)` on a potentially large payload (or the size assumption is documented)
  - [ ] All resources closed in `finally` / `with*`
  - [ ] No static mutable state, no binding variables
  - [ ] Null-safe on message, body, headers, properties, `messageLogFactory`
  - [ ] No hardcoded hosts, credentials, paths or magic business values
  - [ ] Logging switchable; no unconditional MPL attachments
  - [ ] Custom status set where the step is operationally meaningful
  - [ ] Exceptions handled or deliberately propagated (never swallowed silently)
  - [ ] Works when executed twice with the same input (re-entrant)
  - [ ] Comments explain *why*, not *what*

---

## 11. Frequently observed defects

| Symptom | Root cause | Fix |
| :--- | :--- | :--- |
| `OutOfMemoryError` on large messages | `getBody(String)` or a DOM parser on a large payload | Stream with `InputStream`/`Reader` or StAX |
| Cross-message data leakage | `static` mutable field | Use locals; static only for immutable values |
| Memory grows until redeploy | Binding variable holding a payload | Declare a local variable |
| Flow works until two messages arrive at once | Non-thread-safe shared object | Make the script stateless |
| File handle leak / "too many open files" | Stream not closed on the exception path | Close in `finally` |
| MPL storage fills up | Unconditional attachments, permanent Trace level | Gate attachments behind a parameter; control log level |
| Script breaks after a runtime upgrade | Direct use of open source classes | Use Script APIs / native Groovy; upgrade to script step 2.0 |

---

## Further Reading

- [General Scripting Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/general-scripting-guidelines)
- [Add Information to the Message Processing Log](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/add-information-to-the-message-processing-log)
- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
- [CodeNarc](https://github.com/CodeNarc/CodeNarc)
