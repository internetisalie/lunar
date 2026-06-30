---
id: "SCHEMA-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# SCHEMA-02: Risks & Gaps

## RESOLVED — Live IDE schema binding (VNC gate, 2026-06-30)

**Was a blocker; fixed.** **Root cause:** the JSON-Schema provider factory was declared as
`<JavaScript.JsonSchema.ProviderFactory>` under `defaultExtensionNs=""` (empty), which the platform
resolves to the bogus EP name `.JavaScript.JsonSchema.ProviderFactory` (leading dot) and silently
drops — so `LuaSchemaProviderFactory` was never added to the engine and no Lua data file ever got a
schema in a real IDE. A SCHEMA-01 wiring bug, latent because SCHEMA-01 shipped no providers and the
unit tests manually `registerExtension` the factory. **Fix:** register canonically
(`defaultExtensionNs="JavaScript"` + `<JsonSchema.ProviderFactory>`), matching
`intellij.json.backend.xml`.

**VNC re-verified (live, fix applied):** `.rockspec` binds **Rockspec v3.0** (no `rockspec_format`)
and **Rockspec v3.1** (`rockspec_format = "3.1"`) per the status-bar widget; missing-required
("build"), unknown-key rejection ("Property 'bogus_field' is not allowed"), version-pattern and
type validation all fire; top-level completion offers schema keys with types.

### Original symptom (for the record)
Before the fix, in a running GoLand the engine did **not** bind the schema to a `.rockspec`:

### Evidence (three independent signals)
Opened `test-30.rockspec` (`package`/`version`/`source` + `invalid_key = true`, no `rockspec_format`):
1. Status bar reads **"No JSON schema"** (a provider-supplied schema would name it, e.g. "Rockspec v3.0").
2. The enabled-by-default `LuaJsonSchemaComplianceInspection` produces **no** schema warnings —
   `invalid_key` is flagged only by Lunar's unrelated **"Global creation"** inspection. Expected:
   "Property 'invalid_key' is not allowed".
3. Top-level completion (`Ctrl+Space`) offers only **Lua keywords + existing globals** — no schema
   keys (`package`/`version`/`source`/`description`).

### Ruled out
- **Deployment**: the built plugin jar contains `RockspecSchemaProvider$V30/$V31`, the base
  `LuaSchemaFileProvider`, and `jsonschema/rockspec-schema-v30.json` / `-v31.json`. VM sources match
  the committed code; the enabler's `.rockspec` guard is removed.
- **Extension warnings**: no "unknown extension" / "cannot create extension" / EP-resolution
  warnings in `idea.log`; no exception/stack trace through plugin classes.

### Why unit tests masked it
`RockspecSchemaProviderTest` / `RockspecSchemaValidationTest` (and the SCHEMA-01 engine tests) call
`JsonSchemaProviderFactory.EP_NAME.point.registerExtension(LuaSchemaProviderFactory(), testRootDisposable)`
in `setUp` — i.e. they **manually** add the factory to the platform's
`JavaScript.JsonSchema.ProviderFactory` EP. So they never exercise the `plugin.xml` declarative
registration path, which is the only path that runs in a real IDE. **SCHEMA-02 is the first real
consumer of the SCHEMA-01 engine** (SCHEMA-01 shipped zero providers and excluded `.rockspec`), so
this end-to-end live path had never run before.

### Root-cause leads (for the fix)
- Confirm `LuaSchemaProviderFactory` is actually instantiated and `getProviders(project)` is called
  live (add temporary `log.warn` instrumentation or `#com.jetbrains.jsonSchema` debug logging via
  `-Didea.log.debug.categories`, then re-run `runIde`).
- Confirm `getProviders` returns non-empty — i.e. the custom EP
  `net.internetisalie.lunar.schemaFileProvider` actually has the `V30`/`V31` extensions at runtime.
- Confirm `getSchemaFile()` resolves (`JsonSchemaProviderFactory.getResourceFile` against the plugin
  classloader for `/jsonschema/rockspec-schema-v3*.json`).
- Confirm `LuaJsonLikePsiWalkerFactory` claims the live `.rockspec` `LuaFile` (the engine no-ops if
  no walker claims the file even when a schema is supplied).
- Suspect the bug is in **SCHEMA-01 wiring** (factory/walker/enabler), not SCHEMA-02 — it was never
  live-verified.

### Done
1. ✅ Root-caused and fixed the live binding (Phase 3 — the EP-namespace fix above).
3. ✅ Re-ran the VNC gate (`human-verification-checklists.md`) — passes.

### OPEN follow-ups (minor, SCHEMA-01 walker; do NOT block SCHEMA-02)
- **Boolean-valued unknown property not flagged.** In v3.0, `invalid_key = true` is NOT reported as
  "not allowed", although the v3.0 schema has `additionalProperties: false`. A number-valued unknown
  key (`bogus_field = 42`, v3.1) **is** flagged, so the Lua value adapter / walker appears not to
  surface boolean-literal-valued top-level entries as properties. SCHEMA-02's stated TCs use a
  table-valued key (`test_dependencies`) which works; this is a SCHEMA-01 `LuaJsonLikePsiWalker` gap.
- **Empty-table value vs typed schema.** `test_dependencies = {}` (v3.1) raised "Incompatible types"
  live, where the schema expects the dependency-list shape; the walker's representation of an empty
  Lua table `{}` may not match the schema's expected kind. Worth a SCHEMA-01 walker review.
- **Regression test for declarative registration (recommended).** Add a test that does NOT manually
  `registerExtension` the factory — resolve it from the loaded plugin and assert `JsonSchemaService`
  returns a non-null schema for a `.rockspec` fixture — so the EP-namespace class of bug can't recur
  silently.
