---
id: "REDIS-03-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "REDIS-03"
folders:
  - "[[features/redis/03-valkey-target/requirements|requirements]]"
---

# Verification Checklists: REDIS-03 — Valkey Runtime Target

Human-run scenarios in a real IDE for the behaviours automated tests cover awkwardly (editor
resolution UX, notification presentation, quick-fix gutter flow). Each is reproducible from a clean
project. "Set Valkey/Redis target" means make the project's active runtime resolve to the given
platform (via the toolchain environment; see risks-and-gaps Gap 2.2).

## 1. Stub resolution & completion (AC-3, AC-4)

### Scenario 1.1: `server.*` resolves and completes under a Valkey target
- **Setup**: a project with the target resolving to `Valkey 8`; an open `s.lua`.
- **Steps**:
  1. Type `server.` and trigger completion.
  2. Confirm `call`, `pcall`, `error_reply`, `status_reply`, `sha1hex`, `log`, `setresp`,
     `set_repl`, `breakpoint`, `debug`, `acl_check_cmd` appear.
  3. Ctrl/Cmd-click `call` in `server.call("PING")`.
- **Expected**: completion lists the inherited `redis` members; navigation lands in
  `runtime/valkey/valkey-8/server.lua` (or the inherited `redis.lua` member); no "unresolved" red.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: `SERVER_*` globals resolve; `redis.*`/`KEYS`/`ARGV` still work
- **Setup**: Valkey 8 target; `s.lua` with `local n = SERVER_VERSION_NUM` and
  `redis.call("GET", KEYS[1], ARGV[1])`.
- **Steps**:
  1. Hover `SERVER_VERSION_NUM`; hover `SERVER_NAME`; hover `KEYS`.
  2. Confirm no unresolved-reference highlight on `SERVER_*`, `redis`, `KEYS`, `ARGV`.
- **Expected**: `SERVER_VERSION_NUM` shows type `number`, `SERVER_NAME` `string`; `redis`/`KEYS`/
  `ARGV` resolve exactly as under a Redis target.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: No `server.*` docs duplication
- **Setup**: Valkey 8 target.
- **Steps**: Quick-doc (Ctrl/Cmd-Q) on `server.call`.
- **Expected**: doc renders from the inherited `redis.call` signature (the `---@class server :
  redis` parent); `server.lua` contains no per-member re-declarations.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Flavor-mismatch warning (AC-5) — requires REDIS-01

### Scenario 2.1: Valkey server under a Redis target warns exactly once
- **Setup**: target resolves to `Redis 7+`; a REDIS-01 connection pointing at a running
  `valkey/valkey:8` server.
- **Steps**:
  1. Test Connection (or run a script) against the Valkey connection.
  2. Repeat the connect/run against the **same** connection.
- **Expected**: on the first connect, one non-modal WARNING notification appears ("Connected server
  is Valkey but the project target is Redis…"); the second connect shows **no** new notification.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: No warning when flavor matches
- **Setup**: `Redis 7+` target; a connection to a running `redis:8` server.
- **Steps**: Test Connection.
- **Expected**: no flavor-mismatch notification (the Test-Connection result still shows the
  detected version/flavor).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: No warning under a non-Redis/Valkey target
- **Setup**: `Standard 5.4` target; a connection to any server.
- **Steps**: Test Connection.
- **Expected**: no flavor-mismatch notification (mismatch predicate is false for non-flavor targets).
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Portability inspection & quick fix (AC-6, AC-7)

### Scenario 3.1: `server.*` flagged under a Redis target with a quick fix
- **Setup**: `Redis 7+` target; `s.lua` containing `server.call("PING")`.
- **Steps**:
  1. Observe the highlight on `server`.
  2. Alt-Enter → apply "Replace 'server' with 'redis'".
- **Expected**: a WARNING on the `server` segment ("`server.*` is a Valkey-only namespace…"); after
  the fix the line reads `redis.call("PING")` and the warning clears.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: `SERVER_*` flagged with no quick fix
- **Setup**: `Redis 7+` target; `s.lua` with `local x = SERVER_NAME`.
- **Steps**: Observe the highlight; open Alt-Enter.
- **Expected**: a WARNING on `SERVER_NAME` ("…Valkey-only global…"); **no** "Replace with redis"
  quick fix is offered (no 1:1 equivalent).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Silent under a Valkey target
- **Setup**: `Valkey 8` target; `s.lua` with `server.call("PING")` and `local x = SERVER_NAME`.
- **Steps**: Inspect the file.
- **Expected**: no portability warnings on either line.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: `redis.*` never flagged
- **Setup**: `Redis 7+` target; `s.lua` with `redis.call("PING")`.
- **Steps**: Inspect the file.
- **Expected**: no warning (the compat namespace is portable).
- **Result**: ⬜ Pass / ⬜ Fail
