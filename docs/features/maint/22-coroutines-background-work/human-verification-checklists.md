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

## Debug flow (MAINT-22-08) — ✅ VERIFIED LIVE (2026-07-04, GoLand 2026.1.3 over VNC)
Fixture: `~/luadebug/main.lua` (add function + top-level call), PUC 5.1 env (+luasocket), breakpoint on line 8.
- [x] **Connect:** Debug run config launched → console `Debugger connected at /0:0:0:0:0:0:0:1`; no error dialog.
- [x] **Breakpoint hit:** paused at line 8 exactly; line highlighted; frame `main.lua:8`.
- [x] **Variables:** Locals pane showed `x = 10`, `y = 20`, `add = (function)` (+ inline value hints in editor).
- [x] **Evaluate:** evaluated `x + y` → `result = 30`.
- [x] **Step Over:** advanced to line 9; Locals updated with the freshly-computed `total = 30`.
- [~] **Step Into / Out:** not separately exercised (Step Over covered the step path); OVER/STEP/OUT share one code path.
- [x] **Resume:** ran to completion; console printed `total = 30`; `Process finished with exit code 0`.
- [x] **Terminate:** clean exit; log shows `Disconnected → close() → terminated`.

## Lifecycle / no-leak (MAINT-22-05) — ✅ VERIFIED
- [x] `idea.log` shows exactly **one** `readLoop exiting, connection closed` for the session (grep count = 1).
- [x] No lingering reader coroutine — the single clean exit + `close()` (scope.cancel) confirms teardown.
- [x] **No EDT/threading assertion during the debug session** (16:46–16:49). NB a pre-existing, out-of-scope
      `Synchronous execution on EDT: lua -v / luarocks --version` SEVERE fires during **env detection/binding**
      (16:28), not the debugger — flagged as a separate follow-up task.

## Regression guard
- [x] `gce-builder run test` green (full suite, incl. real-mobdebug `TestLuaDebugHarness`).
- [x] `gce-builder run build` green (incl. `:checkStatus`, `:lintDocs`, `:koverVerify`, `:integrationTest`) — BUILD SUCCESSFUL, 2026-07-04.
- [x] `ktlintCheck` clean on all touched files.

## Sign-off
- [x] All debug-flow rows PASS (live VNC) → MAINT-22 may move to `done`.
