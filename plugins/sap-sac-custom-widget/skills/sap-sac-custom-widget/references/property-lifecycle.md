# SAC Custom Widget Property Lifecycle

Use this reference when a widget has several manifest properties, builder or styling panels,
script methods, import/export, or state that can be written by more than one surface.

The rules below are implementation guidance. Tenant observations are marked explicitly and must
not be presented as universal SAP guarantees without a current tenant check.

## The lifecycle model

Design as if SAC can:

1. deliver properties one at a time, in any order;
2. send the widget's own `propertiesChanged` values back as later updates; and
3. provide a value in a different representation than the one originally written.

Render from the merged state after each update. Do not assume that the last property in a batch has
arrived, and do not consume a one-shot adoption flag on an empty first render from a builder panel.
Keep the flag armed until there is a real value to adopt.

If a setter uses reference equality for an object, in-place mutation can leave the widget stale.
Either treat compound values as immutable or compare a canonical representation before deciding
that nothing changed.

## One canonical read path

Use one normalizer for runtime rendering, builder and styling panel display, and export. It should
handle the representations the manifest and tenant can produce, including string-form numbers,
boolean-like strings, localized text objects, blank values, and invisible whitespace. Invalid input
must resolve to the declared default.

Do not write this:

```js
var columns = Number(value) || 1;
```

It turns a valid zero into the fallback. Prefer an explicit finite-value check:

```js
function finiteOrDefault(value, fallback) {
  var number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}
```

Normalize incoming properties before parsing a serialized compound property that contains values
from other properties. Otherwise the panel, runtime, and backup can each see a different state.

Generate manifest declarations, panel fields, normalizer entries, and export enumeration from one
property specification where practical. At minimum, add a check that every manifest property is
read by runtime code. A property that is only stored, shown in a panel, or exported is not wired.

## Script methods are a narrow contract

`methods[].body` is Analytics Designer script, not arbitrary JavaScript. It should only read or
assign properties declared in the same manifest. Put component logic in the Web Component and use a
declared property as the bridge:

```json
{
  "properties": {
    "refreshToken": { "type": "integer", "default": 0 }
  },
  "methods": {
    "refresh": {
      "body": "this.refreshToken = this.refreshToken + 1;"
    }
  }
}
```

Do not call private component methods from a manifest method body. A getter method should return a
declared property and its `returnType` must match that property. Keep array-like results as a
documented string format unless the target tenant and schema support a stronger type.

## Editors and echoed writes

Builder and styling panels are stateful editors, not passive forms.

- Preserve input value, focus, caret, selection, search text, and collapse state during text edits.
- Use targeted DOM updates for keystrokes. Reserve full renders for structural changes.
- Use `composedPath()` for outside-click logic when rerenders can detach the clicked node.
- Derive document-level listeners from `ownerDocument`, and remove them from that same document.
- Keep editor-only state out of persisted widget configuration.
- When an external configuration replacement removes the selected item, clear stale drafts and
  pending targets instead of acting on an arbitrary replacement.
- If a control is disabled by a missing prerequisite, show why it is disabled. Do not silently
  accept a no-op.
- When a pending staged record is committed, clear it only if the slot still contains that exact
  record. A newer staged edit must not be discarded by an older commit.
- A single user action should produce one property dispatch and one undo step. Include adoption or
  normalization in that transaction, rather than dispatching a hidden follow-up from rendering.

When a panel writes a value and SAC echoes it back, avoid treating the echo as a new user edit or
starting a render loop. Verify the exact echo shape in a tenant before depending on identity checks.

For scheduled or expiring content, own one boundary timer: clear before arming, cap the delay at
`2^31 - 1` milliseconds, stop it on destroy and detach, and re-arm when a property changes the
boundary. Re-render only when the visible result changes. Test the old configuration path so a new
timer does not change behavior when the feature is unused.

## Defaults, overrides, and persistence

Distinguish these contracts:

- a new manifest default can change the appearance of a newly created widget;
- an existing stored configuration should remain compatible unless a migration is explicit; and
- a per-item setting can be unset even when the widget-level default is non-empty.

Represent the unset state consistently in the sanitizer, editor control, runtime resolver, and every
serializer. If an override replaces a built-in matcher, it replaces it. Do not silently widen the
match or retarget a displaced stored value. Show a visible warning and require the author to repair
the value.

When two editors write the same configuration, preserve keys unknown to the editor. Use optional
replace-when-present sections for evolving backup formats, and prove an unmodified export to import
to export cycle is a fixed point.

If a property carries script-set, permission, or reader-specific state, review its
`includeInBookmarks` setting and export behavior together. The documented default and tenant
restore order must be verified for the target SAC experience before making a security claim.

## Import and export data

For widgets that import or export structured content:

- Preserve stable identity keys, or disclose exactly what is lost when ids are reminted.
- Apply validation to every language slot, not only the default language.
- Treat an authored empty string as meaningful. Do not use absence and explicit empty as the same
  signal when seeding defaults unless that is the stated format contract.
- Deep-copy nested templates before editing them. A shallow copy can mutate the preset and poison
  later instances or undo history.
- Use `Object.create(null)` for maps keyed by user-authored names and test a `__proto__` key.
- Never key UI state or membership checks on display labels alone. Centralize case-insensitive name
  matching when the product contract is case-insensitive.
- If a value is hidden from rendering but must remain searchable, enumerate every rendering surface,
  including attributes and accessible names, and test mouse and keyboard activation separately.

For derived numbers or dates, define the precision, timezone owner, rollover, and no-input behavior.
If a feature expires at local end of day, compute the next local midnight minus one millisecond and
test around daylight-saving transitions. A widget has no shared server clock, so a local-time rule
is reader-local unless the product deliberately provides another clock.

## Platform boundaries

A custom widget cannot reliably discover SAC user identity, SAC UI language, or a story-wide
keyboard event. Pass role, user-facing text, and locale-dependent values through declared
properties or story script. `navigator.language` is the browser language, not proof of the SAC UI
language. Give every keyboard entry point a visible control and keep builder chrome language
decisions separate from viewer-content localization.

## Sources and evidence

- Consolidated project lessons supplied for this update: `skill-lessons-consolidated.md`, 2026-08-28.
- SAP Help, Custom Widget Developer Guide: https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/0ac8c6754ff84605a4372468d002f2bf/75311f67527c41638ceb89af9cd8af3e.html
- SAP Custom Widget Developer Guide PDF: https://help.sap.com/doc/c813a28922b54e50bd2a307b099787dc/release/en-US/CustomWidgetDevGuide_en.pdf
