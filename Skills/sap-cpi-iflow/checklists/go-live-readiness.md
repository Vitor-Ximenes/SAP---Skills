# Go-Live Readiness Checklist

This checklist is run twice: five working days before go-live, to create the remaining actions, and again on
the go-live day as the final gate. Every item needs evidence — a test report, an MPL ID, a signed
confirmation, a link — not an assurance. Volume and load-test evidence must come from the target environment,
not from a developer tenant.

---

## Functional readiness

- [ ] Every test case from the test strategy is executed and evidenced, with the message processing log ID recorded per pass.
- [ ] Failure paths are tested, not only happy paths: receiver unavailable, receiver returns an error, malformed payload, oversized payload, duplicate message, expired or wrong credential, receiver timeout.
- [ ] The receiver-side test is signed off by the counterpart: they confirmed the data arrived complete, correct and in the expected form in their system.
- [ ] Boundary cases are tested, not only representative ones: maximum agreed message size, all optional fields empty, non-ASCII characters.
- [ ] Every defect found during testing is closed, or explicitly accepted with a documented workaround and an owner.
- [ ] The cut-over sequence is agreed in writing: order of steps, who performs each step, and the sender/receiver switch points.
- [ ] Reconciliation after cut-over is planned: which counts are compared, per document type, and by whom.
- [ ] The expected message volume for the cut-over period is known, so that a silently idle interface is detected instead of being mistaken for a quiet day.

## Technical readiness

- [ ] The deployment order of dependent flows is defined: child flows before the flows that call them, the shared error handler before its callers.
- [ ] Externalized parameters are verified per environment: every `{{parameter}}` has a value in DEV, TEST and PROD, and no unresolved placeholder or leftover development host remains.
- [ ] Security artifacts are deployed and valid in the target tenant: credential aliases exist, OAuth2 clients are active, keystore entries are present and usable.
- [ ] No certificate or secret used by the interface (client certificate, server certificate chain, signing key, PGP key, client secret) expires within the hypercare window plus an agreed buffer.
- [ ] Storage and messaging resources used by the flow exist in the target tenant: JMS queues, stores used for parking or deduplication, number ranges.
- [ ] Rollback is written and rehearsed: the previous artifact version is identified, the redeploy has actually been performed once in TEST, and the time it takes is measured.
- [ ] The rollback plan states what happens to messages processed since go-live — which are replayed, which are corrected manually, and who owns that decision.
- [ ] Transport was executed through the agreed option (CTS+, Cloud Transport Management, manual export/import, mtar) and the deployed revision in the target tenant was confirmed against the reviewed revision.
- [ ] The transport package contains exactly the artifacts to be deployed — no development-only flows, test credentials or sample payloads.
- [ ] Connectivity to every external endpoint was verified from the target tenant, not only from a development tenant.
- [ ] Temporary storage was inspected in the target tenant if large files are expected, and is sufficient for the largest agreed file.

## Volume readiness

- [ ] Peak load was tested at realistic message size *and* realistic rate — not a small payload in a tight loop.
- [ ] Temporary storage and queue depths were observed during the peak test, and the observed values are recorded. No queue build-up, no storage alarm, no consumer starvation.
- [ ] Retention is configured for every store the flow writes (message processing log, queues, parking stores) consistent with the expected daily volume and the audit requirement.
- [ ] Capacity headroom is stated as a number: the tested rate corresponds to `<x> %` of the agreed peak, and the interface degrades first at `<observed bottleneck>`.
- [ ] Resource usage is checked against the tenant's allocation for the resources this interface consumes, with headroom for expected growth stated.
- [ ] Burst behaviour is understood: the decoupling mechanism absorbs the burst, and the maximum queue depth reached during the burst test is documented.

## Operational readiness

- [ ] Monitoring is in place for this interface: failed messages, message absence in the expected interval, and store or queue depth.
- [ ] Alerts are configured *and tested* with a synthetic failure — the alert was actually triggered and received in the on-call channel, and the test timestamp and recipient are recorded.
- [ ] The runbook is published and reachable from the alert: symptoms, first checks, escalation contacts, business impact statement.
- [ ] On-call is informed and briefed: the person on duty knows the interface exists, its business impact, and where the runbook is.
- [ ] The escalation path is agreed end to end (first level → integration team → counterpart → vendor), with target response times.
- [ ] DLQ handling is rehearsed: a parked message was replayed end to end by the support team, not only by the developer.
- [ ] Support access to the productive tenant is confirmed through proper roles — never through shared accounts or personal credentials.

## Business readiness

- [ ] The business owner of the interface is named and reachable during the hypercare window.
- [ ] Communication was sent to the business: what changes, from when, what they will see, and what to do if something looks wrong.
- [ ] The manual fallback is defined and understood by the business: how documents are processed while the interface is unavailable, and who decides to switch to it.
- [ ] The hypercare window is agreed with explicit exit criteria (for example `<n>` consecutive working days without a P1 or P2 incident, with reconciled volumes).
- [ ] Any parallel run or temporary workaround has an agreed end date and a named owner for decommissioning.

## Go-live day

- [ ] Deploy window is confirmed and communicated; unrelated changes are frozen for the duration of the window.
- [ ] Deployment completed in the agreed order, and the deployed revisions were verified in the target tenant.
- [ ] Smoke test executed immediately after deployment with a defined test message and a known expected result.
- [ ] First successful end-to-end message confirmed in the message processing log *and* on the receiver side.
- [ ] Business confirmation received that the first documents are correct in their system.
- [ ] Reconciliation counts checked for the cut-over period against the expected volume.
- [ ] No open P1 or P2 defect; every open lower-severity defect has a documented workaround and an owner.
- [ ] Monitoring and the alert channel are watched live for the first hours by a named person, not left to chance.

---

## Sign-off

| Role | Name | Date | Confirmation |
| :--- | :--- | :--- | :--- |
| Interface owner |  |  |  |
| Business owner |  |  |  |
| Integration architect |  |  |  |
| Operations / on-call lead |  |  |  |
| Counterpart technical owner |  |  |  |

## Rollback decision criteria

The decision is pre-agreed, so it is executed rather than debated under pressure.

| Observed condition | Decision | Decision maker |
| :--- | :--- | :--- |
| Smoke test fails after deployment | Roll back to the previous version | Interface owner |
| Messages fail and the cause is not identified within `<n>` minutes | Roll back, then analyse outside the go-live window | Interface owner with operations |
| The receiver rejects all messages, or accepts none | Roll back and re-open the receiver-side test | Interface owner with counterpart |
| Duplicate business documents are detected in the receiver | Stop the flow immediately, then reconcile before restarting | Business owner with interface owner |
| Business data is corrupted or incomplete in the receiver | Stop the flow, roll back, and start data correction | Business owner |
| The receiver is back-logged by the delivered volume | Throttle to an agreed rate; roll back only if the backlog does not recover | Operations with receiver team |

> 💡 **Forward fix only when all three hold:** the defect is proven and reproduced in TEST, the fix requires no manual data correction, and the receiver has confirmed it can accept the volume during the fix. Otherwise roll back.

## Further Reading

- [Guidelines and Best Practices for Content Transport](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-content-transport)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
- [JMS Resource Limits and Optimizing their Usage](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/jms-resource-limits-and-optimizing-their-usage)
