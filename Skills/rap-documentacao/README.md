# RAP Skills for Claude Code

**GitHub:** [github.com/weiserman/rap-skills](https://github.com/weiserman/rap-skills)

A set of reusable Claude Code skills for building SAP RAP (RESTful Application Programming Model) Business Objects on ABAP Cloud / BTP using AI-assisted development.

These skills give Claude Code the patterns, naming conventions, and step-by-step workflows needed to generate, enhance, test, and troubleshoot RAP BOs — all executed directly in your SAP system via the VSP MCP server.

---

## What This Repo Does

When you clone this repo, you get a ready-to-use set of **skill files** that teach Claude Code how to do RAP development properly. Each skill is a `SKILL.md` file — a detailed playbook that Claude reads before performing a task. Without these skills, Claude would generate generic ABAP code; with them, it follows SAP-specific RAP patterns, naming conventions, and best practices for your exact deployment target.

The repo uses **branches** to deliver different skill variants depending on your SAP system and implementation pattern. Not all RAP development is the same — the syntax, available APIs, BDEF patterns, and tooling constraints differ significantly between BTP Steampunk, S/4HANA Cloud, and on-premise. By checking out the branch that matches your project, you get skills that only reference patterns valid for your target — no misleading cross-environment advice.

### Branch overview

| Branch | Target | Pattern | Status |
|--------|--------|---------|--------|
| `main` | BTP ABAP Environment | Managed | ✅ Available |
| `btp/unmanaged` | BTP ABAP Environment | Unmanaged (BAPI wrappers) | ✅ Available |
| `s4-public/managed` | S/4HANA Cloud Public Ed. | Managed | 🔜 Planned |
| `s4-private/managed` | S/4HANA Cloud Private Ed. | Managed | 🔜 Planned |
| `s4-onprem/managed` | S/4HANA On-Premise | Managed | 🔜 Planned |

See [REPO_CONCEPT.md](REPO_CONCEPT.md) for the full branch strategy and roadmap.

### Quick start

```bash
# Clone the default branch (BTP managed — most common scenario)
git clone https://github.com/weiserman/rap-skills.git .claude/skills/rap

# Or clone a specific branch for your target
git clone -b btp/unmanaged https://github.com/weiserman/rap-skills.git .claude/skills/rap
```

This places the skill files where Claude Code expects them (`.claude/skills/rap/`). When you launch Claude Code from your project root, it automatically discovers these skills and uses them to guide RAP development tasks.

After cloning, the only setup left is connecting Claude Code to your SAP system via the VSP MCP server (see below).

---

## Prerequisites

Before using these skills you need three things set up: Claude Code, the VSP MCP server, and a connected SAP system.

### 1. Install Claude Code

Claude Code is Anthropic's CLI tool for agentic coding. Install it globally via npm:

```bash
npm install -g @anthropic-ai/claude-code
```

Verify the install:

```bash
claude --version
```

If you don't have an Anthropic API key yet, Claude Code will walk you through authentication on first launch. See the official docs at https://docs.claude.com for details.

### 2. Install the VSP MCP Server

VSP (Vibing Steampunk) is the bridge that connects Claude Code to your SAP system. It exposes ABAP Development Tools (ADT) APIs as MCP tools — giving Claude the ability to read source code, write objects, activate, debug, run tests, and more.

**GitHub:** https://github.com/oisee/vibing-steampunk

#### Option A — Download a pre-built binary

Go to the [Releases](https://github.com/oisee/vibing-steampunk/releases) page and download the binary for your OS (macOS, Linux, Windows). Place it somewhere on your PATH — for example:

```bash
# macOS / Linux
mv vsp /usr/local/bin/vsp
chmod +x /usr/local/bin/vsp
```

#### Option B — Build from source

```bash
git clone https://github.com/oisee/vibing-steampunk.git
cd vibing-steampunk
make build
```

The binary will be in the `bin/` directory. Move it to your PATH as above.

#### Verify VSP is working

```bash
vsp --version
```

### 3. Configure the MCP Connection

Claude Code discovers MCP servers through a `.mcp.json` file. Create this file in your project root (or home directory for global use).

#### Basic configuration

Create `.mcp.json`:

```json
{
  "mcpServers": {
    "vsp-btp": {
      "command": "/usr/local/bin/vsp",
      "env": {
        "SAP_URL": "https://your-sap-host:44300",
        "SAP_USER": "YOUR_USER",
        "SAP_PASSWORD": "YOUR_PASSWORD",
        "SAP_CLIENT": "001"
      }
    }
  }
}
```

Replace the values with your actual SAP system details.

#### Connecting to ABAP Cloud / BTP

If you're connecting VSP to an ABAP Cloud (Steampunk) or BTP environment, the setup has some differences from on-premise — including service key configuration and authentication. See this walkthrough for a step-by-step guide:

**[Vibe Steam Punk (VSP) for ABAP Cloud — Mac + Claude](https://medium.com/@warren_eiserman/vibe-steam-punk-vsp-for-abap-cloud-mac-claude-2864d601978f)**

#### Configuration options

| Environment Variable | Required | Description |
|---------------------|----------|-------------|
| `SAP_URL` | Yes | SAP system URL including port (e.g., `https://myhost:44300`) |
| `SAP_USER` | Yes | Your SAP username |
| `SAP_PASSWORD` | Yes | Your SAP password |
| `SAP_CLIENT` | No | SAP client number (defaults to `001`) |
| `SAP_MODE` | No | `focused` (default, 52 tools) or `expert` (99 tools) |
| `SAP_INSECURE` | No | Set to `true` to skip TLS certificate verification |
| `SAP_COOKIE_FILE` | No | Path to Netscape cookie file for SSO environments |
| `SAP_ALLOW_TRANSPORTABLE_EDITS` | No | Set to `true` to edit objects in transportable packages |

#### Security notes

- Never commit `.mcp.json` with real credentials to version control. Add it to `.gitignore`.
- For shared environments, consider using environment variables or a credential manager instead of hardcoding passwords.
- The `SAP_INSECURE` flag should only be used for development systems with self-signed certificates.

### 4. Verify the Connection

Launch Claude Code and test the connection:

```bash
claude
```

Then ask Claude:

> Check my SAP connection info

Claude will call the `GetConnectionInfo` tool and display your system URL, user, and client. If this works, you're ready to go.

---

## Installing the Skills

These skills tell Claude Code how to approach RAP development tasks. Clone the repo into your Claude Code skills directory.

### Option A — Project-level skills (recommended)

Clone into your project's `.claude/skills/` directory so the skills are available when you work in that project:

```bash
# From your project root
git clone https://github.com/weiserman/rap-skills.git .claude/skills/rap
```

This gives you all five skill folders (`rap-generator`, `rap-cds`, `rap-behavior`, `rap-testing`, `rap-troubleshoot`) in one step. To update later, just `git pull` inside the skills folder.

If you need a different branch (e.g., unmanaged BTP development):

```bash
git clone -b btp/unmanaged https://github.com/weiserman/rap-skills.git .claude/skills/rap
```

### Option B — Global skills

Clone into your home directory's Claude config so the skills are available in every project:

```bash
git clone https://github.com/weiserman/rap-skills.git ~/.claude/skills/rap
```

### Option C — Manual copy

If you prefer not to use git, download the ZIP from GitHub and copy the skill folders manually:

```bash
mkdir -p .claude/skills
cp -r rap-generator .claude/skills/
cp -r rap-cds .claude/skills/
cp -r rap-behavior .claude/skills/
cp -r rap-testing .claude/skills/
cp -r rap-troubleshoot .claude/skills/
```

### Verify skills are loaded

Launch Claude Code and ask:

> What RAP skills do you have available?

Claude should list all five skills.

---

## The Skills

Each skill is a self-contained `SKILL.md` file that Claude reads before performing a task. Think of them as expert playbooks.

### rap-generator

**Purpose:** Scaffold a complete managed RAP BO from scratch — database table through to service definition — in a single conversation.

**Use when you say things like:**
- "Create a RAP BO for product management"
- "Build a managed business object for purchase orders"
- "Scaffold a RAP service for time tracking"

**What it creates:**

```
Database Table(s)
  └─ CDS Root View Entity (_R_ layer)
       └─ CDS Child View Entities
            └─ Behavior Definition (BDEF)
                 └─ Behavior Implementation Class (ABP)
                      └─ Service Definition (SRVD)
```

**Key feature — namespace awareness:** The skill will always ask whether you're using a registered SAP namespace (e.g., `/ABU/`, `/DMO/`) or a customer Z-prefix before generating any artifact names. This matters because namespace objects follow different naming patterns and cannot be created in local packages (`$TMP`).

### rap-cds

**Purpose:** Create and modify CDS view entities with proper annotations for data modeling, Fiori UI rendering, search, and value help.

**Use when you say things like:**
- "Add UI annotations to my travel CDS view"
- "Create a projection view for the booking entity"
- "Add value help for the currency field"
- "Create a metadata extension for the object page layout"

**Covers:** Interface views (R-layer), projection views (C-layer), metadata extensions, compositions, associations, and all `@UI`, `@Search`, `@Semantics`, and `@Consumption` annotations.

### rap-behavior

**Purpose:** Add business logic to an existing RAP BO — validations, determinations, actions, draft handling, authorization, side effects, and feature control.

**Use when you say things like:**
- "Add a date validation to the travel BO"
- "Create an accept/reject action with feature control"
- "Enable draft handling"
- "Add a determination to auto-set the status on create"

**Covers:** Full EML (Entity Manipulation Language) patterns, handler class implementation, saver class methods, and all BDEF declaration syntax.

### rap-testing

**Purpose:** Write ABAP Unit tests for RAP BOs using the test doubles framework.

**Use when you say things like:**
- "Write unit tests for the Business Object"
- "Test the date validation with valid and invalid data"
- "Create a test for the accept action"

**Covers:** Test class scaffolding with `CL_BOTD_TXBUFDBL_BO_TEST_ENV`, test patterns for CRUD operations, validations, determinations, actions, and create-by-association.

### rap-troubleshoot

**Purpose:** Diagnose and fix common RAP issues — activation errors, runtime dumps, validations not firing, draft problems, and performance.

**Use when you say things like:**
- "My BDEF won't activate"
- "The validation isn't triggering"
- "I'm getting a dump in my handler method"
- "Draft data is being lost"

**Covers:** The 10 most common RAP issues with step-by-step diagnosis using VSP tools (SyntaxCheck, ListDumps, GetDump, RunATCCheck, debugger, traces).

---

## Typical Workflow

Here's what a typical RAP development session looks like with these skills:

### 1. Start a new BO

```
You: Create a managed RAP BO for "Expense Report" with line items.
     Use namespace /ABU/ and package /ABU/EXPENSE_PKG.

Claude: [reads rap-generator skill]
        [asks clarifying questions about fields, draft support, etc.]
        [creates tables, CDS views, BDEF, class, service definition]
        [activates everything]
        [provides summary of created objects]
```

### 2. Add business logic

```
You: Add a validation that rejects expense reports where the total exceeds 10,000.

Claude: [reads rap-behavior skill]
        [reads current BDEF and ABP class]
        [adds validation declaration to BDEF]
        [implements handler method]
        [activates]
```

### 3. Write tests

```
You: Write unit tests for the amount validation.

Claude: [reads rap-testing skill]
        [creates test class with pass/fail scenarios]
        [runs the tests]
        [reports results]
```

### 4. Fix issues

```
You: The validation isn't triggering when I update the amount.

Claude: [reads rap-troubleshoot skill]
        [checks BDEF trigger conditions]
        [finds the issue — missing field trigger]
        [fixes and re-activates]
```

---

## Optional: Install ZADT_VSP for Advanced Features

Some VSP features (debugging, RFC calls, report execution, abapGit export) require the ZADT_VSP WebSocket handler to be installed on your SAP system. This is optional but recommended.

Claude can install it for you:

```
You: Install the ZADT_VSP handler on the system.

Claude: [calls InstallZADTVSP tool]
```

After installation, you'll need to complete manual SAPC and SICF setup steps in the SAP system (Claude will guide you through what's needed).

Features unlocked by ZADT_VSP:
- Interactive ABAP debugging (set breakpoints, step through code, inspect variables)
- AMDP (HANA SQLScript) debugging
- Execute reports with selection screen parameters
- Call function modules via RFC
- abapGit-format export/import
- Move objects between packages

---

## Tips for Effective Use

**Be specific about your namespace.** Always tell Claude your namespace or prefix upfront (e.g., `/ABU/`, `Z`, `ZMY_`). This avoids rework.

**Work incrementally.** Generate the base BO first, then add validations, then actions, then tests. Each skill is designed for one concern at a time.

**Let Claude check before activating.** The skills instruct Claude to run `SyntaxCheck` before activation, but you can also ask it to run `RunATCCheck` for code quality or `RunUnitTests` to verify behavior.

**Use `$TMP` for prototyping.** Start in a local package, then move to a transportable package once you're happy with the design. Note: namespace objects (`/NS/...`) cannot use `$TMP` — they always need a transportable package and transport request.

**Ask Claude to read existing code first.** If you're enhancing an existing BO, tell Claude the root CDS entity name and it will use `GetSource` to read all the relevant artifacts before making changes.

---

## Folder Structure

```
rap-skills/
├── README.md                     # This file
├── REPO_CONCEPT.md               # Branch strategy & roadmap
├── .claude/
│   └── settings.json             # Shared Claude Code config
├── .mcp.json.template            # Template MCP config (fill in your credentials)
├── .gitignore
├── docs/
│   ├── BRANCH_GUIDE.md           # Which branch to use — decision tree
│   └── VSP_SETUP.md              # VSP installation guide
├── rap-generator/
│   └── SKILL.md                  # End-to-end BO generation
├── rap-cds/
│   └── SKILL.md                  # CDS view entity patterns & annotations
├── rap-behavior/
│   └── SKILL.md                  # Behavior definition & implementation
├── rap-testing/
│   └── SKILL.md                  # ABAP Unit testing for RAP
└── rap-troubleshoot/
    └── SKILL.md                  # Diagnosis & debugging
```

---

## Resources

- **This repo:** [github.com/weiserman/rap-skills](https://github.com/weiserman/rap-skills)
- **VSP MCP Server:** [github.com/oisee/vibing-steampunk](https://github.com/oisee/vibing-steampunk)
- **VSP for ABAP Cloud setup guide:** [Vibe Steam Punk (VSP) for ABAP Cloud — Mac + Claude](https://medium.com/@warren_eiserman/vibe-steam-punk-vsp-for-abap-cloud-mac-claude-2864d601978f)
- **Claude Code docs:** [docs.claude.com](https://docs.claude.com)
- **SAP RAP documentation:** [SAP Help — ABAP Cloud](https://help.sap.com/docs/ABAP_Cloud/f055b8bf582d4f34b91da667bc1fcce6)
- **ABAP Cheat Sheets (RAP):** [github.com/SAP-samples/abap-cheat-sheets](https://github.com/SAP-samples/abap-cheat-sheets)
- **SAP Clean ABAP Style Guide:** [github.com/SAP/styleguides](https://github.com/SAP/styleguides/blob/main/clean-abap/CleanABAP.md)

---

## License

MIT
