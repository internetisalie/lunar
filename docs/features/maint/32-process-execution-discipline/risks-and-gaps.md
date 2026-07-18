---
id: "MAINT-32-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-32"
folders:
  - "[[features/maint/32-process-execution-discipline/requirements|requirements]]"
---

# MAINT-32: Risks & Gaps

## Critical Risks

### Risk 1.1: A `derivedPatterns()` consumer bypasses the widened guard and reaches the bridge under the lock
- **Impact**: The fence is the `isReadAccessAllowed` guard inside `RockspecSourcePathProvider.compute`'s
  `CachedValue`. If some path could invoke `RockspecBridge.read` synchronously without passing through
  that guard while holding the read lock, #11 returns.
- **Likelihood**: LOW — the guard sits at the single choke point (`cache.value` is the only way to
  reach `compute()`), and the complete `derivedPatterns()`/`cModuleRockspecs()` consumer set is
  enumerated and thread-modelled in design §3.1 (eight reachers: `LuaNameReference:88`,
  `LuaRequireReference:21`, `LuaCrossFileCompletionProvider:56`, `LuaModulePathResolver:21`,
  `LuaTypeManagerImpl:108`, `LuaRockSourceRootDecorator:24`, `RockspecRunPathProvider:10/16`, and the
  shared `PathConfiguration.getProjectSourcePathPatterns` entry). Every one routes through the guarded
  `cache.value`, so a read-lock caller is always diverted to degraded+prewarm. (The earlier "three
  identity consumers" framing was wrong — it counted `discoverRockspecData()` consumers, a method that
  is dropped; the real seam is `RockspecSourcePathProvider`, not `LuaRockspecDiscoveryService`.)
- **Mitigation**: DR-01 re-runs the `git grep` consumer enumeration against the branch head and
  confirms no new caller of `RockspecBridge.read` (outside the prewarm) exists on a read-lock path
  before Phase 2 lands. No `assertNoReadAccess()` is added (it would be unreachable and, if placed on
  `compute()`, would crash cold resolution).

### Risk 1.2: Full-suite regression from the guard/prewarm change (resolution blast radius)
- **Impact**: `RockspecSourcePathProvider.derivedPatterns()` feeds run-config source paths and
  cross-file resolution (eight reachers, design §3.1); a mishandled guard (e.g. the degraded branch
  leaking into off-lock callers, or the prewarm's `readAction {}` wrapping the bridge by mistake)
  could break `LuaRecursiveReferenceTest` / `LuaDescriptionIndexTest` (external-fixture, builder-only)
  or the existing `RockspecSourcePathProviderTest`.
- **Likelihood**: medium.
- **Mitigation**: gate on the **full** `gce-builder run test` (not CI, which skips the two
  external-fixture tests); force `--rerun --no-build-cache` on the deletion/split-heavy change
  (gce-builder-cache-masks-tests lesson).

### Risk 1.3: Console off-EDT spawn breaks the `AbstractConsoleRunnerWithHistory` UI contract
- **Impact**: `initAndRun()` internally schedules console-view creation; calling it from a
  background task instead of the EDT could leave the console view unattached or throw a
  threading assertion in the platform.
- **Likelihood**: low-medium.
- **Mitigation**: DR-02 spikes the off-EDT `initAndRun()` in the sandbox IDE; fall back to spawning
  only `createProcess()` off-EDT and marshalling `initAndRun` to the EDT if the platform asserts.

## Design Gaps

_None — every §2.1 concern is resolved in design.md (LIVE items designed; `LuaProcessUtil` and
`LuaRocksEnvironment` items re-verified mooted with file:line). The two implementation choices below
are de-risking spikes, not open design decisions._

## Technical Debt & Future Work
- **TBD: Coroutine migration of `newProjectBackgroundTask` sites** — the console spawn uses
  `newProjectBackgroundTask` for symmetry with the health monitor; a later pass may convert both to
  `withBackgroundProgress` on `LunarCoroutineScopeService.scope` (opportunistic, per contract §2).
- **TBD: `LuaCheckInvoker` PSI reads in the process listener** (`LuaCheckInvoker.kt:55-57`, review
  §2.1) — a distinct threading defect in the same review section, owned by the luacheck epic
  (MAINT-26 already migrated the *pipeline*); out of MAINT-32 scope (no process-execution primitive).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-00-DR-01 | Re-run the `git grep` enumeration of every `RockspecBridge.read` caller and every `derivedPatterns()`/`cModuleRockspecs()` reacher against the branch head; confirm each read-lock reacher routes through the guarded `RockspecSourcePathProvider.cache.value` (degraded+prewarm) and no synchronous bridge call survives on a read-lock path | Risk 1.1 | done — the only `derivedPatterns()`/`cModuleRockspecs()` reachers are `SourcePathPattern.kt:21` (shared entry) and `RockspecRunPathProvider.kt:10,16` (run-config); both route through `cache.value` → the widened `isReadAccessAllowed` fence. The other `RockspecBridge.read` callers (`LuaRocksDependencyResolver`, `LuaRockspecDiscoveryService:81`, `WorkspaceBuildOrchestrator`) are not reached via `derivedPatterns()` and are pre-existing off-lock paths outside scope. |
| MAINT-00-DR-02 | Sandbox-IDE spike: call `LuaConsoleRunner.initAndRun()` from `newProjectBackgroundTask`; confirm the console view attaches and no platform threading assertion fires; else adopt the EDT-marshalled fallback | Risk 1.3, design §3.3 | todo |
| MAINT-00-DR-03 | Measure the §3.4 coverage `clearStaleStats` `.get()` join duration; if it can exceed the slow-ops threshold, switch to fire-and-forget cleanup before `state.execute` returns its handler | design §6 (coverage join) | todo |

## Test Case Gaps
- The EDT-freeze symptoms (typing latency in a rockspec workspace; orphaned `luarocks make` on
  cancel) are not unit-observable — they are VNC-gated live checks (TC-09 live half; the typing-latency
  case). Covered by `human-verification-checklists.md`, declared VNC-gated.
- No unit seam exists for "process started on the EDT vs background" in `LuaConsoleAction` beyond
  asserting the `newProjectBackgroundTask` queue is used; the true freeze is VNC-gated.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
