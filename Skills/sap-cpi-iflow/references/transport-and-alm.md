# SAP Cloud Integration — Transport, CI/CD & ALM Guide

Integration content has two lives: a **design-time artifact** (a versioned integration flow in a package)
and a **runtime artifact** (a deployed unit that executes messages). Transport is the controlled path
between them; ALM is the governance around it — who may change what, who approves, how a bad deployment is
reversed, and what evidence is retained. Companion guides:
[references/security-and-governance.md](security-and-governance.md),
[references/design-guidelines.md](design-guidelines.md).

---

## 1. Landscape and Tenant Strategy

### 1.1 Minimum viable landscape

| Landscape | Purpose | Data | Who may deploy |
| :--- | :--- | :--- | :--- |
| **Development (DEV)** | Authoring, unit simulation, connectivity to sandbox backends. | Synthetic / masked. | Designers, freely. |
| **Test / QA (TEST)** | Integration and end-to-end testing against test backends, UAT. | Masked production-like. | Pipeline or transport operator. |
| **Production (PROD)** | Live business traffic. | Real. | Transport operator only, always approved. |

The minimum is three separate tenants — one per stage. Sharing DEV and TEST destroys the ability to say
"this version passed TEST"; sharing TEST and PROD removes the ability to test without touching live traffic
paths. Worth their cost in larger programs: a **sandbox** tenant for proofs of concept and partner
onboarding, and a **pre-production / staging** tenant when a partner's freeze window cannot be respected.

### 1.2 What must be identical, what may differ

| Aspect | Across tenants | Rationale |
| :--- | :--- | :--- |
| Integration flow structure (steps, routers, splitters, exception subprocess) | **Identical** | A structural difference means TEST validated a different program than PROD runs. |
| Canonical schemas, message mappings, XSLT, scripts | **Identical** | Mappings are business logic; a per-environment mapping is a defect, not a configuration. |
| Property names, header names, custom status values | **Identical** | Monitoring searches and runbooks must work the same way in every stage. |
| Host names, ports, base paths, remote directories, credential aliases | **Must differ** | Environment-specific by definition, and credentials are never promoted. |
| Scheduler expressions, polling intervals, feature flags, queue names | **May differ** | Batch windows, volumes and runtime resource names differ per stage. |

> ⚠️ **Everything environment-specific must be externalized as `{{parameter_name}}`.** A hardcoded host, port, credential alias, path or schedule forces a manual edit of the flow during every promotion, which converts a controlled transport into an uncontrolled change and guarantees that the edit will eventually be made in the wrong field. See [references/security-and-governance.md](security-and-governance.md) §2.

### 1.3 Concrete naming scheme

The step prefixes `CM_`, `SCR_`, `RTR_`, `SPL_`, `AGG_`, `ENC_`, `CAL_`, `FLT_`, `XSL_`, `MM_` and the
property prefix `p_` ([references/security-and-governance.md](security-and-governance.md) §3) are unchanged
here; this scheme extends that convention to the objects that are *transported*.

| Object | Pattern | Example |
| :--- | :--- | :--- |
| Integration package | `IFL_<Domain>_<BusinessObject>` | `IFL_Sales_OrderToERP` |
| Integration flow | `IF_<Sender>_<Domain>_<BusinessObject>_<Direction>` | `IF_S4HANA_Sales_SalesOrder_Outbound` |
| Child flow / local process | `PF_<Domain>_<Purpose>` | `PF_Sales_CanonicalTransform` |
| Shared error handler | `IF_Common_ErrorHandling_Inbound` | one per landscape, called via ProcessDirect |
| Mapping / value mapping artifact | `MAP_<Source>_<Target>`, `VMAP_<Domain>_<CodeList>` | `MAP_SalesOrder_Canonical` |
| Script collection | `SC_<Domain>_<Purpose>` | `SC_Sales_CommonUtils` |
| Externalized parameter | `{{<system>_<object>_<attribute>}}` | `{{s4_erp_host}}`, `{{sftp_incoming_dir}}` |
| Credential alias (security artifact) | `<system>_<purpose>_<env>` | `s4_erp_oauth_dev` |
| Git release tag | `rel/<package>/<semver>` | `rel/IFL_Sales_OrderToERP/1.4.0` |

Rules that make the scheme survive contact with reality:

- `Inbound` means an external sender calls the tenant; `Outbound` means the tenant calls the receiver. Never `Import`/`Export` — those describe data movement, not integration direction.
- `Common` is reserved for artifacts shared by more than one domain; a one-interface `Common` package will be split later at cost.
- Externalized parameter names carry the **logical system**, never the environment: `{{s4_erp_host}}`, not `{{s4_erp_host_prod}}`. The environment lives in the value, which is what makes the artifact portable.
- Credential aliases carry the environment suffix deliberately, because the alias *is* the environment binding. Flows reference it through an externalized parameter (e.g. `{{s4_erp_oauth_alias}}`) so the same flow text works in every stage.

---

## 2. Design-Time vs Runtime Artifacts

### 2.1 The artifact chain

```text
Integration package (IFL_Sales_OrderToERP)
  └── Integration flow (IF_S4HANA_Sales_SalesOrder_Outbound)  ← versioned design-time artifact
        ├── Scripts, mappings, XSLT (embedded or referenced), endpoints, {{parameter}} declarations
                    │  deploy a specific version of the flow
                    ▼
              Runtime artifact                                 ← executes in the tenant
                    └── Consumer resources (queues, Data Store entries, workers), MPL entries
```

A never-deployed design-time artifact is only configuration; a runtime artifact whose source changed but was
not redeployed is the most common cause of "the fix is in the code but not in behaviour".

### 2.2 Reusable artifacts and where they live

Reusable design-time content: **Groovy scripts**, **message mappings**, **XSLT resources**, **value
mappings**, **script collections**, **child integration flows** called via ProcessDirect, and **OData APIs /
API artifacts** exposed to consumers. Embedded artifacts — a script or mapping used by exactly one flow —
travel with that flow; shared artifacts do not:

| Shared artifact | Reuse mechanism | Transport consequence |
| :--- | :--- | :--- |
| Groovy script, message mapping, XSLT | Script collection or mapping artifact in the package | Must be transported and deployed **before** the flows that reference it, in **every** stage where it is used; a change ripples to all consumers at once. |
| Value mapping | Value mapping artifact | Identifier code lists; environment-specific entries are a configuration risk — verify content per stage. |
| Child integration flow | Called via **ProcessDirect** | Separate runtime artifact: deploy and start it before any caller. |
| OData API / API artifact | Exposed interface | Independently versioned and deployed; consumers need a compatibility commitment, not just a transport. |

> 💡 **Reuse beats copy-paste on cost, not only on elegance.** Integration content consumes finite tenant storage (Cloud Foundry integration content: 2 GB), and a domain rule that exists in five copies will be fixed in four of them. Before accepting a copy, ask whether a script collection entry, a mapping artifact or a ProcessDirect child flow removes the duplication.

### 2.3 Versioning an integration flow

An integration flow is versioned as a whole; deploying "a specific version" means selecting that stored
version and deploying it, producing the tenant's runtime artifact. Consequences to plan for:

- **The runtime does not advertise which version it runs.** Keep the deployed version as an explicit release record (Git tag plus transport record), and consider stamping it into the flow description or a non-secret monitoring property so an incident can tell what is live.
- **Deploying an older version is a real rollback mechanism**, which is why version history must never be overwritten by editing directly in PROD — the rollback target *is* that history.
- **Version numbers must not encode the environment.** `1.4.0` is a version; `1.4.0-prod-fix` is a fork waiting to happen.
- **Never rely on the tenant as the archive of truth.** Tenant version history is a convenience, not a backup — see §5.4.

---

## 3. Content Transport Options Compared

The four documented content transport options are **CTS+**, **Cloud Transport Management**, **manual export
and import**, and **mtar download**.

| # | Option | Effort (setup / per transport) | Automation | Approval & audit story | Depth / fit | Choose when |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **CTS+** | High / low | High — triggered by the change-management workflow | Strongest: a change request with owner, approval and documented history in the change system | Suited to organizations already running a CTS+ change process | You already operate CTS+ as the corporate change gate and must not add a second approval channel |
| 2 | **Cloud Transport Management** | Medium / low | High — nodes and forwardings can be driven from a pipeline | Good: the service holds the transport queue and history per node; approvals live in the service or the pipeline | Built for BTP landscapes with several subaccounts/tenants | You want automated multi-stage promotion inside BTP without depending on an on-premise change system |
| 3 | **Manual export / import** | Minimal / **high** | None | Weak: the audit trail is operator discipline and a spreadsheet unless a record is forced | Works everywhere | One-off migrations, a handful of artifacts, air-gapped tenants, emergency hotfix when automation is unavailable |
| 4 | **mtar download** | Minimal / high | None to low | Weak, as manual transport | Works everywhere, including as a fallback when a transport service is down | You need a portable archive: offline archiving, handing content to another team, restoring after tenant loss |

### 3.1 The externalized-parameter consequence

Every option above transports the **design-time artifact**. None is a configuration management system for
environment values, and this is where most transport failures originate:

| Option | What happens to externalized parameters after import |
| :--- | :--- |
| **CTS+** | The artifact arrives with its parameter *declarations*; values are maintained in the target. Unless the process covers it explicitly, the target keeps its previous values — correct on re-transport, silently wrong on first transport. |
| **Cloud Transport Management** | Same behaviour. Plan an explicit post-transport configuration step and treat "parameters configured" as part of the transport's definition of done. |
| **Manual export / import** | Highest risk after mtar: the operator must know which parameters changed. Without a per-stage configuration matrix (§4.3), omission and typo are both likely. |
| **mtar download** | Highest risk, because the archive is often deployed outside any process; the deployer may not even know which parameters exist. |

> ⚠️ **A transport that lands in the target with missing or stale externalized values is a failed transport, even if the deployment reports success.** Deploy status proves the artifact started; it does not prove it points at the right backend. Verify at least one end-to-end message per interface after every transport (§9).

---

## 4. Externalized Parameters and Configuration Management

### 4.1 What must be externalized

| Category | Examples | Why externalize |
| :--- | :--- | :--- |
| Endpoint identity | `{{s4_erp_host}}`, `{{s4_erp_port}}`, `{{s4_erp_base_path}}` | TEST and PROD never share a backend. |
| Credential / keystore reference | `{{s4_erp_oauth_alias}}`, `{{sftp_credential_alias}}`, `{{keystore_alias}}` | The alias resolves to a per-environment security artifact. |
| Remote paths / resource names | `{{sftp_incoming_dir}}`, `{{jms_queue_name}}` | Directory and queue layouts differ per stage. |
| Scheduling | `{{sync_timer_schedule}}`, `{{polling_interval}}` | PROD batch windows and volumes differ from TEST. |
| Resilience tuning | `{{retry_max_attempts}}`, `{{timeout_ms}}` | TEST often needs shorter timeouts to surface failures faster. |
| Operational switches | `{{enable_debug_logging}}`, `{{max_body_size_mb}}` | Debug logging and adapter size guards are switched per stage without a code change. |

### 4.2 What must NOT be externalized

- **Internal routing logic.** Router conditions, splitter settings and default routes are the flow's behaviour; parameterizing them means nobody can reason about the flow by reading it.
- **Canonical schema mapping.** Field mappings, code-list contents and namespace transforms are business logic; if TEST and PROD map differently, the test is meaningless. Neither are step names, property names (`p_...`), header names or custom status values, which are the contract with monitoring and runbooks — per-environment variation breaks every saved search.
- **Structural decisions.** Which adapter, which step order, whether a JMS queue decouples the sender. These differ only if the interfaces differ — and then you need two flows, not one parameterized flow.

### 4.3 The configuration matrix

```text
parameter              DEV                        TEST                       PROD
s4_erp_host            dev-erp.example.internal   test-erp.example.internal  (from PROD change record)
s4_erp_oauth_alias     s4_erp_oauth_dev           s4_erp_oauth_test          s4_erp_oauth_prod
sftp_incoming_dir      /dev/in/sales              /test/in/sales             /prod/in/sales
sync_timer_schedule    0 0/30 * * * ?             0 0/15 * * * ?             0 0 2 * * ?
enable_debug_logging   true                       false                      false
```

### 4.4 Detecting drift

Drift is any difference between the repository, the configuration matrix and what is configured or running
in a tenant. Detect it in three layers:

1. **Parameter-name drift** — the set of declared `{{parameters}}` in the repository differs from the set configured in the tenant. Compare the two lists in a pipeline step and fail on mismatch: a parameter declared but not configured fails at runtime, not at deploy time.
2. **Value drift** — a value changed in the tenant with no corresponding change record. Diff tenant configuration against the matrix on a schedule; every unexplained difference is an undocumented change or an unrecorded incident response.
3. **Structure drift** — the deployed flow differs from the tagged repository version. The strongest defence is procedural: no edits in TEST or PROD, ever, plus a periodic re-deploy of the tagged release to confirm the deployed artifact equals the reviewed source.

> 💡 Re-deploying the tagged release as a drift check is cheap and catches hand-edits nobody reported. Do it before a freeze, not during an incident.

### 4.5 Security artifacts are not business content

Credentials, keystores, OAuth2 client configurations, PGP keys and certificates are **environment-specific
security artifacts**: not transported as business content, and planned as a separate workstream. Their owner
(security/operations, or PKI for keystore entries) creates and rotates them per tenant, and every flow
references them through an externalized alias parameter — never through a literal name.

So every transport record must answer: **are the security artifacts this release depends on already present
and valid in the target?** A flow referencing an alias that does not exist in the target deploys
successfully and fails on the first message — the classic "worked in TEST, fails in PROD" transport defect.
Track certificate and client-secret expiry in the interface runbook; never copy a keystore from TEST to PROD.

---

## 5. CI/CD for Integration Content

### 5.1 Minimum pipeline capability

A pipeline for integration content must, at minimum:

1. **Fetch the artifact** from the repository at an immutable reference (tag or commit SHA), never a mutable branch head.
2. **Apply environment configuration** — the target stage's parameter values from the configuration matrix (§4.3), or the instruction set telling the operator which values to maintain.
3. **Pre-flight check** that every declared `{{parameter}}` has a value for the target stage and that the required security artifact aliases exist there.
4. **Deploy** the artifact to the target tenant.
5. **Verify deployment status** by polling to a terminal state and failing on anything that is not a successful deployment (§5.2).
6. **Run a smoke test** — at least one message through the interface against the target backend, asserting a positive business outcome (a success status in the Message Processing Log, not merely HTTP 200 from the triggering call).
7. **Promote** — record the promoted version and hand the next stage its immutable reference, or stop and await approval.

Static analysis of scripts, schema validation and documentation checks are valuable but additive. A pipeline
that deploys and reports success without steps 5 and 6 is a deployment script, not a delivery pipeline.

### 5.2 Deployment status as a gate

Deployment is asynchronous and can fail *after* the deploy call returns: the artifact is accepted, then
startup fails (invalid configuration, missing security artifact, an absent ProcessDirect partner, resource
limits). Treat deployment status as a first-class gate:

- **Poll** the status rather than inferring success from the acceptance response; only a terminal successful state may allow promotion, and a pending state must time out and fail loudly.
- Poll at a sane interval with bounded attempts and backoff on throttle responses — management APIs are rate limited, so a check every few seconds across parallel deployments is itself a load source.
- Confirm the artifact is actually **started**, and that its ProcessDirect callers resolve (§6.1).
- On failure, capture the deployment error text into the transport record before failing the stage; "deployment failed" without the reason guarantees a second failed attempt.

### 5.3 Where tests belong, and what "test the transport" means

Test content and test layers are described in [references/testing-and-quality.md](testing-and-quality.md);
the transport view adds three obligations:

| Test type | Runs where | Transport relevance |
| :--- | :--- | :--- |
| Simulation / local design-time test | Developer machine or design-time tooling | Catches mapping and script defects **before** a version is transported at all. Cheapest gate. |
| Connectivity test | TEST, after transport | Proves host, port, credential alias and certificate are correct **for that stage** — this is the test that catches parameter drift. |
| End-to-end smoke test | Each stage, after every transport | The gate a transport must pass. Asserts a business result, not a transport-level 200. |
| Regression set | TEST | Confirms the new version did not break neighbouring interfaces sharing a script collection or mapping artifact. |

**"Test the transport"** does not mean "re-run the functional tests". It means deliberately exercising what
only exists once content has moved: the target's externalized values, its security artifacts, the deploy
order of dependent artifacts, and the promotion/rollback path itself. Rehearse a rollback in TEST each
release cycle, so PROD is never the first place it is attempted.

### 5.4 Version control topology

**The tenant is not the source of truth. The repository is.** A tenant can be rebuilt from the repository
plus the configuration matrix plus the security artifacts; the repository cannot be rebuilt from a tenant
that was edited by hand.

| Element | Practice |
| :--- | :--- |
| Mainline | One protected main branch holding released, deployable content. No direct pushes. |
| Working branches | Short-lived, per change, named after the interface and the change intent. |
| Hotfix branches | Branch from the **release tag** PROD actually runs, never from main. Cherry-pick back to main once verified, or the fix disappears at the next release. |
| Release tags | Immutable tag per released version (e.g. `rel/IFL_Sales_OrderToERP/1.4.0`). The pipeline deploys the tag, never a branch head. |
| Reviews | At least one peer review before merge and a separate approval before PROD. Reviewers check externalization, error handling and naming — not formatting. |
| Secrets | No credentials, tokens, keystores or tenant URLs in the repository, ever — including test files and pipeline logs. Parameter *names* and non-secret placeholders only. |

---

## 6. Runtime Operations and Promotion

### 6.1 Deploy order for dependent flows

Deploying a caller before its callee creates a **broken window** in which the caller is live and its
dependency is not. Order deployments by dependency:

1. **Security artifacts and shared configuration** for the target stage — verified present first; not transported content.
2. **Shared script collections and mapping artifacts** in their new version, plus the **central error-handling flow** (`IF_Common_ErrorHandling_*`) so failures in the rest of the window are already handled and alerted.
3. **Child / utility flows called via ProcessDirect** — callees before callers.
4. **Process and mapping flows** that orchestrate business logic.
5. **Sender-facing flows**, so the entry point to a new chain opens only when everything behind it runs.
6. **Consumer-facing flows** (OData APIs, exposed interfaces) last, coordinated with the consumer's release window and compatibility commitments.

For incompatible changes, do not rely on ordering alone: use an expand/contract sequence over two releases —
release 1 adds the new structure while both old and new are valid, release 2 removes the old once consumers
have moved. The same discipline applies to backends: a flow requiring a new receiver API version must not be
promoted before that version exists in the target backend.

### 6.2 Rollback

Rollback means **redeploying a previous version** of the affected artifact, from the tenant's design-time
version history or from the tagged repository version. It is fast and mechanical — and strictly limited:

> ⚠️ **A rollback does not undo side effects that already happened.** Messages already delivered stay delivered. Entries already written to a Data Store or a database stay written. Files already placed on SFTP stay there. JMS messages already consumed are gone. Documents already posted in a backend stay posted. Rolling back the artifact restores *future* behaviour only.

So every release needs a rollback plan naming:

- **The rollback target** — the exact previous version/tag per artifact, written down *before* deployment, not looked up during the incident.
- **The side-effect assessment** — which receivers, data stores, queues or backend documents could have been touched between deploy and detection.
- **The compensation plan** — for each affected side effect, who reverses or repairs it, how, and how it is verified. Compensation is a business action (credit memo, reversal posting, file cleanup, manual replay), not a deployment action.
- **The reconciliation and the decision owner** — the query establishing how many messages were affected and with which keys (searchable Message Processing Log custom header properties exist for exactly this), plus who chooses between rollback, forward fix and rollback-plus-compensation within what time budget.

Undeploying a flow with undrained JMS queues or pending aggregations loses in-flight messages: check queue
depth and pending correlated groups *before* undeploying.

### 6.3 Undeploying unused artifacts

Runtime artifacts consume tenant resources and cognitive budget: treat cleanup as a periodic governance duty.
Inventory runtime artifacts per tenant and classify them (active, superseded, experimental, orphaned), then
**undeploy** the superseded and orphaned ones that still run and archive the design-time artifact per the
retention policy. Before undeploying, confirm nothing calls it via ProcessDirect, no scheduler will fire it,
no JMS queue it owns holds messages, and no external consumer still posts to its endpoint — check the
interface register, not memory. Keep the repository copy: undeploying is a runtime decision, not a deletion
of history, and an operator facing forty running artifacts cannot tell which five matter at 03:00.

---

## 7. Governance

### 7.1 Roles and separation of duties

| Role | Responsibility | Must not also be |
| :--- | :--- | :--- |
| Integration designer | Designs the flow, externalizes parameters, provides interface spec and runbook. | Sole approver of their own transport. |
| Reviewer / architect | Reviews design against [references/design-guidelines.md](design-guidelines.md) and §7.2, checks externalization and naming. | The designer of the same change. |
| Transport operator | Executes or triggers transports, verifies deployment status, records evidence, configures externalized values. | The person who requested the change without a second approval. |
| Release approver | Approves PROD promotion based on TEST evidence, rollback plan and business readiness. | The transport operator. |
| Security owner | Owns per-stage security artifacts, rotates them, tracks expiry. | The designer of the interfaces consuming them. |
| Operations / monitoring | Owns alerting, on-call, hypercare, escalation and the incident record. | Responsible for authoring the flows they support. |

The non-negotiable separation is between **who writes the change**, **who approves it** and **who has
hands-on PROD access**. A small team may fill several roles, but approvals are always recorded and nobody
approves their own PROD deployment.

### 7.2 Review gates

- **Gate 1 — Design review (before the build is complete).** Naming conforms to §1.3; every environment-specific value is externalized and named after the logical system; error handling is present with a defined outcome and custom status; the monitoring story is defined (what is searched, what alerts); security artifacts and their owners are identified; size and volume assumptions are stated; receiver compatibility and idempotency are considered.
- **Gate 2 — Transport readiness (before every promotion).** The §9 checklist is executed and attached to the change record.
- **Gate 3 — Go-live readiness (before PROD).** TEST evidence attached; business process owner and backend/consumer side confirmed ready; security artifacts valid and not near expiry; rollback target and compensation plan written; monitoring alerts active **before** traffic starts; freeze calendar respected.
- **Gate 4 — Post-go-live hypercare.** A defined window (typically the first business cycle) with heightened monitoring of volume against expectation, failure rate, queue depth and receiver response times, plus a decision point at the end to close, extend or roll back. Close in writing, moving residual issues into normal support.

### 7.3 Documentation duty

Every interface needs two documents, versioned with the content:

- **Interface specification** — sender and receiver systems, direction, protocol and adapter, message formats and versions, mapping intent, frequency and expected volume, error and retry behaviour, the externalized parameter list with per-stage values, and the security artifacts referenced.
- **Runbook** — what the interface does in business terms, how to recognize a failure, which monitoring views and search keys to use (custom header properties, custom status values), known error codes, first-line actions, escalation path, replay/reprocess procedure, and the rollback procedure with its side-effect caveats.

An interface without a runbook is supported by whoever answers the phone — a governance failure, not an
operations failure.

### 7.4 Audit — what every transport must record

| Field | Example content |
| :--- | :--- |
| Change / ticket and artifact | Approved change reference, package, flow, exact version, repository tag. |
| Source and target stage | From which stage to which stage. |
| Approver and executor | Named persons with timestamps, not a team mailbox. |
| Parameter changes | Which externalized values were created or changed for the target. |
| Security artifact status | Which aliases were required and their verified presence and validity. |
| Deploy result | Terminal deployment status and the error text if any. |
| Verification evidence | Smoke test result, business document or MPL reference proving the interface works. |
| Rollback target | The previous version to redeploy, recorded before deployment. |

Retain these records for the audit period your organization requires; they are the only way to answer "what
was running in PROD on that date, and who approved it".

---

## 8. Anti-Patterns

| # | Symptom | Root cause → fix |
| :--- | :--- | :--- |
| 1 | A fix exists in PROD that nobody can find in the repository; behaviour changes after the next transport with no code change. | Flows edited directly in PROD or TEST instead of being changed, reviewed and transported → treat the repository as the source of truth, forbid design-time edits outside DEV, and re-deploy the tagged release to overwrite hand-edits and re-establish the baseline. |
| 2 | Credentials or tokens appear in a script, a committed properties file, or pipeline logs. | No rule against secrets in version control, and a literal is convenient → never commit secrets, keep per-stage security artifacts in the tenant, reference them by externalized alias, and rotate anything committed as compromised. |
| 3 | Deployment succeeds, first message fails: connection refused, unknown host, authentication rejected. | Transported without adapting externalized parameters or without creating the security artifact in the target → pre-flight parameter completeness check, connectivity test per stage, and a configuration matrix per environment. |
| 4 | An incident worsens because nobody can say which version to go back to, or the rollback silently loses in-flight messages. | No rollback plan, no recorded rollback target, no check of queues and aggregations before undeploy → record the rollback target before deploying, rehearse rollback in TEST, and check queue depth and pending groups before undeploying. |
| 5 | One shared credential or keystore artifact serves DEV, TEST and PROD. | Fewer artifacts to maintain looked simpler → one security artifact per environment, the alias externalized so the same flow text binds per stage, and never copy a keystore from TEST to PROD. |
| 6 | PROD deployment on Friday afternoon, or during a month-end freeze. | Schedule pressure and no freeze calendar → publish and enforce freeze windows, deploy early in the week and early in the day so hypercare is staffed, and require explicit exception approval. |
| 7 | A caller flow is deployed before its ProcessDirect callee, or a consumer is switched on before its flow is live. | No dependency-aware deploy order → deploy security artifacts, shared artifacts, error handler, callees, business logic, senders, consumers (§6.1), and use expand/contract for incompatible changes. |
| 8 | The same mapping or script logic exists in five flows and fixes land in three of them. | Copy-paste instead of reusable artifacts or child flows → extract to a script collection, mapping artifact or ProcessDirect child flow, version it once, and document the shared dependency in each consumer's runbook. |
| 9 | TEST passes, PROD behaves differently, and nobody changed anything. | Structural drift: PROD was hand-edited, or a shared script collection reached only one stage → keep structure identical across stages, vary only externalized values, and transport shared artifacts to all stages in the same release. |
| 10 | Rollback restores the flow but the wrong business documents remain posted. | Belief that a rollback reverses delivered messages and written data → accept that rollback changes future behaviour only, and always pair it with a side-effect assessment and a compensation plan (§6.2). |
| 11 | Tenant storage fills up with dozens of running artifacts nobody recognizes. | No periodic undeploy and inventory duty, experiments left running → quarterly inventory, verify no callers, undeploy, archive the design-time artifact, and keep the repository copy. |
| 12 | A transport happens with no record; during an audit nobody can produce who approved it. | Manual transports outside the change process → mandatory transport record per §7.4, part of the same change ticket as the approval, and "no record" is a failed transport. |

---

## 9. Transport Readiness Checklist

Run this before **any** transport, including emergency ones; every unchecked box is a reason to stop, or to
record an explicit, approved exception.

- [ ] **Version immutable and change approved** — the artifact is a tagged/immutable version, the deployment references that tag and not a branch head, and an approved change reference exists whose approver is neither the author nor the executor.
- [ ] **Externalization complete** — no host, port, path, credential alias or schedule is hardcoded anywhere in the artifact, including embedded scripts and Content Modifiers.
- [ ] **Parameter completeness** — every declared `{{parameter}}` has a value for the target stage, and the parameter-name set in the repository matches the set configured in the target.
- [ ] **Security artifacts ready** — every referenced credential alias, keystore entry and OAuth2 client exists in the target, is valid, and is not about to expire.
- [ ] **No secrets in content or logs** — the artifact, the repository and the transport record contain no credentials, tokens or tenant URLs.
- [ ] **Deploy order respected** — dependencies (shared script collections, mapping artifacts, error handler, ProcessDirect callees) are deployed or already running in the target.
- [ ] **Rollback target recorded** — the exact previous version per affected artifact is written into the change record, with the decision owner named.
- [ ] **Side-effect assessment done** — receivers, queues and data stores that could be affected are identified, with the compensation approach for each.
- [ ] **Monitoring ready** — alerts, search keys (custom header properties, custom status values) and the runbook are in place for the target stage *before* traffic starts.
- [ ] **Deployment status verified** — the pipeline polled the deployment status to a terminal successful state and retained the result, rather than assuming success from the acceptance response.
- [ ] **Smoke test passed** — at least one message passed end to end against the target backend with a verified business result, and the transport record was written per §7.4.

---

## Further Reading

- [Guidelines and Best Practices for Content Transport](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-content-transport)
- [Guidelines to Design Enterprise-Grade Integration Flows](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-to-design-enterprise-grade-integration-flows)
- [Guidelines and Best Practices for Message Monitoring](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-and-best-practices-for-message-monitoring)
- [Transaction Handling Guidelines](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/transaction-handling-guidelines)
- [System Scope in the Cloud Foundry Environment](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/system-scope-in-the-cloud-foundry-environment)
