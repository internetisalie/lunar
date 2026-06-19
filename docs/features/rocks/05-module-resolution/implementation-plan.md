---
id: "ROCKS-05-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# ROCKS-05: Implementation Plan

## Phases

### Phase 1: Bridge & Data Model Extension [Must]
- **Goal**: Extend `RockspecBridge`/`RockspecData` to parse `build.modules`; add tree-wide rockspec discovery.
- **Tasks**:
  - [ ] Add `buildModules: Map<String, String>` field to `RockspecData` — realizes design §2.2
  - [ ] Add `readBuildModules(obj)` private method to `RockspecBridge.parse()` implementing §3.2 algorithm — realizes design §3.2
  - [ ] Wire `readBuildModules` into `parse()` to populate the new field
  - [ ] Add `allProjectRockspecs(project): List<Path>` to `LuaRocksTreeLocator` — realizes design §2.3, §3.1
  - [ ] Unit test: `RockspecBridge.parse()` with `build.modules` JSON → verify map populated (TC #1)
  - [ ] Unit test: `RockspecBridge.parse()` with no `build` field → verify empty map (TC #2)
  - [ ] Unit test: `RockspecBridge.parse()` with C module entry → verify skipped (TC #3)
  - [ ] Unit test: `allProjectRockspecs()` with nested rockspecs → verify discovered (TC #7)
  - [ ] Unit test: `allProjectRockspecs()` excludes `lua_modules/` paths
- **Exit criteria**: `./gradlew test` green; `RockspecData.buildModules` populated from real rockspec JSON.

### Phase 2: Pattern Derivation & Provider [Must]
- **Goal**: Create `RockspecSourcePathProvider` with the pattern derivation algorithm; wire into `PathConfiguration`.
- **Tasks**:
  - [ ] Create `RockspecSourcePathProvider` as `@Service(PROJECT)` — realizes design §2.1
  - [ ] Implement `derivePatterns(rockspecDir, buildModules)` — realizes design §3.3
  - [ ] Implement `getPatterns()` with `CachedValuesManager` + `ProjectRootModificationTracker` — realizes design §3.4
  - [ ] Implement background computation for `RockspecBridge.read()` calls — realizes design §3.4
  - [ ] Modify `PathConfiguration.getProjectSourcePathPatterns()` to append rockspec patterns — realizes design §2.4
  - [ ] Unit test: `derivePatterns()` with `"adt.orderedmap" → "lua/adt/orderedmap.lua"` → verify pattern (TC #4)
  - [ ] Unit test: `derivePatterns()` with init.lua module → verify pattern (TC #5)
  - [ ] Unit test: `derivePatterns()` with multiple modules sharing same root → verify deduplication
  - [ ] Unit test: `derivePatterns()` with non-matching module path → verify skipped
- **Exit criteria**: `PathConfiguration.getProjectSourcePathPatterns()` returns rockspec-derived patterns; all derivation unit tests green.

### Phase 3: Integration Verification [Should]
- **Goal**: Verify end-to-end that require resolution, completion, auto-import, and library roots work.
- **Tasks**:
  - [ ] Integration test: configure a test project with sub-rockspec layout, verify `LuaRequireReference.resolve()` navigates correctly (TC #6)
  - [ ] Integration test: verify `PlatformLibraryProvider.getExternalLibraries()` includes rockspec source dirs
  - [ ] Integration test: verify `LuaModulePathResolver.resolve()` reverse-maps a rockspec file to module name
  - [ ] Manual verification: run `human-verification-checklists.md` scenarios
- **Exit criteria**: All integration tests green; Ctrl+click on `require("adt.orderedmap")` navigates to the correct file in a workspace layout.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-05-01 | M | Phase 1 |
| ROCKS-05-02 | M | Phase 1 |
| ROCKS-05-03 | M | Phase 2 |
| ROCKS-05-04 | M | Phase 2 |
| ROCKS-05-05 | S | Phase 2 |
| ROCKS-05-06 | S | Phase 2 |

## Verification Tasks
- [ ] Unit tests for `RockspecBridge.parse()` with `build.modules` — covers TC #1, #2, #3
- [ ] Unit tests for `derivePatterns()` — covers TC #4, #5
- [ ] Unit tests for `allProjectRockspecs()` discovery — covers TC #7
- [ ] Integration test for `LuaRequireReference.resolve()` — covers TC #6
- [ ] Integration test for cache invalidation — covers TC #8
- [ ] Run `human-verification-checklists.md`

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Bridge & Data Model Extension | todo | Must |
| Phase 2: Pattern Derivation & Provider | todo | Must |
| Phase 3: Integration Verification | todo | Should |
