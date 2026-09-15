# Branch Guide — Which Skills Do I Need?

This repository uses a **branch-per-scenario** strategy. Each branch contains a complete, self-contained skill set tailored to one SAP deployment target and implementation type.

---

## Decision Criteria

Three dimensions determine which branch you need:

| Dimension | Question to Ask |
|-----------|----------------|
| **Deployment target** | Where is your SAP system running? (BTP, S/4 Public, S/4 Private, On-Premise) |
| **Implementation type** | Are you building from scratch (managed) or wrapping existing logic (unmanaged)? |
| **Extension pattern** | Are you building a new BO or extending a standard SAP BO? |

---

## Branch Details

### `btp/managed` — Greenfield on BTP Steampunk

**Use when:** You're building a brand-new RAP BO on the BTP ABAP Environment with no legacy dependencies.

- ABAP Cloud syntax only, `strict(2)`
- Only C1-released APIs available
- Managed persistence — framework handles INSERT/UPDATE/DELETE
- Draft support via `with draft`
- UUID-based keys (framework generates via `numbering : managed`)

### `btp/unmanaged` — Brownfield on BTP Steampunk

**Use when:** You're wrapping released BAPIs or building a RAP facade over existing logic on BTP.

- ABAP Cloud syntax only, `strict(2)`
- Only C1-released APIs available
- Unmanaged persistence — you handle the transactional buffer, handler methods, and saver class
- Business keys (NUMC/CHAR) instead of UUIDs
- Early or late numbering (you assign keys)
- BAPI wrapper pattern for persistence in saver `save` method

### `s4-public/managed` — Embedded Steampunk on S/4 Public Cloud

**Use when:** You're building a custom BO within S/4HANA Cloud Public Edition.

- Same ABAP Cloud restrictions as BTP
- Access to S/4 released CDS views (C0 contract) like `I_BusinessPartner`
- Access to released BAPIs via wrapper classes
- Embedded development — your BO lives alongside standard S/4 BOs

### `s4-private/managed` — S/4 Private Cloud

**Use when:** You're building on S/4HANA Cloud Private Edition where you have more flexibility.

- `strict(1)` as fallback (more syntax available than Public Cloud)
- Access to classic ABAP where needed via custom code migration
- More APIs available than Public Cloud
- Migration key for accessing unreleased APIs with documented risk

### `s4-onprem/managed` — S/4 On-Premise

**Use when:** Full ABAP syntax is available. No released API restrictions.

- Full ABAP syntax, `strict(1)` or no strict mode
- Direct access to DDIC tables, function modules, BAPIs
- No C0/C1 contract restrictions
- Classic `AUTHORITY-CHECK OBJECT` available directly

---

## What Changes Between Branches

| Aspect | BTP | S/4 Public | S/4 Private | On-Premise |
|--------|-----|-----------|-------------|------------|
| Strict mode | `strict(2)` | `strict(2)` | `strict(1)` fallback | Optional |
| API access | C1-released only | C0/C1-released | C0/C1 + migration key | Everything |
| SAP CDS views | Limited | `I_BusinessPartner` etc. | More available | All |
| Classic BAPIs | Only if released | Only if released | With wrapper | Directly |
| DDIC tables | Custom only | Custom only | Limited classic | All |
| `cl_abap_context_info` | Yes | Yes | Yes | Yes (7.77+) |

---

## Cloning a Specific Branch

```bash
# Clone only the branch you need (no other branches downloaded)
git clone -b btp/managed --single-branch https://github.com/weiserman/rap-skills.git .claude/skills/rap
```

To switch branches later:

```bash
cd .claude/skills/rap
git fetch origin
git checkout s4-public/managed
```
