# Contributing

This repository is a **knowledge package**: its value comes from being precise, verified and consistent.
A contribution is judged on correctness and usefulness, not on length.

---

## 1. Ground rules

1. **Every factual claim about SAP Cloud Integration must be verifiable** in the official SAP Help
   documentation for SAP Cloud Integration (`help.sap.com/docs/cloud-integration`) or in an official SAP
   source (SAP Notes, SAP Business Accelerator Hub content, official SAP blog posts).
2. **Never invent** adapter parameter labels, API class or method names, limits, error strings or feature
   names. If a detail is release-dependent or you could not verify it, describe the behaviour generically and
   say that the exact label must be checked in the tenant's release. An honest "verify this in your release"
   is worth more than a plausible-looking falsehood.
3. **No environment-specific content**: no credentials, tokens, tenant URLs, customer names, personal data or
   internal hostnames — in prose, in code or in commit messages.
4. **Prefer decisions over descriptions.** A guide should help a reader choose, not just catalogue options.
   Tables that compare alternatives, decision procedures and trade-offs are the expected form.

---

## 2. Where content belongs

| Content type | Location |
| :--- | :--- |
| Entry point, routing, golden rules, working procedure | `SKILL.md` |
| Deep-dive on one domain | `references/<domain>.md` |
| A concrete, copy-pasteable solution to a recurring problem | `references/groovy-recipes.md` or `examples/` |
| A gate someone must run | `checklists/` |
| A document someone must fill in | `templates/` |
| Project overview, scope, installation | `README.md` |

Add a new reference only if the topic is not covered by an existing one. Growing an existing guide is
usually better than creating a fifteenth file — but split it when it passes roughly 400 lines or covers two
distinct decisions.

Whenever you add or rename a file, **update**: the routing table in `SKILL.md`, the structure block in
`README.md`, the example index in `examples/README.md` (for scripts), and `CHANGELOG.md`.

---

## 3. Writing style

- **English** for everything except `sap_cpi_skill_guide.md` (Italian).
- Open with `# Title`, then a one-paragraph purpose statement, then `---`.
- Numbered `##` sections. GitHub-flavoured Markdown tables for anything comparative.
- Fenced code blocks with a language tag (`` ```groovy ``, `` ```xml ``, `` ```json ``, `` ```text ``).
- `> ⚠️ **…**` for warnings and anti-patterns, `> 💡 **…**` for tips.
- Relative links only, so the package works cloned anywhere: link a sibling file by its bare name from
  inside `references/`, and use `../references/<file>` from inside `examples/`, `checklists/` and
  `templates/`.
- End every file with a single trailing newline and no trailing whitespace.
- Every reference guide ends with a `## Further Reading` section containing 2–5 **real** SAP Help links.

---

## 4. Code contributions (Groovy examples)

Every example must:

- Use the CPI script API signature: `def Message processData(Message message) { … return message }`
  with `import com.sap.gateway.ip.core.customdev.util.Message`.
- Start with a comment block stating **the problem it solves, when to use it, how to wire it into the iFlow,
  and any size or performance assumption**.
- Be null-safe on the message, the body, headers, properties and `messageLogFactory`.
- Close every stream, reader and writer in `finally` (or use `withReader` / `withWriter` / `withStream`).
- Avoid: `getBody(String)` on potentially large payloads, static mutable state, Groovy binding variables,
  `Eval()`, `TimeZone.setDefault`, `XmlSlurper.parseText(String)`, hardcoded hosts or credentials.
- Never write credentials or personal data into headers, properties or MPL attachments.
- Be re-entrant: running it twice with the same input must not corrupt anything.
- Be honest about uncertainty: if a tenant-specific API could not be verified, implement the logic with the
  plain Groovy/JDK standard library and say so in the comment block.

---

## 5. How to submit

1. Branch from `main`.
2. Make the change, then self-review against the list below.
3. Commit with a message that states the **why**, not just the what:
   `Add streaming CSV recipe (avoid loading 500 MB flat files into heap)`.
4. Open a pull request describing: what was added or changed, the SAP documentation that supports it, and any
   detail deliberately left generic.

### Pre-submission checklist

- [ ] `python3 scripts/check-consistency.py` passes (headings, code fences, relative links, Groovy balance)
- [ ] Every platform fact is traceable to official SAP documentation, or explicitly flagged as
      release-dependent
- [ ] No invented API names, parameter labels, limits or error strings
- [ ] No secrets, tenant URLs, customer names or personal data
- [ ] Relative links resolve; new files are registered in `SKILL.md`, `README.md` and `CHANGELOG.md`
- [ ] Code follows the rules in section 4 and would run as-is
- [ ] Markdown renders correctly (tables aligned, code fences balanced)
- [ ] Single trailing newline, no trailing whitespace
