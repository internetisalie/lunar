---
id: "ROCKS-09-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-09"
folders:
  - "[[features/rocks/09-workspace-discovery/requirements|requirements]]"
---

# ROCKS-09: Implementation Plan

## Phases

### Phase 0: De-risking [Must]
- **Goal**: retire the redefinition + index-API unknowns before code (see risks-and-gaps.md).
- **Tasks**:
  - [x] ROCKS-09-00-01: confirm `FilenameIndex.getAllFilesByExt(project,"rockspec",scope)` returns
        a `Kernel/v0`-shaped fixture's 10 rockspecs in a `BasePlatformTestCase` (spike) — resolves Risk 1.2.
  - [x] ROCKS-09-00-02: file the ROCKS-01 / ROCKS-03 / ROCKS-05 redefinition deltas as tracked
        items per risks-and-gaps.md (DO NOT edit those docs here).
- **Exit criteria**: spike test green; redefinition items recorded.

### Phase 1: Exclusion filter [Must]
- **Goal**: pure, fully-tested inclusion predicate.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.RockspecExclusionFilter` — realizes design §2.2, §3.2, §3.3.
- **Exit criteria**: unit tests for TC #2, #3, #4, #10, #11 inputs (string-level, no platform) pass.

### Phase 2: Discovery service [Must]
- **Goal**: cached, exclusion-aware recursive discovery.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService` + `DiscoveredRockspec`
        — realizes design §2.1, §3.1 (CachedValue + `PsiModificationTracker`, `runReadAction`,
        `DumbService` guard).
- **Exit criteria**: TC #1, #5, #6 pass (`BasePlatformTestCase` with a `Kernel/v0`-shaped fixture
        and a stubbed/counting bridge).

### Phase 3: Locator + ROCKS-05 delegate [Must]
- **Goal**: route the existing locator through the service; provide the ROCKS-05 hook.
- **Tasks**:
  - [x] Edit `net.internetisalie.lunar.rocks.LuaRocksTreeLocator`: delete `projectRockspec`, add
        `allProjectRockspecs(project)` delegating to the service — realizes design §2.3.
- **Exit criteria**: TC #12 passes; no remaining references to `projectRockspec` compile-fail.

### Phase 4: Multi-root resolution + panel [Must]
- **Goal**: resolve and render a forest.
- **Tasks**:
  - [x] Edit `net.internetisalie.lunar.rocks.LuaRocksDependencyResolver`: add `resolveAll`,
        `resolveOne`; make `resolve` a `resolveAll().firstOrNull()` shim — realizes design §2.4, §3.4.
  - [x] Edit `net.internetisalie.lunar.rocks.ui.DependencyTreePanel`: render `resolveAll` roots —
        realizes design §2.5.
- **Exit criteria**: TC #7, #8 pass; dependency panel shows one root per rock.

### Phase 5: Remove `workspace.lua` scaffolding [Must]
- **Goal**: delete the orphan workspace path and collapse `RockKind`.
- **Tasks**:
  - [x] Edit `LuaRocksTemplates` (delete `workspaceLua`), `LuaRocksScaffolder` (delete
        `scaffoldWorkspace`, collapse `scaffold`), `LuaRocksProjectSettings` (delete `RockKind`,
        `kind`, `workspaceName`, `initialRocks`), `LuaRocksGeneratorPeer` (remove kind/workspace UI)
        — realizes design §2.6.
  - [x] Delete the dead workspace tests in `LuaRocksTemplatesTest`, `LuaRocksScaffolderTest`.
- **Exit criteria**: TC #9 passes; `./gradlew build` green; single-rock generator still scaffolds.

### Phase 6: Membership override [Should]
- **Goal**: per-project include/exclude globs.
- **Tasks**:
  - [x] Edit `net.internetisalie.lunar.settings.LuaProjectSettings.State`: add
        `rockspecIncludeGlobs` / `rockspecExcludeGlobs` — realizes design §2.7.
  - [x] Wire the service to read them (already in §3.1 step 3) — realizes design §3.3.
- **Exit criteria**: TC #10, #11 pass.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-09-01 | M | Phase 2 |
| ROCKS-09-02 | M | Phase 1 (+ used in Phase 2) |
| ROCKS-09-03 | M | Phase 2 |
| ROCKS-09-04 | M | Phase 2 |
| ROCKS-09-05 | M | Phase 4 |
| ROCKS-09-06 | M | Phase 5 |
| ROCKS-09-07 | S | Phase 6 |
| ROCKS-09-08 | S | Phase 3 |

## Verification Tasks
- [x] Unit: `RockspecExclusionFilter` truth table — covers TC #2, #3, #4, #10, #11.
- [x] Unit/light: `LuaRockspecDiscoveryService` over a `Kernel/v0`-shaped fixture — covers TC #1, #5, #6.
- [x] Unit/light: `resolveAll` forest + missing-dep flag — covers TC #7, #8.
- [x] Unit/light: `LuaRocksTreeLocator.allProjectRockspecs` delegation — covers TC #12.
- [x] Build/compile: workspace removal — covers TC #9.
- [x] Run `human-verification-checklists.md` (dependency tool window over `Kernel/v0`).
- [x] `./gradlew test`, `ktlintFormat`, `ktlintCheck` (match surrounding style).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: De-risking | done | Must |
| Phase 1: Exclusion filter | done | Must |
| Phase 2: Discovery service | done | Must |
| Phase 3: Locator + ROCKS-05 delegate | done | Must |
| Phase 4: Multi-root resolution + panel | done | Must |
| Phase 5: Remove `workspace.lua` scaffolding | done | Must |
| Phase 6: Membership override | done | Should |
