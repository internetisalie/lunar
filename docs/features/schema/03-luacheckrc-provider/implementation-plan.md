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

## Phase 3: Tests and Verification [Must]
**Goal**: Prove the provider maps correctly and triggers the SCHEMA-01 engine.

- Create `LuacheckrcSchemaTest` extending `BasePlatformTestCase`.
- Create a dummy `.luacheckrc` file in the test fixture.
- Verify that inserting an invalid value (e.g. `globals = true`) produces a schema warning.
- Verify basic completion lookup keys (`globals`, `std`).
