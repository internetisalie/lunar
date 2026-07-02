---
id: "REDIS-05"
parent_id: "REDIS"
type: "feature"
status: "todo"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-05: Redis Functions Workflow"
---

# REDIS-05: Redis Functions Workflow

**Requirement**: First-class support for Redis Functions (Redis 7+/Valkey): shebang-aware
editing, typed `redis.register_function`, a FUNCTION LOAD deploy mode on the run
configuration, and a server functions panel.
**Priority**: Could
**Status**: Not Implemented

---

## Overview

Redis 7 replaced ad-hoc `EVAL` scripts with **Functions**: a library file starting with
`#!lua name=<libname>`, registering named callbacks via `redis.register_function`, loaded
once with `FUNCTION LOAD` and invoked with `FCALL <fn> <numkeys> …`. Valkey retains the
same machinery. This feature closes the loop for teams that have moved from EVAL to
Functions.

Function callbacks receive `(keys, args)` as arrays — a different ambient contract from
EVAL's `KEYS`/`ARGV` globals — and library files must not use the `KEYS`/`ARGV` globals at
all.

## Acceptance Criteria

- [ ] Files beginning with `#!lua name=<lib>` are recognized as Redis Function libraries
      under Redis 7+/Valkey targets: the shebang line lexes/parses cleanly (no error
      elements) and is rendered distinctly
- [ ] LuaCATS-typed stubs for the Functions API: `redis.register_function` in both the
      positional form `(name, callback)` and the table form
      (`function_name`, `callback`, `flags` — `no-writes`, `allow-oom`,
      `allow-stale`, `no-cluster`, `allow-cross-slot-keys` — and `description`), with
      callback signature `fun(keys: string[], args: string[]): any`
- [ ] Inspection: `KEYS`/`ARGV` usage inside a function library file is flagged (they are
      EVAL-only); REDIS-04's ambient globals are suppressed in library files
- [ ] REDIS-01 run configuration gains the `FUNCTION LOAD` + `FCALL` execution mode:
      library file, `REPLACE` toggle, function name to call, KEYS/ARGV inputs; loading a
      library without calling is a valid "deploy" run
- [ ] REDIS-01's read-only toggle maps to `FCALL_RO` in this mode; a hint suggests
      enabling it when the selected function declares the `no-writes` flag (and the run
      fails with a clear server error surface if a write is attempted)
- [ ] `FCALL` mode validates the requested function name against the names registered in
      the library file (best-effort static scan; dynamic names skipped)
- [ ] Functions tool-window panel (or run-config gutter view) listing `FUNCTION LIST`
      output for a configured connection: libraries, functions, flags; per-library actions
      Deploy (LOAD REPLACE from the local file) and Delete, with confirmation
- [ ] Local-vs-server drift indicator: the panel marks a library whose local source hash
      differs from the loaded `library_code` (when the server reports it via
      `FUNCTION LIST WITHCODE`)
- [ ] REDIS-02 debugging note surfaced in the UI: LDB does not support stepping FCALL
      invocations (Redis limitation) — the Debug executor for FCALL mode is disabled with
      an explanatory tooltip
- [ ] Integration test against dockerized `redis:8`: load, list, call, replace, delete;
      Valkey parity run
