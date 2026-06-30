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
- **Goal**: Add the `busted-config.schema.json` and register the `BustedSchemaProviderFactory`.
- **Tasks**:
  - [ ] Add `<depends>com.intellij.modules.json</depends>` to `plugin.xml` if not already present.
  - [ ] Create `src/main/resources/jsonschema/busted-config.schema.json` containing the schema for busted configurations (design §4.1).
  - [ ] Create `net.internetisalie.lunar.lang.schema.providers.BustedSchemaProvider` (design §2.2, §3.1).
  - [ ] Create `net.internetisalie.lunar.lang.schema.providers.BustedSchemaProviderFactory` (design §2.1).
  - [ ] Register `BustedSchemaProviderFactory` as `<jsonSchema.providerFactory>` in `plugin.xml` (design §7).
- **Exit criteria**: The platform loads the `.busted` schema when a `.busted` file is opened.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SCHEMA-04-01 | C | Phase 1 |
| SCHEMA-04-02 | C | Phase 1 |

## Verification Tasks
- [ ] Add `LuaJsonSchemaBustedProviderTest` covering a valid `.busted` file and catching invalid fields.
- [ ] Run `human-verification-checklists.md` for manual UI checks.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Bundled Schema and Provider | todo | Could |
