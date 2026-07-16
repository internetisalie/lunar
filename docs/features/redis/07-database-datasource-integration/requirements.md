---
id: "REDIS-07"
parent_id: "REDIS"
type: "feature"
status: "planned"
priority: "low"
folders:
  - "[[features/redis/requirements|requirements]]"
title: "REDIS-07: Reuse an IntelliJ Database Redis Data Source"
---

# REDIS-07: Reuse an IntelliJ Database Redis Data Source

**Requirement**: When the paid-IDE `com.intellij.database` plugin is present, let the user
import an existing Redis **data source** (its host/port/db/auth/TLS/SSH definition) into a
native Lunar `LuaRedisServerConnection`, so Lunar scripts run against a connection the user
already configured once in the Database tool window — reusing its richer TLS-trust / SSH-tunnel
/ URL support without Lunar reimplementing them.
**Priority**: Could
**Status**: Planned (post-spike; core viability gated on DR-1/DR-2 — see
[risks-and-gaps.md](risks-and-gaps.md))

---

## Overview

Lunar owns its Redis/Valkey connection model (`LuaRedisServerConnection`, REDIS-01) and its own
RESP transport (`RespClient`). That model is deliberately minimal: TLS is bare system-default
trust, there is no SSH tunnel, no URL field, and no cluster support (see
[design.md §1 Current State](design.md)). The IntelliJ **Database** plugin, bundled in
GoLand / IDEA Ultimate / PyCharm Pro / CLion, already lets a user define a Redis data source with
full TLS trust config, SSH tunnelling, a `jdbc:redis://…` URL, and standalone/cluster modes.

This feature **borrows that connection definition** — it does **not** route RESP or the LDB
debugger through the Database plugin. An "Import from Database data source" action reads the
selected Redis `DbDataSource`, extracts its endpoint (host/port/db/user/TLS/SSH) and stored
password, and snapshots them into a native `LuaRedisServerConnection` + credential-store entry.
Everything downstream (the REDIS-01 run configuration, the REDIS-02 LDB debugger) then consumes
that native connection unchanged via `LuaRedisConnectionSettings.findById(id)`.

The capability is **optional and additive**: Lunar's native connection form stays the baseline
and the only path on Community/CE editions, which do not ship the Database plugin. All
`com.intellij.database` code lives in an optional plugin module (`lunar-database.xml`) that loads
only when the plugin is present, and no always-loaded code references a Database class.

Parent epic: [REDIS: Redis & Valkey Integration](../requirements.md). This feature fills a gap the
epic's existing non-goal never evaluated: the non-goal ("IntelliJ Database Tools covers [the
general client]", [../requirements.md](../requirements.md) Non-goals) argued only against Lunar
*building* a key browser — it never considered *reusing* a Database data source as a connection
source. That reuse is exactly this feature's scope.

## Scope

### In Scope
- An optional plugin module (`lunar-database.xml`) that loads only when `com.intellij.database`
  is present; `com.intellij.database` added to `platformBundledPlugins`.
- An "Import from Database data source…" action on the Redis Connections settings page
  ([`LuaRedisConnectionsConfigurable`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt))
  that opens a picker of the project's Redis data sources.
- Reading a Redis `DbDataSource`'s connection endpoint (host, port, database index, username,
  TLS on/off) and its stored password from the Database plugin's credential store.
- Parsing the data source's `jdbc:redis://…` URL when typed getters are insufficient.
- Snapshotting the extracted endpoint into a native `LuaRedisServerConnection`
  (`provisioning = Remote`) via `LuaRedisConnectionSettings.upsert(...)`, and the password into
  `LuaRedisCredentialStore.setPassword(...)`.
- Clean degradation when the plugin is absent (no action surfaces; no class-load failure).

### Out of Scope
- **Routing RESP / EVAL / FCALL or the LDB debugger through the Database plugin's JDBC Redis
  connection.** Lunar keeps its own raw RESP socket; the LDB debugger speaks the LDB wire
  protocol over RESP, which a JDBC console cannot carry. This is a hard architectural boundary.
- Being the only or required connection path (paid-IDE-only ⇒ strictly optional; native form
  remains the baseline).
- A Redis data browser / key editor (the Database tool window's job; the epic's existing
  non-goal).
- Reimplementing SSH-tunnel or TLS-trust config in Lunar's *own* connection dialog. This feature
  is the alternative to that work: reuse the Database data source instead of rebuilding it.
- A **live** reference to a Database data source (a connection that re-reads the data source on
  every run). Import is a one-time **snapshot** (see [design.md §9](design.md) for the trade-off).
  Re-import on change is future work, not this feature.
- Cluster-mode connections. Lunar's `RespClient` is single-node; a `jdbc:redis:cluster://` data
  source is detected and **rejected** with a clear message (not silently imported as node 1).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| REDIS-07-01 | **Optional-module isolation** | M | All `com.intellij.database` references live in the `lunar-database.xml` optional module; the plugin loads and all always-loaded code runs unchanged on a Community IDE and in CI where the Database plugin is absent. |
| REDIS-07-02 | **Redis data-source enumeration** | M | List the project's Database data sources filtered to Redis (`Dbms.getName() == "REDIS"`), by display name, for the import picker. Non-Redis data sources are excluded. |
| REDIS-07-03 | **Endpoint extraction** | M | From a selected Redis `LocalDataSource`, extract host, port, database index, username, and TLS on/off into a `RespEndpoint`-shaped result, parsing the `jdbc:redis://…` URL where typed getters are absent. |
| REDIS-07-04 | **Password extraction** | M | Read the data source's stored password (no modal prompt when a password is saved) via the Database credential API, or degrade to a documented re-prompt path when it is not readable. |
| REDIS-07-05 | **Import snapshot** | M | Persist the extracted endpoint as a native `LuaRedisServerConnection` (`provisioning = Remote`) via `LuaRedisConnectionSettings.upsert`, and the password via `LuaRedisCredentialStore.setPassword`, keyed by a fresh connection id. |
| REDIS-07-06 | **Cluster rejection** | M | A `jdbc:redis:cluster://` data source is not imported; the user sees a clear "cluster connections are not supported" message. |
| REDIS-07-07 | **Off-EDT I/O** | M | Data-source credential reads (which may touch PasswordSafe / disk) run off the EDT; the settings form marshals the result back on the EDT. |
| REDIS-07-08 | **SSH-tunnel notice** | S | When the imported data source defines an SSH tunnel, the import surfaces a notice that the tunnel is not carried into the native connection (documented limitation), rather than silently importing an unreachable host. |

## Detailed Specifications

### REDIS-07-02: Redis data-source enumeration
Redis is contributed to the Database plugin by the *redis dialect module* as a `Dbms` constant
`RedisDbms.REDIS` (grounded: `intellij.database.dialects.redis.jar` →
`com.intellij.database.dialects.redis.RedisDbms.REDIS`; registered `<dbms id="REDIS" .../>` in
`intellij.database.dialects.redis.xml`). To avoid a hard reference to the dialect module (which
may itself be absent even when the base Database plugin is present), a data source is classified
Redis by **string identity**: `localDataSource.getDbms().getName() == "REDIS"`. See
[design.md §3.1](design.md).

### REDIS-07-03: Endpoint extraction
The connection endpoint is read from `LocalDataSource` and, where necessary, from its
`getUrl()` string, whose exact grammar (from the bundled `redis-drivers.xml`) is specified in
[design.md §4.1](design.md). The extraction produces the same primitive fields Lunar's
`RespEndpoint` already carries: host, port, `tls: Boolean`, database, username. TLS is `true`
when the URL scheme is `jdbc:redis:s://` / `rediss` **or** the data source's SSL configuration is
enabled (`getSslCfg().isEnabled == true`). See [design.md §3.2](design.md).

### REDIS-07-04: Password extraction
The stored password is read via `DatabaseCredentials.getInstance().getPassword(dasDataSource)`
(returns a `com.intellij.credentialStore.OneTimeString`, or null when none is stored). No modal
prompt is shown for a saved password. When the password is absent or unreadable, the import still
proceeds with a null password and the user is told to set it in the native connection (DR-1
fallback). See [design.md §3.3](design.md).

### REDIS-07-06: Cluster rejection
A cluster data source is identified by a URL beginning `jdbc:redis:cluster://` (grammar in
[design.md §4.1](design.md)). On detection the import aborts for that data source with a
`Messages.showErrorDialog` message; no `LuaRedisServerConnection` is written.

## Behavior Rules
- **Additive, never destructive.** Import always creates a *new* `LuaRedisServerConnection` with a
  fresh `UUID` id; it never overwrites an existing native connection.
- **Absent plugin ⇒ absent feature.** With `com.intellij.database` not installed, the import
  action must not appear and no `com.intellij.database` class may be loaded (REDIS-07-01).
- **Snapshot semantics.** After import the native connection is fully independent of the data
  source; later edits to the data source do not propagate (documented in the import confirmation).
- **TLS is imported best-effort.** Lunar's `RespClient` uses system-default trust
  ([`RespClient.kt:152`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/resp/RespClient.kt)).
  A `tls = true` flag is imported, but a custom CA / client cert configured on the data source is
  **not** carried (documented limitation; cross-references the same `RespClient` gap REDIS-07 is
  the workaround for, not a fix of).

## Test Cases

Endpoint-parse logic is pure and unit-tested with fixture URL strings (no Database plugin needed
at test time). UI and live credential reads are VNC-verified (see
[human-verification-checklists.md](human-verification-checklists.md)).

| TC | Requirement | Input | Expected output |
|----|-------------|-------|-----------------|
| TC-1 | REDIS-07-03 | URL `jdbc:redis://localhost:6379/0` | `RespEndpoint(host="localhost", port=6379, tls=false, database=0, username=null, password=null)` |
| TC-2 | REDIS-07-03 | URL `jdbc:redis://alice:s3cr3t@redis.example:6380/3` | endpoint `host="redis.example", port=6380, database=3, username="alice"` (password from URL used only if credential store returns none) |
| TC-3 | REDIS-07-03 | URL `jdbc:redis://cache:6390` (no db segment) | `host="cache", port=6390, database=0` |
| TC-4 | REDIS-07-03 | URL `jdbc:redis://host:6379/0` with `getSslCfg().isEnabled == true` | endpoint `tls=true` |
| TC-5 | REDIS-07-03 | URL `jdbc:redis:s://host:6379/0` | endpoint `tls=true` |
| TC-6 | REDIS-07-06 | URL `jdbc:redis:cluster://n1:6379,n2:6379/0` | parse classified `cluster`; import rejected with error message; no connection persisted |
| TC-7 | REDIS-07-02 | data sources: one `Dbms.name=="REDIS"`, one `"POSTGRES"` | picker lists only the Redis one |
| TC-8 | REDIS-07-05 | import a Redis data source named "prod-cache" (host `r:6379`) | a new `LuaRedisServerConnection(name="prod-cache", host="r", port=6379, provisioning=Remote)` is present via `LuaRedisConnectionSettings.connections()`; password present in `LuaRedisCredentialStore.getPassword(id)` |
| TC-9 | REDIS-07-03 | URL with only host, no port `jdbc:redis://cache/0` | `port` defaults to 6379 |
| TC-10 | REDIS-07-01 | plugin built/run with `com.intellij.database` absent (CI profile) | plugin loads; no import action surfaces; no `NoClassDefFoundError` |

## See Also
- Design: [design.md](design.md)
- Implementation plan: [implementation-plan.md](implementation-plan.md)
- Risks & gaps (spike-first — read this first): [risks-and-gaps.md](risks-and-gaps.md)
- Human verification: [human-verification-checklists.md](human-verification-checklists.md)
