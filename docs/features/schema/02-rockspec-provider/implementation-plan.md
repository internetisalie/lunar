---
id: "SCHEMA-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# SCHEMA-02: Implementation Plan

## Phases

### Phase 1: Rockspec Schema Provider [Must]
- **Goal**: Implement the providers and factory to map `.rockspec` files to their bundled schemas.
- **Tasks**:
  - [x] ~~Create `RockspecSchemaProviderFactory`~~ — **not needed**: SCHEMA-01's existing `LuaSchemaProviderFactory` aggregates the `schemaFileProvider` EP (design §2.1, reconciled).
  - [x] Create `net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProvider` and its nested `V30`/`V31` subclasses implementing the `isAvailable` algorithm (design §2.2, §3.1).
  - [x] Register the providers via `<schemaFileProvider>` EP extensions in `plugin.xml`; remove the SCHEMA-02 `.rockspec` guard in `LuaJsonSchemaEnabler` (design §7, §7.1).
- **Exit criteria**: The IntelliJ platform properly delegates to `RockspecSchemaProviderFactory` for `.rockspec` files; tests confirm v3.0 fallback and v3.1 detection.

### Phase 2: Test Case Parity [Must]
- **Goal**: Port ROCKS-13 test cases to ensure the engine correctly validates rockspecs.
- **Tasks**:
  - [x] Add unit tests verifying schema selection (v3.0 vs v3.1) based on `rockspec_format` (`RockspecSchemaProviderTest`).
  - [x] Add inspection tests (`doHighlighting()`/`checkHighlighting()`/`completeBasic()`) covering unknown keys, missing required fields, value-kind mismatches, and v3.0-vs-v3.1 key allowance (`RockspecSchemaValidationTest`, TC#1-#6).
- **Exit criteria**: All ported ROCKS-13 test cases pass via the schema engine.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SCHEMA-02-01 | M | Phase 1 |
| SCHEMA-02-02 | S | Phase 1 |
| SCHEMA-02-03 | M | Phase 2 |

## Verification Tasks
- [x] Implement `RockspecSchemaProviderTest` for schema version selection.
- [x] Implement `RockspecSchemaValidationTest` for the JSON-Schema engine highlighting.
- [ ] Run `human-verification-checklists.md` (manual VNC DoD gate — outstanding follow-up; automated gates pass).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Rockspec Schema Provider | done | Must |
| Phase 2: Test Case Parity | done | Must |
