---
id: "SCHEMA-04-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SCHEMA-04"
folders:
  - "[[features/schema/04-busted-provider/requirements|requirements]]"
---

# SCHEMA-04: Implementation Plan

## Phases

### Phase 1: Bundled Schema and Provider [Could]
- **Goal**: Add the `busted-config.schema.json` and register the no-arg `BustedSchemaProvider` on the shared SCHEMA-01 `schemaFileProvider` EP.
- **Tasks**:
  - [x] `<depends>com.intellij.modules.json</depends>` already present in `plugin.xml` (SCHEMA-02).
  - [x] Create `src/main/resources/jsonschema/busted-config.schema.json` containing the schema for busted configurations (design §4.1).
  - [x] Create `net.internetisalie.lunar.lang.schema.providers.BustedSchemaProvider` mirroring `LuacheckrcSchemaProvider` (no-arg, no `Project` injection).
  - [x] Register `BustedSchemaProvider` as a `<schemaFileProvider>` (the shared `LuaSchemaProviderFactory` already registered by SCHEMA-02 returns it). No second `<jsonSchema.providerFactory>` is added.
  - [x] Associate `.busted` with the Lua file type (`fileNames`) so the SCHEMA-01 walker engages.
- **Exit criteria**: The platform loads the `.busted` schema when a `.busted` file is opened.

> **Scope note:** This phase deviates from the stale design.md (§2.1/§3/§7), which predates the
> SCHEMA-02 EP-namespace fix. There is no `BustedSchemaProviderFactory`, no `Project` injection, and
> no second provider-factory/`<depends>` registration — that would duplicate the shared
> `LuaSchemaProviderFactory`. The implementation mirrors the SCHEMA-03 prior art exactly.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SCHEMA-04-01 | C | Phase 1 |
| SCHEMA-04-02 | C | Phase 1 |

## Verification Tasks
- [x] Add `BustedSchemaTest` covering a valid `.busted` file and catching invalid fields (TC #1-#4, all green).
- [x] Run `human-verification-checklists.md` for manual UI checks (VNC-verified live in GoLand 2026.1.3: status bar binds "Schema: Busted Config"; TC #1 valid → no warnings; TC #2 → "Property 'bogus' is not allowed"; TC #3 → "Incompatible types. Required: boolean. Actual: string."; TC #4 completion suggests `output`/`verbose`/`coverage`/`pattern`/`ROOT`/`tags` with schema docs; isolation: plain `main.lua` → "No JSON schema").

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Bundled Schema and Provider | done | Could |
