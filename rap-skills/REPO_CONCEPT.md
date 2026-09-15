# Concept: RAP Skills GitHub Repository

A GitHub repository that developers clone at the start of any RAP project to get pre-configured Claude Code skills tailored to their specific SAP deployment target and implementation pattern.

---

## The Problem

Not all RAP development is the same. The ABAP syntax you can use, the APIs available to you, the BDEF patterns you write, and even the tooling constraints differ significantly depending on where you're building and what you're wrapping. A skill set written for BTP Steampunk will mislead a developer working on S/4HANA Cloud Private Edition — and vice versa.

The key dimensions that change the skills:

| Dimension | Options | What Changes |
|-----------|---------|-------------|
| **Implementation type** | Managed, Unmanaged, Managed w/ unmanaged save | BDEF header, handler methods, saver class, buffer management |
| **Deployment target** | BTP ABAP Env, Cloud Public, Cloud Private, On-premise | Available APIs, strict mode level, released objects, transport model |
| **Extension pattern** | Side-by-side (new app), Embedded (extend S/4) | Data model approach, API consumption, extensibility patterns |

---

## Repository Structure

```
rap-skills/
├── README.md                    # Repo overview, branch picker guide
├── .claude/
│   └── settings.json            # Shared Claude Code config
├── .mcp.json.template           # Template MCP config (user fills in credentials)
├── docs/
│   ├── BRANCH_GUIDE.md          # Which branch to use decision tree
│   ├── VSP_SETUP.md             # VSP installation (all platforms)
│   └── CLAUDE_CODE_SETUP.md     # Claude Code installation & config
└── skills/                      # Skill files (content varies by branch)
    ├── rap-generator/
    │   └── SKILL.md
    ├── rap-cds/
    │   └── SKILL.md
    ├── rap-behavior/
    │   └── SKILL.md
    ├── rap-testing/
    │   └── SKILL.md
    └── rap-troubleshoot/
        └── SKILL.md
```

The `docs/` folder and repo scaffolding stay the same across all branches. The `skills/` content is what changes per branch.

---

## Branch Strategy

Each branch contains a complete, self-contained skill set for one development scenario. A developer checks out the branch that matches their project and gets skills that only reference patterns, APIs, and syntax valid for that target.

### Branch naming convention

```
{target}/{implementation-type}
```

### The branches

#### Tier 1 — Primary scenarios (build first)

| Branch | Target | Impl. Type | Description |
|--------|--------|-----------|-------------|
| `btp/managed` | BTP ABAP Environment | Managed | **Default branch.** Greenfield RAP on Steampunk. Strictest constraints — only released APIs, `strict(2)`, ABAP Cloud syntax only. This is what we have today. |
| `btp/unmanaged` | BTP ABAP Environment | Unmanaged | Brownfield on BTP — wrapping released BAPIs/APIs behind a RAP facade. Unmanaged save, unmanaged locking, late numbering. |
| `s4-public/managed` | S/4HANA Cloud Public Ed. | Managed | Embedded Steampunk — new custom entities within S/4 Public Cloud. Same ABAP Cloud restrictions as BTP but with access to S/4 released CDS views and BOs. |
| `s4-private/managed` | S/4HANA Cloud Private Ed. | Managed | More relaxed than Public — access to classic ABAP where needed via custom code migration, plus released APIs. Supports `strict(1)` as fallback. |

#### Tier 2 — Extended scenarios (build next)

| Branch | Target | Impl. Type | Description |
|--------|--------|-----------|-------------|
| `s4-public/unmanaged` | S/4HANA Cloud Public Ed. | Unmanaged | Wrapping S/4 BAPIs in embedded Steampunk. BAPI wrapper pattern with released BAPI interfaces. |
| `s4-private/unmanaged` | S/4HANA Cloud Private Ed. | Unmanaged | Wrapping legacy on Private Cloud. More APIs available, classic function modules accessible. |
| `s4-onprem/managed` | S/4HANA On-Premise | Managed | Full ABAP syntax, no released API restrictions. Can use classic DDIC, full ABAP statements. |
| `s4-onprem/unmanaged` | S/4HANA On-Premise | Unmanaged | Legacy wrapping on-premise. Full access to all BAPIs, function modules, and classic persistence. |

#### Tier 3 — Specialized patterns

| Branch | Target | Pattern | Description |
|--------|--------|---------|-------------|
| `btp/managed-bapi-save` | BTP ABAP Environment | Managed w/ unmanaged save | Hybrid: managed BO that delegates persistence to a BAPI. Common integration pattern. |
| `s4-public/extend-sap-bo` | S/4HANA Cloud Public Ed. | Extension | Extending a standard SAP RAP BO (e.g., Purchase Order, Sales Order) via BDEF extensions. Not a new BO — an extension of an existing one. |

---

## What Changes Between Branches

The skill content isn't cosmetically different — it's structurally different. Here's what actually changes:

### BDEF Header

```
# btp/managed (strictest)
managed implementation in class ZBP_R_ENTITY unique;
strict ( 2 );
with draft;

# btp/unmanaged
unmanaged implementation in class ZBP_R_ENTITY unique;
strict ( 2 );

# btp/managed-bapi-save
managed implementation in class ZBP_R_ENTITY unique;
strict ( 2 );
with unmanaged save;

# s4-onprem/managed (relaxed)
managed implementation in class ZBP_R_ENTITY unique;
strict ( 1 );   -- or no strict at all
with draft;
```

### Available APIs and CDS Views

| What | BTP | S/4 Public (Embedded) | S/4 Private | On-Premise |
|------|-----|----------------------|-------------|------------|
| Custom tables | Yes | Yes | Yes | Yes |
| SAP released CDS views (e.g., `I_BusinessPartner`) | No (unless released for BTP) | Yes (C0 released) | Yes | Yes (no contract needed) |
| Classic BAPIs | Only if released + wrapped | Only if released | Yes (with wrapper) | Yes (directly) |
| Classic DDIC (SE11 tables) | No | No | Limited | Yes |
| Classic function modules | No | No | Limited | Yes |
| `AUTHORITY-CHECK OBJECT` | Only released auth objects | Only released | Yes | Yes |
| `cl_abap_context_info` | Yes | Yes | Yes | Yes (7.77+) |
| `cl_system_uuid` | Yes | Yes | Yes | Yes (7.55+) |

### Handler/Saver Patterns

**Managed** — framework handles persistence:
```abap
" No saver class needed — framework does INSERT/UPDATE/DELETE
" Handler class only for validations, determinations, actions
```

**Unmanaged** — developer handles everything:
```abap
CLASS lsc_Entity DEFINITION INHERITING FROM cl_abap_behavior_saver.
  PROTECTED SECTION.
    METHODS finalize REDEFINITION.
    METHODS check_before_save REDEFINITION.
    METHODS save REDEFINITION.
    METHODS cleanup REDEFINITION.
    METHODS cleanup_finalize REDEFINITION.
ENDCLASS.

CLASS lsc_Entity IMPLEMENTATION.
  METHOD save.
    " Developer must handle persistence:
    " - READ from transactional buffer
    " - INSERT/UPDATE/DELETE on database tables
    " - Or call BAPIs with COMMIT WORK
  ENDMETHOD.
ENDCLASS.
```

**Managed with unmanaged save** — framework manages buffer, developer handles save:
```abap
" Handler: same as managed (framework manages buffer)
" Saver: developer implements save_modified to call BAPIs
METHOD save_modified.
  " create-entity contains instances to create
  " update-entity contains instances to update
  " delete-entity contains instances to delete
  " Developer maps these to BAPI calls
ENDMETHOD.
```

### Testing Patterns

The test approach differs too:

| Scenario | Test Approach |
|----------|--------------|
| Managed (any target) | EML-based with `CL_BOTD_TXBUFDBL_BO_TEST_ENV` — straightforward |
| Unmanaged | Must also mock the persistence layer and/or BAPI calls |
| BAPI wrapper | Need to double the BAPI interface for isolated testing |
| Extension | Test against the extended BO — different test setup |

### Troubleshooting Differences

The error patterns are different per scenario:

| Scenario | Common Issues |
|----------|---------------|
| BTP managed | Released API violations, strict mode syntax errors, missing C0/C1 contracts |
| Embedded Steampunk | Same as BTP + S/4 version-specific CDS view availability |
| Unmanaged | Buffer consistency, save sequence errors, lock handling, BAPI return message mapping |
| Private Cloud | Migration key issues, custom code migration scope |
| On-premise | Fewer restrictions but older syntax issues on lower kernel versions |

---

## Developer Onboarding Flow

The README would include a decision tree:

```
Which SAP system are you developing on?
│
├─ SAP BTP ABAP Environment (Steampunk)
│  ├─ Building a new app from scratch?
│  │  └─ Use branch: btp/managed
│  ├─ Wrapping existing BAPIs/APIs?
│  │  └─ Use branch: btp/unmanaged
│  └─ New BO that saves via BAPI?
│     └─ Use branch: btp/managed-bapi-save
│
├─ S/4HANA Cloud Public Edition
│  ├─ New custom BO?
│  │  └─ Use branch: s4-public/managed
│  ├─ Wrapping S/4 BAPIs?
│  │  └─ Use branch: s4-public/unmanaged
│  └─ Extending a standard SAP BO?
│     └─ Use branch: s4-public/extend-sap-bo
│
├─ S/4HANA Cloud Private Edition
│  ├─ New custom BO?
│  │  └─ Use branch: s4-private/managed
│  └─ Wrapping legacy code?
│     └─ Use branch: s4-private/unmanaged
│
└─ S/4HANA On-Premise
   ├─ New custom BO?
   │  └─ Use branch: s4-onprem/managed
   └─ Wrapping legacy code?
      └─ Use branch: s4-onprem/unmanaged
```

### Clone and go

```bash
# Example: starting a new managed BO on BTP
git clone -b btp/managed https://github.com/weiserman/rap-skills.git .claude/skills/rap

# Example: wrapping BAPIs on S/4 Public Cloud
git clone -b s4-public/unmanaged https://github.com/weiserman/rap-skills.git .claude/skills/rap
```

After cloning, the developer just needs to configure `.mcp.json` with their system credentials and they're ready to go.

---

## Git Workflow for Maintaining the Repo

### Branch inheritance

The branches should be maintained with a merge strategy that flows common updates downstream:

```
main (shared docs, templates, no skills)
 │
 ├─ base/managed (shared managed patterns)
 │   ├─ btp/managed
 │   ├─ s4-public/managed
 │   ├─ s4-private/managed
 │   └─ s4-onprem/managed
 │
 ├─ base/unmanaged (shared unmanaged patterns)
 │   ├─ btp/unmanaged
 │   ├─ s4-public/unmanaged
 │   ├─ s4-private/unmanaged
 │   └─ s4-onprem/unmanaged
 │
 └─ base/extension (extension patterns)
     └─ s4-public/extend-sap-bo
```

When a common RAP pattern changes (e.g., new annotation syntax), update the `base/*` branch and merge down to the target branches. Target-specific changes (e.g., a new released API on BTP) go directly on that branch.

### Tagging releases

Use semantic versioning tags on each branch:

```
btp/managed @ v1.0.0   — initial release
btp/managed @ v1.1.0   — added side effects patterns
btp/managed @ v2.0.0   — updated for ABAP Cloud 2025 release
```

This lets developers pin to a known-good version:

```bash
git clone -b btp/managed --single-branch https://github.com/weiserman/rap-skills.git .claude/skills/rap
cd .claude/skills/rap
git checkout v1.1.0
```

---

## Build Sequence

A practical order for building this out:

### Phase 1 — Foundation (what we have now)
- [x] `btp/managed` — current skill set, already built
- [ ] Repo scaffolding: README with decision tree, `.mcp.json.template`, setup docs
- [ ] CI: markdown linting, YAML frontmatter validation

### Phase 2 — Core variants
- [ ] `btp/unmanaged` — add unmanaged BDEF patterns, saver class templates, BAPI wrapper guide
- [ ] `s4-public/managed` — fork from `btp/managed`, add S/4 released CDS view references, embedded Steampunk specifics
- [ ] `s4-private/managed` — fork from `s4-public/managed`, relax strict mode, add classic API access guidance

### Phase 3 — Extended variants
- [ ] `s4-onprem/managed` — full syntax, no released API constraints
- [ ] `btp/managed-bapi-save` — hybrid pattern for BAPI integration
- [ ] `s4-public/extend-sap-bo` — BDEF extension patterns

### Phase 4 — Polish
- [ ] Automated tests: validate each branch's skill YAML + code examples against target syntax
- [ ] Contribution guide: how to add a new branch, how to maintain cross-branch consistency
- [ ] GitHub Actions: on PR, check that skill examples pass syntax validation against the declared target

---

## Open Questions

A few things to decide before building:

1. **Monorepo with directories vs. branches?** Branches are cleaner for the developer experience (clone one branch, get exactly what you need). But a monorepo with `skills/btp-managed/`, `skills/btp-unmanaged/`, etc. is easier to maintain. The trade-off is developer simplicity vs. maintainer simplicity. This document assumes branches.

2. **Should the skills reference each other across branches?** For example, `rap-troubleshoot` on the `btp/unmanaged` branch could reference the `btp/managed` branch for comparison. Or should each branch be fully self-contained?

3. **ABAP release versioning within a target?** BTP gets quarterly updates that can add new released APIs. S/4 Private Cloud has specific FPS levels. Do we tag per SAP release, or just maintain a rolling latest?

4. **Community contributions?** If this goes public, should there be a contribution model where SAP developers can submit branch-specific patterns they've battle-tested?
