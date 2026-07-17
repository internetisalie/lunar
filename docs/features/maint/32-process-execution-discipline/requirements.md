---
id: "MAINT-32"
title: "32: Process-Execution Discipline (LuaProcessUtil)"
type: "feature"
parent_id: "MAINT"
status: "todo"
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
| 11 (rest) | Rockspec bridge (`lua` subprocess, 10 s timeout each) still runs inside `CachedValue.compute()` **under the read lock** — typing can stall 10 s × #rockspecs (EDT guard in `LuaToolExecutionService` already improved) |
| §2.1 | `LuaProcessUtil.capture` has no EDT guard; read-lock branch blocks `.get()` non-cancellably |
| §2.1 | `LuaConsoleAction.actionPerformed` spawns the interpreter synchronously on the EDT |
| §2.1 | `LuaCoverageProgramRunner.doExecute` does file I/O on the EDT |
| §2.1 | `LuaToolHealthMonitor` does per-tool disk I/O + `File.canonicalPath` in the `AsyncFileListener` **prepare phase** |
| §2.1 | `LuaRocksEnvironment` documented "no I/O" but transitively does `File.exists()/canExecute()` per inventory entry, incl. from EDT notification paths |
| §2.1 | Cancellation gaps: `WorkspaceBuildRunner.waitFor()` ignores the indicator; the 2-minute install task is non-cancellable |

## Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-32-01 | Hardened primitive | M | Not Implemented | `LuaProcessUtil`: EDT assertion, cancellable wait, no blocking `.get()` under the read lock |
| MAINT-32-02 | Bridge off the lock | M | Not Implemented | Enumerate rockspec paths under read action; run bridge processes strictly outside the lock; consumers read cached data only (#11) |
| MAINT-32-03 | Caller migration | S | Not Implemented | Console action, coverage runner, health monitor prepare-phase, rocks environment I/O — all off the EDT/fast paths (§2.1) |
| MAINT-32-04 | Cancellable builds | S | Not Implemented | Indicator-aware `waitFor` + kill-on-cancel in build/install tasks (§2.1) |

**Verification:** thread-assertion tests where feasible; the EDT-freeze cases are
`verify-in-ide` DoD gates (type in a rockspec workspace; cancel a build mid-flight).
