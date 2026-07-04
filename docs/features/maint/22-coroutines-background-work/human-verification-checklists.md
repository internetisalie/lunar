---
id: "MAINT-22-verification"
title: "MAINT-22 Human Verification Checklists"
type: "qa"
parent_id: "MAINT-22"
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-22 — Human / Live Verification Checklists

Live debug flow cannot be exercised by unit tests (real DBGp socket + Lua debuggee). Run these via the
`verify-in-ide` skill against the containerized GoLand over VNC. Archive a screenshot per checked row.

## Setup
- [ ] Containerized GoLand launched clean (never `docker restart`); plugin jar hot-swapped; license/trial active.
- [ ] Interpreter configured; open the Lua test project at `~/Documents/src/lua/test`.
- [ ] Pick a script with a known line to break on; MobDebug/DBGp client wired to port 8172.

## Debug flow (MAINT-22-08)
- [ ] **Connect:** launch the Debug run config → console prints `Debugger connected at …`; no error dialog; no ~1s stall.
- [ ] **Breakpoint hit:** set a line breakpoint, run → execution pauses at that exact line; the line is highlighted.
- [ ] **Variables:** the Variables/Frames pane lists locals with correct values.
- [ ] **Evaluate:** evaluate `1 + 1` → `2`; evaluate a local variable → its live value; evaluate a bad expression
      → error surfaced (not a hang).
- [ ] **Step Over:** advances exactly one line.
- [ ] **Step Into / Out:** enter a called function / return to caller correctly.
- [ ] **Resume:** continues to the next breakpoint or to normal completion.
- [ ] **Terminate:** Stop ends the session cleanly; console shows disconnect; the run config can be re-launched immediately.

## Lifecycle / no-leak (MAINT-22-05)
- [ ] After Stop, `idea.log` shows one clean `readLoop`/connection-exit per session (no repeated exceptions).
- [ ] No lingering debugger reader thread/coroutine after the session ends (spot-check `jstack` or repeated
      start/stop ×3 without accumulating threads).
- [ ] No EDT/threading-assertion (`Access is allowed from …`, `Must be called on EDT`) in `idea.log` during the flow.

## Regression guard
- [ ] `gce-builder run test` green (regression-relative to Wave baseline).
- [ ] `gce-builder run build` green (incl. `:checkStatus`, `:lintDocs`) after `gen_status.py` regen.
- [ ] `ktlintFormat ktlintCheck` clean on all touched files.

## Sign-off
- [ ] All debug-flow rows PASS with archived screenshots → MAINT-22 may move to `done`.
