---
id: "SCHEMA-02-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "SCHEMA-02"
folders:
  - "[[features/schema/02-rockspec-provider/requirements|requirements]]"
---

# SCHEMA-02: Implementation Plan

## Phases

### Phase 1: Rockspec Schema Provider [Must]
- **Goal**: Implement the providers and factory to map `.rockspec` files to their bundled schemas.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProviderFactory` (design §2.1).
  - [ ] Create `net.internetisalie.lunar.lang.schema.providers.RockspecSchemaProvider` and its nested `V30`/`V31` subclasses implementing the `isAvailable` algorithm (design §2.2, §3.1).
  - [ ] Register `RockspecSchemaProviderFactory` in `plugin.xml` (design §7).
- **Exit criteria**: The IntelliJ platform properly delegates to `RockspecSchemaProviderFactory` for `.rockspec` files; tests confirm v3.0 fallback and v3.1 detection.

### Phase 2: Test Case Parity [Must]
- **Goal**: Port ROCKS-13 test cases to ensure the engine correctly validates rockspecs.
- **Tasks**:
  - [ ] Add unit tests verifying schema selection (v3.0 vs v3.1) based on `rockspec_format`.
  - [ ] Add inspection tests (e.g. `doHighlighting()`) checking for unknown keys, missing required fields, and value kind mismatches using the Rockspec schema.
- **Exit criteria**: All ported ROCKS-13 test cases pass via the schema engine.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SCHEMA-02-01 | M | Phase 1 |
| SCHEMA-02-02 | S | Phase 1 |
| SCHEMA-02-03 | M | Phase 2 |

## Verification Tasks
- [ ] Implement `RockspecSchemaProviderTest` for schema version selection.
- [ ] Implement `RockspecSchemaValidationTest` for the JSON-Schema engine highlighting.
- [ ] Run `human-verification-checklists.md`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Rockspec Schema Provider | todo | Must |
| Phase 2: Test Case Parity | todo | Must |
