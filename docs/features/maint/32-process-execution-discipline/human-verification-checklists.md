---
id: "MAINT-32-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "MAINT-32"
folders:
  - "[[features/maint/32-process-execution-discipline/requirements|requirements]]"
---

# Verification Checklists: MAINT-32 — Process-Execution Discipline

Live VNC scenarios (builder VM, `verify-in-ide` skill) — the EDT-freeze / kill-on-cancel behavior
that unit tests cannot observe. **All rows here are VNC-gated.** Setup for all: a Lua project with a
configured runtime + `luarocks` toolchain; reuse `~/Documents/src/lua/test` with several `.rockspec`
files (Kernel-style, ≥5 rockspecs) for the rockspec-heavy cases.

## 1. EDT responsiveness (MAINT-32-02, MAINT-32-03)

### Scenario HV-01: Typing latency in a rockspec-heavy workspace (VNC-gated)
- **Setup**: open the rockspec-heavy project; ensure a Lua runtime is configured so the bridge
  actually runs.
- **Steps**:
  1. Open a `.lua` file and type continuously for ~10 s (hold a key / paste-and-edit).
  2. Watch for editor stalls or a "IDE not responding" freeze.
- **Expected**: no multi-second freeze; typing stays fluid. (Before MAINT-32-02, discovery ran the
  `lua` bridge under the read lock → up to 10 s × #rockspecs stall.)
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario HV-02: Open Lua Console (VNC-gated)
- **Setup**: a configured runtime; a slow-to-start interpreter path if available.
- **Steps**:
  1. Tools → invoke the Lua Console action.
- **Expected**: the UI does not freeze while the interpreter starts; the REPL prompt appears; with
  no runtime configured, an error notification appears instead of a hang.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario HV-03: Coverage run (MAINT-32-03) (VNC-gated)
- **Setup**: a `LuaTestRunConfiguration` with `luacov` installed and a stale `luacov.stats.out`
  present in the working dir.
- **Steps**:
  1. Run the test config with the Coverage executor.
- **Expected**: no `SlowOperationsException` in idea.log at launch; the stale stats file is removed
  and fresh coverage is collected.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Cancellation (MAINT-32-04)

### Scenario HV-04: Cancel a workspace build mid-flight (VNC-gated)
- **Setup**: a workspace with ≥2 first-party rocks that build via `luarocks make` (make the first
  rock's build non-trivial / slow).
- **Steps**:
  1. Invoke Build Workspace (dependency order).
  2. While rock 1 is building, click Cancel on the progress indicator.
  3. Check for a lingering `luarocks`/`lua` process (`ps` in the VNC terminal).
- **Expected**: the build stops promptly; the console reports a failed/cancelled outcome; **no
  orphaned `luarocks make` process** remains running.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario HV-05: Cancel a LuaRocks install (VNC-gated)
- **Setup**: trigger a LuaRocks install (e.g. the coverage-runner "Install via LuaRocks" action, or
  the rocks browser install).
- **Steps**:
  1. Start the install.
  2. Click Cancel on the background task.
- **Expected**: the task shows a Cancel affordance and responds to it (the task is constructed
  cancellable); the install stops.
- **Result**: ⬜ Pass / ⬜ Fail
