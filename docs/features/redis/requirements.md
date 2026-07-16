---
id: "REDIS"
title: "REDIS: Redis & Valkey Integration"
type: "epic"
status: "done"
priority: "medium"
folders:
  - "[[features]]"
---

# Redis & Valkey Integration Requirements (`REDIS`)

Extend Lunar's existing Redis runtime target (TARGET epic: Redis 5/6/7+ stdlib stubs,
luacheck `--std redisN`) into an end-to-end Redis/Valkey Lua development loop: connections,
script Run configurations, debugging via the server's built-in Lua debugger (LDB), Valkey
as a first-class target, command-aware language-engine support, and a Redis Functions
workflow.

**Competitive rationale** (see [Marketplace & Competitive Analysis](../../plugin-feature-comparison.md)):
no IDE or editor plugin — JetBrains, VS Code, or otherwise — offers Redis Lua run
configurations or integrates the server-side LDB debugger. Combined with the existing Redis
stdlib target, this epic makes Lunar the only IDE with an end-to-end Redis/Valkey Lua story.

**Protocol grounding** (verified 2026-07-02): both Redis and Valkey ship the Lua debugger
(LDB) reachable over a plain RESP connection (`SCRIPT DEBUG YES|SYNC`, the protocol spoken
by `redis-cli --ldb` / `valkey-cli --ldb`). Valkey retains full Redis 7.2 script
compatibility and adds the `server.*` namespace plus `SERVER_NAME`/`SERVER_VERSION`/
`SERVER_VERSION_NUM` globals. References: [Redis Lua debugging](https://redis.io/docs/latest/develop/programmability/lua-debugging/),
[Valkey LDB](https://valkey.io/topics/ldb/), [Valkey Lua API](https://valkey.io/topics/lua-api/),
[Valkey migration notes](https://valkey.io/topics/migration/).

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| [`REDIS-01`](01-connections-run-config/requirements.md) | **Connections & Script Run Configuration** | **M** | **Not Implemented** | RESP client, server connections (remote / local binary / Docker), and a "Redis Script" run configuration (EVAL / EVALSHA / FCALL) with KEYS/ARGV inputs and a reply-tree console. |
| [`REDIS-02`](02-ldb-debugger/requirements.md) | **LDB Debug Adapter** | **M** | **Not Implemented** | XDebugger adapter speaking the server-side Lua debugger protocol: breakpoints, stepping, locals, watches, mid-debug Redis commands, fork vs sync sessions. |
| [`REDIS-03`](03-valkey-target/requirements.md) | **Valkey Runtime Target** | **S** | **Not Implemented** | Valkey platform entries in `PlatformVersionRegistry`, `server.*` stubs, flavor detection on connect, and a Redis↔Valkey portability inspection. |
| [`REDIS-04`](04-language-integration/requirements.md) | **Language-Engine Integration** | **S** | **Not Implemented** | `KEYS`/`ARGV` typing, `redis.call`/`pcall` command-name completion + arity validation from a bundled command spec, and script-sandbox inspections. |
| [`REDIS-05`](05-functions-workflow/requirements.md) | **Redis Functions Workflow** | **C** | **Not Implemented** | `#!lua name=lib` shebang support, typed `redis.register_function`, FUNCTION LOAD deploy mode, and a server functions panel. |
| [`REDIS-06`](06-sandbox-quickdoc-refinements/requirements.md) | **Sandbox & Quick-Doc Gating Refinements** | **C** | **Planned** | Two deferred REDIS-04 correctness refinements: side-effect-free local-binding resolution so `LuaRedisSandboxInspection` no longer false-flags a shadowed global, and a caret-on-STRING gate so `RedisCommandDocumentationTargetProvider` quick-doc only triggers on the command-name literal. |
| [`REDIS-07`](07-database-datasource-integration/requirements.md) | **Reuse an IntelliJ Database Redis Data Source** | **C** | **Planned** | Optional (`com.intellij.database`) integration: import an existing Redis data source's endpoint (host/port/db/auth/TLS/SSH-aware URL) + stored password into a native `LuaRedisServerConnection`, reusing the Database plugin's richer connection config without routing RESP/LDB through it. Spike-gated (DR-1/DR-2). |

---

## Execution order & dependencies

`REDIS-01` is the foundation (RESP client + connection model) and unblocks everything else.
`REDIS-02` builds the debug adapter on the REDIS-01 client. `REDIS-03` is small and can run
in parallel with REDIS-02 (only its flavor-detection criterion touches the client).
`REDIS-04` is independent of the connection stack (pure language engine) and can run any
time. `REDIS-05` depends on REDIS-01 (deploy mode) and benefits from REDIS-03/04 stubs.

## Non-goals

- Not a general Redis database client (key browser, data editing) — IntelliJ Database Tools
  covers that; Lunar integrates at the Lua-scripting seam only.
- No Redis cluster-aware script routing in the first iteration (single-node connections;
  cluster `EVAL` key-slot validation is future work).
- No RESP3 push/pubsub tooling.

## Cross-cutting engineering constraints

- The debug adapter and RESP client are written to the hardened patterns from
  [docs/review.md](../../review.md) — byte-accurate framing with explicit charsets, no
  blocking waits on the EDT, error propagation to the debug UI, no `!!` in payload parsing —
  not copied from the existing MobDebug adapter.
- All server I/O runs on pooled threads with cancellation; connections are `Disposable`
  and tied to the run/debug session lifecycle.
- Integration tests run against dockerized `redis:8` and `valkey/valkey:8` (the LDB wire
  behavior is defined by the redis-cli implementation, not a written spec — tests are the
  compatibility contract).
