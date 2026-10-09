# Integration Incident Triage Checklist

This is the runbook for the person on call when an interface is reported broken. Work the steps in order:
stabilise the information before touching anything, localise the failure layer before fixing, and mitigate
before investigating root cause. Record times and evidence as you go — the post-incident review depends on
them. Never change more than one thing at a time.

---

## Step 0 — Stabilise the information

Ask the reporter these questions before opening any tool. The answers decide the severity.

| Question | Why it matters |
| :--- | :--- |
| Which interface, and which business process step is blocked? | Identifies the artifact to look at. |
| What did you observe — an error message, missing data, or wrong data? | Distinguishes a technical failure from a data or business-rule problem. |
| Since when? Has it ever worked, or has it never worked? | "Never worked" points at the cut-over or configuration; "stopped" points at a change. |
| How many documents are affected, and which types? | Sets the business impact and the size of the eventual replay. |
| Is it everything or only some messages, and only some senders or all? | Separates a total outage from partial degradation. |
| Did anything change recently — release, patch, credential rotation, counterpart change? | The single most useful question; correlates the failure with a cause. |
| Who else is seeing this? | Reveals the blast radius early. |
| Is there a deadline (statutory, payroll, cut-off) attached to these documents? | Escalation trigger. |

- [ ] Severity assigned and the incident owner named — one person owns communication from now on.

## Step 1 — Confirm and scope

- [ ] Reproduce or confirm the symptom at the interface, not through the reporter's description.
- [ ] Find the affected messages in the message processing log for the reported window and filter by the interface and by the business key if available.
- [ ] Count the impacted business documents — not the failed message count if one message carries many documents.
- [ ] Establish the last known good message (timestamp and version) and the first failing message.
- [ ] Decide explicitly: total outage (nothing is processed) or partial degradation (specific payloads, senders, branches or receivers are affected).
- [ ] Check whether other interfaces on the same tenant, the same queue, the same receiver or the same credential are also affected.
- [ ] State the working impact estimate to the incident owner: documents affected per hour while the incident continues.

## Step 2 — Localise the failure layer

Find the layer first; do not fix until you can name it.

| Layer | How to recognise it | First three checks | Likely owner |
| :--- | :--- | :--- | :--- |
| Sender / authentication | Nothing at all appears in monitoring; the sender reports a rejected call or a login failure | Does any message exist for the window? Is the sender's call actually being made? Is the caller's credential valid and unexpired? | Sender system team, interface owner |
| Inbound adapter | Messages appear but fail at the very start; adapter-specific errors | Does the source still exist where the adapter looks (directory, file pattern, queue)? Are adapter credentials and certificates valid? Did the polling or consumer configuration change? | Integration team |
| Flow logic | Messages fail mid-flow with a script, routing or step error | What changed between the last good and the first failing message (deployment, parameter, payload)? Do failures follow one branch only? Can the failing payload be replayed in a non-productive environment? | Integration team |
| Mapping / validation | Only specific payloads fail while others succeed; errors name a field or node | Is the incoming structure still what the mapping expects (new optional field, changed code list)? What does the error text name? Did the counterpart change their format without notice? | Integration team, counterpart |
| Outbound call to receiver | Timeouts, connection resets, receiver error responses | Is the receiver reachable and is the endpoint unchanged? Is the receiver in a maintenance window or degraded? Does one manual call with a known good payload succeed? | Receiver system team, integration team |
| Receiver application | The call succeeds technically but the document is not visible or is rejected by business rules | Did the receiver log the document in its own monitor? Were validation errors returned in the response? Is receiver-side processing (job, queue, batch) stopped? | Receiver application team |
| Credential / certificate | Authentication or TLS failures that start at a specific time or after a change | Expiry date of the certificate or secret involved? Is the credential alias resolvable and the technical user still valid? Did the counterpart rotate a secret without notice? | Integration team, security, counterpart |
| Quota / storage | Growing backlog, retries, storage- or size-related errors, several interfaces failing together | Current temporary storage usage and queue depth? Growth of the persistence used by monitoring? Which interfaces share the affected resource? | Integration operations, tenant administrator |
| Infrastructure | Many unrelated interfaces fail at the same moment | Platform status for the region? Are other tenants affected? Was there a platform-level maintenance or certificate rollover? | Platform team, SAP support |

- [ ] The layer is named and written into the incident record, with the evidence that identifies it.
- [ ] The blast radius is confirmed (which other interfaces, senders or receivers are affected).

## Step 3 — Mitigate

Choose one option, apply it, and observe. Do not stack mitigations.

| Option | When it fits | Risk / what it does not fix |
| :--- | :--- | :--- |
| Replay parked messages (DLQ or store) | The cause was transient and is confirmed resolved | Creates duplicates if the original call actually reached the receiver and the interface is not idempotent |
| Restart the queue consumer or redeploy the flow | The flow is not consuming although messages are waiting | Interrupts in-flight processing and can trigger retries; the current running version must be recorded before redeploying |
| Redeploy the last known good version | The failure correlates with the most recent deployment | Loses the ability to investigate the failed version unless it is exported first; later configuration or security changes may not be compatible |
| Fail over to the manual process | Business impact is high and the duration is unknown | Manual work must be reconciled afterwards; duplicates if both paths run; needs a business owner decision |
| Temporary bypass (skip a validation or enrichment step, route to a holding queue, reduced frequency) | Something must keep flowing while the real fix is prepared | Bypassed controls tend to survive the incident; needs a named owner and a removal date before it is applied |
| Temporary capacity change (queue capacity, consumers, parallelism) | A one-off spike, not a design defect | Can degrade other interfaces on the same resource and masks the underlying sizing problem |

- [ ] The chosen mitigation, its start time and its expected effect are recorded in the incident.
- [ ] The effect is observed in monitoring within `<n>` minutes; if it does not help, revert it before trying the next option.
- [ ] Anything temporary has an owner and a removal date from the moment it is applied.

## Step 4 — Communicate

- [ ] First update to the business within `<n>` minutes of the report, and even when there is nothing new.
- [ ] The update is in business language: which documents, which process step, what the business must do meanwhile. No MPL IDs, no stack traces.
- [ ] The update states what is *not* affected as well as what is — this stops the business from halting unrelated work.
- [ ] Estimates carry a confidence statement and are revised as facts change; a revised estimate is not a broken promise, silence is.
- [ ] The business is told what they must not do (for example re-trigger documents manually, which creates duplicates).
- [ ] A fixed update cadence is agreed with the incident owner and honoured until the incident is closed.
- [ ] The counterpart system team is informed when the failure is on their side of the boundary.

## Step 5 — Recover and verify

- [ ] All affected messages are accounted for: the count before replay equals replayed plus permanently rejected, with the difference explained.
- [ ] Duplicate prevention is active *before* a mass replay, and the receiver has confirmed that the same business key is treated as a duplicate rather than a second document.
- [ ] Replay is done in a bounded first batch, verified on the receiver, and only then continued.
- [ ] Business confirmation that recovered documents are present and correct, based on a reconciliation of counts per document type — not on "it looks fine".
- [ ] Any document that cannot be replayed is listed with a business owner for manual handling; nothing is left implicitly lost.
- [ ] Monitoring and alert thresholds are returned to their normal values.
- [ ] Temporary bypasses and temporary capacity changes are removed, or scheduled with a named owner and a date.
- [ ] The incident record holds start and end time, business documents affected, mitigation, and links to the evidence.

## Step 6 — Root cause and prevention

- [ ] Post-incident review held within 5 working days of service restoration.
- [ ] Timeline reconstructed from evidence (monitoring, deployment history, alerts, counterpart logs) rather than from memory.
- [ ] Root cause stated separately from the trigger: "the interface had no idempotency guard" is a cause, "the counterpart retried" is only the trigger.
- [ ] The reason the failure was not detected earlier is stated explicitly (missing alert, threshold too high, no monitoring of message absence).
- [ ] Every action item has an owner and a date, and is a design change, a monitoring change or a process change — not an intention.
- [ ] The specific check that would have caught this earlier is added and tested (alert, test case, idempotency guard, capacity check).
- [ ] Actions are tracked to closure; an item closed with "will be more careful" is reopened.

---

## Appendix — Do not do this

- **Do not delete queue entries or store entries to "clean up".** You destroy the evidence and the only copy of the business documents, and replay becomes impossible.
- **Do not replay non-idempotent messages blindly.** Every replay can create a second business document, which is worse than the outage.
- **Do not redeploy without recording the running version first.** Export or note it, otherwise you cannot return to it and cannot correlate the failure with a version.
- **Do not disable alerts to stop the noise.** The incident is exactly when they are needed; if unavoidable, silence them with a documented expiry.
- **Do not change several things at once.** You lose the ability to attribute the recovery and to explain the next failure.
- **Do not test fixes in production with live business documents.** Use one known test message or a bounded batch.
- **Do not state a root cause you have not proven.** "Probably the network" delays the real fix and damages trust.
- **Do not leave a temporary bypass in place after the incident.** Schedule its removal at the moment you apply it.
- **Do not close the incident before the business confirms the reconciliation.** Technically green monitoring is not business recovery.

## Further Reading

- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
- [Apply the Retry Pattern with JMS Queue](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apply-the-retry-pattern-with-jms-queue)
- [Data Store, Variables and JMS Queues – When to Use Which Option](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/data-store-variables-and-jms-queues-when-to-use-which-option)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
