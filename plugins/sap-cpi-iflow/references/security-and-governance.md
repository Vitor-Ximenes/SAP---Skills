# SAP Cloud Integration — Security & Governance Guide

This guide covers how an iFlow authenticates, how secrets are stored and rotated, how naming and
externalization are governed, and which security checks belong in a design review.

---

## 1. Authentication and authorization

### Inbound (who may call the iFlow)

| Mechanism | Use when | Notes |
| :--- | :--- | :--- |
| **Basic authentication** | Internal, low-risk interfaces | Requires a credential artifact on the tenant; avoid for external partners |
| **OAuth 2.0 client credentials** | System-to-system calls from a trusted client | The client secret belongs in a security artifact, never in a script |
| **OAuth 2.0 SAML bearer assertion** | Propagation of a named business user toward a cloud application | The user context comes from the propagated assertion |
| **Client certificate (mTLS)** | Partner interfaces with strong identity requirements | Requires trust configuration and certificate lifecycle management |
| **Principal propagation** | Calls that must reach the backend *as the business user* | Needs Cloud Connector and backend trust configuration |
| **API key / custom header token** | Legacy or partner-specific schemes | Document the header name and treat it as a credential |

### Outbound (how the iFlow authenticates to the receiver)

Rule: **the channel authenticates, not the script.** Configure the receiver adapter with the appropriate
security artifact. Reading a credential inside a Groovy script and writing it into an HTTP header is both
unnecessary and dangerous — tracing can render headers in clear text.

---

## 2. Credential and key management

Use **SAP BTP security material** on the tenant for every secret:

| Artifact type | Typical use |
| :--- | :--- |
| **User credentials** | Basic authentication, technical users, mail accounts |
| **OAuth2 client credentials** | Modern REST/OData APIs; the runtime handles token acquisition and renewal |
| **OAuth2 SAML bearer assertion** | User-context propagation |
| **Keystore entries** | Certificates and key pairs for mTLS, signing and decryption |
| **Secure parameters** | Values that must not appear in the integration flow model |

Lifecycle rules:
1. **Never hardcode** a secret in a script, a Content Modifier, a header, an exchange property, a parameter
   value, or a test file in git.
2. **Separate artifacts per environment and per counterpart.** Sharing one credential artifact across DEV,
   TEST and PROD makes rotation risky and audit meaningless.
3. **Know the expiry date of every certificate** and monitor it. An expired certificate is the most common
   cause of a "sudden" 3 a.m. interface outage.
4. **Rotate before expiry, and test the rotation** in a non-production tenant.
5. **Least privilege:** a technical user should have exactly the authorizations the interface needs — not a
   dialog user, not an administrator.
6. **Audit who can read security material** and keep that group small; treat read access as a privileged role.

> ⚠️ **Do not store credentials as exchange properties or headers.** MPL properties at Debug/Trace level and
> tracing expose them in clear text. If a script truly must obtain a secret (rare), retrieve it at the moment
> of use and never place it into a message field.

---

## 3. Transport security

| Concern | What to configure |
| :--- | :--- |
| **Server authentication** | Trust the receiver's certificate chain; never disable hostname verification to "make it work" |
| **Client authentication** | Keystore entry with the client certificate for mTLS |
| **Message integrity** | XML signature or PKCS#7 signature where the contract requires non-repudiation |
| **Confidentiality** | HTTPS/TLS at transport level; PGP or PKCS#7 encryption when the payload must be protected end to end, including at rest on a file server |
| **SFTP** | Host key verification; do not accept unknown host keys silently |
| **On-premise connectivity** | Cloud Connector with a dedicated technical user and least-privilege resource mappings |

> ⚠️ **"Disable certificate validation" is not a fix.** It converts a configuration problem into an
> undetected man-in-the-middle exposure. Fix the trust store instead.

---

## 4. Standard naming conventions

Consistent naming makes a 40-step flow readable, greppable and reviewable.

### Step name prefixes

| Prefix | Component type | Example |
| :--- | :--- | :--- |
| `CM_` | Content Modifier | `CM_InitProperties` |
| `SCR_` | Groovy / JavaScript script | `SCR_ValidatePayload` |
| `RTR_` | Content-Based Router | `RTR_RouteByStatus` |
| `SPL_` | Splitter | `SPL_IterateOrderLines` |
| `AGG_` | Aggregator | `AGG_CombineSplits` |
| `ENC_` | Content Enricher | `ENC_LookupCustomer` |
| `CAL_` | Request-Reply external call | `CAL_CallS4HanaOData` |
| `FLT_` | Filter | `FLT_StripUnneededNodes` |
| `XSL_` | XSLT mapping | `XSL_CanonicalTransform` |
| `MM_` | Message mapping | `MM_Orders_To_IDoc` |
| `SUB_` | ProcessDirect call to a sub-flow | `SUB_CentralErrorHandler` |
| `DLQ_` | Dead-letter handling | `DLQ_ParkFailedMessage` |

### Variable and artifact naming

| Element | Convention | Example |
| :--- | :--- | :--- |
| Exchange property | `p_<camelCase>` | `p_orderId`, `p_retryCount` |
| Header set by us | `h_<camelCase>` or the protocol name | `h_sourceSystem`, `Content-Type` |
| Externalized parameter | `<domain>_<name>` lowercase with underscores | `s4_host`, `sftp_incoming_dir` |
| Integration package | `<Domain> – <Purpose>` | `Sales – Order Replication` |
| iFlow | `<Direction> <Source> to <Target> – <Purpose>` | `Inbound CRM to S4 – Order Create` |
| Sub-flow | `SUB <Responsibility>` | `SUB Canonicalise Order` |

Naming rules:
- A step name states the **business intent**, not the technical action: `CM_SetOrderDefaults`, not `CM_1`.
- Never rename a step after go-live without updating the runbook — support instructions reference step names.
- Keep names stable across environments so that MPL searches and runbooks remain valid after a transport.

---

## 5. Parameter externalization

To promote a package from DEV to TEST to PROD without editing the model:

### Must be externalized
- Receiver hostnames and base URLs (including on-premise virtual hosts)
- Ports
- Credential alias names
- SFTP/FTP directories and file patterns
- Scheduler and polling intervals
- Receiver-specific identifiers (company codes, plant codes, business system IDs) where they differ per landscape
- Operational switches, e.g. `enable_debug_logging`, `enable_payload_logging`, `dry_run`

### Must NOT be externalized
- Internal routing logic or conditions
- Canonical schema mappings
- Anything that is part of the interface contract and must be identical everywhere

### Configuration governance
- Maintain a **configuration matrix** (parameter / description / DEV / TEST / PROD / secret?) as part of the
  interface documentation — see [templates/iflow-design-document.md](../templates/iflow-design-document.md).
- After every transport, verify the configured values. Drift between environments is one of the most
  common causes of "it worked in test".
- Security artifacts are **not** business content: they are deployed separately, per environment, and are
  never part of the transport of the integration package.

---

## 6. CSRF handling for SAP backends

SAP Gateway / S/4HANA OData services reject modifying calls (`POST`, `PUT`, `PATCH`, `DELETE`) that do not
carry a valid CSRF token and session cookie.

**With the standard OData adapter:** enable CSRF protection in the channel and let the adapter manage token
fetching and cookie handling. This is the default choice — do not reimplement it.

**With a plain HTTP adapter, the sequence is:**
1. Send a `GET` (or a `HEAD`, where supported) to the service root with the header `x-csrf-token: fetch`.
2. Read the `x-csrf-token` value and the session cookie(s) from the response.
3. Send the modifying request with both `x-csrf-token` and the cookie header.
4. Expect the session to expire: handle a rejected token by re-fetching and retrying once, rather than
   failing the whole message.

Implementation notes:
- Store the token and cookie in **exchange properties**, not headers, until the moment they are needed;
  then set them on the outbound request only.
- Take only the `name=value` part of each `Set-Cookie` entry; drop attributes such as `Path`, `Secure`,
  `HttpOnly` and `SameSite`.
- Never log the token or the cookie value.
- A ready-to-use implementation is in
  [examples/groovy-csrf-cookie-handler.groovy](../examples/groovy-csrf-cookie-handler.groovy).

---

## 7. Data protection in flows

- **Minimise:** do not transport fields the receiver does not need. Unused personal data in a payload is
  pure liability.
- **Do not persist what you do not need.** Payload attachments in the MPL and payload copies in Data Stores
  are stored personal data with a retention obligation.
- **Mask in logs:** never write identity numbers, bank details or credentials into MPL properties or
  attachments, even at Debug level.
- **Respect retention:** define how long failed messages and dead-letter entries are kept, and enforce it.
- **Trace carefully:** tracing and Debug logging make payloads visible to a wider audience on the tenant.
  Enable only for the troubleshooting window, then turn it off.

---

## 8. Roles and access governance

| Role area | Who needs it | Control |
| :--- | :--- | :--- |
| Design / modeling | Integration developers | In the development tenant primarily |
| Deploy / transport | Release managers, pipeline service accounts | Not granted to every developer in production |
| Monitor / operate | Support and operations | Read access to MPL, no design access |
| Security material | A small, named group | Treated as privileged access; audited |

Separation of duties: the person who models an interface should not be the only person who approves its
promotion to production. Access to production tenants should be exceptional and recorded — never the
default working environment.

---

## 9. Security review checklist

- [ ] No credential, token, key or password anywhere in the model, the scripts or the repository
- [ ] All environment specifics externalized as `{{parameters}}`
- [ ] Each environment has its own security artifacts (no sharing across DEV/TEST/PROD)
- [ ] Certificate expiry dates known and monitored; rotation tested
- [ ] Least-privilege technical users for every receiver
- [ ] TLS verification enabled; trust configured properly (no validation bypass)
- [ ] CSRF handled by the adapter where possible; otherwise by the documented token flow
- [ ] No credentials or personal data written to headers, properties, MPL attachments or dead-letter stores
- [ ] Tracing and Debug logging disabled by default in production
- [ ] Payload logging gated behind an externalized switch that defaults to off
- [ ] Production access limited and audited; deployment rights separated from design rights
- [ ] Dead-letter and MPL retention defined and enforced

---

## 10. Anti-patterns

| Anti-pattern | Consequence | Fix |
| :--- | :--- | :--- |
| Secret committed to git "temporarily" | Permanent exposure in history | Rotate the secret, purge the history, use the keystore/credential store |
| One credential artifact shared by all environments | Rotation breaks production unpredictably | Per-environment artifacts |
| Certificate expiry unmanaged | Sudden outage with no code change | Expiry monitoring and a rotation calendar |
| Hostname verification disabled | Undetected man-in-the-middle exposure | Fix the trust store |
| Credentials written into HTTP headers by a script | Clear-text exposure via tracing | Configure authentication on the channel |
| Payload logged unconditionally for "supportability" | Quota exhaustion plus a privacy problem | Gate behind a parameter; retention policy |
| Everyone has production deploy rights | Untested changes reach production | Separate design, transport and approval duties |
| Interface documentation only in someone's mailbox | Nobody can maintain or audit the interface | Versioned specification and design document |
| Renaming steps without updating the runbook | Support instructions point to non-existent steps | Treat step names as an interface |

---

## Further Reading

- [Apply the Highest Security Standards](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apply-the-highest-security-standards)
- [Decoupling Development from Transport Using Externalized Parameters](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/decoupling-development-from-transport-using-externalized-parameters)
- [Managing Security Material](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/managing-security-material)
- [Guidelines on Role Assignments](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/guidelines-on-role-assignments)
- [OWASP XXE Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
