# Interface Specification — <Interface ID> <Name>

This specification is the contract for one interface, shared with the counterpart system team (internal or
partner). Replace every `<angle bracket>` placeholder and agree the completed document with the counterpart
in writing before implementation starts. Anything not written here is not agreed, and will be discovered
during an incident.

---

## 1. Identification

- **Interface ID and name:** `<ID>` — `<name>`
- **Version:** `<x.y>` (see Change management)
- **Business owner:** `<name, role>`
- **Technical owner (our side):** `<name, team>`
- **Counterpart:** `<company / team / system>`, technical contact `<role-based contact>`
- **Status:** `<draft / agreed / in operation / retired>`

## 2. Purpose and business process

- **Business process served:** `<process and step>`
- **What the interface does:** `<one paragraph in business language>`
- **Trigger in the business process:** `<the business event that produces or consumes the messages>`
- **Consequence of an outage:** `<what the business cannot do while the interface is down>`

## 3. Communication

- **Direction:** `<we send / we receive / bidirectional>`
- **Protocol:** `<HTTPS / SFTP / SOAP / IDoc / JMS / …>`
- **Endpoint:** `<externalized parameter name — the value is environment-specific and is not part of this document>`
- **Authentication:** `<method>`
- **Transport security:** `<TLS version, certificate requirements, mutual TLS; allow-listing, proxy or VPN prerequisites>`
- **Synchronous or asynchronous:** `<sync request-reply / async fire-and-forget>`, acknowledged by `<what the sender receives on success, and when>`
- **Timeout and retry expectation:** `<timeout per message>`; retries by `<who, how often, with what backoff, until when>`

## 4. Payload

- **Format and encoding:** `<XML / JSON / CSV / IDoc / binary>`, `<UTF-8 / other>`
- **Schema and version:** `<schema artifact, version, location>`
- **Size limits:** `<maximum message size accepted, and what happens above it>`
- **Naming and ID conventions:** `<file naming pattern, message identifier, allowed characters>`
- **Correlation and business keys:** `<correlation field linking request, response and log entry; field identifying the business document>`
- **Empty and optional elements:** `<how absent values are represented: omitted / empty element / null>`

## 5. Sample message

One complete, realistic example each, with all mandatory fields populated:

```xml
<!-- Request -->
<request>
  <!-- mandatory and optional fields with realistic values -->
</request>

<!-- Response -->
<response>
  <!-- successful response for the request above -->
</response>

<!-- Error -->
<error>
  <code><!-- code from section 6 --></code>
  <message><!-- human-readable description --></message>
  <correlationId><!-- echo of the request correlation key --></correlationId>
</error>
```

## 6. Error handling and error codes

- **Error format:** `<as specified in section 5>`
- **Where errors are returned:** `<delivery mechanism: response payload, response status, file, queue>`

| Code | Meaning | Permanent / transient | Expected reaction of the sender |
| :--- | :--- | :--- | :--- |
| `<code>` | `<meaning>` | `<permanent>` | `<do not retry; correct the data and resend>` |
| `<code>` | `<meaning>` | `<transient>` | `<retry with backoff>` |
| `<code>` | `<meaning>` | `<transient>` | `<retry after the counterpart confirms recovery>` |
| `<code>` | `<meaning>` | `<permanent>` | `<escalate to the technical contact>` |

## 7. Idempotency and duplicate handling

- **Business key identifying a message:** `<field(s)>`
- **Behaviour on duplicate:** `<reject as duplicate / update / ignore and acknowledge>`, key remembered for `<window>`
- **Sender obligation:** `<whether the sender may resend after a timeout, and with which key>`
- **Consequence of an undetected duplicate:** `<the business impact, agreed by both sides>`

## 8. Sequencing and ordering guarantees

- **Ordering guarantee:** `<none / guaranteed per business key / guaranteed globally>`, scope `<per sender, per business object, per day>`
- **Out-of-order handling:** `<what the receiver does with a message that arrives before its predecessor>`
- **Batching:** `<whether messages may be delivered as a batch, and how a batch failure is reported>`

## 9. Non-functional requirements

| Requirement | Agreed value |
| :--- | :--- |
| Availability window | `<service window, maintenance window; business tolerance for an outage>` |
| Volume and message size | `<n> / <n>` messages per hour on average / at peak; `<n> / <n>` MB per message |
| Latency target | `<from send to availability at the receiver>`, timeout `<per message>` |
| Retention and replayability | `<how long a message can be replayed or re-requested>` |

## 10. Change management

- **Versioning rules:** `<what counts as a compatible change and what requires a new version>`
- **Notification lead time:** `<minimum notice before a change on either side, and how the change is verified jointly>`
- **Decommissioning:** `<notice period before the interface is switched off>`

| Role | Party | Contact route | Escalation order |
| :--- | :--- | :--- | :--- |
| Business owner | `<party>` | `<role-based channel>` | `<1>` |
| Technical owner | `<party>` | `<role-based channel>` | `<2>` |
| On-call / operations and vendor support | `<party>` | `<role-based channel>` | `<3>` |

## Further Reading

- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
