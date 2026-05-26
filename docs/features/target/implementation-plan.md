---
id: TARGET-PLAN
parent_id: TARGET
type: plan
folders:
  - "[[features/target/requirements|requirements]]"
title: "Implementation Plan"
status: not_implemented
---

# TARGET Implementation Plan

**Feature**: Runtime environment configuration (platform + version selection)  
**Design Document**: [design.md](design.md)  
**Specification**: [Features](requirements.md)  
**Status**: Planning phase  

---

## Overview

This plan decomposes the 6 TARGET requirements into 25 concrete implementation tasks grouped into 6 phases. Tasks are ordered by dependency; where independent, they can be parallelized.

---

---

## Phase 0: Preparatory Activities (IMPL-00)

**Goal**: Complete all prerequisite work before beginning implementation phases; ensure designs are approved, code is ready, and risks are mitigated.  
**Duration**: ~2-3 days  
**Depends on**: None (runs in parallel with or before all phases)  
**Deliverable**: All blockers removed; team aligned; test data prepared; risk mitigation in place.

| ID | Task | Owner | Notes |
|:---|:-----|:------|:------|
| `IMPL-00-01` | Review & sign-off on `design.md` | — | All stakeholders confirm design is correct; capture feedback |
| `IMPL-00-02` | Review & sign-off on all 6 spec documents | — | Verify no contradictions between specs; acceptance criteria are testable |
| `IMPL-00-03` | Identify edge case clarity gaps | — | Walk through each spec; document handling of Lua 5.5, null stds, unknown versions, etc. |
| `IMPL-00-04` | Add `LUAJIT` and `NGX` to `LuaPlatform` enum | — | Standalone PR; unblocks Phase 1 IMPL-01; includes `pathSegment` property |
| `IMPL-00-05` | Create `runtime/` directory skeleton | — | Create subdirs: `standard/`, `luajit/`, `redis/`, etc. (empty for now) |
| `IMPL-00-06` | Verify luacheck supports `lua55` std | — | Check luacheck docs/source; if not supported yet, document fallback plan (`lua54`) |
| `IMPL-00-07` | Create synthetic legacy project test data | — | Generate `.idea/lunar.xml` files with old `languageLevel` and `platform` fields for Phase 6 tests |
| `IMPL-00-08` | CI/CD alignment: verify test infrastructure | — | Ensure `./gradlew test` can run new unit + integration tests without modification |
| `IMPL-00-09` | Risk mitigation: document known issues | — | List any blockers found; document workarounds; flag for escalation if needed |
| `IMPL-00-10` | Create risk register | — | Document risks from design review (e.g., "luacheck may not have lua55 std yet") |

---

## Phase 0 Acceptance Criteria

- [ ] All stakeholders have reviewed and approved design + specs
- [ ] No contradictions found in spec documents
- [ ] `LuaPlatform` enum updated with `LUAJIT`, `NGX`, and `pathSegment` property
- [ ] `runtime/` directory skeleton exists in `src/main/resources/`
- [ ] Luacheck `lua55` support verified (or workaround documented)
- [ ] Synthetic legacy project files created and staged for Phase 6 tests
- [ ] CI/CD infrastructure confirmed ready
- [ ] Risk register documented and shared with team

---

## Implementation Sequence

1. **Execute Phase 0** → Get team alignment and prerequisites done
2. **Execute Phase 1** → Build data model foundation
3. **Execute Phases 2-5** → Implement features (can parallelize 3/4/5)
4. **Execute Phase 6** → Migration validation
5. **Execute Phase 7** → QA, docs, release

---

## Phase 1: Data Model Foundation

**Goal**: Establish the core `Target`, `LuaPlatform`, `VersionEntry`, and registry types.  
**Duration**: ~2-3 days  
**Deliverable**: All types compile, registry is populated, no integration yet.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-01` | Add `LUAJIT` and `NGX` to `LuaPlatform` enum | — | — | Add enum entries; no `pathSegment` yet |
| `IMPL-02` | Add `pathSegment: String` property to `LuaPlatform` enum | `IMPL-01` | — | Assign stable path names per design.md Table 1 |
| `IMPL-03` | Create `VersionEntry` data class | — | — | File: `src/main/kotlin/.../platform/target/VersionEntry.kt` |
| `IMPL-04` | Create `Target` data class | `IMPL-02`, `IMPL-03` | — | Implement `getLibraryRootPath()`, `getLuacheckStd()`, `getImplicitLanguageLevel()` stubs |
| `IMPL-05` | Create `PlatformVersionRegistry` singleton | `IMPL-04` | — | Populate with all version entries per design.md §1.3 |
| `IMPL-06` | Implement `Target.getImplicitLanguageLevel()` | `IMPL-04` | — | Use derivation rules from TARGET-02 spec |
| `IMPL-07` | Add unit tests for `Target` | `IMPL-04`, `IMPL-06` | — | Test all (platform, version) → language level mappings |
| `IMPL-08` | Add unit tests for `PlatformVersionRegistry` | `IMPL-05` | — | Test `getVersions()`, `defaultVersion()`, `findVersion()` |

---

## Phase 2: Settings Integration

**Goal**: Update `LuaProjectSettings` to store and migrate `Target`; deprecate legacy fields.  
**Duration**: ~2-3 days  
**Depends on**: Phase 1  
**Deliverable**: Settings round-trip correctly; legacy projects migrate on load.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-09` | Add `target: Target?` field to `LuaProjectSettings.State` | `IMPL-04` | — | File: `src/main/kotlin/.../settings/LuaProjectSettings.kt` |
| `IMPL-10` | Mark `platform` field in State as deprecated | `IMPL-09` | — | Document that it is superseded by `target.platform` |
| `IMPL-11` | Implement `migrateFromLegacySettings()` | `IMPL-09` | — | Ref: TARGET-06 spec; handle all `LuaLanguageLevel` values |
| `IMPL-12` | Add `getTarget()` accessor on `LuaProjectSettings` | `IMPL-09`, `IMPL-11` | — | Calls `migrateFromLegacySettings()` if `target` is null |
| `IMPL-13` | Add `setTarget()` accessor on `LuaProjectSettings` | `IMPL-09` | — | Persists target and keeps `languageLevel` in sync (transition period) |
| `IMPL-14` | Update XML serialization for `target` field | `IMPL-09` | — | Write `{platform: enumName, version: label}` map |
| `IMPL-15` | Update XML deserialization for `target` field | `IMPL-09`, `IMPL-05` | — | Call `PlatformVersionRegistry.findVersion()`; fallback to `defaultVersion()` |
| `IMPL-16` | Add unit tests for settings round-trip | `IMPL-14`, `IMPL-15` | — | Serialize, deserialize, verify `Target` is correct |
| `IMPL-17` | Add unit tests for legacy migration | `IMPL-11` | — | Test all scenarios in TARGET-06 spec table |

---

## Phase 3: UI Update

**Goal**: Replace language-level dropdown with contextual platform + version dropdowns.  
**Duration**: ~2-3 days  
**Depends on**: Phase 1, Phase 2  
**Deliverable**: Settings panel UI works; language level is read-only; version list updates on platform change.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-18` | Create `ComboBox<LuaPlatform>` in settings panel | `IMPL-01` | — | File: `src/main/kotlin/.../settings/LuaProjectSettingsPanel.kt` |
| `IMPL-19` | Create `ComboBox<VersionEntry>` in settings panel | `IMPL-03` | — | Renderer must display `version.label` |
| `IMPL-20` | Replace language-level dropdown with `JLabel` | — | — | Label is read-only, updated dynamically |
| `IMPL-21` | Implement `onPlatformChanged()` handler | `IMPL-18`, `IMPL-19`, `IMPL-05` | — | Repopulate version combo from `PlatformVersionRegistry`; select default |
| `IMPL-22` | Implement `updateLanguageLevelDisplay()` | `IMPL-20`, `IMPL-06` | — | Derive level from current `(platform, version)` and display |
| `IMPL-23` | Update `apply()` / `reset()` / `isModified()` | `IMPL-12`, `IMPL-13`, `IMPL-21` | — | Ref: TARGET-03 spec |
| `IMPL-24` | Add unit tests for UI state transitions | `IMPL-18`, `IMPL-19`, `IMPL-21` | — | Test platform change → version repopulation flow |

---

## Phase 4: Library Resolution & Migration

**Goal**: Migrate resources to `runtime/` tree; implement unified library provider.  
**Duration**: ~3-4 days  
**Depends on**: Phase 1, Phase 2  
**Deliverable**: Resources migrated; library loading works for all platforms; old paths removed.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-25` | Prepare resource directory structure | `IMPL-02` | — | Create `src/main/resources/runtime/` subdirs per design.md §2 |
| `IMPL-26` | Migrate `platform/Lua51/` → `runtime/standard/lua-5.1/` | `IMPL-25` | — | Move all `.lua` files; delete old directory |
| `IMPL-27` | Migrate `platform/Lua52/` → `runtime/standard/lua-5.2/` | `IMPL-25` | — | Repeat for Lua 5.3, 5.4 |
| `IMPL-28` | Migrate `sdk/redis-5/` → `runtime/redis/redis-5/` | `IMPL-25` | — | Repeat for redis-6; rename `redis-8/` → `redis-7/` |
| `IMPL-29` | Create `RuntimeLibraryProvider` class | `IMPL-04`, `IMPL-25` | — | File: `src/main/kotlin/.../platform/target/RuntimeLibraryProvider.kt` |
| `IMPL-30` | Update `PlatformLibraryProvider.getPlatformLibrary()` | `IMPL-29`, `IMPL-12` | — | Use `target.getLibraryRootPath()` instead of hardcoded path |
| `IMPL-31` | Add `LuaSettingsChangedEvent` | — | — | Fire when `setTarget()` is called |
| `IMPL-32` | Listen for `LuaSettingsChangedEvent` in library provider | `IMPL-31` | — | Clear cache / refresh index when target changes |
| `IMPL-33` | Add unit tests for `RuntimeLibraryProvider` | `IMPL-29` | — | Test `getLibraryRoot()` and `getLibraryFiles()` for all platforms |
| `IMPL-34` | Add integration test: library files load after target change | `IMPL-32`, `IMPL-33` | — | Verify completion works after switching target |

---

## Phase 5: Luacheck Integration

**Goal**: Pass correct `--std` to luacheck based on `Target`.  
**Duration**: ~1-2 days  
**Depends on**: Phase 1, Phase 2  
**Deliverable**: Luacheck invokes with correct `--std` for all platforms; cache invalidates on target change.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-35` | Update luacheck invocation to use `target.getLuacheckStd()` | `IMPL-13`, `IMPL-06` | — | File: `src/main/kotlin/.../analysis/luacheck/LuacheckRunner.kt` |
| `IMPL-36` | Handle null `luacheckStd` (omit `--std` argument) | `IMPL-35` | — | Do not pass invalid std; luacheck uses default |
| `IMPL-37` | Listen for `LuaSettingsChangedEvent` in luacheck | `IMPL-31` | — | Clear cached analysis when target changes |
| `IMPL-38` | Add unit tests for `getLuacheckStd()` | `IMPL-06` | — | Test all platforms/versions from spec table in TARGET-05 |
| `IMPL-39` | Add integration test: luacheck std changes with target | `IMPL-35`, `IMPL-37` | — | Switch target, verify `--std` is updated in next analysis |

---

## Phase 6: Migration Validation

**Goal**: Validate that legacy projects migrate correctly; verify no data loss; ensure backward compatibility.  
**Duration**: ~1-2 days  
**Depends on**: Phase 2 (settings implementation), Phase 4 (resource migration)  
**Deliverable**: All migration paths tested; legacy data integrity confirmed.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-40` | Create synthetic legacy project files for testing | `IMPL-14` | — | Generate `.idea/lunar.xml` with old `languageLevel` and `platform` fields |
| `IMPL-41` | Unit test: migrate STANDARD + languageLevel | `IMPL-17` | — | Verify all `LuaLanguageLevel` values → correct version label |
| `IMPL-42` | Unit test: migrate non-STANDARD platform | `IMPL-17` | — | REDIS, TARANTOOL, etc. → platform's default version |
| `IMPL-43` | Unit test: handle unknown version label on load | `IMPL-15` | — | Verify unknown label falls back to `defaultVersion()` |
| `IMPL-44` | Integration test: load legacy project and inspect state | `IMPL-40`, `IMPL-12` | — | Verify `getTarget()` returns migrated target after load |
| `IMPL-45` | Manual testing: legacy project with language level only | `IMPL-44` | — | Open project with just `languageLevel` set; verify target appears correctly |
| `IMPL-46` | Manual testing: legacy project with platform only | `IMPL-44` | — | Open project with just `platform` set; verify target appears and uses default version |
| `IMPL-47` | Verify resource migration integrity | `IMPL-26`, `IMPL-27`, `IMPL-28` | — | Verify file counts match; no files left behind; old paths are deleted |
| `IMPL-48` | Verify backward compat: old launcher/debugger still works | `IMPL-44` | — | If legacy code reads `state.languageLevel`, ensure it gets correct derived value |

---

## Phase 7: Final QA & Release

**Goal**: Comprehensive test coverage; verify end-to-end functionality; document for users and QA; prepare for release.  
**Duration**: ~3-4 days  
**Depends on**: All previous phases; particularly Phase 6 completion  
**Deliverable**: All tests pass; no regressions; feature is production-ready; QA scenarios and user docs prepared.

| ID | Task | Depends | Owner | Notes |
|:---|:-----|:--------|:------|:------|
| `IMPL-49` | Run full test suite (`./gradlew test`) | All phases | — | All tests must pass; fix any new failures |
| `IMPL-50` | Verify old `platform/` resource directory removed | `IMPL-47` | — | Confirm deletion in git; no stale files left |
| `IMPL-51` | Verify old `sdk/` resource directory removed | `IMPL-47` | — | Confirm deletion in git; no stale files left |
| `IMPL-52` | Clean up deprecated code | — | — | Remove `SdkDiscoveryService` or similar if it exists; design changed to pre-populated SDKs |
| `IMPL-53` | Manual testing: settings panel interaction | `IMPL-23` | — | Open project, verify platform/version combos, language level updates |
| `IMPL-54` | Manual testing: library completion for each platform | `IMPL-34` | — | Verify code completion works for STANDARD, REDIS, other platforms after switching targets |
| `IMPL-55` | Manual testing: luacheck analysis with different stds | `IMPL-39` | — | Create test files, verify no false positives/negatives per platform |
| `IMPL-56` | Manual testing: settings panel with multiple projects | `IMPL-53` | — | Verify targets don't bleed between projects; each project has independent target |
| `IMPL-57` | Performance profiling: no indexing/completion regressions | All phases | — | Verify settings change and library switch don't slow down IDE |
| `IMPL-58` | Create QA verification scenarios document | `IMPL-53`, `IMPL-55` | — | File: `docs/features/target/qa-verification-scenarios.md`; formal test cases for QA sign-off |
| `IMPL-59` | Create user guide / walkthrough document | `IMPL-53`, `IMPL-58` | — | File: `docs/features/target/user-guide.md`; screenshots, step-by-step for end users |
| `IMPL-60` | Update CHANGELOG.md | All phases | — | Summarize TARGET feature, list new platforms, migration notes, user-visible changes |
| `IMPL-61` | Code review & merge | All phases | — | One reviewer; check for style, performance, completeness, spec compliance |

---

## Dependency Graph

```
Phase 0: Preparatory Activities
├── IMPL-00-01 (design review)
├── IMPL-00-02 (spec review)
├── IMPL-00-03 (edge cases)
├── IMPL-00-04 (LuaPlatform enum) ← unblocks Phase 1
├── IMPL-00-05 (runtime dir) ← unblocks Phase 4
├── IMPL-00-06 (luacheck lua55)
├── IMPL-00-07 (legacy test data) ← needed for Phase 6
├── IMPL-00-08 (CI/CD)
├── IMPL-00-09 (risk mitigation)
└── IMPL-00-10 (risk register)

Phase 1: Data Model Foundation (after IMPL-00-04)
├── IMPL-01 → IMPL-02 → IMPL-04
├── IMPL-03 → IMPL-04
├── IMPL-04 → IMPL-06, IMPL-07
└── IMPL-05

Phase 2: Settings Integration
├── IMPL-09 → IMPL-11, IMPL-12, IMPL-13
├── IMPL-14 → IMPL-16
├── IMPL-15 → IMPL-16
└── IMPL-11, IMPL-09 → IMPL-17

Phase 3: UI Update
├── IMPL-18, IMPL-19, IMPL-20
├── IMPL-21 ← IMPL-18, IMPL-19, IMPL-05
├── IMPL-22 ← IMPL-20, IMPL-06, IMPL-21
└── IMPL-23 ← IMPL-12, IMPL-13, IMPL-21, IMPL-24

Phase 4: Library Resolution (after IMPL-00-05)
├── IMPL-25 ← IMPL-02
├── IMPL-26, IMPL-27, IMPL-28 ← IMPL-25
├── IMPL-29 ← IMPL-04, IMPL-25
├── IMPL-30 ← IMPL-29, IMPL-12
├── IMPL-31
├── IMPL-32 ← IMPL-31
└── IMPL-34 ← IMPL-32, IMPL-33

Phase 5: Luacheck
├── IMPL-35 ← IMPL-13, IMPL-06
├── IMPL-36 ← IMPL-35
├── IMPL-37 ← IMPL-31
└── IMPL-39 ← IMPL-35, IMPL-37

Phase 6: Migration Validation (after IMPL-00-07)
├── IMPL-40 ← IMPL-14
├── IMPL-41-43 ← IMPL-17
├── IMPL-44 ← IMPL-40, IMPL-12
└── IMPL-45-48 ← IMPL-44

Phase 7: Final QA & Release
└── All phases
```

---

## Estimated Timeline

| Phase | Duration | Est. Start |
|:------|:---------|:-----------|
| Phase 0 | 2-3 days | Day 0 |
| Phase 1 | 2-3 days | Day 3 |
| Phase 2 | 2-3 days | Day 6 |
| Phase 3 | 2-3 days | Day 9 |
| Phase 4 | 3-4 days | Day 9 (parallel with Phase 3) |
| Phase 5 | 1-2 days | Day 12 (parallel with Phase 4) |
| Phase 6 | 1-2 days | Day 15 |
| Phase 7 | 3-4 days | Day 16 |
| **Total** | **~17-24 days** | — |

---

## Parallelization Opportunities

- **Phases 3 & 4** can run in parallel (both depend on Phase 1 & 2)
- **Phase 5** can start as soon as Phase 1 is complete
- **Phase 6** must complete all other phases first

---

## Key Implementation Notes

### Resource Migration (Phase 4)

1. Ensure git history is preserved during migration (use `git mv`, not copy+delete)
2. Update any hard-coded path references in code or tests
3. After migration, run full test suite to verify resource loading still works

### Settings Serialization (Phase 2)

1. Dual-field strategy: write both `target` and `languageLevel` during transition period
2. Deserialization tries `target` first; falls back to migration logic if absent
3. All unknown version labels gracefully fallback to `defaultVersion()` — no exceptions

### UI Event Handling (Phase 3)

1. Use `ItemListener` or `ActionListener` on platform combo, not `ChangeListener`
2. Ensure `updateLanguageLevelDisplay()` is called after version combo is repopulated, not before
3. Test `reset()` flow with a dirty panel to ensure state consistency

### Luacheck Integration (Phase 5)

1. Null std → omit `--std` argument entirely (do not pass empty string or fallback value)
2. Listen for `LuaSettingsChangedEvent` to invalidate cached analysis results
3. Test with Redis and STANDARD projects to verify `--std` is correct in invocation logs

---

## Testing Strategy

**Unit Tests** (per phase):
- Phase 1: 2-3 tests for enum, registry, target derivation
- Phase 2: 2-3 tests for settings serialization/deserialization, migration scenarios
- Phase 3: None (UI unit tests are minimal; prefer manual + integration)
- Phase 4: 2-3 tests for resource resolution, library file loading
- Phase 5: 1-2 tests for `getLuacheckStd()` mapping, invocation argument construction

**Integration Tests**:
- Settings round-trip with all target combinations
- Library completion after target switch
- Luacheck analysis with different stds
- Legacy project migration

**Manual Tests** (Phase 6):
- Open real project; switch platforms and versions; verify no crashes
- Code completion works for selected platform
- Luacheck produces correct results per platform
- Opening old project with legacy settings auto-migrates

---

## Acceptance Criteria (Feature-Level)

- [ ] Phase 0: All preparatory activities complete; team aligned; no blockers
- [ ] All 61 implementation tasks (Phase 1-7) completed and tested
- [ ] Phase 6 migration validation: all legacy projects migrate without data loss
- [ ] Phase 7 QA: feature test suite passes; no regressions in full test suite
- [ ] Phase 7 documentation: QA verification scenarios and user guide created
- [ ] All 6 requirements (TARGET-01 through TARGET-06) are satisfied
- [ ] No deprecation warnings introduced
- [ ] Code reviewed and approved
- [ ] CHANGELOG.md updated
