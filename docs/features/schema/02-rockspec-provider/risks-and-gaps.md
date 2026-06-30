---
id: "SCHEMA-02-RISKS"
title: "Risks & Gaps"
type: "risks"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# SCHEMA-02: Risks & Gaps

## OPEN — Live IDE schema binding fails (VNC gate, 2026-06-30)

**Severity: blocker.** SCHEMA-02's unit tests are green, but the **live DoD gate fails**: in a
running GoLand (sandbox `runIde`) the JSON-Schema engine does **not** bind the rockspec schema to a
`.rockspec` file.

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

### Required follow-up
1. Root-cause and fix the live binding (Phase 3).
2. Add a regression test that exercises the **declarative** registration (do NOT manually
   `registerExtension` the factory) — e.g. resolve the factory from the loaded plugin and assert a
   `.rockspec` fixture gets a non-null schema from `JsonSchemaService`.
3. Re-run the VNC gate (`human-verification-checklists.md`) before marking SCHEMA-02 done.
