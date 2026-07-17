---
id: "MAINT-24-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "MAINT-24"
folders:
  - "[[features/maint/24-debugger-hardening/requirements|requirements]]"
---

# Verification Checklists: MAINT-24 — Debugger & Test-Runner Hardening

Live VNC scenarios (builder VM, `verify-in-ide` skill) — the DBGp/busted flow that unit tests
cannot cover. Setup for all: a Lua project with a `busted`-installed toolchain and a script with a
line breakpoint (reuse `~/Documents/src/lua/test`).

## 1. Debugger (DBGp)

### Scenario HV-04: UTF-8 value + Run to Cursor
- **Setup**: a script `local x = "café"; print(x)` with a breakpoint on the `print` line; a second
  line further down with no breakpoint.
- **Steps**:
  1. Debug the script; wait for the breakpoint pause.
  2. Inspect `x` in the Variables panel.
  3. Right-click the further-down line → Run to Cursor.
- **Expected**: `x` shows `café` (not truncated/garbled); Run to Cursor pauses exactly at that line
  with no `AbstractMethodError` crash dialog; no leftover breakpoint marker on that line afterward.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario HV-07: Configurable debug port
- **Setup**: the run configuration's Debug port field set to `9000`.
- **Steps**:
  1. Start a debug session.
  2. Confirm the breakpoint hits.
- **Expected**: the session connects on port 9000 (not 8172) and the breakpoint fires normally.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario HV-08: Graceful stop
- **Setup**: an active debug session paused at a breakpoint.
- **Steps**:
  1. Press Stop.
- **Expected**: the debuggee receives `EXIT` and terminates cleanly; the idea.log shows the `EXIT`
  send (no dangling process, no error dialog).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Test Runner (busted)

### Scenario HV-06: Live console output
- **Setup**: a busted spec whose test body calls `print("running…")` and contains an apostrophe
  (`assert(("doesn't"):match("n't"))`).
- **Steps**:
  1. Run the busted configuration.
  2. Watch the Run console during execution.
- **Expected**: `running…` appears in the console *during* the run (not only at the end); the test
  tree still populates correctly from the terminal JSON (the apostrophe does not swallow the report).
- **Result**: ⬜ Pass / ⬜ Fail
