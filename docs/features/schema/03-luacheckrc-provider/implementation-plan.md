---
id: "SCHEMA-03-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SCHEMA-03"
folders:
  - "[[features/schema/03-luacheckrc-provider/requirements|requirements]]"
---

# Implementation Plan: SCHEMA-03 — Luacheckrc Schema Provider

## Phase 1: Schema Asset [Must]
**Goal**: Bundle the luacheck configuration JSON schema.

- Create `src/main/resources/jsonschema/luacheck-config.schema.json`.
- Populate with draft-07 JSON schema covering all documented luacheck options (`std`, `globals`, `ignore`, etc.).
- Ensure `additionalProperties` handling is relaxed to support undocumented or future luacheck keys gracefully.

## Phase 2: Schema Provider and Factory [Must]
**Goal**: Implement the provider and register it to map `.luacheckrc` files.

- Create `net.internetisalie.lunar.lang.schema.LuacheckrcSchemaProvider` extending `LuaSchemaFileProvider`.
- Implement `isAvailable` targeting `.luacheckrc` and `.luacheckrc.lua`.
- Create `net.internetisalie.lunar.lang.schema.LuacheckrcSchemaProviderFactory`.
- Register the factory in `plugin.xml` under `<JavaScript.JsonSchema.ProviderFactory>`.

## Phase 3: Tests and Verification [Must]
**Goal**: Prove the provider maps correctly and triggers the SCHEMA-01 engine.

- Create `LuacheckrcSchemaTest` extending `BasePlatformTestCase`.
- Create a dummy `.luacheckrc` file in the test fixture.
- Verify that inserting an invalid value (e.g. `globals = true`) produces a schema warning.
- Verify basic completion lookup keys (`globals`, `std`).
