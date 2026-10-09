# SAP Skills

Repositório das skills SAP do Vitor. Tudo que é skill fica na pasta [`Skills/`](Skills). São **72 skills**.

Na raiz ficam só este índice, o prompt operacional e o compilado. A pasta `plugins` e a pasta `rap-skills` não existem mais.

## Como achar uma skill

Abra `Skills/` e procure o `SKILL.md`.

| Onde | Caminho do `SKILL.md` |
|---|---|
| Pacote técnico (ABAP, BTP, UI5, SAC…) | `Skills/<pacote>/skills/<nome>/SKILL.md` |
| ARC-1, RAP e CPI | `Skills/<nome>/SKILL.md` |
| Funcional (PP, MM, SD, FI e os outros módulos) | `Skills/sap-functional-skill/skills/<nome>/SKILL.md` |

O arquivo que o agente carrega é sempre o `SKILL.md`. Os `.md` de apoio (referências, checklists, README do pacote) ficam na mesma pasta da skill.

## Regra deste README

Este arquivo é o catálogo. Sempre que uma skill entrar, sair ou mudar de pasta, a linha correspondente daqui tem que ser atualizada no mesmo commit: nome, caminho e para que serve. Se o README e a pasta `Skills/` divergirem, o README está errado.

## Fora de `Skills/`

| Arquivo | O que é |
|---|---|
| `Prompt universal.md` | Regras operacionais do dia a dia (teste, massa, mandante). Não é skill. |
| `SAP-SKILLS-COMPLETE.md` | Cópia única, em um arquivo só, de várias skills técnicas. A fonte usada no dia a dia é a pasta `Skills/`. |
| `.claude-plugin/marketplace.json` | Índice do marketplace. Aponta para `Skills/`. Não guarda skill nem markdown. |

Cada pacote técnico ainda traz `.claude-plugin/plugin.json`. Isso é o manifesto daquele pacote, não o conteúdo da skill.

## ABAP, CDS e Clean Core

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-abap | `Skills/sap-abap/skills/sap-abap` | ABAP clássico e ABAP Cloud: tabelas internas, SQL, OO, RAP, exceções e teste unitário. |
| sap-abap-cds | `Skills/sap-abap-cds/skills/sap-abap-cds` | CDS: views, anotações, associações, DCL, parâmetros e erros de modelagem. |
| sap-api-style | `Skills/sap-api-style/skills/sap-api-style` | Como documentar API SAP no padrão do style guide. |
| sap-api-policy | `Skills/sap-api-policy/skills/sap-api-policy` | Avalia se um uso de API SAP está alinhado à API Policy (publicado, interno, Clean Core). |
| sap-clean-core-atc | `Skills/sap-clean-core-atc/skills/sap-clean-core-atc` | Classifica objeto Z/Y nos níveis A–D de Clean Core pelo que a API usa. |
| sap-sqlscript | `Skills/sap-sqlscript/skills/sap-sqlscript` | SQLScript, procedure HANA e método AMDP. |

## RAP

Cinco skills que estavam em `rap-skills`. A pasta antiga foi removida. As notas do repositório RAP ficaram em `Skills/rap-documentacao` (não é skill).

| Skill | Caminho | Para que serve |
|---|---|---|
| rap-behavior | `Skills/rap-behavior` | Behavior definition: validação, determination, action, draft, autorização, side effect e evento. |
| rap-cds | `Skills/rap-cds` | CDS do RAP: root, child, projection e metadata extension. |
| rap-generator | `Skills/rap-generator` | Gera a pilha inteira do business object RAP a partir do requisito. |
| rap-testing | `Skills/rap-testing` | ABAP Unit do RAP, com test doubles e EML. |
| rap-troubleshoot | `Skills/rap-troubleshoot` | Erro de ativação, dump, draft, autorização e EML no RAP. |

## ARC-1

Pacote em `Skills/arc-1-skills`. Cada subpasta é uma skill.

| Skill | Para que serve |
|---|---|
| analyze-chat-session | Revisa a conversa e o uso das ferramentas ARC-1. |
| bootstrap-system-context | Lê o sistema SAP alvo e grava release, componentes e restrições antes de codar. |
| explain-abap-code | Explica um objeto ABAP com as dependências. |
| generate-abap-unit-test | Gera teste ABAP Unit da classe. |
| generate-cds-unit-test | Gera teste de CDS com o framework de test double. |
| generate-rap-service | Gera o serviço RAP OData a partir da descrição do objeto. |
| generate-rap-service-researched | Planeja o serviço RAP com pesquisa no sistema antes de escrever código. |
| generate-rap-logic | Preenche determination, validation e action de um behavior que já existe. |
| generate-analytics-star-schema | Gera modelo analítico CDS (cube, dimensão e texto). |
| generate-cds-analytical-query | Gera query analítica em cima de um cube. |
| debug-slow-sql | Investiga SQL ABAP ou OData lento. |
| migrate-custom-code | Migração de código Z guiada por ATC e substituição Clean Core. |
| migrate-segw-to-rap | Leva um serviço SEGW (OData V2) para RAP V4. |
| sap-clean-core-atc | A mesma auditoria A–D, na versão do pacote ARC-1. |
| sap-migration-dossier | Dossiê de prontidão ECC para S/4 do código customizado. |
| sap-object-documenter | Documenta em markdown os objetos Z de um pacote. |
| sap-unused-code | Acha objeto Z/Y sem uso, cruzando SCMON e where-used. |
| sap-transport-overview | Lista requests abertas, dono e tamanho. |
| sap-transport-review | Mostra o que mudou na request ou no rascunho. |
| setup-abap-mirror | Espelha um pacote ABAP no disco, no estilo abapGit. |
| convert-ui5-to-fiori-elements | Gera app Fiori Elements V4 (list report e object page). |
| modernize-ui5-app | Moderniza app UI5 freestyle antigo para UI5 atual. |

## Fiori e UI5

| Skill | Caminho | Para que serve |
|---|---|---|
| sapui5 | `Skills/sapui5/skills/sapui5` | App SAPUI5: freestyle, Fiori Elements, binding, rota e OData. |
| sapui5-cli | `Skills/sapui5-cli/skills/sapui5-cli` | UI5 Tooling: `ui5.yaml`, build e servidor local. |
| sapui5-linter | `Skills/sapui5-linter/skills/sapui5-linter` | UI5 Linter: API depreciada e análise estática. |
| sap-fiori-tools | `Skills/sap-fiori-tools/skills/sap-fiori-tools` | Extensões Fiori tools no VS Code e no BAS. |
| sap-browser-automation | `Skills/sap-browser-automation/skills/sap-browser-automation` | Operar tela web SAP autenticada pelo browser. |

## Integração e CPI

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-cpi-iflow | `Skills/sap-cpi-iflow` | iFlow no Integration Suite: desenho, Groovy, adapter, erro, segurança, transporte e operação. |
| sap-btp-integration-suite | `Skills/sap-btp-integration-suite/skills/sap-btp-integration-suite` | Integration Suite no BTP, além do iFlow em si. |
| sap-btp-connectivity | `Skills/sap-btp-connectivity/skills/sap-btp-connectivity` | Conectividade BTP, inclusive Cloud Connector. |
| sap-cap-capire | `Skills/sap-cap-capire/skills/sap-cap-capire` | CAP: modelo CDS, serviço e deploy. |

## BTP

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-btp-best-practices | `Skills/sap-btp-best-practices/skills/sap-btp-best-practices` | Conta, segurança e operação do BTP. |
| sap-btp-cloud-platform | `Skills/sap-btp-cloud-platform/skills/sap-btp-cloud-platform` | Plataforma BTP e ABAP environment. |
| sap-btp-developer-guide | `Skills/sap-btp-developer-guide/skills/sap-btp-developer-guide` | Guia de desenvolvimento no BTP. |
| sap-btp-business-application-studio | `Skills/sap-btp-business-application-studio/skills/sap-btp-business-application-studio` | SAP Business Application Studio. |
| sap-btp-cloud-identity-services | `Skills/sap-btp-cloud-identity-services/skills/sap-btp-cloud-identity-services` | Identity Authentication e Identity Provisioning. |
| sap-btp-cloud-logging | `Skills/sap-btp-cloud-logging/skills/sap-btp-cloud-logging` | Cloud Logging. |
| sap-btp-cloud-transport-management | `Skills/sap-btp-cloud-transport-management/skills/sap-btp-cloud-transport-management` | Cloud Transport Management. |
| sap-btp-cias | `Skills/sap-btp-cias/skills/sap-btp-cias` | Cloud Integration Automation Service. |
| sap-btp-job-scheduling | `Skills/sap-btp-job-scheduling/skills/sap-btp-job-scheduling` | Job Scheduling Service. |
| sap-btp-service-manager | `Skills/sap-btp-service-manager/skills/sap-btp-service-manager` | Service Manager: instância, binding e broker. |
| sap-btp-master-data-integration | `Skills/sap-btp-master-data-integration/skills/sap-btp-master-data-integration` | Master Data Integration. |
| sap-btp-build-work-zone-advanced | `Skills/sap-btp-build-work-zone-advanced/skills/sap-btp-build-work-zone-advanced` | Build Work Zone, advanced edition. |
| sap-btp-intelligent-situation-automation | `Skills/sap-btp-intelligent-situation-automation/skills/sap-btp-intelligent-situation-automation` | Situação legada do Intelligent Situation Automation. Só para tenant que ainda usa. |
| sap-dependency-security | `Skills/sap-dependency-security/skills/sap-dependency-security` | Segurança de dependência e confiança de executável MCP. |

## Dados, HANA, BW e SAC

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-hana-cli | `Skills/sap-hana-cli/skills/sap-hana-cli` | CLI do HANA: objetos, HDI e administração. |
| sap-hana-ml | `Skills/sap-hana-ml/skills/sap-hana-ml` | Machine learning in-database com `hana-ml`. |
| sap-hana-cloud-data-intelligence | `Skills/sap-hana-cloud-data-intelligence/skills/sap-hana-cloud-data-intelligence` | Pipelines do SAP Data Intelligence Cloud. |
| sap-datasphere | `Skills/sap-datasphere/skills/sap-datasphere` | Datasphere: modelo analítico, fluxo e replicação. |
| sap-bw-query | `Skills/sap-bw-query/skills/sap-bw-query` | Query BW e metadado de InfoProvider. |
| sap-sac-planning | `Skills/sap-sac-planning/skills/sap-sac-planning` | Planejamento no SAP Analytics Cloud. |
| sap-sac-scripting | `Skills/sap-sac-scripting/skills/sap-sac-scripting` | Script de Analytics Designer e Optimized Story. |
| sap-sac-custom-widget | `Skills/sap-sac-custom-widget/skills/sap-sac-custom-widget` | Custom widget do SAC. |
| sap-sac-test-automation | `Skills/sap-sac-test-automation/skills/sap-sac-test-automation` | Teste automatizado de story e planning no SAC. |
| sap-rpt1 | `Skills/sap-rpt1/skills/sap-rpt1` | Previsão tabular local com SAP-RPT-1 em CSV de FI/CO. |

## IA no BTP

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-ai-core | `Skills/sap-ai-core/skills/sap-ai-core` | SAP AI Core e AI Launchpad. |
| sap-cloud-sdk-ai | `Skills/sap-cloud-sdk-ai/skills/sap-cloud-sdk-ai` | SDK de IA para JavaScript e Java. |
| sap-cloud-sdk-ai-python | `Skills/sap-cloud-sdk-ai-python/skills/sap-cloud-sdk-ai-python` | SDK de IA para Python. |

## Funcional

Pacote `Skills/sap-functional-skill`. A base de módulos é a `sap-trench`.

| Skill | Caminho | Para que serve |
|---|---|---|
| sap-trench | `Skills/sap-functional-skill/skills/sap-trench-skill` | Consulta funcional. Carrega o módulo da pergunta. |
| sap-sto-create | `Skills/sap-functional-skill/skills/sap-sto-create` | Cria STO no S/4 pela OData. Obrigatório ver a prévia antes de gravar. |
| sap-stock-availability | `Skills/sap-functional-skill/skills/sap-stock-availability` | Consulta estoque, ATP e MD04. Só leitura. |

Módulos dentro de `sap-trench-skill/references/`:

| Módulo | Arquivo | Foco |
|---|---|---|
| ABAP | `abap.md` | Sintaxe, performance, BAdI, debug |
| MM | `mm.md` | Pedido, estoque, STO, determinação de conta |
| SD | `sd.md` | Ordem, preço, remessa, faturamento, crédito, ATP |
| FI/CO | `fico.md` | Documento, câmbio, split, COPA, pagamento |
| PP | `pp.md` | Ordem de produção, BOM, roteiro, MRP, ATP, MTO |
| WM | `wm.md` | Transfer order, posição, inventário |
| PM | `pm.md` | Equipamento, local de instalação, ordem de manutenção |
| QM | `qm.md` | Lote de inspeção, decisão de uso, notificação |
| VMS | `vms.md` | IS-AUTO, VELO, IDoc |
| Integração | `integration.md` | PI/PO, IDoc, Proxy, OData |
| Autorização | `auth.md` | AUTHORITY-CHECK, papel, SU53 |
| Impressão | `print.md` | SmartForms, SAPscript, NACE |
| Referência | `reference-tables.md` | Transação, tabela e BAPI |
| Troubleshooting | `troubleshooting.md` | Casos reais, CASE-001 a CASE-015 |
