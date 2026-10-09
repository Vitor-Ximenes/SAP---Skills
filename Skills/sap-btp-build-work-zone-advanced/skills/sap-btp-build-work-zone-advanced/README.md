# BTP Build Work Zone Advanced Skill

Claude Code skill for SAP Build Work Zone, advanced edition digital workplace development.

## Capability Index

| Capability | Status |
|------------|--------|
| Commands | 1: `/work-zone-content-check` |
| Agents | 0 |
| Hooks | No |
| MCP | No |
| LSP | No |
| Source Freshness | `last_verified`: 2025-11-27; Work Zone template/source freshness pending docs-only recheck. |
| Verification | `npm run validate`; tenant content import/export checks pending. |

## Overview

This skill provides comprehensive guidance for implementing SAP Build Work Zone, advanced edition - a digital workplace platform for unified access to business applications, collaboration, and content management.

## Keywords for Discovery

### Primary Keywords
- SAP Build Work Zone advanced edition
- digital workplace
- SAP Work Zone
- SAP Jam
- enterprise portal
- intranet platform

### Core Components
- workspaces
- workpages
- UI Integration Cards
- content packages
- workspace templates
- widgets
- feeds
- wikis
- blogs
- forums
- knowledge base

### Development
- SAP Business Application Studio
- UI Card development
- SAPUI5 cards
- card types
- content package development
- workspace template development
- chatbot development
- SAP Conversational AI

### Administration
- Administration Console
- user management
- role assignment
- theming
- branding
- email templates
- feature enablement
- compliance monitor
- profanity monitor

### APIs
- SCIM API
- OData API
- REST API
- Workzone API Client
- webhooks
- business records
- 2-legged OAuth

### Integrations
- Microsoft Teams
- Microsoft 365
- Office 365 SharePoint
- Google Drive
- SAP SuccessFactors
- SAP Cloud for Customer
- SAP Task Center
- SAP Build Process Automation

### Authentication
- SAML IdP
- OAuth clients
- Single Sign-On
- SSO
- trusted certificate authority

### User Management
- internal users
- external users
- user lists
- static user lists
- dynamic user lists
- imported user lists
- SCIM provisioning

### Content
- content permissions
- content templates
- content administration
- file sharing
- multimedia
- video upload
- audio upload

### Reports
- activity summary
- user contribution
- workspace activity
- compliance report
- engagement report
- analytics

### Troubleshooting
- rate limiting
- X-RateLimit headers
- HTTP 503
- OAuth authentication
- card deployment

## Skill Contents

### Main Files
- `SKILL.md` - Primary skill instructions and quick reference
- `README.md` - This file with keywords

### Reference Files
- `references/ui-integration-cards.md` - Card development guide
- `references/content-packages.md` - Content package development
- `references/workspace-templates.md` - Template creation
- `references/chatbots.md` - Chatbot configuration
- `references/api-reference.md` - SCIM and OData APIs
- `references/security.md` - Security configuration
- `references/administration.md` - Administration and lifecycle operations
- `references/auditing.md` - Audit logging and monitoring
- `references/mobile-app.md` - Mobile app enablement
- `references/notifications.md` - Notification configuration
- `references/workspaces.md` - Workspace management
- `references/troubleshooting.md` - Error resolution

### Templates
- `templates/card-manifest.json` - UI Integration Card manifest template
- `templates/content-package-manifest.json` - Content package manifest template
- `templates/dt-configuration.js` - Deployment configuration template
- `templates/workspace-template-config.json` - Workspace template configuration

## Use Cases

This skill should be triggered when users ask about:
- Creating workspaces and workpages
- Developing UI Integration Cards
- Building content packages
- Creating workspace templates
- Configuring chatbots
- Setting up Microsoft Teams integration
- Implementing SCIM API user provisioning
- Configuring OData business records
- Managing themes and branding
- Setting up SSO and authentication
- Working with Administration Console
- Enabling site features
- Troubleshooting deployment issues

## Version Information

- **Created**: 2025-11-22
- **Documentation Source**: SAP Help Portal (November 2025)
- **GitHub Docs**: [https://github.com/SAP-docs/sap-btp-build-work-zone-advanced](https://github.com/SAP-docs/sap-btp-build-work-zone-advanced)
- **SAP Help Portal**: [https://help.sap.com/docs/build-work-zone-advanced-edition](https://help.sap.com/docs/build-work-zone-advanced-edition)

## License

GPL-3.0 License

## Related Skills

- sap-btp-setup
- sapui5
- sap-fiori-elements
- sap-cloud-identity
