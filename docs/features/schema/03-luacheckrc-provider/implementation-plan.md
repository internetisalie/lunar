---
id: "SCHEMA-03-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SCHEMA-03"
folders:
  - "[[features/schema/03-luacheckrc-provider/requirements|requirements]]"
---

# Implementation Plan: SCHEMA-03 — Luacheckrc Schema Provider

## Phase 1: Schema Asset [Must] — Done

**Goal**: Bundle the luacheck configuration JSON schema.

- [x] Create `src/main/resources/jsonschema/luacheck-config.schema.json`.
- [x] Populate with draft-07 JSON schema covering all documented luacheck options (`std`, `globals`, `ignore`, etc.).
- [x] Ensure `additionalProperties` handling is relaxed (root `additionalProperties: true`) to support undocumented or future luacheck keys gracefully.

## Phase 2: Schema Provider [Must] — Done

**Goal**: Implement the provider and register it to map `.luacheckrc` files.

> **Architecture note:** The original plan called for a per-provider
> `LuacheckrcSchemaProviderFactory` under a second `<JavaScript.JsonSchema.ProviderFactory>`.
> That is stale — it predates the SCHEMA-02 EP-namespace fix and would re-introduce the bug
> SCHEMA-02 resolved. The real architecture contributes providers to the custom
> `net.internetisalie.lunar.schemaFileProvider` EP, which the single shared
> `LuaSchemaProviderFactory` returns to the platform. So: no factory subclass, one
> `<schemaFileProvider>` line.

- [x] Create `net.internetisalie.lunar.lang.schema.providers.LuacheckrcSchemaProvider` extending `LuaSchemaFileProvider`.
- [x] Implement `isAvailable` targeting `.luacheckrc` and `.luacheckrc.lua`.
- [x] Register the provider in `plugin.xml` as a `<schemaFileProvider>` under `net.internetisalie.lunar`.
- [x] Associate bare `.luacheckrc` (extensionless dotfile) with the Lua file type via `fileNames=".luacheckrc"` on the existing Lua `<fileType>` — required so the SCHEMA-01 engine (gated on `LuaFileType`) engages. `.luacheckrc.lua` already maps via the `lua` extension.

## Phase 3: Tests and Verification [Must] — Done

**Goal**: Prove the provider maps correctly and triggers the SCHEMA-01 engine.

- [x] Create `LuacheckrcSchemaTest` extending `BasePlatformTestCase` (mirrors `RockspecSchemaValidationTest`: registers `LuaSchemaProviderFactory` on the platform provider-factory EP and enables `LuaJsonSchemaComplianceInspection`).
- [x] TC #1 `globals = true` → type-mismatch (expected array) warning. **Passes** — the SCHEMA-01 boolean-value walker gap (flagged in SCHEMA-02 risks) did NOT manifest for a bare top-level boolean assignment; the engine surfaces it and validation fires. An integer-mismatch control (`globals = 1`) is also asserted.
- [x] TC #2 `max_line_length = 120` → no warning (integer allowed).
- [x] TC #3 `max_line_length = false` → no warning (boolean false disables; intOrBool union).
- [x] TC #4 top-level completion suggests `std`/`globals`/`ignore`/`max_line_length`.
- [x] TC #5 isolation: plain `main.lua` with `globals = true` → no schema warning.

All 6 tests green; full suite 1326 tests / 0 failures / 1 skip; `ktlintCheck` clean.
