# iFlow Package Authoring

**Sources**: Derived from SAP Cloud Integration manual export/import documentation, SAP Cloud Integration package conventions, and repository-local synthetic template validation.

Use this reference when a user asks an AI coding assistant to generate or modify a SAP Cloud Integration package that must import into the Cloud Integration designer.

## Importable Package Contract

Cloud Integration iFlows are not a single Markdown or XML snippet. A package export is a ZIP archive with an Eclipse-style integration project layout. Keep this shape intact:

```text
<package-root>/
├── .project
├── META-INF/
│   └── MANIFEST.MF
├── metainfo.prop
└── src/
    └── main/
        └── resources/
            ├── parameters.prop
            ├── parameters.propdef
            └── scenarioflows/
                └── integrationflow/
                    └── <IntegrationFlowId>.iflw
```

The `.iflw` file is BPMN 2.0 XML with SAP `ifl:*` extension metadata. The manifest must identify the artifact as an Integration Flow with `SAP-RuntimeProfile: iflmap`, `SAP-NodeType: IFLMAP`, and `SAP-BundleType: IntegrationFlow`.

## HTTPS-to-SFTP Template

Use `templates/https-to-sftp-iflow-package/` as the starting point for HTTPS-to-SFTP generation. It is stored unzipped so changes are reviewable.

Customize only these placeholders unless the user explicitly asks for a different design:

| Placeholder | Purpose |
|-------------|---------|
| `{{HTTPS_ENDPOINT_PATH}}` | HTTPS sender path such as `/inbound/orders` |
| `{{SFTP_HOST}}` | SFTP host supplied by the user or externalized per landscape |
| `{{SFTP_PORT}}` | SFTP port, usually `22` |
| `{{SFTP_DIRECTORY}}` | Target directory on the SFTP server |
| `{{SFTP_FILENAME}}` | Target file name or expression |
| `{{SFTP_CREDENTIAL_ALIAS}}` | Existing Cloud Integration security material alias |

Do not invent real hostnames, credentials, tenant URLs, certificates, or deployed runtime state. Keep these values externalized so the same package can move across development, test, and production tenants.

## ZIP Creation Rules

Create the ZIP from inside the package root so `.project`, `META-INF/`, `metainfo.prop`, and `src/` are at archive root:

```bash
cd templates/https-to-sftp-iflow-package
zip -r ../https-to-sftp-iflow-package.zip .project META-INF metainfo.prop src
```

Do not zip the parent folder itself. A parent-folder wrapper changes the archive root and can make Cloud Integration import fail or create the wrong package structure.

## Validation Before Sharing

Before giving the archive to a user:

1. Run XML validation on `.project` and the `.iflw` file.
2. Confirm there is exactly one `.iflw` under `src/main/resources/scenarioflows/integrationflow/`.
3. Confirm the manifest contains the Integration Flow headers.
4. Confirm all tenant-specific settings are placeholders or externalized parameters.
5. Import into a non-production Cloud Integration tenant when available and open the iFlow in the designer.

Local structure checks cannot prove tenant import. If no tenant is available, state that tenant import is pending.
