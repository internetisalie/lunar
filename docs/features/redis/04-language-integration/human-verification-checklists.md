---
id: "REDIS-04-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "REDIS-04"
folders:
  - "[[features/redis/04-language-integration/requirements|requirements]]"
---

# Verification Checklists: REDIS-04 — Language-Engine Integration

Manual scenarios covering behaviors a light fixture cannot fully assert (rendered popups,
gutter/severity visuals, quick-fix UX). Each is reproducible from a clean project. Set the
target via **Settings → Languages & Frameworks → Lua** (or the target picker) to the stated
platform/version before each scenario.

## 1. Completion (AC-3)

### Scenario 1.1: Command completion popup content
- **Setup**: project target **Redis 7+**; a `.lua` file with `redis.call("")` (caret between
  the quotes).
- **Steps**:
  1. Invoke basic completion (Ctrl/Cmd-Space) inside the quotes.
- **Expected**: a popup of Redis command names (`GET`, `SET`, `HSET`, …); each row shows the
  command summary as grey tail text; typing `HG` narrows to `HGET`, `HGETALL`, etc.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Version filtering is visible
- **Setup**: target **Redis 5**; file with `redis.call("SINTERCARD")` context and an empty
  `redis.call("")`.
- **Steps**:
  1. Invoke completion inside the empty literal.
- **Expected**: `SINTERCARD` (Redis 7.0) is **not** offered; switching the target to **Redis
  7+** and re-invoking now offers `SINTERCARD`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: No Redis completion off-target
- **Setup**: target **Standard 5.4**; file with `redis.call("")`.
- **Steps**:
  1. Invoke completion inside the quotes.
- **Expected**: no Redis command names appear (only ordinary string content, if any).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Quick Documentation (AC-5)

### Scenario 2.1: Command doc popup
- **Setup**: target **Redis 7+**; `redis.call("GET", KEYS[1])`.
- **Steps**:
  1. Place caret on the `"GET"` literal; invoke Quick Documentation (Ctrl/Cmd-Q / F1).
- **Expected**: a doc popup showing the command name, its summary, `Since 1.0.0`, and
  `Arity 2`.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Inspections — visuals & quick fix (AC-4, AC-7, AC-8, AC-9)

### Scenario 3.1: Unknown-command did-you-mean fix
- **Setup**: target **Redis 7+**; `redis.call("Gte", KEYS[1])`.
- **Steps**:
  1. Observe the highlight on `"Gte"`; open the intention menu (Alt-Enter).
  2. Apply the suggested fix.
- **Expected**: a WARNING squiggle "Unknown Redis command 'GTE'"; the intention "Change to
  'GET'" is offered; applying it rewrites the literal to `"GET"` and the warning clears.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Arity warning
- **Setup**: target **Redis 7+**; `redis.call("GET")`.
- **Expected**: a WARNING on the argument list stating `GET` expects at least 1 argument,
  found 0; adding `KEYS[1]` clears it.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Sandbox-API warnings
- **Setup**: target **Redis 7+**; a file using `print("x")`, `require("m")`, `io.read()`,
  `dofile("f")`, and `os.time()`.
- **Expected**: `print`, `require`, `io`, `dofile` are each flagged "not available in the
  Redis script sandbox"; `os.time()` is **not** flagged (allowlisted). Switching to **Standard
  5.4** clears all of these.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: Global-creation escalates to ERROR
- **Setup**: target **Redis 7+**; `myGlobal = 1` at file scope.
- **Steps**:
  1. Observe the severity (red ERROR stripe vs yellow WARNING) on `myGlobal`.
  2. Switch target to **Standard 5.4** and re-observe.
- **Expected**: ERROR-level highlight under Redis; the same code is WARNING-level under
  Standard. A `-- luacheck: ignore` covering the line suppresses it under both.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.5: Determinism warning (Redis 5/6 only)
- **Setup**: target **Redis 6**; `local t = redis.call("TIME"); redis.call("SET", KEYS[1],
  t[1])`.
- **Steps**:
  1. Observe the highlight on `"TIME"`.
  2. Add `redis.replicate_commands()` as the first line; re-observe.
  3. Switch target to **Redis 7+**; re-observe.
- **Expected**: WARNING on `"TIME"` about verbatim replication in step 1; cleared after the
  guard call in step 2; not present at all under Redis 7+ in step 3.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Ambient typing (AC-1, AC-6)

### Scenario 4.1: KEYS/ARGV typing and pcall narrowing
- **Setup**: target **Redis 7+**; a file with
  `local k = KEYS[1]; local n = #ARGV; local r = redis.pcall("GET", KEYS[1]); if r.err then
  print(r.err) end`.
- **Expected**: no "undeclared variable" on `KEYS`/`ARGV`; hovering `k` shows `string`, `n`
  shows `integer`; inside the `if`, `r.err` completes/types as `string` (member `err` is
  offered on `r`).
- **Result**: ⬜ Pass / ⬜ Fail
