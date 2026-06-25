---
id: "ROCKS-05-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# ROCKS-05: Implementation Plan

Sequence the design (design.md) into verifiable phases. Discovery is **not** built here — it is
consumed from ROCKS-09 (`LuaRockspecDiscoveryService.discoverRockspecPaths`); a TEST-ONLY stub of
that seam may stand in for unit tests until ROCKS-09 lands.

## Phases

### Phase 1: Bridge build data [Must]
- **Goal**: `RockspecBridge` surfaces `build.type` / `build.modules` (Lua vs C entries).
- **Tasks**:
  - [x] Add `buildType: String?`, `luaModules: Map<String,String>`, `cModules: Map<String,List<String>>`
    to `RockspecData` ([RockspecBridge.kt:13](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt)) — realizes design section 2.3.
  - [x] Extend `RockspecBridge.parse()` ([RockspecBridge.kt:49](../../../../src/main/kotlin/net/internetisalie/lunar/rocks/RockspecBridge.kt))
    to read the `build` JSON object per design section 4.1 (string -> luaModules, array -> cModules).
- **Exit criteria**: TC #1, #2, #3 pass (`RockspecBridgeTest` over the section 4.1 JSON shapes).

### Phase 2: Source-root derivation [Must]
- **Goal**: pure transform from `build.modules` to source-root `SourcePathPattern`s.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.RockspecModuleDerivation` (design section 2.1)
    implementing the section 3.1 common-prefix algorithm, emitting `?.lua` + `?/init.lua` per root.
- **Exit criteria**: TC #4, #5 pass (`RockspecModuleDerivationTest`, pure unit, no fixture).

### Phase 3: IDE side — consumer A [Must]
- **Goal**: derived patterns appear at the single chokepoint and feed require-resolution/indexing.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.RockspecSourcePathProvider`
    (`@Service(Service.Level.PROJECT)`, design section 2.3): consume
    `LuaRockspecDiscoveryService.discoverRockspecPaths`, call `RockspecBridge.read` per path,
    derive via `RockspecModuleDerivation`, cache via `CachedValuesManager` +
    `PsiModificationTracker` (mirror `LuaTypeManagerImpl`); add `cModuleRockspecs()`.
  - [ ] Edit `PathConfiguration.getProjectSourcePathPatterns`
    ([SourcePathPattern.kt:19](../../../../src/main/kotlin/net/internetisalie/lunar/lang/path/SourcePathPattern.kt))
    to append derived patterns, user-first, `distinctBy { spec }` — realizes design section 2.2.
- **Exit criteria**: TC #6 (require resolves to rockspec-mapped source); TC #9 (edit invalidates).
  Use a TEST-ONLY discovery stub where ROCKS-09 is not yet present.

### Phase 4: Runtime LUA_PATH side — consumer B [Must / Should]
- **Goal**: run/debug `LUA_PATH` unions local roots before installed; C modules -> `LUA_CPATH`.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.rocks.RockspecRunPathProvider` (design section 2.4)
    with `luaPathPrefix` (section 3.2) and `luaCPath` (section 3.3).
  - [ ] Splice `luaPathPrefix` + `luaCPath` into the `else` branch of `LuaRunConfiguration`
    ([LuaRunConfiguration.kt:265-272](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt))
    — do NOT touch the `if`/per-config branch or the debug preloader (`:256-260`).
  - [ ] Apply the same splice to `LuaTestCommandLineState.configureLuaPath`'s `else` branch
    ([LuaTestCommandLineState.kt:137-143](../../../../src/main/kotlin/net/internetisalie/lunar/run/test/LuaTestCommandLineState.kt)).
- **Exit criteria**: TC #7 (2-rock union, local-before-installed, TEST-ONLY discovery stub);
  TC #8 (C-module `LUA_CPATH` from built tree, `5.4` from `languageLevel`).

### Phase 5: End-to-end [Should]
- **Goal**: prove A and B together on a workspace-shaped fixture.
- **Tasks**:
  - [ ] Light-fixture test: workspace rock -> `require` resolves (A) and run-state `LUA_PATH`/`LUA_CPATH`
    carry the same roots (B).
  - [ ] Run human-verification-checklists.md (breakpoint-from-source; C-module CPATH).
- **Exit criteria**: all requirement TCs green; checklist scenarios pass.

## Requirement -> Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-05-01 Build Modules Parsing | M | Phase 1 |
| ROCKS-05-02 Source-Root Derivation | M | Phase 2 |
| ROCKS-05-03 IDE Path Integration (A) | M | Phase 3 |
| ROCKS-05-04 Run/Debug LUA_PATH Union (B) | M | Phase 4 |
| ROCKS-05-05 C-Module LUA_CPATH | S | Phase 4 |
| ROCKS-05-06 Invalidation | S | Phase 3 |

## Verification Tasks
- [x] `RockspecBridgeTest` over the section 4.1 JSON shapes — covers TC #1-#3.
- [ ] `RockspecModuleDerivationTest` (pure) — covers TC #4-#5.
- [ ] `RockspecSourcePathProviderTest` with a TEST-ONLY discovery stub + light fixture — covers TC #6, #9.
- [ ] `RockspecRunPathProviderTest` (union + CPATH) with a TEST-ONLY discovery stub — covers TC #7, #8.
- [ ] Run human-verification-checklists.md.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Bridge build data | done | Must |
| Phase 2: Source-root derivation | todo | Must |
| Phase 3: IDE side (A) | todo | Must |
| Phase 4: Runtime LUA_PATH side (B) | todo | Must |
| Phase 5: End-to-end | todo | Should |
