---
id: "MAINT-32"
title: "32: Process-Execution Discipline (LuaProcessUtil)"
type: "feature"
parent_id: "MAINT"
status: "in_progress"
priority: "medium"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-32: Process-Execution Discipline (LuaProcessUtil)

Executes the review's §2.1 "fix once at the primitive" prescription
([`docs/review.md`](../../../review.md), re-verified 2026-07-17): subprocess execution on the EDT
or under the read lock is the *enabling defect* behind several P1s. Harden `LuaProcessUtil` once
(EDT assertion + cancellable, read-lock-free path), then migrate the offending callers.

## Absorbed review findings

| Review # | Defect |
| :--- | :--- |
| 11 (rest) | Rockspec bridge (`lua` subprocess, 10 s timeout each) runs inside `RockspecSourcePathProvider.compute()` (`:61`) **under the read lock** — reached from reference resolution (`LuaNameReference:88`/`LuaRequireReference:21`) on a background read-lock thread; the `CachedValue` guard (`:24`) fires only on `isDispatchThread`, so a background read-lock thread falls through to the synchronous bridge, stalling 10 s × #rockspecs |
| §2.1 | `LuaProcessUtil.capture` has no EDT guard; read-lock branch blocks `.get()` non-cancellably |
| §2.1 | `LuaConsoleAction.actionPerformed` spawns the interpreter synchronously on the EDT |
| §2.1 | `LuaCoverageProgramRunner.doExecute` does file I/O on the EDT |
| §2.1 | `LuaToolHealthMonitor` does per-tool disk I/O + `File.canonicalPath` in the `AsyncFileListener` **prepare phase** |
| §2.1 | `LuaRocksEnvironment` documented "no I/O" but transitively does `File.exists()/canExecute()` per inventory entry, incl. from EDT notification paths |
| §2.1 | Cancellation gaps: `WorkspaceBuildRunner.waitFor()` ignores the indicator; the 2-minute install task is non-cancellable |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-32-01 | Hardened primitive | M | Full | `LuaToolExecutionService` (the surviving primitive; `LuaProcessUtil` confirmed deleted): EDT soft-assert, cancellable wait, read-lock offload to a pooled thread. Verify-and-fence + regression tests TC-01/TC-02. |
| MAINT-32-02 | Bridge off the lock | M | Not Implemented | Widen the `RockspecSourcePathProvider` `CachedValue` guard from `isDispatchThread` to `isReadAccessAllowed`: read-lock callers get degraded static patterns + a deduplicated off-read-lock prewarm that runs `RockspecBridge.read` outside any read action; full derived patterns land on a later pass (#11) |
| MAINT-32-03 | Caller migration | S | Not Implemented | Console action, coverage runner, health monitor prepare-phase, rocks environment I/O — all off the EDT/fast paths (§2.1) |
| MAINT-32-04 | Cancellable builds | S | Not Implemented | Indicator-aware `waitFor` + kill-on-cancel in build/install tasks (§2.1) |

**Verification:** thread-assertion tests where feasible; the EDT-freeze cases are
`verify-in-ide` DoD gates (type in a rockspec workspace; cancel a build mid-flight).

## Test Cases

Each `Must` requirement has ≥1 concrete input→output case (design section in parens).
VNC-gated rows are the live EDT-freeze halves; see `human-verification-checklists.md`.

| TC | Requirement | Input | Action | Expected Output |
| :--- | :--- | :--- | :--- | :--- |
| TC-01 | MAINT-32-01 | `LuaToolExecutionService.capture(sh("echo x"))` invoked from a pooled thread that is inside `ReadAction.run { }` (read lock held, not EDT) | Call `capture` under the read lock (§3.2) | Returns `COMPLETED`, stdout `"x\n"`; the process ran on a **pooled** thread (offload branch `LuaToolExecutionService.kt:47-49`), not the read-lock thread |
| TC-02 | MAINT-32-01 | Same read-lock-held call as TC-01 | Wrap in `LoggedErrorProcessor` (§3.2) | **No** `softAssertBackgroundThread` soft-error is logged (read-lock offload is the sanctioned path); contrast existing `testCaptureOnEdtLogsSoftAssert` which DOES log on the EDT |
| TC-03 | MAINT-32-02 | Fixture with ≥1 rockspec + a configured interpreter; `RockspecBridge.BRIDGE_INVOCATIONS: AtomicInteger` (`@VisibleForTesting`, incremented as the first statement of `RockspecBridge.read`) snapshotted | Call `PathConfiguration.getProjectSourcePathPatterns(project)` **inside a `runReadAction { }` on a pooled thread** (simulating background resolution under the read lock, `isReadAccessAllowed && !isDispatchThread`) (§3.1) | Returns the **degraded static patterns** (== `getStaticSourcePathPatterns(project)`, no rockspec-derived roots) and does **not** throw; `BRIDGE_INVOCATIONS` is **unchanged** in the read action (bridge never runs under the lock); a prewarm job is scheduled on `LunarCoroutineScopeService.scope` |
| TC-04 | MAINT-32-02 | Same fixture; `RockspecSourcePathProvider.cache` cold; `prewarmInFlight` false | Under a `runReadAction { }` (pooled), call `derivedPatterns()` **three times** in the same read action (simulating N unresolved references) (§3.1) | Exactly **one** prewarm job is launched (assert via a test counter on the launch, or `BRIDGE_INVOCATIONS` incremented at most #rockspecs once the single job runs) — the `prewarmInFlight` CAS + `hasUpToDateValue()` dedup prevents N jobs; all three calls return degraded patterns |
| TC-05 | MAINT-32-02 | Fixture, one rockspec `package = "adt"`, stub interpreter echoing JSON `{"package":"adt", ...}`; drive the prewarm to completion (await the launched job) | After the prewarm completes and `forceRefreshTracker` ticks, re-read `derivedPatterns()` **off-lock** (pooled thread, no read action) (§3.1) | Returns the **full derived patterns** including the rockspec-derived root; `BRIDGE_INVOCATIONS` incremented (bridge ran, but only inside the prewarm, off the read lock) — the eventual-consistency contract holds |
| TC-06 | MAINT-32-03 | `LuaToolHealthMonitor` with a watched tool path | Fire `HealthFileListener.prepareChange(events)`; assert no `File.canonicalPath` runs on the listener path (seam: `rebuildWatchSet()` populates `@Volatile watchSet`; `prepareChange` reads the field) (§3.5) | Match decision uses the pre-computed `watchSet` — a matching event still schedules revalidation; an I/O tripwire (`SlowOperationsException` assertion or a canonicalize counter) shows **zero** I/O in `prepareChange` |
| TC-07 | MAINT-32-03 | Coverage run with a stale `luacov.stats.out` in `workDir` | `LuaCoverageProgramRunner.clearStaleStats(workDir)` invoked off the EDT before `state.execute` (§3.4) | Stale file deleted before the run; `workDir == null` → no-op (no exception) |
| TC-08 | MAINT-32-04 | `LuaToolExecutionService.stream(sh("sleep 5"), listener, INSTALL, indicator=ind)` with `ind.cancel()` after 200 ms | Cancel the indicator mid-stream (§3.6) | Returns `CANCELLED` in < 4 s; the process was `destroyProcess()`d (mirrors existing `testCaptureCancelledViaIndicator`) — locks the kill-on-cancel `WorkspaceBuildRunner` migrates onto |
| TC-09 | MAINT-32-04 | Build a 2-rock workspace, cancel the indicator during `luarocks make` of rock 1 | `WorkspaceBuildRunner.run(...)` under a cancellable indicator (§3.6) | `run` returns a failure `BuildOutcome` (non-null `failedRock`), the topo loop stops before rock 2, and the `luarocks make` process is killed (VNC-gated live confirmation of no orphan process) |
