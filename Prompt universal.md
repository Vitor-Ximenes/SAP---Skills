# Prompt universal

Fonte da verdade: https://github.com/Vitor-Ximenes/SAP---Skills/blob/main/Prompt%20universal.md

Use este arquivo em teste, criação e modificação SAP. Ele não é só de teste unitário.

---

## Memória

1. Antes de testar, criar ou modificar programa, classe, CDS, RAP, Fiori ou RDT, ler a versão mais recente deste arquivo no GitHub.
2. Quando o usuário ensinar algo que melhore esse trabalho, atualizar este arquivo e publicar no repositório `Vitor-Ximenes/SAP---Skills` na hora. Não esperar o pedido "salve na memória".
3. Não gravar senha, token, cookie ou dado pessoal.

---

## Quando vale

- Teste, evidência, SAP GUI e RDT
- Criação de programa ou objeto, quando o usuário pedir
- Modificação, quando o usuário pedir e a conexão permitir escrita

---

## Escrita no sistema

- MCP do DEV client 100 (`mcp-abap-adt`) está somente leitura até o usuário liberar modificação. Nesse modo, não gravar objeto no SAP. Entregar o trecho para colar.
- Com escrita liberada, alterar somente o objeto nomeado. Não aproveitar para mudar vizinho, binding ou classe que não foi pedido.

---

## Criação e modificação

1. Ler o objeto e o que ele chama antes de propor mudança.
2. Preferir CDS released do de-para do projeto (`docs/sap-ddic-to-cds-successors.md`) em vez de tabela ou função clássica, quando houver successor.
3. Não ampliar o escopo. Corrigir o que foi pedido.
4. Action RAP `static` não recebe a linha marcada no Fiori. A key traz `%cid` e `%param`. A linha (`Lifnr` / `Parvw` / `Bukrs`) só chega em action de instância.
5. No app Fiori, o popup da action de instância mostra os parâmetros. No Try it out OData V2, a mesma action lista também as chaves. O Swagger do binding UI não lista o POST da action `static`.
6. Parâmetro `abap_boolean` no Postman é `true` ou `false`, sem aspas. Texto leva aspas (`e_bukrs='LC01'`).
7. Se `e_keep` for caractere, o valor aceito é `X`, `x` ou vazio. Outro valor deve ir para `failed` da action, senão o Postman não mostra erro.

---

## Objetivo do teste no SAP GUI

1. Identificar no código o que a tela exige (arquivo, parâmetros, rádios, F4, botões).
2. Executar a transação no mandante informado sem ficar travado em diálogos.
3. Tratar toda trava interativa conhecida.
4. Capturar prints (antes do F8, popups relevantes, resultado) e, ao final, preencher o RDT no template Word sem alterar cores ou estilos.

### Entradas

- Transação e/ou programa
- Sistema, ambiente e mandante
- Caminho do template RDT Word, quando houver evidência
- Pasta de trabalho para prints e arquivos
- Se pode criar massa de teste sintética

---

## Fase 0 — Ler o código antes de clicar

| O que procurar | Para quê |
|---|---|
| `PARAMETERS` / `SELECT-OPTIONS` / rádios / checkbox | Campos da tela |
| `AT SELECTION-SCREEN ON VALUE-REQUEST` / `F4IF_*` / `HELP-*` | F4 / matchcode |
| `cl_gui_frontend_services=>file_open_dialog` / `file_save_dialog` / `gui_upload` / `gui_download` | Explorer Windows |
| `POPUP_TO_CONFIRM` / `CALL SCREEN` / `POPUP_*` | Popups Sim/Não |
| Botão de template / `Template_*.xlsx` | Template oficial |
| Estrutura do arquivo (`TYPES`, DDIC, "linha 2 = dados") | Montar Excel/CSV |
| `MESSAGE ... TYPE 'E'/'A'/'X'` e `TRY/CATCH` | Resultado esperado vs dump |

Se existir Download Template, usar o arquivo gerado pelo programa.

---

## Fase 1 — Travas do SAP GUI

### A) Popup de confirmação (`SAPLSPO1`)

- `wnd[1]/usr/btnBUTTON_1` = Sim. `btnBUTTON_2` = Não.
- Não usar Enter se o botão padrão for Não.
- Teste positivo: clicar Sim. Print quando for evidência.

### B0) "Um script está tentando acessar SAP GUI"

- Janela Win32, não é `wnd[1]`.
- Clicar OK.
- Watcher a cada 300–500 ms. Não confundir com a janela principal do SAP Logon.
- Opcional: `WarnOnAttach=0`, `WarnOnConnection=0`, `UserScripting=1`. Manter o watcher.

### B) Segurança SAP GUI

- Janela Windows `#32770`.
- Primeira leitura ou gravação: Memorizar + Permitir.
- Watcher em paralelo. Sem isso a automação trava.

### C) Explorer (abrir/salvar)

- Preferir preencher o caminho no parâmetro e não abrir o explorer.
- Se o diálogo abrir, informar o caminho completo e confirmar.

### D) F4 / matchcode

- Preferir valor lido por ADT, CDS ou tabela.
- Se abrir o F4, selecionar a linha e confirmar. Não deixar a janela aberta.

### E) Outros modais

- Logon múltiplo, express document, catálogo de variantes (`SAPLSVAR`).
- Variante útil: selecionar e confirmar. Se não for usar, cancelar e seguir com os parâmetros.
- Dump ou exceção na status bar: print e o texto exato.

### F) Job e espera

- Polling com timeout. Sem loop infinito.

### G) Erro tratado no CATCH

- Mensagem na status bar é resultado do teste. Documentar. Não repetir o cenário em loop.

---

## Fase 2 — Arquivo de entrada

1. Se houver Download Template, usar esse arquivo.
2. Senão, montar pelo código.
3. Cabeçalho na linha 1. Dados a partir da linha 2.
4. Preencher o path na tela antes do F8.
5. Se o usuário pediu para não alterar o arquivo, não reformatar.

### Números

| Situação | Como gravar | Exemplo | Não usar |
|---|---|---|---|
| Inteiro | Só dígitos | `100` | `100.00` / `100,00` |
| Decimal | Ponto | `100.50` | `100,50` |
| BR completo | Ponto de milhar e vírgula decimal | `1.234.567,89` | vírgula curta |

Vírgula decimal curta (`100,00`) e `.00` em inteiro quebram carga XCO (`CX_SY_REF_IS_INITIAL`).

---

## Fase 3 — Execução

Para cada rádio, checkbox ou variante:

1. Abrir a transação.
2. Print do ambiente (mandante no rodapé).
3. Preencher e marcar a opção.
4. Print antes do F8.
5. F8 e tratar Sim, Permitir e demais travas.
6. Print do resultado.
7. Registrar APROVADO, REPROVADO ou PARCIAL com o texto da status bar.

---

## Fase 4 — Entrega do teste

1. Prints nomeados.
2. Arquivos usados.
3. Word do template RDT, sem mudar cor, logo, estilo ou estrutura.
4. Parar e esperar o próximo pedido.

---

## Não fazer

- Enter em popup cujo padrão é Não.
- Tratar Segurança SAP GUI como `wnd[1]` do Scripting.
- Inventar layout de Excel quando existe template.
- Loop sem timeout.
- Mudar cores do RDT.
- Seguir para o próximo passo sem o usuário pedir.
- Gravar senha neste arquivo.
- Alterar objeto SAP com MCP somente leitura.
- Alterar objeto que o usuário não nomeou.

---

## Aprendizados

- 2026-09-24 — MCP DEV client 100 é somente leitura. Modificação no sistema fica para quando o usuário liberar escrita.
- 2026-09-24 — Action RAP static não enxerga a linha do rádio. Instância enxerga. Popup do Fiori pede os parâmetros. Try it out V2 da instância também lista as chaves.
- 2026-09-24 — `Edm.Boolean` no Postman: `e_keep=true`, sem aspas. Campo caractere: só `X`, `x` ou vazio; o erro precisa ir para `failed`.
- 2026-09-23 — Teste ZPS063: três rádios, template oficial de PEP, destravar SAP GUI, RDT com APROVADO em verde e REPROVADO em vermelho, sem mudar a cor do template.
