---
id: "ROCKS-10-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
priority: "medium"
parent_id: "ROCKS-10"
folders:
  - "[[features/rocks/10-workspace-build/requirements|requirements]]"
---

# ROCKS-10: Implementation Plan

Precondition: ROCKS-09 (`LuaRockspecDiscoveryService.discoverRockspecPaths()`) is implemented and
green — it is the build set source (risks-and-gaps Risk 1.1). All phases live in the new package
`net.internetisalie.lunar.rocks.build`.

## Phases

### Phase 1: Graph + topo-sort core [Must]
- **Goal**: pure, fully unit-testable ordering logic with no platform dependency.
- **Tasks**:
  - [ ] Create `WorkspaceRock` + `BuildPlan` (`Ordered`/`Cycle`/`Empty`) data types — realizes design §2.1.
  - [ ] Create `WorkspaceBuildGraph.topoSort(rocks)` implementing the §3.1 DAG build and the §3.2
        Kahn sort with name tie-break and cycle detection — realizes design §2.2, §3.1, §3.2.
- **Exit criteria**: unit tests for TC #1 (A→B→C ⇒ `["A","B","C"]`), TC #2 (A↔B ⇒ `Cycle({a,b})`),
  TC #3 (independent pair ⇒ name-sorted), TC #4 (external dep ⇒ no edge) pass. Build green.

### Phase 2: Orchestrator (discovery + bridge + normalize) [Must]
- **Goal**: turn the discovered set into a `BuildPlan` using ROCKS-09 + ROCKS-03.
- **Tasks**:
  - [ ] Create `WorkspaceBuildOrchestrator.computeBuildOrder(project)` and private `loadRocks`
        consuming `LuaRockspecDiscoveryService.discoverRockspecPaths()` and `RockspecBridge.read`,
        with `normalizeDepName` (§3.0 step 4 regex) — realizes design §2.1, §3.0.
  - [ ] Drop unparseable rockspecs (null bridge read) with a logged warning.
- **Exit criteria**: TC #7 (unparseable rockspec dropped) passes; a `Kernel/v0`-shaped fixture with
  a stubbed bridge yields a correct `Ordered` plan. Background-only (no EDT calls).

### Phase 3: Sequential runner (ROCKS-04 reuse) [Must]
- **Goal**: run `luarocks make` per rock in order, streaming to a console, stopping on first failure.
- **Tasks**:
  - [ ] Create `WorkspaceBuildRunner.run(project, order, console, indicator)` building each rock's
        command via a transient `LuaRocksRunConfiguration` (`command="make"`, `rockspecPath=<path>`)
        + `buildCommandLine(LuaRocksSettings.getInstance().executablePath)`, attaching the
        `OSProcessHandler` to the console and awaiting `waitFor()` — realizes design §2.3, §3.3.
  - [ ] Stop and return `BuildOutcome` on the first non-zero exit; catch `ExecutionException`
        (missing `luarocks`) as a failure.
- **Exit criteria**: TC #5 (each rock built once, correct work dir/order) and TC #6 (failure at B
  stops C) pass against a stubbed/fake `luarocks` on PATH.

### Phase 4: Action + UI + registration [Must]
- **Goal**: the user-facing action wired into menus, off-EDT, with the gate and console.
- **Tasks**:
  - [ ] Create `BuildWorkspaceAction` (`DumbAwareAction`, `getActionUpdateThread()=BGT`) with the
        §3.4 gate (≥2 discovered rocks) and the §3.5 drive (console via `TextConsoleBuilderFactory`,
        `RunContentManager.showRunContent`, `Task.Backgroundable`) — realizes design §2.4, §3.4, §3.5.
  - [ ] Register `Lua.Rocks.BuildWorkspace` in `plugin.xml` `<actions>` (Tools menu +
        ProjectViewPopupMenu) — realizes design §7.
- **Exit criteria**: TC #8 (1 rock ⇒ disabled) and TC #9 (≥2 rocks ⇒ enabled) pass; manual
  human-verification scenario (build a `Kernel/v0`-shaped workspace, confirm order) passes.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-10-01 Discover Build Set | M | Phase 2 |
| ROCKS-10-02 Inter-Rock DAG | M | Phase 1 |
| ROCKS-10-03 Topological Order | M | Phase 1 |
| ROCKS-10-04 Cycle → Fail, No Build | M | Phase 1 (detect) + Phase 4 (no-build report) |
| ROCKS-10-05 Sequential `luarocks make` | M | Phase 3 |
| ROCKS-10-06 Console Streaming | M | Phase 3 + Phase 4 |
| ROCKS-10-07 Off-EDT Execution | M | Phase 4 |
| ROCKS-10-08 Action Registration | M | Phase 4 |

## Verification Tasks

- [ ] Unit-test `WorkspaceBuildGraph.topoSort` — covers TC #1, #2, #3, #4.
- [ ] Unit-test `WorkspaceBuildOrchestrator.loadRocks`/`normalizeDepName` with a stubbed discovery
      + bridge — covers TC #7 and the dep-string parse format (§3.0 step 4).
- [ ] Light-fixture test (`BasePlatformTestCase`) for `WorkspaceBuildRunner` with a fake `luarocks`
      script on PATH — covers TC #5, #6.
- [ ] Action-gate test (`BasePlatformTestCase`) for `BuildWorkspaceAction.update` — covers TC #8, #9.
- [ ] Run `human-verification-checklists.md` (build `Kernel/v0`-shaped workspace, confirm order).
- [ ] `./gradlew ktlintFormat ktlintCheck` on the new `rocks/build/` files; `./gradlew test`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Graph + topo-sort core | todo | Must |
| Phase 2: Orchestrator | todo | Must |
| Phase 3: Sequential runner | todo | Must |
| Phase 4: Action + UI + registration | todo | Must |
