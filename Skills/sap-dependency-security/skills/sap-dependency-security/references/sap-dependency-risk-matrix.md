# SAP Dependency Risk Matrix

Use this reference when a dependency update touches SAP development tooling, SAP runtime libraries, deployment assets, or MCP servers. The default SAP posture is stricter than the generic dependency workflow: 14-day cooldown, exact pins for executable tooling, frozen installs, and manual review for tenant-connected tools.

## Ecosystem Matrix

| Area | Typical Artifacts | Main Risks | Required Checks |
|------|-------------------|------------|-----------------|
| CAP / UI5 / Fiori Node tooling | `package.json`, lockfiles, `.npmrc`, `ui5.yaml`, `mta.yaml` | Lifecycle scripts, transitive tooling compromise, UI build drift | Exact tool pins, frozen install, script blocking, lockfile review, `npm audit` or Socket/OSV |
| SAP MCP servers | `.mcp.json`, env vars, local source checkout | Executing unreviewed packages with SAP project or tenant access | Exact npm pin or source commit pin, no `@latest`, inventory entry, validator pass |
| SAP Cloud SDK Java / CAP Java | `pom.xml`, `mvnw`, `.mvn/maven.config` | BOM drift, repository substitution, vulnerable transitive JARs | Maven wrapper, strict checksums, dependency tree review, OSV-Scanner |
| Gradle Java services | `build.gradle`, `settings.gradle`, `gradle.lockfile` | Dynamic versions, unverified artifacts, plugin portal drift | Dependency locking, verification metadata, no dynamic versions, OSV-Scanner |
| SAP AI SDK / HANA ML Python | `pyproject.toml`, `requirements*.txt`, `uv.lock` | Unlocked wheels, extras pulling broad provider trees, typosquats | Lockfile, hashes where practical, `pip-audit`, `uv pip compile` or equivalent |
| Containers / Kyma | `Dockerfile`, Helm/Kubernetes manifests | Base image CVEs, mutable tags, privileged runtime | Digest-pinned base images, Trivy scan, non-root user, minimal capabilities |
| BTP / CF / mbt tooling | `mta.yaml`, `xs-security.json`, pipeline scripts | Global CLI drift, service binding changes, accidental deploy target changes | Pin CLI versions in CI, review service bindings, verify `cf target`, inspect MTAR diff |
| ABAP / gCTS / transports | Software components, transport requests, abapGit repos | Transport dependency gaps, unreviewed generated objects, cross-system drift | Import queue review, dependency order check, ATC/security checks, rollback transport |

## SAP Defaults

- Use a 14-day cooldown for SAP enterprise projects unless a maintainer records an exception.
- Prefer project-local tooling over global installs in CI.
- Treat MCP servers as executable dependencies, not passive documentation.
- Keep runtime authorization design in the relevant SAP skill. This skill covers dependency, package, source, and executable trust.
- For credentialed MCPs, review both package/source trust and whether the configured credential is least-privilege.

## Review Prompts

- What executable code changes during this update: package tarball, JAR, wheel, container layer, or source checkout?
- Does the update alter tenant access, deployment targets, service bindings, or generated artifacts?
- Is the new version old enough for the SAP cooldown, or is there an explicit exception?
- Can CI reproduce the install from locked inputs without network-time latest resolution?
- Is rollback possible without manually reconstructing tool versions or generated output?
