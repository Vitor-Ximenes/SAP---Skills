# iFlow Design Document — <Interface Name>

This is a fill-in template for the developer of an interface, completed *before* the flow is built; replace
every `<angle bracket>` placeholder. It is the input to the design review
([checklists/iflow-design-review.md](../checklists/iflow-design-review.md)) and the artifact an operations
team reads during an incident, so write it for them rather than for the reviewer.

---

## 1. Document control

- **Interface ID / name:** `<ID>` / `<name>`
- **Document version / status:** `<0.1>` / `<draft / in review / approved / superseded>`
- **Author / reviewer:** `<name, role>` / `<name, role>`
- **Date:** `<YYYY-MM-DD>`

## 2. Business context

- **Business process:** `<process name and step>`
- **Business owner:** `<name, role>`
- **Why this interface exists:** `<the business outcome it enables>`
- **Consequence of one hour of downtime:** `<business impact>`

## 3. Interface overview

- **Sender / receiver:** `<system / party>` / `<system / party>`
- **Direction:** `<inbound to CPI / outbound from CPI / both>`
- **Protocol(s):** `<e.g. HTTPS, SFTP, SOAP, IDoc, JMS>`
- **Synchronous or asynchronous:** `<sync request-reply / async fire-and-forget>`
- **Trigger and frequency:** `<event / timer schedule / polling interval> — <continuous, per event, daily at …>`

## 4. Volumes and sizing

| Metric | Average | Peak | Notes |
| :--- | :--- | :--- | :--- |
| Message size | `<n>` MB | `<n>` MB | `<largest realistic file>` |
| Messages per hour | `<n>` | `<n>` | `<peak window, e.g. month-end>` |
| Concurrency | `<n>` | `<n>` | `<simultaneous senders / parallel branches>` |
| Retention need | — | — | `<how long messages must remain replayable>` |
| Size class | — | — | `<small < 5 MB / medium 5–40 MB / large > 40 MB>` |

Design implications from these numbers: `<streaming required? split strategy? decoupling mechanism? parallelism limit?>`.

## 5. Message contract

- **Payload format and encoding:** `<XML / JSON / CSV / IDoc / binary>`, `<UTF-8 / other>`
- **Schema reference:** `<XSD / WSDL / OpenAPI / JSON Schema, version>`
- **Sample message:** `<link to the stored sample>`
- **Mandatory fields:** `<field paths, or "see the interface specification">`
- **Error response contract:** `<format and codes returned to the sender>`

## 6. Flow design

Step list, using the agreed prefixes — `CM_` Content Modifier, `SCR_` script, `RTR_` router, `SPL_` splitter,
`AGG_` aggregator, `ENC_` enricher, `CAL_` external call, `FLT_` filter, `XSL_` XSLT, `MM_` message mapping:

```text
 1. Sender                 : <adapter and trigger>
 2. CM_InitProperties      : <which properties, read from where>
 3. SCR_Validate<Object>   : <mandatory fields, rejection behaviour>
 4. RTR_RouteBy<Criterion> : <branch conditions>, default: <fallback>
 5. MM_<Source>_To_<Target>: <mapping>
 6. CAL_<TargetCall>       : <adapter, endpoint parameter, timeout>
 7. Receiver               : <adapter and target>
 8. Exception Subprocess   : <end event and behaviour>
```

Sub-flows and reuse (called via the ProcessDirect adapter):

| Sub-flow | Input contract (properties / payload) | Returns | Reused by |
| :--- | :--- | :--- | :--- |
| `<name> (<address>)` | `<required inputs>` | `<payload>` | `<flows>` |

Decoupling mechanism and why: `<none / JMS queue / store — and the reason>`.

## 7. Mapping design

- **Source → target summary:** `<one line per logical group>`
- **Where the mapping lives:** `<Message Mapping / XSLT / script artifact name>`
- **Canonical model:** `<canonical schema used, or "direct mapping" plus justification>`
- **Defaults, fallbacks and code lists:** `<value used when a source field is empty; code list source and mapping table location>`

## 8. Security design

- **Authentication and authorization:** inbound `<method>`, outbound `<method>`; roles or scopes `<technical user roles>`, credential alias `<alias, externalized as {{parameter}}>`
- **Transport security:** `<TLS version, trust configuration, mTLS>`
- **Data protection:** `<personal or sensitive data in the payload; masking; logging restrictions>`

## 9. Error handling design

- **Exception Subprocess:** `<present — which steps it covers>`
- **End event and justification:** `<End / End Message / Error End> — <why this one>`
- **Custom status on error:** `<value, at most 40 alphanumeric characters>`
- **Retry strategy and timeouts:** `<retry count, backoff, retryable error classes; connect and read timeouts>`
- **DLQ / parking:** `<queue or store name, who monitors it>`
- **Duplicate and replay handling:** `<business key, detection mechanism, behaviour on duplicate>`

## 10. Observability design

- **Custom status values:** `<success / handled error / technical error / parked>`
- **Searchable business keys:** `<custom header properties added to the MPL>`
- **Alerts, thresholds and runbook:** `<condition, threshold, recipient group>`, runbook link `<link>`
- **Production log level:** `<Info>`; temporary Debug/Trace only with `<who authorises it, until when>`

## 11. Externalized parameters

| Parameter | Description | DEV | TEST | PROD | Secret? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `{{<param_name>}}` | `<what it configures>` | `<value>` | `<value>` | `<value>` | `<yes/no>` |

> ⚠️ **Never write a secret value in this table or in the repository.** Record the parameter name, the security artifact alias, and where the value is maintained.

## 12. Transport and deployment

- **Transport option:** `<CTS+ / Cloud Transport Management / manual export-import / mtar>`
- **Deployment order and dependencies:** `<which artifacts before which; security artifacts, queues, stores, sub-flows, receiver-side prerequisites>`
- **Rollback plan:** `<previous version, measured redeploy time, handling of messages created since go-live>`

## 13. Test strategy

| Test | Data | Environment | Pass criterion |
| :--- | :--- | :--- | :--- |
| Happy path | `<sample>` | `<env>` | `<observable result>` |
| Boundary / maximum size | `<sample>` | `<env>` | `<observable result>` |
| Failure paths (receiver down, invalid payload, duplicate) | `<sample>` | `<env>` | `<observable result>` |
| Volume / peak load | `<sample>` | `<env>` | `<observable result>` |

## 14. Open points and decisions log

| # | Date | Open point / decision | Rationale | Owner | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `<YYYY-MM-DD>` | `<point>` | `<reason>` | `<owner>` | `<open / decided>` |

## Further Reading

- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [Define Exception Subprocess](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/define-exception-subprocess)
- [Add Information to the Message Processing Log](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/add-information-to-the-message-processing-log)
