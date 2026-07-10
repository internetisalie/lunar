---
id: "REDIS-05-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "REDIS-05"
folders:
  - "[[features/redis/05-functions-workflow/requirements|requirements]]"
---

# Verification Checklists: REDIS-05 — Redis Functions Workflow

Human-run scenarios in a real IDE for the behaviours automated tests cover awkwardly (editor
render/completion UX, run-config round-trips against a live server, tool-window flows, disabled-
action tooltips). Each is reproducible from a clean project. "Set a Redis 7+ target" means make the
project's active runtime resolve to `Redis 7+` (via the toolchain environment). A running server is
provided by `docker run --rm -p 6379:6379 redis:8` (and `valkey/valkey:8` for parity), or a
REDIS-01 local/Docker provisioning connection.

## 1. Shebang recognition & rendering (AC-1)

### Scenario 1.1: A library shebang lexes cleanly and is rendered distinctly
- **Setup**: Redis 7+ target; new file `lib.lua` beginning with:
  ```lua
  #!lua name=mylib
  redis.register_function('f', function(keys, args) return keys[1] end)
  ```
- **Steps**:
  1. Confirm no red error highlight anywhere on the shebang line.
  2. Observe the shebang line color vs the code below it.
- **Expected**: the `#!lua name=mylib` line renders in the comment color (distinct from code); no
  "unexpected token"/error underline; the file parses (structure view shows the
  `register_function` call).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Non-library file is not treated as a library
- **Setup**: Redis 7+ target; `plain.lua` with `local x = KEYS[1]` and no shebang.
- **Steps**: Inspect the file.
- **Expected**: no "KEYS/ARGV in a Redis Function library" warning (it is an EVAL script, not a
  library); `KEYS` resolves normally (REDIS-04 typing).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. `register_function` completion & typing (AC-2, AC-3)

### Scenario 2.1: `redis.register_function` completes and types the callback
- **Setup**: Redis 7+ target; `lib.lua` with the shebang; type `redis.regi` and complete.
- **Steps**:
  1. Confirm `register_function` appears in completion.
  2. Complete `redis.register_function('f', function(keys, args) ... end)`; hover `keys` inside the
     callback.
- **Expected**: `register_function` is offered; `keys` and `args` type as `string[]` (hover shows
  `string[]`); `keys[1]` types as `string`. No unresolved-reference red on `register_function`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: KEYS/ARGV flagged inside a library
- **Setup**: Redis 7+ target; `lib.lua` shebang; a callback body reading `KEYS[1]`.
- **Steps**: Observe the highlight on `KEYS`; open Alt-Enter.
- **Expected**: a WARNING on `KEYS` ("not available in a Redis Function library; use the callback's
  `keys`/`args`"); the suppression comment option is available; no quick-fix rewrite offered.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Table-form registration resolves
- **Setup**: Redis 7+ target; `lib.lua` with
  `redis.register_function{ function_name='g', callback=function(keys, args) end, flags={'no-writes'} }`.
- **Steps**: Hover the call; confirm no unresolved reference.
- **Expected**: the table-form overload resolves (`function_name`/`callback`/`flags`/`description`
  fields recognized); no red highlight.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. FCALL run configuration (AC-4, AC-5, AC-6)

### Scenario 3.1: Deploy-only run (FUNCTION LOAD without FCALL)
- **Setup**: Redis 7+ target; a running server + REDIS-01 connection; a Redis Script run config for
  `lib.lua`, mode = FCALL, "deploy only" checked, REPLACE checked.
- **Steps**: Run the config.
- **Expected**: the console shows the loaded library name (e.g. `mylib`); no FCALL error; re-running
  succeeds (REPLACE).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Load + FCALL round-trip
- **Setup**: same config, "deploy only" unchecked, function name = `f`, KEYS = `k1`, ARGV = `a1`.
- **Steps**: Run.
- **Expected**: the reply tree shows the `FCALL f 1 k1 a1` result (e.g. the value of `k1`); no error.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Function-name validation
- **Setup**: same config; set function name = `ghost` (not registered in `lib.lua`).
- **Steps**: Open the run config editor / try to run.
- **Expected**: a validation error "Function 'ghost' is not registered in lib.lua (registered: f)";
  correcting the name to `f` clears it.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: `no-writes` hint and read-only mapping
- **Setup**: `lib.lua` registers `f` with `flags={'no-writes'}`; FCALL config, function = `f`,
  read-only unchecked.
- **Steps**:
  1. Observe the settings-editor hint.
  2. Check "read-only"; run.
- **Expected**: a non-error hint suggests enabling read-only (`FCALL_RO`) because `f` declares
  `no-writes`; with read-only on, the run uses `FCALL_RO` and succeeds; if `f` attempts a write,
  the console shows the server error (`Write commands are not allowed`).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Debug executor disabled for FCALL (AC-9) — requires REDIS-02

### Scenario 4.1: Debug is greyed for a FCALL config with a tooltip
- **Setup**: a Redis Script run config in FCALL mode; REDIS-02 (LDB debugger) implemented.
- **Steps**:
  1. Open the run-config's executor buttons; hover the Debug action.
  2. Switch the same config to EVAL mode; hover Debug again.
- **Expected**: in FCALL mode the Debug action is disabled/greyed with a tooltip ("LDB cannot debug
  FCALL (Function) invocations (Redis limitation)"); in EVAL mode Debug is enabled.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Functions tool window (AC-7, AC-8)

### Scenario 5.1: List loaded libraries with flags
- **Setup**: a running server with `mylib` loaded (from Scenario 3.1); open the "Redis Functions"
  tool window; select the connection.
- **Steps**: Refresh the panel.
- **Expected**: `mylib` appears with its function(s) `f` and any flags (`no-writes`); the tree
  expands library → functions.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.2: Deploy from local file
- **Setup**: the panel; a library row backed by a local `lib.lua`.
- **Steps**: Trigger Deploy on the library.
- **Expected**: the library is (re)loaded via `FUNCTION LOAD REPLACE`; the list refreshes; no error.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.3: Delete with confirmation
- **Setup**: the panel with `mylib` listed.
- **Steps**: Trigger Delete on `mylib`.
- **Expected**: a Yes/No confirmation dialog appears; on Yes the library is removed from the server
  and the row disappears; on No nothing changes.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.4: Drift indicator
- **Setup**: a server (supporting `FUNCTION LIST WITHCODE`) with `mylib` loaded; edit the local
  `lib.lua` so its body differs from the loaded code.
- **Steps**: Refresh the panel.
- **Expected**: `mylib` shows a drift/warning glyph (local ≠ server); after a Deploy, the glyph
  clears (in sync). On a server without WITHCODE, no drift glyph is shown.
- **Result**: ⬜ Pass / ⬜ Fail

## 6. Valkey parity (AC-10)

### Scenario 6.1: Same workflow against Valkey
- **Setup**: a running `valkey/valkey:8`; a connection to it; a `#!lua name=vlib` library.
- **Steps**: Deploy (LOAD), list in the panel, FCALL, delete.
- **Expected**: every step behaves as against `redis:8`; `server.register_function` (if authored
  with the `server.*` namespace) also resolves under a Valkey target.
- **Result**: ⬜ Pass / ⬜ Fail
