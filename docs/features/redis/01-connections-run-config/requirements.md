---
id: "REDIS-01"
parent_id: "REDIS"
type: "feature"
status: "done"
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

The acceptance criteria above are referenced by position below as **AC-1** (RESP client) …
**AC-10** (unit + integration tests), top to bottom.

## Test Cases

Concrete input → expected output for every `Must` acceptance criterion. Each TC seeds the
automated test named in [implementation-plan.md](implementation-plan.md) "Verification Tasks";
the design section that specifies the behavior is cited in the last column. Every AC maps to at
least one TC (see the AC → TC coverage matrix after the table).

| # | AC | Given (input) | When (action) | Then (expected) | Design |
|---|-----|---------------|---------------|-----------------|--------|
| TC-RESP-1 | AC-1 | `listOf("SET".toByteArray(), "café".toByteArray(UTF_8))` | `RespCodec.encodeCommand(args)` | bytes = `*2\r\n$3\r\nSET\r\n$5\r\ncafé\r\n` (the `$5` is the **byte** length of `café`, not `4`) | §3.2 |
| TC-RESP-2 | AC-1 | server bytes `+OK\r\n` / `:42\r\n` / `-WRONGTYPE bad\r\n` / `$3\r\nfoo\r\n` / `*2\r\n:1\r\n:2\r\n` / RESP3 `%1\r\n$1\r\nk\r\n$1\r\nv\r\n`, `,3.14\r\n`, `#t\r\n`, `_\r\n` | `RespCodec.decode(stream)` on each | `Simple("OK")`, `Integer(42)`, `Error("WRONGTYPE","bad")`, `Bulk("foo")`, `Array([Integer(1),Integer(2)])`, `Map([(Bulk("k"),Bulk("v"))])`, `Double(3.14)`, `Bool(true)`, `Null` | §3.3 |
| TC-RESP-3 | AC-1 | bulk `$5\r\ncafé\r\n` (café = 5 UTF-8 bytes) | `decode` then `Bulk.asString()` | `"café"` — exactly 5 bytes consumed for the payload, trailing `\r\n` consumed | §3.3 |
| TC-RESP-4 | AC-1 | a `$6\r\nabcdef\r\n` reply delivered in fragments (e.g. `$6\r\nab`, then `cd`, then `ef\r\n`) via a chunked `PushbackInputStream` | `decode(stream)` | `Bulk("abcdef")` — the length-prefixed loop reassembles the fragmented stream | §3.3 |
| TC-TIMEOUT-1 | AC-1 | a socket whose read throws `SocketTimeoutException` (readMs elapsed) | `RespClient.command(...)` (or `open`) | throws `RespException.Timeout` (op-named), never a raw `SocketTimeoutException` | §2.3, §2.10 |
| TC-CANCEL-1 | AC-1 | a `RespClient.command(...)`/`open` running under a `ProgressIndicator` reporting `isCanceled == true` | the indicator is cancelled before the read completes | the call aborts in-flight (throws `ProcessCanceledException`) without completing the read; client is disposable | §2.3, §6 |
| TC-CONN-1 | AC-2 | a `LuaRedisServerConnection(id="u1", name="local", host="127.0.0.1", port=6379, tls=false, database=2, username="app", provisioning=Remote)` | `LuaRedisConnectionSettings.upsert(conn)`; serialize state to XML; deserialize; `findById("u1")` | round-trips to an equal connection (all scalar fields incl. `database=2`, `username="app"`); no password in the XML | §2.4, §2.5 |
| TC-CONN-2 | AC-2 | `setPassword("u1", "s3cr3t")` | `getPassword("u1")`, then `setPassword("u1", "")`, then `getPassword("u1")` | first read `"s3cr3t"`; after blank set, `null` (cleared) | §2.9 |
| TC-CON-1 | AC-6 | `Array([Bulk("a"), Array([Integer(1), Integer(2)]), Map([(Bulk("k"), Bulk("v"))])])` | `RespReplyTreeModel.build(reply)` | root has 3 children; child 0 = leaf `[0] "a"`; child 1 = expandable node with 2 integer leaves; child 2 = expandable node with one `k = v` child; scalar leaves render inline | §3.5 |
| TC-CON-2 | AC-6 | `RespValue.Error("WRONGTYPE", "Operation against a key holding the wrong kind of value")` | `RespReplyTreeConsole.showError(error)` | console line shows the error **class** tag `WRONGTYPE` (e.g. `(error) WRONGTYPE Operation against …`) | §3.4, §2.6 |
| TC-CON-3 | AC-7 | console line `user_script:12: bad arg` with the run's script URL bound; and `@user_script: 7` | `LuaRedisErrorLinkFilter.applyFilter(line, entireLength)` | a `Filter.Result` whose hyperlink opens the script `VirtualFile` at editor line **11** (12 → 0-based) for the first; **6** for the second; both `@`/colon/whitespace forms match; unresolvable file URL → `null` (no `!!`) | §3.6 |
| TC-RC-1 | AC-4 | a `LuaRedisRunConfiguration` with `scriptPath="a.lua"`, `connectionId="u1"`, `execMode=EVALSHA`, `readOnly=true`, `keys=["k1","k2"]`, `argv=["v1"]` | write options → read options back (round-trip); then clear `scriptPath` and call `checkConfiguration()` | options persist all fields (keys/argv survive the `\n`-joined `keysRaw`/`argvRaw` bridge); with blank scriptPath, `checkConfiguration` throws `RuntimeConfigurationException("Script path is not defined")` | §2.8, §3.7 |
| TC-PROD-1 | AC-5 | a `.lua` file context with project target `LuaPlatform.REDIS`; and a second context with target `LUA` | `LuaRedisRunConfigurationProducer.setupConfigurationFromContext(...)` | returns `true` (config created) for the REDIS target; `false` for the LUA target | §7 |
| TC-LAUNCH-1 | AC-3 | provisioning `LocalBinary("redis-server")` with the kind resolved to `/usr/bin/redis-server`, free port 12345 | `LuaRedisServerLauncher.launch(provisioning)` (command assembled, not executed) | command line = `redis-server --port 12345 --save ""` (order per §3.9) | §3.9 |
| TC-LAUNCH-2 | AC-3 | provisioning `Docker("redis:8")` with `docker` on PATH, free port 12345 | `launch(provisioning)` (command assembled) | command line = `docker run --rm -d -p 12345:6379 redis:8` | §3.9 |
| TC-LAUNCH-3 | AC-3 | provisioning `LocalBinary("redis-server")` unresolved **and** no `docker` on PATH | `launch(provisioning)` | throws `ExecutionException` whose message names both the missing binary (Settings path) and Docker as alternatives | §3.9 |
| TC-SHA-1 | AC-8 | `execMode=EVALSHA`, an empty sha cache, a server that replies `-NOSCRIPT …` to the first `EVALSHA` then succeeds | `LuaRedisScriptExecutor.execute(...)` | on `NOSCRIPT`, one `SCRIPT LOAD <body>` is issued, the sha is (re)cached per `(connectionId, sha)`, and `EVALSHA` is retried once and succeeds; a second `NOSCRIPT` surfaces the error | §3.8 |
| TC-RO-1 | AC-9 | `readOnly=true` against a server whose `INFO server` reports `redis_version:6.2.0` | `LuaRedisScriptExecutor.execute(...)` | fails fast with `RespException.ServerVersion` whose message names the required version (Redis 7 / Valkey); no `EVAL_RO` is sent | §3.8 |
| TC-INT-1 | AC-10 | dockerized `redis:8` **and** `valkey/valkey:8` | `RedisIntegrationTest` runs a small script via EVAL, EVALSHA, and `_RO` against each flavor | each run returns the expected reply on both flavors; the read-only run succeeds against v7+/v8 servers | impl-plan Phase 6 |

### AC → TC coverage matrix

| AC | Requirement | Test Case(s) |
|----|-------------|--------------|
| AC-1 | RESP client (framing, timeouts, cancellable, no EDT I/O) | TC-RESP-1..4, TC-TIMEOUT-1, TC-CANCEL-1 |
| AC-2 | Connection settings UI + PasswordSafe + Test Connection | TC-CONN-1, TC-CONN-2 (Test Connection UX: human checklist §1) |
| AC-3 | Launch-local (binary + Docker, error if neither) | TC-LAUNCH-1, TC-LAUNCH-2, TC-LAUNCH-3 |
| AC-4 | `LuaRedisRunConfiguration` persisted fields + checkConfiguration | TC-RC-1 |
| AC-5 | Run-config producer (target-gated) | TC-PROD-1 |
| AC-6 | Console reply tree + error class | TC-CON-1, TC-CON-2 |
| AC-7 | Error-link filter (`user_script:N`, 1→0-based) | TC-CON-3 |
| AC-8 | SCRIPT LOAD sha cache + NOSCRIPT fallback | TC-SHA-1 |
| AC-9 | Read-only `_RO` + Redis < 7 fail-fast | TC-RO-1 |
| AC-10 | Unit + Docker integration tests | TC-RESP-1..4, TC-INT-1 |
