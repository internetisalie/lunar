---
id: "SCHEMA-01-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "SCHEMA-01"
folders:
  - "[[features/schema/01-engine/requirements|requirements]]"
---

# SCHEMA-01: Implementation Plan

All new code in `net.internetisalie.lunar.lang.schema`. De-risking spikes (DR-01..DR-04, see
risks-and-gaps.md) should run **before** Phase 2 since they confirm platform behaviours the design
depends on.

## Phase 0: De-risk platform behaviours [Must]
- **Tasks**:
  - [ ] DR-01 pointer oracle; DR-02 base-inspection accessibility; DR-03 engine-supplied completion;
    DR-04 bundled-schema resource helper (risks-and-gaps.md).
- **Exit criteria**: all four DR questions answered; design §2.5/§2.6/§3.2 confirmed or adjusted.

## Phase 1: Dependency + skeleton [Must]
- **Goal**: the JSON engine is on the classpath and a no-op walker is registered.
- **Tasks**:
  - [ ] Add `com.intellij.modules.json` to `gradle.properties` `platformBundledPlugins` and
    `<depends>` in `plugin.xml` (design §2.1).
  - [ ] `LuaJsonLikePsiWalkerFactory` + `LuaJsonSchemaEnabler` (returns false until providers exist),
    registered under `com.intellij.json` (design §2.4).
- **Exit criteria**: project compiles + verifies (`./gradlew build`) with the JSON dependency; no
  behaviour change yet.

## Phase 2: Adapters + walker [Must]
- **Goal**: Lua data PSI presents as a JSON-like model.
- **Tasks**:
  - [ ] `LuaValueAdapter` / `LuaObjectAdapter` / `LuaFileObjectAdapter` / `LuaArrayAdapter` /
    `LuaPropertyAdapter` (design §2.3).
  - [ ] `LuaJsonLikePsiWalker` method mapping incl. `getRoots` (§3.1), `findPosition` (§3.2),
    `isObjectTable` (§3.4), name/value flags (§3.3).
- **Exit criteria**: adapter/`getRoots`/`isObjectTable`/`findPosition` unit tests green (DR-01 oracle).

## Phase 3: Compliance inspection + engine wiring [Must]
- **Goal**: validation surfaces on provider-claimed Lua files.
- **Tasks**:
  - [ ] `LuaJsonSchemaComplianceInspection` (`language="Lua"`) per design §2.5; enabler now gates on
    `JsonSchemaService.getSchemaFilesForFile`.
  - [ ] Base `LuaSchemaFileProvider` / `LuaSchemaProviderFactory` (design §2.6).
  - [ ] TEST-ONLY provider + harness schema for the fixture extension(s).
- **Exit criteria**: TC #1-#6 pass (`LuaJsonSchemaEngineTest`), including the plain-`.lua` negative
  (TC #6) and the enum/array checks (TC #2, #3) proving real JSON-Schema semantics.

## Phase 4: Completion + docs [Must / Should]
- **Goal**: schema completion + hover flow through the walker.
- **Tasks**:
  - [ ] Confirm engine-supplied completion/docs fire for Lua (DR-03); add the thin delegating
    `language="Lua"` completion contributor only if DR-03 shows it is required (Gap 2.3).
- **Exit criteria**: TC #7 (completion) + TC #8 (Quick-Doc) pass.

## Phase 5: Verify in IDE [Should]
- **Tasks**:
  - [ ] verify-in-ide: a fixture-mapped Lua data file shows schema squiggles, completion, and
    Quick-Doc; a plain `.lua` shows none (human-verification-checklists.md).
- **Exit criteria**: all checklist scenarios pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| SCHEMA-01-01 JSON plugin dependency | M | Phase 1 |
| SCHEMA-01-02 Lua walker | M | Phase 2 |
| SCHEMA-01-03 Lua adapters | M | Phase 2 |
| SCHEMA-01-04 Two document shapes | M | Phase 2 |
| SCHEMA-01-05 Object vs array | M | Phase 2 |
| SCHEMA-01-06 Walker factory + enabler | M | Phase 1 / Phase 3 |
| SCHEMA-01-07 Compliance inspection | M | Phase 3 / Phase 4 |
| SCHEMA-01-08 Provider seam + safety | M | Phase 3 |

## Verification Tasks
- [ ] Adapter + walker unit tests (DR-01 pointer oracle).
- [ ] `LuaJsonSchemaEngineTest` — TC #1-#8 via the TEST-ONLY provider + harness schema.
- [ ] `./gradlew test`, `ktlintFormat`, `ktlintCheck`.
- [ ] verify-in-ide pass.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risk platform behaviours | todo | Must |
| Phase 1: Dependency + skeleton | todo | Must |
| Phase 2: Adapters + walker | todo | Must |
| Phase 3: Compliance inspection + wiring | todo | Must |
| Phase 4: Completion + docs | todo | Must |
| Phase 5: Verify in IDE | todo | Should |
