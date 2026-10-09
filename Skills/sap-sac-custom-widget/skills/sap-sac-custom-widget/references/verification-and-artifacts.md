# SAC Custom Widget Verification and Artifact Discipline

Use this reference before SAC import, after a build change, or when local preview and tenant
behavior disagree.

## Test the public seam

Drive the interface SAC will use, not only private helpers:

- set properties through public setters or an SAC-shaped update path;
- dispatch real DOM events and use `element.click()` where native disabled behavior matters;
- inspect rendered text, attributes, events, and serialized output; and
- test the final bundled `widget.js`, `builder.js`, and `styling.js` independently.

## Test the delivered component

The unit under test is the component JavaScript that SAC will load, not only a source helper.
For every build:

1. Generate the manifest and component files from the same source state.
2. Validate all outputs before writing any output file. A failure in the last bundle must not leave
   earlier bundles written from a different build.
3. Rebuild the Resource-ZIP after every manifest or JavaScript change.
4. Check syntax, permitted ZIP contents, root-relative URLs for ZIP mode, and exact integrity bytes.
5. Mount the final `widget.js`, `builder.js`, and `styling.js` independently in preview tests.

If a validation tool is optional, distinguish "not installed" from "ran and passed". Every emitted
probe must still be parsed before it is treated as upload evidence. An absent optional tool must
produce a clear skip or blocked result, never a passing result.

Do not claim SAC importability from a Node suite, `node --check`, a valid ZIP, or a local preview.
Those are useful gates, not tenant evidence.

## High-value behavior checks

Prefer behavior checks over markup checks:

- Set two properties in one update tick and assert the final merged state.
- Assert every manifest property is read by runtime behavior, not only by a panel or serializer.
- Test string-form numbers, boolean-like strings, blank values, localized objects, and legal zero.
- Test that a builder text edit keeps focus, caret, selection, search, and collapse state.
- Use `element.click()` when testing disabled controls. `dispatchEvent()` can bypass native disabled
  behavior.
- Simulate touch pointer sequences and ensure hover-only behavior checks `event.pointerType`.
- Check every button for a handler and accessible name. Check icon-only buttons for a rendered glyph.
- Test both keyboard and pointer activation for controls and popovers.
- Test that a full-cover pseudo-element cannot intercept clicks when it is decorative.
- Test output/import round trips with gaps, reordered items, duplicate-like names, and data that is
  present only in a non-default language.
- Test a property received before builder initialization, a structural edit after a text edit, and
  an open popover followed by rerender or focus departure.

Use a small control sweep for panels instead of one test per current button. Keep the sweep honest:
record the handler or observe the public effect, and do not count a control as covered merely because
it exists in the DOM.

## Know the test environment's blind spots

DOM emulation generally has no real layout, focus movement, font metrics, or rendering lifecycle.
Zero geometry can make a conditional assertion never run. A hidden preview iframe may also fail to
fire `ResizeObserver` or `requestAnimationFrame` even when the DOM is correct.

Move the check to a real browser when it depends on:

- `getBoundingClientRect`, overflow, clipping, or narrow viewport behavior;
- focus, caret movement, blur, popover dismissal, or pointer coordinates;
- font loading and post-font measurements; or
- `ResizeObserver`, animation frames, scrolling, or visibility transitions.

Real-browser checks should state the behavioral rule, use asymmetric content at wide and narrow
viewports, wait for fonts, and record measurements. Do not lower an expected count or threshold just
to make a stale check pass. If a browser is unavailable, report the check as pending.

## Mutation and failure evidence

Every new guard should be shown to fail when the guarded rule is removed or changed in a throwaway
copy. Examples:

- remove a runtime property read and confirm the property-use check fails;
- change a legal-zero fallback to `||` and confirm the coercion test fails;
- change one bundle after SRI generation and confirm the artifact check fails; and
- replace a handler or CSS rule with a no-op and confirm the public behavior check fails.

Restore from the pre-test copy, not from a destructive checkout that could discard unrelated work.
If a test cannot reach a platform API, instrument the observation seam and add a positive control so
the assertion is not vacuous.

## Upload failure triage

For an opaque `CUSTOM_WIDGET_SERVICE_EXCEPTION` or HTTP 500, first scan source and bundles for raw
control or ambiguous code points, including BOM, zero-width characters, and line separators, and
confirm the manifest and ZIP are a matching pair. Then create complete,
uploadable probes that change one variable at a time. Keep probes disposable and use unique ids and
versions. Record confirmed non-causes so later debugging does not repeat them.

If a failure is tenant-observed rather than documented by SAP, label it as such and verify it again
before turning it into a universal rule.

## Deterministic artifact chain

The final package should be reproducible from source. Pin these relationships:

- manifest version and component versions;
- manifest component tags and the tags registered by each bundle;
- manifest integrity values and the exact bytes inside the final ZIP;
- the source bundle and the delivered bundle; and
- the ZIP file list and the SAC upload contract.

Do not let line-ending normalization change digest-pinned files. Keep byte-contract files outside
automatic text normalization when their format requires a specific encoding or line ending.

## Sources and evidence

- Consolidated project lessons supplied for this update: `skill-lessons-consolidated.md`, 2026-08-28.
- SAP Help, Custom Widget Developer Guide: https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/0ac8c6754ff84605a4372468d002f2bf/75311f67527c41638ceb89af9cd8af3e.html
- SAP Custom Widget Developer Guide PDF: https://help.sap.com/doc/c813a28922b54e50bd2a307b099787dc/release/en-US/CustomWidgetDevGuide_en.pdf
