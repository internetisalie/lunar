---
id: "MAINT-32-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-32"
folders:
  - "[[features/maint/32-process-execution-discipline/requirements|requirements]]"
---

# MAINT-32: Implementation Plan

Sequenced from `design.md`. Baseline is `main` @ `0a99cb35` (2123 pass / 0 fail / 1 ignored).
Every phase leaves the full suite green (`tooling/gce-builder/gce-builder.sh run test`); the
blast radius touches resolution (rockspec discovery) and run/coverage, so the **full** suite is
mandatory — never gate on an isolated `--tests` pattern (isolated-tests-masks-full-suite lesson).

## Phases

### Phase 1: Primitive verify-and-fence [Must]
- **Goal**: Prove `LuaToolExecutionService` already satisfies §2.1 and forbid regressions; confirm
  `LuaProcessUtil` is gone.
- **Tasks**:
  - [ ] Add `git grep LuaProcessUtil src/` assertion to the phase notes (must be empty) — realizes design §1.
  - [ ] Add `LuaToolExecutionServiceTest.testCaptureUnderReadLockOffloadsToPool` — realizes design §3.2 (TC-01).
  - [ ] Add `LuaToolExecutionServiceTest.testCaptureUnderReadLockDoesNotLogSoftAssert` (the read-lock
        branch is sanctioned — no `softAssertBackgroundThread` error) — realizes design §3.2 (TC-02).
- **Exit criteria**: TC-01, TC-02 green; existing `testCaptureOnEdtLogsSoftAssert` still green; no
  `LuaProcessUtil` symbol in `src/`.

### Phase 2: Rockspec bridge off the read lock [Must]
- **Goal**: The `lua` bridge subprocess never runs inside `RockspecSourcePathProvider.compute()`
  under the read lock — the real #11 path (`LuaNameReference:88`/`LuaRequireReference:21` →
  `getProjectSourcePathPatterns` → `derivedPatterns()` → `compute():61`).
- **Tasks**:
  - [ ] In `RockspecSourcePathProvider` (`:22-46`), widen the `CachedValue` guard at `:24` from
        `app.isDispatchThread && !app.isUnitTestMode` to `app.isReadAccessAllowed && !app.isUnitTestMode`;
        in the guarded branch return degraded static patterns
        (`PathConfiguration.getStaticSourcePathPatterns(project) to emptyList<CModuleRock>()`) and call
        `prewarm()` — realizes design §2.1, §3.1 steps 1-3.
  - [ ] Add `prewarm()` with `AtomicBoolean prewarmInFlight` dedup + `cache.hasUpToDateValue()`
        guard; it `readAction {}`-wraps only `discoverRockspecPaths()` then runs
        `computePatternsFromPaths(paths)` (the `RockspecBridge.read` loop) **outside** any read action
        on `LunarCoroutineScopeService.scope`, bumping `forceRefreshTracker` on completion — realizes
        design §3.1 steps 4-6.
  - [ ] Refactor the current `compute()` (`:54-73`) into `computePatternsFromPaths(paths)`; the
        synchronous `else` branch (no read access) calls `readAction { discoverRockspecPaths() }` then
        `computePatternsFromPaths(...)` — realizes design §3.1 step 5.
  - [ ] Add `@VisibleForTesting internal val BRIDGE_INVOCATIONS: AtomicInteger` to `RockspecBridge`,
        incremented as the first statement of `read` — realizes the TC-03/04/05 seam.
  - [ ] Do NOT touch `LuaRockspecDiscoveryService`, do NOT add `discoverRockspecData()`, do NOT add
        `assertNoReadAccess()` — design §3.1 (dropped alternatives). `BuildWorkspaceAction.update()`
        already uses `discoverRockspecPaths()` (path-only); no change.
- **Exit criteria**: TC-03, TC-04, TC-05 green; `RockspecSourcePathProviderTest`,
  `LuaRockspecDiscoveryServiceTest`, and `BuildWorkspaceActionTest` still green (the widened guard
  bypasses in unit-test mode, so the existing pooled-thread `getProjectSourcePathPatterns` tests still
  compute synchronously).

### Phase 3: Caller migrations [Should]
- **Goal**: Console spawn, coverage file I/O, and health-monitor prepare-phase I/O all leave their
  fast/EDT path.
- **Tasks**:
  - [ ] Wrap `LuaConsoleAction.actionPerformed` process start in `newProjectBackgroundTask`; marshal
        failure notification via `invokeLater` — realizes design §2.3, §3.3.
  - [ ] Extract `LuaCoverageProgramRunner` stats-file cleanup to `clearStaleStats(workDir)` run off
        the EDT before `state.execute` — realizes design §2.4, §3.4.
  - [ ] Add `@Volatile watchSet` + `rebuildWatchSet()` to `LuaToolHealthMonitor`; call from `start()`
        and `toolchainChanged`; make `prepareChange` read the field — realizes design §2.5, §3.5.
        Reuse the existing `LuaHealthWatchSet.EMPTY` (`LuaHealthWatchSet.kt:20`) as the initial value —
        no new constant needed.
- **Exit criteria**: TC-06, TC-07, TC-08 green; `LuaToolHealthMonitorTest` (if present) still green.

### Phase 4: Cancellable build + install [Should]
- **Goal**: `luarocks make` is killed on cancel; the install task is cancellable.
- **Tasks**:
  - [ ] Migrate `WorkspaceBuildRunner.executeMake` onto `LuaToolExecutionService.stream(cmd,
        listener, INSTALL, colored=true, indicator)`; delete the manual `handler.waitFor()` —
        realizes design §2.6, §3.6.
  - [ ] In `LuaRocksInstallExecutor`: flip `Task.Backgroundable(project, job.title, false)` → `true`
        (`:53`), change `run(indicator) = execute(job)` → `execute(job, indicator)` (`:54`), change
        `execute(job: Job)` → `execute(job: Job, indicator: ProgressIndicator)` (`:58`), and pass
        `indicator = indicator` into `capture(command, LuaExecTimeout.INSTALL, indicator = indicator)`
        (`:62`) — realizes design §2.7, §3.6.
- **Exit criteria**: TC-09 green; `WorkspaceBuildRunnerTest` / install tests (if present) still green.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-32-01 | M | Phase 1 |
| MAINT-32-02 | M | Phase 2 |
| MAINT-32-03 | S | Phase 3 |
| MAINT-32-04 | S | Phase 4 |

## Verification Tasks
- [ ] Add TC-01, TC-02 to `LuaToolExecutionServiceTest` — covers MAINT-32-01.
- [ ] Add TC-03/04/05 to `RockspecSourcePathProviderTest` (read-lock call returns degraded static
      patterns, no bridge under the lock, prewarm scheduled; single-job dedup; full patterns after
      prewarm) — covers MAINT-32-02.
- [ ] Add TC-06/07/08 unit tests where seams allow (health-monitor prepare-phase I/O-free;
      coverage `clearStaleStats`; console off-EDT queue) — covers MAINT-32-03.
- [ ] Add TC-09 (build cancel kills process) via `LuaToolExecutionService.stream` indicator path —
      covers MAINT-32-04.
- [ ] Run `docs/features/maint/32-process-execution-discipline/human-verification-checklists.md`
      (VNC-gated rows: rockspec-workspace typing latency; cancel a build mid-flight).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Primitive verify-and-fence | todo | Must |
| Phase 2: Rockspec bridge off the read lock | todo | Must |
| Phase 3: Caller migrations | todo | Should |
| Phase 4: Cancellable build + install | todo | Should |
