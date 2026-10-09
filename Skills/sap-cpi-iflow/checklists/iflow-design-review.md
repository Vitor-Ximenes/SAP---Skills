# iFlow Design Review Checklist

This checklist is run by an architect or reviewer before an iFlow is accepted for implementation or
promotion to a higher environment. It is a gate, not a suggestion list: every unchecked item becomes either
a required action with a named owner or a rejection. Review the design against
[references/design-guidelines.md](../references/design-guidelines.md),
[references/error-handling-and-monitoring.md](../references/error-handling-and-monitoring.md) and
[references/security-and-governance.md](../references/security-and-governance.md), and record the evidence
(artifact name, MPL ID, document link) for anything that is not self-evident from the artifact.

---

## Scope and contract

- [ ] The interface contract is written down and agreed with the counterpart: payload format, version, field semantics, and who is responsible for each step of the end-to-end chain.
- [ ] The idempotency expectation is stated: which business key identifies a message, and what the flow does when it sees the same key twice. (Replay is the normal recovery path; an unstated rule makes replay unsafe.)
- [ ] Average and peak message size are known numbers, not estimates.
- [ ] Average and peak message rate (messages per hour), plus expected peak concurrency, are known. (Split strategy, parallelism, queue capacity and timeouts are all sized from these.)
- [ ] Synchronous or asynchronous processing is decided, and the decision matches what the caller actually expects.
- [ ] The error contract is agreed: which failures are returned to the sender, in which format, and which failures the flow absorbs silently.
- [ ] The direction of the interface and the owner of the receiver-side integration are named.

## Naming and structure

- [ ] Integration flow, package and artifact names follow the team convention and contain no environment token or personal name. (The same artifact must be transportable to every environment unchanged.)
- [ ] Every step name carries the agreed prefix — `CM_`, `SCR_`, `RTR_`, `SPL_`, `AGG_`, `ENC_`, `CAL_`, `FLT_`, `XSL_`, `MM_` — and the suffix states what the step does.
- [ ] Exchange properties written by the flow use the `p_` prefix and do not shadow framework properties such as `SAP_MessageProcessingLogCustomStatus` or the splitter properties (`CamelSplitIndex`, `CamelSplitSize`).
- [ ] No step keeps a default name (`Content Modifier 1`, `Script 2`). (A reviewer must be able to read the step list and understand the flow without opening every step.)
- [ ] Readability: the main sequence is linear wherever possible, branches are labelled, and no connector crosses another without a routing justification.
- [ ] The flow is not a monolith — it either fits comfortably on one screen or is decomposed into sub-flows (see Modularity and reuse).
- [ ] Every artifact in the package (mapping, script, XSD, WSDL) is referenced by at least one deployed flow; no orphan artifacts remain.

## Modularity and reuse

- [ ] Reusable logic (validation, enrichment, target lookup, error handling) is implemented once and called, not copy-pasted.
- [ ] Child flows are invoked through the **ProcessDirect** adapter rather than duplicated, and each sub-flow has a documented contract: required input properties/headers, expected payload shape, returned payload.
- [ ] Mappings and scripts are separate reusable artifacts referenced by the flow, not inline copies per branch.
- [ ] No logic is duplicated across flows in the same package (identification, conversion, transformation, error handling).
- [ ] Sub-flow granularity is justified: no sub-flow that wraps a single Content Modifier and adds indirection without reuse.
- [ ] ProcessDirect is used only for in-tenant reuse and nothing depends on its invocation being remotely addressable.

## Payload and performance

- [ ] The message size class is identified and recorded in the design document (small `< 5 MB`, medium `5–40 MB`, large `> 40 MB`).
- [ ] For large payloads the streaming chain is unbroken end to end: every sender adapter, step, transformer and script on the path supports streaming. (One non-streaming step forces the whole message into memory for the rest of the flow.)
- [ ] Where FTP or SFTP is in a large-payload path, *Change Directories Stepwise* is disabled — otherwise the adapter does not stream.
- [ ] Where SOAP is in a large-payload path, WS-Security is not enabled — the SOAP adapter does not stream with WS-Security.
- [ ] No non-streaming step sits in the middle of a large-payload path: Message Mapping, XML Schema Validation, CSV/XML and EDI conversions, GZIP/ZIP decoding, MIME decoding, XML signature steps, Router and Aggregator.
- [ ] Data Store operations are kept out of a large-payload path where they would break streaming: Data Store Get and Select never stream, and Data Store Write does not stream on Cloud Foundry.
- [ ] A Content Enricher in a large-payload path uses the *Enrich* mode — the *Combine* mode does not stream.
- [ ] For each non-streaming step that is unavoidable, the payload is split *before* it, with the split strategy chosen deliberately — Iterating Splitter where the envelope is not needed, General Splitter only when child messages must keep the root tags, streaming enabled.
- [ ] No `getBody(String)` or equivalent full-body string conversion on a large payload. Any justified exception states the size assumption in a comment.
- [ ] HTTP-based sender adapters have the *Body Size* limit configured to the agreed maximum. (Oversized messages are then rejected with a clear message before any step executes.)
- [ ] Temporary storage headroom is checked for the maximum expected file. (The maximum streamable file size is bounded by tenant temporary storage.)
- [ ] Parallelism is bounded everywhere it exists — splitter concurrency, multicast branches, JMS consumers, scheduled runs. The numbers are ones the receiver can absorb, and they are documented. (Unbounded parallelism moves the outage to the receiver.)
- [ ] Where split chunks are recombined by Gather or Aggregator, the design states why recombination is needed instead of forwarding chunks.

## Mapping and transformation

- [ ] The canonical model is respected: source → canonical → target, or a documented, justified exception.
- [ ] All mandatory target fields are mapped, and no mandatory field is filled with a hardcoded literal that a source field should provide.
- [ ] Every router and every value-mapping decision has a defined default outcome; an unmatched message is never silently dropped.
- [ ] No conversion round-trip anywhere (XML → JSON → XML, encode then decode, base64 used only to carry binary through a text step).
- [ ] The mapping was tested with at least: one complete message, one message missing all optional fields, one message with empty or null elements, and one message at maximum expected size.
- [ ] A second developer reviewed the mapping and the review comments are resolved.
- [ ] Character encoding is explicit (adapter charset or `CamelCharsetName` where the source is not the default), and non-ASCII content is covered by a test.

## Error handling

- [ ] An Exception Subprocess exists in the main integration process.
- [ ] The end event of the exception subprocess (**End**, **End Message**, **Error End**, Escalation End) is documented with its justification and matches the contract: `Error End` reports the message as failed and propagates the error to the sender or rolls back the JMS transaction; `End Message` reports it as completed and returns the configured response.
- [ ] Where `End Message` is used, the flow still sets `SAP_MessageProcessingLogCustomStatus` to a value that distinguishes the handled error from a successful run — otherwise operations cannot see it in monitoring.
- [ ] Custom status values come from the agreed status catalogue and respect the framework limit (at most 40 alphanumeric characters).
- [ ] The retry strategy is explicit and numeric: number of retries, backoff, and which error classes are retryable. (Permanent business rejections must not be retried.)
- [ ] A DLQ or DLQ-equivalent exists for messages that exhaust retries (dedicated queue, or a store used for parking), and the owner who monitors it is named.
- [ ] Duplicate and replay handling is defined: business key, duplicate detection mechanism, and the behaviour on duplicate (skip, update, reject, park).
- [ ] The outbound leg is covered by error handling: timeout, connection failure and receiver-side error responses are distinguished from each other.
- [ ] Where several flows share the same error handling, it is centralised in a dedicated handler flow called via ProcessDirect rather than duplicated.

## Security

- [ ] No credential, token, key or password appears anywhere in the artifact: not in scripts, Content Modifiers, properties, headers or step names.
- [ ] All authentication uses security artifacts referenced by alias (user credentials, OAuth2 client credentials, keystore entry), and the alias itself is externalized.
- [ ] No credential is carried in a message header or exchange property. (Tracing and logging can expose them in clear text.)
- [ ] Authorization is least-privilege: the technical user of each system holds only the roles this interface needs.
- [ ] TLS and trust are configured for every outbound call; hostname verification is not disabled without a documented exception and an owner.
- [ ] Sensitive data is not written to the message processing log: no unconditional payload attachment, no custom header property containing personal or payment data. Payload attachments are conditional and off by default.
- [ ] Every environment-specific value is externalized as `{{parameter}}` — hosts, ports, paths, credential aliases, timer schedules, feature flags.
- [ ] Externalized parameters that hold secrets are marked as secrets in the design document; the repository never contains their values.

## Observability

- [ ] `SAP_MessageProcessingLogCustomStatus` is set on every terminal path (success, handled business error, technical error, message parked).
- [ ] Business search keys are added to the message processing log as custom header properties, so support can search by business identifier instead of by timestamp.
- [ ] The search keys were chosen with the business: the identifier the business will quote in a ticket.
- [ ] At least one alert is configured for the failure mode that matters (message failed, no message processed in an expected interval, queue or store growing beyond a threshold), with a threshold and a recipient group.
- [ ] A runbook exists and is linked from the design document: symptom → check → action → escalation.
- [ ] The log level in production is not permanently Debug or Trace. Script-added MPL properties are visible only at Debug or Trace, so any temporary increase is documented, time-boxed and reverted.

## Operations

- [ ] Retention for the message processing log and for every store the flow writes to is agreed with operations and satisfies the audit requirement.
- [ ] Cleanup is scheduled for every persistent store the flow leaves entries in. (An unbounded store is a scheduled outage.)
- [ ] No unused artifact remains deployed in the productive tenant (superseded versions, test flows, timer flows writing to non-productive targets).
- [ ] Ownership is assigned: a named team owns the interface in production, with an escalation path.
- [ ] The artifact is in source control and the revision to be deployed matches the reviewed revision.

---

## Review outcome

```text
Interface / iFlow : <name>
Package           : <package name>
Reviewed revision : <git commit / artifact version>
Reviewer          : <name, role>
Date              : <YYYY-MM-DD>
Decision          : [ ] Approved   [ ] Approved with actions   [ ] Rejected
```

Required actions (mandatory when the decision is *Approved with actions* or *Rejected*):

| # | Section | Required action | Owner | Due date |
| :--- | :--- | :--- | :--- | :--- |
| 1 |  |  |  |  |
| 2 |  |  |  |  |
| 3 |  |  |  |  |

> ⚠️ **Approved with actions** permits implementation to start but never promotion to a higher environment until every action is closed and re-verified. An unchecked item in **Security** or **Error handling** is a rejection, not an action.

## Further Reading

- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Optimize Integration Flow Design for Streaming](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/optimize-integration-flow-design-for-streaming)
- [Define Exception Subprocess](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/define-exception-subprocess)
- [Limit Size of Incoming Messages](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/limit-size-of-incoming-messages)
- [Headers and Exchange Properties Provided by the Integration Framework](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/headers-and-exchange-properties-provided-by-the-integration-framework)
