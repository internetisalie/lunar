---
id: "REDIS-02"
parent_id: "REDIS"
type: "feature"
status: "todo"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-02: LDB Debug Adapter"
---

# REDIS-02: LDB Debug Adapter

**Requirement**: An XDebugger adapter for the Redis/Valkey server-side Lua debugger (LDB),
enabling breakpoint debugging of scripts executed via the REDIS-01 run configuration.
**Priority**: Must
**Status**: Not Implemented

---

## Overview

Redis and Valkey ship a built-in remote Lua debugger: after `SCRIPT DEBUG YES|SYNC`, an
`EVAL` on the same connection enters a stepping session driven by simple commands — the
protocol `redis-cli --ldb` speaks. No IDE integrates it; this feature maps it onto the
IntelliJ debugging UI as a second debug adapter alongside the MobDebug one.

**Protocol note:** the LDB wire behavior is defined by the redis-cli implementation rather
than a formal spec. The adapter is implemented against the redis-cli source and pinned by
integration tests against dockerized Redis and Valkey (both flavors gate the DoD).

### LDB ↔ XDebugger mapping

| LDB primitive | IDE feature |
| :--- | :--- |
| `break <line>` / `break -<line>` / `break 0` | add / remove / clear line breakpoints |
| `redis.breakpoint()` in script | pre-set breakpoint (documented; also used for conditional support) |
| `step` / `next` / `continue` | Step Into / Step Over / Resume (LDB has no step-out; action disabled) |
| `print` | Variables view (all locals in frame) |
| `print <var>` / `eval <expr>` | watches + Evaluate dialog |
| `redis <cmd>` | "Redis command" console tab while paused |
| `list` / `whole` | source sync check (IDE already has the file; used for line validation) |
| `abort` / `restart` | Stop / Rerun |
| session mode `YES` (forked) vs `SYNC` | run-config toggle (see below) |

### Session modes

- **Forked (default, `SCRIPT DEBUG YES`)**: server forks; all writes are rolled back on
  session end. Safe against shared servers. The UI labels the session "changes rolled back".
- **Sync (`SCRIPT DEBUG SYNC`)**: blocks the server event loop and commits writes. Guarded
  by a run-config checkbox with an explicit warning, and refused with a confirmation dialog
  when the connection is not a session-local ("launch local") server.

## Acceptance Criteria

- [ ] "Debug" executor on the REDIS-01 run configuration starts an LDB session over the
      REDIS-01 RESP client (`SCRIPT DEBUG` + `EVAL`), with fork/sync mode from the config
- [ ] Line breakpoints in the script file are registered via `break <line>` before `EVAL`
      resumes, and can be added/removed while paused; unresolvable lines (non-code) are
      marked invalid in the UI
- [ ] Conditional breakpoints supported (evaluated IDE-side on pause via `eval`; resume when
      false)
- [ ] Step Into / Step Over / Resume / Stop / Rerun mapped to `step`/`next`/`continue`/
      `abort`/`restart`; Step Out disabled with a tooltip (LDB limitation)
- [ ] Variables view shows all frame locals from `print` output (including table values
      pretty-parsed into expandable nodes); watches and Evaluate dialog use `eval`
- [ ] A "Redis" console tab executes `redis <cmd>` in the paused session and renders the
      reply tree
- [ ] Errors (script compile errors, `eval` failures, connection loss) surface in the debug
      session UI — never as silent hangs or IDE fatal-error reports; rejected evaluations
      call `errorOccurred`
- [ ] Sync mode: warning text on the config; confirmation dialog when the target connection
      is not session-local; session banner states writes will be committed
- [ ] Forked mode session banner states writes are rolled back; forked-session server
      timeout surfaced as a session-ended message, not an exception
- [ ] Engineering-contract compliance verified in review: no EDT blocking, no `!!` in
      protocol parsing, explicit charsets, all waits cancellable
- [ ] Integration tests against dockerized `redis:8` **and** `valkey/valkey:8`: breakpoint
      hit, step, locals, eval, redis command, abort, both session modes
