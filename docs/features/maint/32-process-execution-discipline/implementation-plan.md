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
  - [x] Add `git grep LuaProcessUtil src/` assertion to the phase notes (must be empty) — realizes design §1.
  - [x] Add `LuaToolExecutionServiceTest.testCaptureUnderReadLockOffloadsToPool` — realizes design §3.2 (TC-01).
  - [x] Add `LuaToolExecutionServiceTest.testCaptureUnderReadLockDoesNotLogSoftAssert` (the read-lock
        branch is sanctioned — no `softAssertBackgroundThread` error) — realizes design §3.2 (TC-02).
- **Exit criteria**: TC-01, TC-02 green; existing `testCaptureOnEdtLogsSoftAssert` still green; no
  `LuaProcessUtil` symbol in `src/`.

### Phase 2: Rockspec bridge off the read lock [Must]
- **Goal**: The `lua` bridge subprocess never runs inside `RockspecSourcePathProvider.compute()`
  under the read lock — the real #11 path (`LuaNameReference:88`/`LuaRequireReference:21` →
  `getProjectSourcePathPatterns` → `derivedPatterns()` → `compute():61`).
- **Tasks**:
  - [x] In `RockspecSourcePathProvider` (`:22-46`), widen the `CachedValue` guard at `:24` from
        `app.isDispatchThread && !app.isUnitTestMode` to `app.isReadAccessAllowed && !app.isUnitTestMode`;
        in the guarded branch return degraded static patterns
        (`PathConfiguration.getStaticSourcePathPatterns(project) to emptyList<CModuleRock>()`) and call
        `prewarm()` — realizes design §2.1, §3.1 steps 1-3.
  - [x] Add `prewarm()` with `AtomicBoolean prewarmInFlight` dedup + `cache.hasUpToDateValue()`
        guard; it `readAction {}`-wraps only `discoverRockspecPaths()` then runs
        `computePatternsFromPaths(paths)` (the `RockspecBridge.read` loop) **outside** any read action
        on `LunarCoroutineScopeService.scope`, bumping `forceRefreshTracker` on completion — realizes
        design §3.1 steps 4-6.
  - [x] Refactor the current `compute()` (`:54-73`) into `computePatternsFromPaths(paths)`; the
        synchronous `else` branch (no read access) calls `readAction { discoverRockspecPaths() }` then
        `computePatternsFromPaths(...)` — realizes design §3.1 step 5.
  - [x] TC-03/04/05 seam — the planned global `RockspecBridge.BRIDGE_INVOCATIONS` counter proved
        fragile under the full interleaved suite (a concurrent test's async prewarm bumps the shared
        global, breaking an absolute-count assertion). Pivoted to a **project-local** seam:
        `RockspecSourcePathProvider.prewarmLaunchCount(project)` (per-test-project, deterministic) proves
        the N-refs-one-prewarm dedup (TC-04); TC-03/TC-05 assert on the returned **patterns** (degraded
        static set under the lock vs full rockspec-derived roots after prewarm), which is the race-free
        invariant. No production counter left on the hot `RockspecBridge.read` path.
  - [x] Do NOT touch `LuaRockspecDiscoveryService`, do NOT add `discoverRockspecData()`, do NOT add
        `assertNoReadAccess()` — design §3.1 (dropped alternatives). `BuildWorkspaceAction.update()`
        already uses `discoverRockspecPaths()` (path-only); no change.
- **Exit criteria**: TC-03, TC-04, TC-05 green; `RockspecSourcePathProviderTest`,
  `LuaRockspecDiscoveryServiceTest`, and `BuildWorkspaceActionTest` still green (the widened guard
  bypasses in unit-test mode, so the existing pooled-thread `getProjectSourcePathPatterns` tests still
  compute synchronously).
- **Deviation (minimal, necessary)**: production predicate is exactly
  `app.isReadAccessAllowed && !app.isUnitTestMode` as specified. Because the `!isUnitTestMode` bypass
  makes the fenced branch unreachable in unit-test mode, a `@TestOnly testForceReadLockGuard` flag was
  added so TC-03/04/05 exercise the degraded+prewarm path; when unset (the default, all non-MAINT-32
  tests) behavior is identical to the plan. A `@TestOnly isPrewarmComplete` await-seam (reads
  `cachedFull`, no compute) was added so the prewarm-await does not itself trigger an off-lock
  synchronous bridge and skew `BRIDGE_INVOCATIONS`.

### Phase 3: Caller migrations [Should]
- **Goal**: Console spawn, coverage file I/O, and health-monitor prepare-phase I/O all leave their
  fast/EDT path.
- **Tasks**:
  - [x] Wrap `LuaConsoleAction.actionPerformed` process start in `newProjectBackgroundTask`; marshal
        failure notification via `invokeLater` — realizes design §2.3, §3.3.
  - [x] Extract `LuaCoverageProgramRunner` stats-file cleanup to `clearStaleStats(workDir)` run off
        the EDT before `state.execute` — realizes design §2.4, §3.4.
  - [x] Add `@Volatile watchSet` + `rebuildWatchSet()` to `LuaToolHealthMonitor`; call from `start()`
        and `toolchainChanged`; make `prepareChange` read the field — realizes design §2.5, §3.5.
        Reuse the existing `LuaHealthWatchSet.EMPTY` (`LuaHealthWatchSet.kt:20`) as the initial value —
        no new constant needed.
- **Exit criteria**: TC-06, TC-07, TC-08 green; `LuaToolHealthMonitorTest` (if present) still green.
- **Deviation (test seams only)**: `LuaToolHealthMonitor` gained `@TestOnly rebuildWatchSetNow()` +
  `prepareChangeNow(events)`; `LuaCoverageProgramRunner.clearStaleStats` is `@VisibleForTesting internal`;
  TC-08 (stream-cancel kills the process) was added to `LuaToolExecutionServiceTest` since it locks the
  kill-on-cancel path Phase 4's `WorkspaceBuildRunner` migrates onto. No production-behavior change.

### Phase 4: Cancellable build + install [Should]
- **Goal**: `luarocks make` is killed on cancel; the install task is cancellable.
- **Tasks**:
  - [x] Migrate `WorkspaceBuildRunner.executeMake` onto `LuaToolExecutionService.stream(cmd,
        listener, INSTALL, colored=true, indicator)`; delete the manual `handler.waitFor()` —
        realizes design §2.6, §3.6.
  - [x] In `LuaRocksInstallExecutor`: flip `Task.Backgroundable(project, job.title, false)` → `true`
        (`:53`), change `run(indicator) = execute(job)` → `execute(job, indicator)` (`:54`), change
        `execute(job: Job)` → `execute(job: Job, indicator: ProgressIndicator)` (`:58`), and pass
        `indicator = indicator` into `capture(command, LuaExecTimeout.INSTALL, indicator = indicator)`
        (`:62`) — realizes design §2.7, §3.6.
- **Exit criteria**: TC-09 green; `WorkspaceBuildRunnerTest` / install tests (if present) still green.
- **Deviation (test correction)**: migrating `executeMake` to `stream` (which soft-asserts a background
  thread) surfaced that the three existing `WorkspaceBuildRunnerTest` cases called `run` on the EDT —
  production only ever invokes `run` from a `Task.Backgroundable` (off the EDT). They now invoke it on a
  pooled thread (`runOffEdt`), matching production; behavior assertions (exit 0/2, topo-stop) are
  unchanged. `executeMake` carries `indicator` as a 4th param — the design §2.7/§3.6 lifecycle-handle
  carve-out (not counted against the 3-arg cap).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-32-01 | M | Phase 1 |
| MAINT-32-02 | M | Phase 2 |
| MAINT-32-03 | S | Phase 3 |
| MAINT-32-04 | S | Phase 4 |

## Verification Tasks
- [x] Add TC-01, TC-02 to `LuaToolExecutionServiceTest` — covers MAINT-32-01.
- [x] Add TC-03/04/05 to `RockspecSourcePathProviderTest` (read-lock call returns degraded static
      patterns, no bridge under the lock, prewarm scheduled; single-job dedup; full patterns after
      prewarm) — covers MAINT-32-02.
- [x] Add TC-06/07/08 unit tests where seams allow (health-monitor prepare-phase I/O-free;
      coverage `clearStaleStats`; stream cancel-kill) — covers MAINT-32-03.
- [x] Add TC-09 (build cancel kills process) via `LuaToolExecutionService.stream` indicator path —
      covers MAINT-32-04.
- [ ] Run `docs/features/maint/32-process-execution-discipline/human-verification-checklists.md`
      (VNC-gated rows: rockspec-workspace typing latency; cancel a build mid-flight) — **pending live DoD**.

## Final Gate

Full cache-defeated suite (`gce-builder run "test --rerun-tasks --no-build-cache"`, JUnit XML
aggregate read on the builder): **2215 tests / 0 failures / 0 errors / 1 skipped** (baseline 2205 + 10
new MAINT-32 tests). `ktlintCheck` green (no violations in touched files).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Primitive verify-and-fence | done | Must |
| Phase 2: Rockspec bridge off the read lock | done | Must |
| Phase 3: Caller migrations | done | Should |
| Phase 4: Cancellable build + install | done | Should |
