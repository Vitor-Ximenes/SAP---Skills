# Production Custom Widget Lessons

Use this reference when a custom widget passes local checks but SAC rejects the upload, or when a widget has builder panels, script methods, icons, fonts, or user-specific state.

The evidence labels are intentional:

- **[VERIFIED]** observed in a controlled tenant upload, jsdom test, or real browser.
- **[DOCUMENTED]** stated by SAP documentation.
- **[UNCONFIRMED]** plausible but not proven on a tenant. Keep the proposed test with the rule.

## Critical upload failures

### Control characters

**[VERIFIED]** A raw character below `U+0020` in a bundled JavaScript file can make SAC reject the complete Resource-ZIP with `CUSTOM_WIDGET_SERVICE_EXCEPTION` and HTTP 500. The response may identify neither the file nor the cause.

Do not use control characters as sentinels or delimiters:

```js
// Bad: a NUL can survive local checks but fail at SAC upload.
var option = "\u0000new";

// Good: use a namespaced printable prefix.
var option = "action:new";
```

Node syntax checks, tests, ZIP integrity checks, and browser previews do not catch this reliably. The manifest should remain pure ASCII. Ordinary non-ASCII characters were accepted in a controlled probe, but still reject `U+007F`, C1 controls, `U+2028`, `U+2029`, `U+FEFF`, and zero-width characters in bundles.

### Script method bodies

**[VERIFIED]** A `methods[].body` is Analytics Designer script. Its `this` can access only properties declared in the same manifest. It cannot call private web component methods or inspect component internals.

```json
{
  "properties": {
    "activeTab": { "type": "string", "default": "" }
  },
  "methods": {
    "openTab": {
      "parameters": [{ "name": "id", "type": "string" }],
      "body": "this.activeTab = id;"
    },
    "getActiveTab": {
      "body": "return this.activeTab;",
      "returnType": "string"
    }
  }
}
```

The component must echo user-driven state through `propertiesChanged` if a script getter is expected to report it:

```js
this.dispatchEvent(new CustomEvent("propertiesChanged", {
  detail: { properties: { activeTab: currentTab } }
}));
```

A declared method with an empty body is not a useful implementation. Give every method a body that
uses the declared property bridge, or do not declare the method.

Rules:

- Declare a property for every value a script must read or write.
- A method cannot return a freshly computed internal value. Return a declared property.
- Omit `returnType` when the body cannot return that type.
- Prefer a delimited `string` for array-like script results because array return types are not documented here.

### Integrity

**[VERIFIED]** Every `webcomponents[]` entry must declare `integrity`. Use exactly one of these states:

```json
{ "integrity": "", "ignoreIntegrity": true }
```

```json
{ "integrity": "sha256-...", "ignoreIntegrity": false }
```

The first is development mode and may show a non-production badge. The second is production mode. Do not put a made-up digest beside `ignoreIntegrity: true`.

Generate the digest from the exact bytes that will be served, preferably from the buffers placed into the ZIP:

```js
function digestOf(file) {
  return "sha256-" + crypto.createHash("sha256")
    .update(fs.readFileSync(file))
    .digest("base64");
}
```

Before delivery, verify all three relationships:

1. The manifest digest matches the built bundle.
2. Editing one source file changes only its digest.
3. The digest matches the bytes inside the final ZIP.

### Upload semantics

**[VERIFIED]** Selecting `widget.json` runs client-side validation. The manifest alone does not reach the server. When the dialog has a Resource File step, the manifest and ZIP are submitted together.

The Resource-ZIP limit is 5 MB **[DOCUMENTED]**. A tenant can hold up to 50 SAC-hosted custom widgets **[DOCUMENTED]**, so delete diagnostic probes. Give every probe a unique `id` and `newInstancePrefix`.

### Confirmed non-causes

**[VERIFIED]** The following passed controlled probes and should not be the first suspect for an opaque upload failure: a 272 KB ZIP, a 208 KB file, three web components in one ZIP, ordinary non-ASCII characters in JavaScript, comments, `localStorage`, absolute HTTPS strings, the text `iframe`, the text `http://`, the `draggable` DOM property, and root-relative `"/widget.js"` URLs. This does not clear the dangerous code points listed above.

## Icons and fonts

### Icons

**[VERIFIED]** Do not resolve icon names with `IconPool` from inside a custom widget. `sap.ui.require` is not reliably reachable there. A missing name renders as an empty icon with no useful warning.

Bundle a committed icon map generated from UI5's `_IconRegistry.js`. When parsing it:

- quoted names usually contain a hyphen, while simple names are unquoted;
- take the low 16 bits of the registry value to remove the flag bits;
- search the registry by the English business term instead of guessing a picture name.

Accept that some requested motifs do not exist. A wrong icon silently becomes a blank space.

### Fonts

- Form controls do not inherit the font. Add `font-family: inherit` for `button`, `input`, `select`, `textarea`, and `optgroup` beside the box-sizing reset.
- Design-time panels should normally declare no font. Font family inherits across the shadow boundary, so a custom stack in a panel must track SAC themes.
- Put brand fonts in the end-user widget, not in builder or styling panels.
- Tenant font registration is not reliably visible inside an isolated widget. Import the approved stylesheet into the widget's shadow style and use a raw `&` in the URL, not `&amp;`.

## Framework boundaries

### User and language context

**[DOCUMENTED]** A custom widget receives no user context or SAC UI language. `Application.getUserInfo()` belongs to story script. `navigator.language` is the browser language and can differ from the SAC language.

Pass personal or locale-dependent values through declared properties and script methods. Render nothing while those properties are unset.

### Keyboard shortcuts

**[VERIFIED]** A widget cannot listen on the story document. A shortcut on the widget shell works only while focus is inside the widget.

Every keyboard entry point needs a visible companion control.

### Bookmarks

**[UNCONFIRMED]** `includeInBookmarks` is documented second-hand as defaulting to `true`, but this was not settled on a tenant. If true, configuration blobs, permission state, and user identity can be restored from another user's bookmark.

Until verified, set `includeInBookmarks: false` on script-set properties and whole configuration blobs. Keep the default only for genuine per-user state such as favourites, dismissals, and recents.

Settle the behavior with this sequence: script `setX("A")`, bookmark, set `X` to `"B"`, reopen the bookmark, then call `getX()`. Also record whether bookmark restore happens before or after `onInitialization`.

## Debugging opaque SAC failures

**[VERIFIED]** When SAC returns a bare 500, build complete uploadable JSON and ZIP probes. Change one variable per probe and preserve the file size when testing content.

Use this order:

```text
0  tiny one-file stub              -> does upload work at all?
1  real bundles, minimal manifest  -> ZIP or manifest?
2  all non-printables escaped      -> encoding?
3  main file only                 -> which file?
4  remove the largest file        -> which file?
5  add the suspect back           -> confirm the file
6  replace suspect with a stub    -> content or size?
7  pad another file to that size  -> rule out size
8  blank comments                 -> comments?
9  blank string literals          -> strings?
10 blank one literal              -> which string?
```

Use a real parser or scanner for source transforms. Regexes can mistake comment-like text inside strings and can damage `customElements.define`. Escape all non-printables, including `U+0000`, not only characters above `U+007F`.

## Preflight checks

Add a source and bundle check before every upload:

```js
test("no source or bundle contains a control character", () => {
  files.forEach((relative) => {
    const source = fs.readFileSync(path.join(ROOT, relative), "utf8");
    for (let i = 0; i < source.length; i++) {
      const code = source.codePointAt(i);
      const allowedControl = code === 0x09 || code === 0x0a || code === 0x0d;
      const dangerous = (!allowedControl && code < 0x20) || code === 0x7f ||
        (code >= 0x80 && code <= 0x9f) || code === 0x2028 || code === 0x2029 ||
        code === 0x200b || code === 0xfeff;
      if (!dangerous) continue;
      const line = source.slice(0, i).split("\n").length;
      assert.fail(relative + " line " + line + " contains U+" +
        code.toString(16).padStart(4, "0").toUpperCase());
    }
  });
});
```

Also check:

| Guard | Prevents |
| --- | --- |
| Pure ASCII manifest | Encoding ambiguity in the manifest |
| Required `integrity` on every component | Hard manifest rejection |
| Method bodies use declared properties only | Analytics Designer script rejection |
| `returnType` matches the returned property | Invalid return type errors |
| ZIP contains permitted root-level files only | Resource package rejection |
| JS tag equals manifest tag | A loaded file that never renders |
| Manifest is generated with the bundles | Stale manifest and SRI drift |
| SRI matches ZIP bytes | A valid-looking manifest with an unloadable file |

Test behavior, not only markup:

- Every button has a click handler and accessible name.
- Icon-only buttons render a glyph.
- Full-cover pseudo-elements use `pointer-events: none`.
- Every manifest property is read by runtime code, not only by a panel.
- Touch hover handlers filter `event.pointerType`.
- Data-keyed maps use `Object.create(null)`.
- Round-trip fixtures include gaps, pinned entries, and out-of-order data.

## Implementation rules

- Keep `widget.js`, `builder.js`, and `styling.js` self-contained. Inline shared logic at build time from one source so copies do not drift.
- If a module cannot import the core escape helper, every caller must escape interpolated user input. Test the interpolated path.
- With two editors over one config, every write path must preserve keys unknown to that editor.
- Coerce values at every boundary. A string `"false"` is truthy.
- Generate manifest defaults, widget defaults, and panel fields from one source where possible.
- Do not use `Number(value) || fallback` when zero is legal. Use an explicit finite check.
- Never key UI state on a display label.
- Patch text fields in place. Full rerenders lose focus and uncommitted text.
- Use `composedPath()` for outside-click detection because rerenders detach the clicked node.
- Wrap `localStorage` in `try/catch` inside SAC iframes.
- Measure after `document.fonts.ready` and do not cache an earlier measurement.

## Preview limits

**[VERIFIED]** A browser preview pane that is not displayed may calculate geometry and styles, but it may not run the rendering lifecycle. In one test, `ResizeObserver` and `requestAnimationFrame` did not fire and smooth scrolling did nothing, while direct `scrollLeft` assignment worked.

Drive DOM events directly and test wiring with jsdom stubs. A clean Node suite, `node --check`, `unzip -t`, or a correct editor view is not proof of SAC importability. A green suite is also not proof of correctness. Review stored-but-unused values, touch behavior, and tests that accidentally pin the wrong behavior.

## Sources

- Project field notes: Configurable Menu Navigation custom widget audit and SAC-hosted Resource-ZIP probes.
- SAP Help: Custom Widgets Developer Guide: https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/0ac8c6754ff84605a4372468d002f2bf/75311f67527c41638ceb89af9cd8af3e.html
- SAP Help: Custom Widget Developer Guide PDF: https://help.sap.com/doc/c813a28922b54e50bd2a307b099787dc/release/en-US/CustomWidgetDevGuide_en.pdf
