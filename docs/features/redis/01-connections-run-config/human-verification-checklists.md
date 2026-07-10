---
id: "REDIS-01-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "REDIS-01"
folders:
  - "[[features/redis/01-connections-run-config/requirements|requirements]]"
---

# Verification Checklists: REDIS-01 — Connections & Script Run Configuration

Manual, human-run scenarios that confirm REDIS-01 works in a real IDE — the behaviors the
automated tests ([implementation-plan.md](implementation-plan.md) Verification Tasks) cannot
easily cover (live sockets, real Docker, the settings UI, editor gestures, console rendering).
Each scenario is reproducible from a clean state with an explicit expected result. Scenario
areas map to the `Must` acceptance criteria in [requirements.md](requirements.md) (AC-1..AC-9).

## 1. Connection Settings & Test Connection (AC-2)

### Scenario 1.1: Create a connection and Test Connection succeeds
- **Setup**: a Lua project; a reachable Redis 8 server on `127.0.0.1:6379` (no auth).
- **Steps**:
  1. Open Settings | Languages & Frameworks | Lua | Redis Connections.
  2. Add a connection: name `local`, host `127.0.0.1`, port `6379`, TLS off, db `0`.
  3. Click **Test Connection**.
- **Expected**: a success message names the server **flavor** (Redis) and **version** (e.g. `7.4.0`/`8.x`),
  obtained via `HELLO`/`INFO`. No EDT freeze during the probe.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Credentials persist in PasswordSafe, not in project XML
- **Setup**: a Redis server requiring AUTH (`requirepass` or an ACL user).
- **Steps**:
  1. Create a connection with username + password; save.
  2. Click **Test Connection** — verify success.
  3. Inspect `.idea/lunar-redis.xml` in the project.
- **Expected**: Test Connection authenticates and reports flavor/version; the password does **not**
  appear in `lunar-redis.xml` (it is in `PasswordSafe`, keyed by connection id).
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Handshake: TLS & AUTH (AC-1, AC-2)

### Scenario 2.1: TLS handshake
- **Setup**: a Redis/Valkey server with TLS enabled and a trusted cert.
- **Steps**:
  1. Create a connection with **TLS on** against the TLS port.
  2. Click **Test Connection**.
- **Expected**: the connection is established over TLS (`SSLSocketFactory` path) and reports
  flavor/version; a cert/handshake failure produces a clear error, not a stack trace in the UI.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: AUTH + RESP3/RESP2 negotiation
- **Setup**: (a) a Redis 8 server (RESP3-capable) with AUTH; (b) a Redis 5/managed server that
  rejects `HELLO 3`.
- **Steps**:
  1. Test Connection against (a); then against (b).
- **Expected**: (a) negotiates RESP3 via `HELLO 3 AUTH …`; (b) falls back to RESP2 `AUTH` and still
  connects. A wrong password surfaces the server error (`WRONGPASS`/`NOAUTH`) verbatim, not a crash.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Timeout & Cancellation UX (AC-1)

### Scenario 3.1: Connect/read timeout is reported, not hung
- **Setup**: a host:port that accepts TCP but never replies (e.g. a black-hole port), or an
  unreachable host.
- **Steps**:
  1. Create a connection to the unreachable/black-hole endpoint.
  2. Click **Test Connection** (or run a Redis Script against it).
- **Expected**: after the configured timeout, the UI reports a timeout error (mapped from
  `RespException.Timeout`); the IDE never hangs and the EDT stays responsive throughout.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Cancelling an in-flight run aborts the connect/command
- **Setup**: a slow/unreachable server, or a long-running script.
- **Steps**:
  1. Run a Redis Script configuration against the slow endpoint.
  2. While it is connecting/executing, cancel the run (Stop button / cancel the progress).
- **Expected**: the run aborts promptly (the cancelled `ProgressIndicator` interrupts the in-flight
  connect/command); the `RespClient` is disposed and any session-launched server is stopped. No
  orphaned connection or leaked progress.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Launch-local: Binary & Docker (AC-3)

### Scenario 4.1: Launch local binary, run, and teardown
- **Setup**: a `redis-server` binary registered under the Lua Toolchain settings; no server
  currently running on the chosen port.
- **Steps**:
  1. Create a connection with provisioning **Launch local (binary)**.
  2. Run a Redis Script against it.
  3. After the run finishes, check for leftover `redis-server` processes.
- **Expected**: the server starts before the run, the script executes, and the server is
  **terminated after** the run — no orphaned `redis-server` process remains.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Launch Docker container, run, and teardown
- **Setup**: Docker available on PATH; image `redis:8` pullable.
- **Steps**:
  1. Create a connection with provisioning **Docker (`redis:8`)**.
  2. Run a Redis Script against it.
  3. After the run, run `docker ps -a` to check for the container.
- **Expected**: a container starts (`docker run --rm -d -p <freePort>:6379 redis:8`), the script
  runs against the mapped port, and the container is removed on teardown (`--rm` + explicit stop) —
  no leftover container.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.3: Neither Docker nor a binary available
- **Setup**: no `redis-server`/`valkey-server` binary registered and `docker` not on PATH.
- **Steps**:
  1. Create a Launch-local connection and run a Redis Script.
- **Expected**: the run fails fast with a clear error naming **both** the missing binary (with the
  Settings path to register one) and Docker as alternatives — not a generic exception.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Run Configuration & Editor Producer (AC-4, AC-5)

### Scenario 5.1: Editor-menu producer appears only on a Redis/Valkey target
- **Setup**: a `.lua` file open in the editor; project target set to **Redis** (or Valkey).
- **Steps**:
  1. Right-click in the editor (or use Run | Run…) on the `.lua` file.
  2. Switch the project target to plain **Lua** and repeat.
- **Expected**: with a Redis/Valkey target, a **Run 'Redis Script'** action is offered and creates a
  `LuaRedisRunConfiguration` for the current file; with a plain Lua target the Redis producer does
  **not** appear (the standard Lua run config is unaffected).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 5.2: checkConfiguration validation
- **Setup**: an edited (unsaved-to-server) Redis Script run configuration.
- **Steps**:
  1. Clear the script path; try to run.
  2. Restore the path but clear the connection; try to run.
  3. Select FCALL mode; try to run.
- **Expected**: each invalid state blocks the run with a specific message ("Script path is not
  defined", "No Redis connection selected", "FCALL mode is not available until REDIS-05").
- **Result**: ⬜ Pass / ⬜ Fail

## 6. Console: Reply Tree & Error-Link (AC-6, AC-7)

### Scenario 6.1: Reply rendered as an expandable tree
- **Setup**: a connection to a live server; a script returning a nested reply, e.g.
  `return {1, {'a','b'}, redis.status_reply('OK')}`.
- **Steps**:
  1. Run the Redis Script configuration.
- **Expected**: the run console shows the reply as an **expandable tree** — nested arrays/maps are
  expandable nodes, scalar replies render inline; a `WRONGTYPE`/`ERR` error shows the server error
  **class** tag.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 6.2: Error-link click jumps to the script line
- **Setup**: a script that raises a server-side error carrying a `user_script:<N>` reference, e.g. a
  deliberate `redis.call('INCR')` arity error or `error('boom')` on a known line.
- **Steps**:
  1. Run it; locate the `user_script:<N>` (or `@user_script: <N>`) reference in the console.
  2. Click the hyperlink.
- **Expected**: the editor opens the run's script file at line **N** (1-based server line correctly
  mapped to the 0-based editor line); an unresolvable file simply produces no link (no error).
- **Result**: ⬜ Pass / ⬜ Fail

## 7. Execution Modes (AC-8, AC-9)

### Scenario 7.1: SCRIPT LOAD + EVALSHA with NOSCRIPT recovery
- **Setup**: a connection; a run config in **EVALSHA** mode.
- **Steps**:
  1. Run the script (loads + caches the SHA).
  2. `SCRIPT FLUSH` on the server out-of-band, then run again.
- **Expected**: the first run loads and caches the SHA; after `SCRIPT FLUSH` the second run recovers
  from `NOSCRIPT` by re-loading once and succeeds (no user-visible failure).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 7.2: Read-only toggle version gate
- **Setup**: (a) a Redis 8 server; (b) a Redis 6.x server; run config with **read-only** on.
- **Steps**:
  1. Run against (a); then against (b).
- **Expected**: (a) executes via `EVAL_RO`/`EVALSHA_RO` and returns a reply; (b) fails fast with a
  message naming the required server version (Redis 7 / Valkey), without sending `_RO`.
- **Result**: ⬜ Pass / ⬜ Fail
