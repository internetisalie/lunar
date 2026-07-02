---
id: "REDIS-01"
parent_id: "REDIS"
type: "feature"
status: "todo"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-01: Connections & Script Run Configuration"
---

# REDIS-01: Connections & Script Run Configuration

**Requirement**: A native RESP client, a Redis/Valkey server connection model, and a
"Redis Script" run configuration that executes the current Lua file against a server.
**Priority**: Must
**Status**: Not Implemented

---

## Overview

The foundation of the epic: everything else (LDB debugging, flavor detection, Functions
deploy) rides on a connection model and a small native RESP client. RESP is deliberately
implemented in-plugin (it is a trivial length-prefixed protocol) rather than shelling out
to `redis-cli` — this gives structured replies for the console tree, avoids PTY handling,
and is the prerequisite for the REDIS-02 debug adapter, which shares the connection.

A **Redis Script run configuration** executes a `.lua` file against a configured server
with user-supplied `KEYS` and `ARGV`, in one of three modes:

- `EVAL` — send the script body each run (default; simplest loop).
- `SCRIPT LOAD` + `EVALSHA` — load once, run by SHA (exercises the production code path).
- `FUNCTION LOAD` + `FCALL` — deferred to REDIS-05; the mode enum reserves the slot.

Both EVAL modes support a **read-only** toggle mapping to the `EVAL_RO`/`EVALSHA_RO`
variants (Redis 7+/Valkey), for safely exercising read paths against non-local servers.

The run console renders the RESP reply as an expandable tree (nested arrays/maps, RESP3
types when negotiated) and hyperlinks server-side error locations (`user_script:12:`) back
to the editor line.

### Connection model

A project-level list of named server connections: host/port, optional TLS, optional
AUTH (username/password via `PasswordSafe`, mirroring the ROCKS-06 API-key pattern),
database index, and a *provisioning* variant: "launch local" — a `redis-server` /
`valkey-server` binary resolved through the TOOL manager, or a Docker container
(`redis:8` / `valkey/valkey:8`) started for the session and stopped with it.

## Acceptance Criteria

- [ ] RESP client: RESP2 protocol support (RESP3 `HELLO` negotiation attempted, graceful
      fallback); byte-accurate framing with explicit UTF-8 handling for bulk strings;
      connect/read timeouts; cancellable from a `ProgressIndicator`; no EDT I/O
- [ ] Connection settings UI: named connections with host/port/TLS/auth/db; credentials in
      `PasswordSafe`; "Test Connection" button reporting server flavor + version (via
      `HELLO`/`INFO`)
- [ ] "Launch local" connection variants: tool-manager-resolved server binary and Docker
      container; server lifecycle bound to the run session (started before, terminated
      after); clear error if neither Docker nor a binary is available
- [ ] `LuaRedisRunConfiguration`: script path, connection reference, execution mode
      (EVAL / SCRIPT LOAD+EVALSHA), `KEYS` list, `ARGV` list — all persisted;
      `checkConfiguration()` validates script path and connection
- [ ] Run-configuration producer: available from the editor context menu for `.lua` files
      when the project target platform is Redis or Valkey
- [ ] Console: RESP reply rendered as an expandable tree; scalar replies inline; errors
      shown with the server error class (`ERR`, `WRONGTYPE`, …)
- [ ] Console filter hyperlinks `user_script:<N>` / `@user_script: <N>` error references to
      the script file at line N (1-based → editor 0-based conversion correct)
- [ ] `SCRIPT LOAD` mode caches the SHA per (connection, script hash) and falls back to
      re-`LOAD` on `NOSCRIPT`
- [ ] Read-only toggle on the run configuration executes via `EVAL_RO`/`EVALSHA_RO`; when
      the connected server predates the `_RO` variants (Redis < 7), the run fails fast
      with a message naming the required server version
- [ ] Unit tests for RESP encode/decode (incl. multi-byte UTF-8 payloads, partial reads);
      integration test executing a script against dockerized `redis:8` and
      `valkey/valkey:8`
