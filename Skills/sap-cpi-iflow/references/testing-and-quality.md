# SAP Cloud Integration — Testing & Quality Guide

Integration logic is rarely unit tested and almost never tested on its failure paths, so defects surface in
production at 03:00, found by someone who did not write the flow. This guide defines the quality gates for a
CPI iFlow — static analysis, script unit tests, simulation, integration and volume testing, test data,
environment strategy, go-live and hypercare — and complements [groovy-best-practices.md](groovy-best-practices.md)
and [error-handling-and-monitoring.md](error-handling-and-monitoring.md).

---

## 1. The quality problem

An iFlow has no compiler gate and usually no test suite. Three properties make it worse than ordinary code:
the logic is configuration rather than source, failure appears asynchronously in a system owned by another
team, and the environment (credentials, certificates, routes, receiver behaviour) exists only in production.

The test pyramid adapted to integration — cheapest first, each layer catching what the one below cannot:

| Layer | What it proves | Cost | Runs |
| :--- | :--- | :--- | :--- |
| Static analysis (CodeNarc) | No obvious script defects, no forbidden API usage | Seconds | Every commit, in CI |
| Script unit tests (JVM) | Script logic, null and malformed-input handling | Seconds | Every commit, in CI |
| Mapping tests | Field-to-field correctness, cardinality, mandatory fields | Minutes | Every mapping change |
| Flow simulation | Routing, splitter/aggregator behaviour, step sequence | Minutes | Every flow model change |
| Integration / E2E tests | Connectivity, credentials, real receiver contract | Hours | Before each transport |
| Production smoke tests | The deployment actually worked | Minutes | Right after go-live |

> ⚠️ **The layers are not interchangeable.** A green simulation says nothing about credentials, and a passing
> smoke test says nothing about the malformed-payload branch that will be hit next month.

---

## 2. Static analysis and review

### 2.1 Static analysis for Groovy scripts

Run **CodeNarc 3.4.0** in CI, not only on a developer machine. Rules that catch defects reaching production:

| Rule category | Defect caught in CPI scripts | Consequence if ignored |
| :--- | :--- | :--- |
| Unused imports, variables, private fields | Copy-paste remains from other scripts | Dead code nobody dares to delete |
| Empty catch blocks | Swallowed exceptions; the flow continues with a wrong payload | Silent data corruption |
| Catching `Throwable` / `Exception` too broadly | Errors turned into "handled" messages | Failures never reach monitoring |
| `printStackTrace`, `System.exit`, `println` | Console output instead of SLF4J; process-level behaviour in a shared runtime | Lost evidence, unstable workers |
| Direct `java.io` package access | Bypassing the streaming APIs of the framework | Memory and file-handle leaks |
| Cyclomatic complexity, nested block depth | Unmaintainable branching inside a script | Defects on the untested branch |

> 💡 **Rule names and rule sets belong to CodeNarc, not to SAP.** Pin the version and the rule set in the
> repository, so a build that passed yesterday still passes today.

### 2.2 Code review checklist for Groovy scripts

- [ ] Every API return value is null-checked before use (headers, properties, `messageLogFactory`)
- [ ] Streams and readers are closed (`try/finally` or `withReader`); no `InputStream` leaked to a later step
- [ ] No Groovy binding variables (`body = …`) — typed or `def` locals only
- [ ] No `static` mutable state, no caches that survive redeployment
- [ ] No hardcoded hosts, credentials, paths or schedules — externalized parameters `{{param}}` instead
- [ ] `getBody(String)` used only where the size assumption is stated and small
- [ ] Logging through SLF4J and switchable (log level or externalized parameter)
- [ ] No `Eval()`, no `TimeZone.setDefault`, no `XmlSlurper.parseText(String)`
- [ ] No credentials or personal data in headers, properties or log entries
- [ ] A standard integration flow step would not have solved the problem more cheaply

### 2.3 Design review

Script review is not enough: most incidents come from the flow model, not the script. Run the design review
in [checklists/iflow-design-review.md](../checklists/iflow-design-review.md) before the first transport.

---

## 3. Testing Groovy scripts outside the tenant

### 3.1 Make the script testable

1. **Logic lives in `processData`** — no work in field initializers, no assumptions beyond the documented state.
2. **Only the injected context is used** — the message and `messageLogFactory`; receiver clients and tenant
   configuration belong in adapter configuration, not in the script.
3. **Null handling is defensive** — `messageLogFactory.getMessageLog(message)` can return `null` (during local
   simulation, for example); no flow may fail because logging is unavailable.

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Size assumption: one order per message, << 1 MB (sender adapter Body Size limit).
    String payload = message.getBody(String.class)
    if (!payload?.trim()) {
        throw new IllegalArgumentException('ORDER_EMPTY_PAYLOAD')
    }
    String orderId
    try {
        // parse(Object) with a Reader, never parseText(String). XmlSlurper parses lazily, so the access
        // that forces parsing — and throws on malformed XML — must stay inside the try block.
        def order = new XmlSlurper(false, false).parse(new StringReader(payload))
        orderId = order.OrderId.text()?.trim()
    } catch (Exception e) {
        throw new IllegalArgumentException('ORDER_MALFORMED_PAYLOAD: ' + e.message, e)
    }
    if (!orderId) {
        throw new IllegalArgumentException('ORDER_ID_MISSING')
    }
    String sourceSystem = message.getHeader('SourceSystem', String.class) ?: 'UNKNOWN'
    message.setProperty('p_orderId', orderId)
    message.setProperty('p_sourceSystem', sourceSystem)
    message.setProperty('SAP_MessageProcessingLogCustomStatus', 'ORDER_VALIDATED')

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {        // null outside the tenant: never fail a flow because of logging
        messageLog.addCustomHeaderProperty('OrderId', orderId)
    }
    return message
}
```

### 3.2 A minimal `Message` test double

The real `com.sap.gateway.ip.core.customdev.util.Message` interface declares more methods than a unit test
needs. Groovy dispatches dynamically, so a plain test double covering the accessors your scripts call is enough.

```groovy
/** Test double for the CPI Message: not the SAP API, only the accessors the scripts use. */
class FakeMessage {
    private Object body
    private final Map<String, Object> headers = [:]
    private final Map<String, Object> properties = [:]

    FakeMessage(Object body = null) { this.body = body }

    Object getBody() { return body }
    Object getBody(Class type) {
        if (body == null || type == null || type.isInstance(body)) { return body }
        if (type == String) { return body.toString() }
        throw new IllegalArgumentException("FakeMessage cannot convert the body to ${type.name}")
    }
    void setBody(Object body) { this.body = body }

    Map<String, Object> getHeaders() { return headers }
    Object getHeader(String name) { return headers[name] }
    Object getHeader(String name, Class type) { return headers[name] }
    void setHeader(String name, Object value) { headers[name] = value }

    Map<String, Object> getProperties() { return properties }
    Object getProperty(String name) { return properties[name] }
    Object getProperty(String name, Class type) { return properties[name] }
    void setProperty(String name, Object value) { properties[name] = value }
}
```

The script under test imports `com.sap.gateway.ip.core.customdev.util.Message`, so that symbol must be on the test
classpath. If the SDK classes are unavailable, add a **test-only** interface with the same fully qualified name.

### 3.3 Unit tests with a JVM framework

JUnit or Spock, a JVM build, and the script parsed by `GroovyShell`. The only CPI-specific setup is the
`messageLogFactory` binding variable, which the runtime injects and a local run must therefore supply.

```groovy
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals

class OrderValidationTest {

    private Script script

    @Before
    void setUp() {
        // CPI injects messageLogFactory into the script binding; outside the tenant it must exist and be null.
        def binding = new Binding()
        binding.setVariable('messageLogFactory', null)
        script = new GroovyShell(binding).parse(new File('scripts/OrderValidation.groovy'))
    }

    @Test
    void 'nominal order is validated and the custom status is set'() {
        def message = new FakeMessage('<Order><OrderId>4711</OrderId></Order>')
        message.setHeader('SourceSystem', 'CRM')
        script.processData(message)
        assertEquals('4711', message.getProperty('p_orderId'))
        assertEquals('ORDER_VALIDATED', message.getProperty('SAP_MessageProcessingLogCustomStatus'))
    }

    @Test
    void 'missing optional header falls back to the documented default'() {
        def message = new FakeMessage('<Order><OrderId>4711</OrderId></Order>')
        script.processData(message)
        assertEquals('UNKNOWN', message.getProperty('p_sourceSystem'))
    }

    @Test
    void 'malformed payload is rejected before any receiver call'() {
        try {
            script.processData(new FakeMessage('<Order><OrderId>4711'))
            throw new AssertionError('expected ORDER_MALFORMED_PAYLOAD, nothing was thrown')
        } catch (IllegalArgumentException e) {
            assertEquals(true, e.message.startsWith('ORDER_MALFORMED_PAYLOAD'))
        }
    }
}
```

Cover at minimum: nominal case, missing mandatory field, missing optional header (default behaviour), empty
payload, malformed payload. Every branch that throws must be asserted on its *error code*, not just "it failed".

### 3.4 What cannot be unit tested

| Not unit testable | Must be verified by |
| :--- | :--- |
| Adapter configuration: connection, credentials, certificate, proxy | Integration test against a real or mocked endpoint |
| Step order and routing of the flow model | Simulation, then E2E test |
| Splitter / aggregator / gather behaviour at volume | Volume test (§6.2) |
| MPL enrichment actually landing in the log table | One verification per interface in a real tenant |

---

## 4. Simulation and mocks

### 4.1 Design-time simulation

The simulation feature of the integration flow editor runs the flow model with a payload you supply and shows the
message at the selected steps. It needs no deployment and is the cheapest functional check available.

| Simulation covers | Simulation does not cover |
| :--- | :--- |
| Step sequence, routers and default branches | External connectivity, credentials, certificates |
| Mappings and transformations for the supplied payload | Receiver contract, real response codes, timeouts |
| Splitter / aggregator cardinality | Volume, throughput, temporary storage usage |
| Exception subprocess behaviour for synthetic errors | Whether monitoring and alerting actually fire |

> ⚠️ **Expect external calls not to reach the real system.** Check which steps the simulation replaces and
> which it stops on, and never treat a green simulation as evidence that the receiver accepts the payload.

### 4.2 Mock receivers and clients

- A **local HTTP stub** on the workstation (any lightweight mock server) returning canned responses, delays or
  recorded payloads, or a **mock endpoint in TEST** that echoes what it received so the produced message can
  be asserted byte for byte.
- For file-based interfaces, a disposable test directory or test system — never a production path.
- The mock must return a valid success response, a 4xx, a 5xx, a timeout and a malformed body — a mock that
  returns only success tests only the happy path.

### 4.3 Testing failure paths

| Failure path | How to provoke it | Behaviour to verify |
| :--- | :--- | :--- |
| Timeout | Mock delays longer than the configured timeout | Flow fails, status set, no partial send claimed as success |
| 4xx | Mock returns a client error | Message rejected, not retried forever, error reaches monitoring |
| 5xx | Mock returns a server error | Retry/DLQ per design, retries bounded |
| Malformed response | Mock returns unparsable content | Failure caught in the Exception Subprocess |
| Duplicate delivery | Send the same message twice | Idempotency or `DUPLICATE_IGNORED`; no double posting |
| Empty response | Success status with empty body | No corrupt document written downstream |

---

## 5. Test data and interface contracts

### 5.1 Mandatory test data set

| Case | Why it breaks production |
| :--- | :--- |
| Empty payload | Scripts and receivers frequently assume at least one record |
| Single record | Baseline correctness |
| Many records (realistic batch) | Splitter, aggregator and mapping loops |
| Maximum realistic size | Memory, temporary storage, body size limits |
| Special characters and non-ASCII | Encoding problems (`CamelCharsetName` exists for a reason) |
| Missing optional fields | Defaults, null handling, target mandatory-field rules |
| Unexpected extra fields | Forward compatibility, schema strictness |
| Duplicate keys or records | Idempotency and aggregation behaviour |
| Mandatory field present but invalid | Business validation vs. technical validation |

### 5.2 Contract testing

- Validate the produced structure against the agreed schema — field list, types, cardinality, mandatory fields.
- Version the interface specification and its test data together: test data without an interface version is
  worthless six months later.
- When the receiver changes a field, add the matching test-data variant *before* changing the mapping.

---

## 6. Environment testing

### 6.1 What can be tested where

| Test | DEV | TEST | Production |
| :--- | :--- | :--- | :--- |
| Script unit tests, static analysis | Yes | Yes (CI) | Yes (CI) |
| E2E against a mock receiver | Yes | Yes | No |
| E2E against the real receiver system | Partially | Yes | Only as a controlled smoke test |
| Volume and performance | Small scale | Yes | Never as a test |
| Security (authentication, certificate, unauthorized caller) | Partially | Yes | Verify configuration, do not attack |
| Alerting and DLQ behaviour | Partially | Yes | One controlled failure after go-live |

> ⚠️ **TEST is not production.** Externalized parameter values, certificate aliases, quota headroom, receiver
> configuration and data volume are exactly the differences that cause go-live incidents. Keep a per-interface
> list of what cannot be tested outside production and get business sign-off on it.

### 6.2 Volume and performance testing

Test **size and rate together**: a 40 MB single message and 40 000 small messages have nothing in common
operationally, and both must be measured on the same interface.

1. Reproduce the peak rate, not the average — hold it for at least 15 minutes.
2. Combine payload shapes: typical, maximum realistic, and worst-case optional-field load.
3. Include a slow receiver or delayed mock, then run one pass with the receiver failing intermittently to see
   retry and DLQ behaviour under load.

| Signal to watch | Why it matters |
| :--- | :--- |
| Temporary storage usage | Streaming uses a temporary file; its limit caps maximum file size and concurrency |
| JMS queue depth and transactions | Decoupling absorbs bursts only until queue capacity is reached |
| MPL growth | Verbose logging during the test consumes the persistence budget |
| Processing duration and thread occupancy | Saturation shows up as growing latency before it shows up as errors |
| Public API request rates | Load generators and monitoring tools are subject to per-tenant and per-user rate limits |

### 6.3 Security testing

- Authentication failure, including an expired certificate or keystore entry: clear, non-secret error and a
  fired alert; rehearse the renewal procedure.
- Unauthorized caller: a sender that must not reach the endpoint is rejected, not silently processed.
- Secret leakage: inspect headers, properties, log entries and MPL attachments for credentials or personal data.

---

## 7. Go-live and hypercare

### 7.1 Readiness gate

Run the gate in [checklists/go-live-readiness.md](../checklists/go-live-readiness.md), plus: E2E test passed in
TEST with production-like configuration, volume test at peak rate, failure paths tested (timeout, 4xx, 5xx,
duplicate), alert configured and tested, runbook and ownership documented, replay proven.

### 7.2 Smoke tests immediately after deployment

1. One message with a known-good payload end-to-end, verified *at the receiver*, not only green in Monitor.
2. One message with a known-bad payload: it fails, produces the expected custom status and lands in the DLQ or
   error path as designed.
3. Both messages are findable by business key and by correlation identifier.
4. The alert channel received the failure of step 2, and no alert fired for step 1.

### 7.3 Hypercare

| Watch during the first days | Exit criterion |
| :--- | :--- |
| Volume and error rate vs. expected baseline | Within the agreed threshold for N consecutive days |
| DLQ entries and unresolved messages | Zero unresolved entries older than the agreed age |
| MPL retention actually available | Retention window meets the agreed value |
| Queue depth and temporary storage peaks | No peak above the agreed headroom |
| Receiver-side complaints and manual corrections | No open business escalation |
| Alert quality: fired, actionable, owned | No unexplained alerts, none without an owner |

Exit hypercare explicitly, in writing, with the criteria above evidenced.

---

## 8. Quality anti-patterns

| Symptom | Root cause | Fix |
| :--- | :--- | :--- |
| Defects always found by the business | Only the happy path was tested | Failure-path and edge-case test data set (§5.1) |
| "It works in DEV" is the only evidence | No environment-specific test in TEST | Test in TEST with production-like configuration |
| Mapping change breaks the receiver | No contract or schema validation | Validate the produced structure in the integration test |
| Volume test used one huge file | Size tested, rate ignored | Test size and rate together at peak rate (§6.2) |
| Scripts are never unit tested | Logic not extractable from the flow | Pure `processData` logic plus a `Message` test double (§3) |
| Simulation declared sufficient for go-live | External calls and credentials untested | E2E in TEST before transport, smoke test after deployment |
| Duplicate documents in the target system | Duplicate delivery never tested | Idempotency key, plus a duplicate test case |
| Rollback impossible | No previous version or transport path | Keep the previous artifact version, rehearse the redeploy |

---

## 9. Test plan template

One row per test case; keep the plan with the interface documentation and its test data.

| ID | Test case | Layer | Test data | Expected result | Evidence | Owner | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| T-01 | Nominal message end-to-end | E2E (TEST) | `<typical payload>` | Document created at receiver; Completed | MPL ID + receiver screen | `<name>` | `<open>` |
| T-02 | Empty payload | Script + flow | `<empty>` | Rejected with documented code; no alert | MPL entry | `<name>` | `<open>` |
| T-03 | Maximum realistic size | Volume | `<largest file>` | Inside SLA; storage within limits | Storage + timings | `<name>` | `<open>` |
| T-04 | Peak rate for 15 minutes | Volume | `<n msg per min>` | No queue overflow; error rate 0 | Monitoring trend | `<name>` | `<open>` |
| T-05 | Missing mandatory field | Script + flow | `<payload without key>` | `BUSINESS_REJECTED`; no receiver call | MPL custom status | `<name>` | `<open>` |
| T-06 | Malformed payload | Script + flow | `<broken XML/JSON>` | Handled failure; DLQ entry | MPL ID + DLQ | `<name>` | `<open>` |
| T-07 | Special characters / non-ASCII | Mapping | `<umlauts, CJK, &, quotes>` | Byte-identical values at receiver | Receiver extract | `<name>` | `<open>` |
| T-08 | Receiver timeout | Integration | `<delayed mock>` | Retry per design, then DLQ; alert fires | Alert + DLQ | `<name>` | `<open>` |
| T-09 | Duplicate delivery | Integration | `<same payload twice>` | Second message flagged or ignored | Receiver extract | `<name>` | `<open>` |
| T-10 | Authentication failure | Security | `<invalid credential>` | Clear failure, no secret in MPL, alert fires | MPL + alert | `<name>` | `<open>` |
| T-11 | Searchability of a message | Operations | `<known business key>` | Found by custom header property and correlation ID | Search result | `<name>` | `<open>` |
| T-12 | Smoke test after go-live | Production | `<known-good payload>` | Document created; monitoring green | Receiver confirmation | `<name>` | `<open>` |

---

## Further Reading

- [General Scripting Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/general-scripting-guidelines)
- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
- [Limit Size of Incoming Messages](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/limit-size-of-incoming-messages)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
