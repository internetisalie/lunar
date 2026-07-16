---
id: "REDIS-07-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "REDIS-07"
folders:
  - "[[features/redis/07-database-datasource-integration/requirements|requirements]]"
---

# Technical Design: REDIS-07 — Reuse an IntelliJ Database Redis Data Source

Realizes the acceptance criteria in [requirements.md](requirements.md).

> **SPIKE-FIRST — read [risks-and-gaps.md](risks-and-gaps.md) first.** The
> `com.intellij.database` API is **closed-source** (not in the local
> `~/Documents/src/lua/intellij-community` checkout); every Database symbol below is grounded to
> the **bundled `database-plugin` jars** in the GoLand 2026.1.3 sandbox (via `javap`), not to
> platform source. This design is the **spike-validated approach**: the concrete signatures are
> confirmed to *exist*, but two runtime behaviours (password readability without a modal —
> **DR-1**; the exact endpoint surfacing — **DR-2**) are only fully verified by running the spike.
> Assumptions those DRs retire are flagged **[SPIKE-ASSUMPTION]** inline, each with a documented
> fallback. Post-spike phases in [implementation-plan.md](implementation-plan.md) are conditional
> on DR-1/DR-2 passing.

## 1. Architecture Overview

### Current State
Lunar's connection model and transport:
- `LuaRedisServerConnection` — immutable value model: `id, name, host, port, tls: Boolean,
  database, username, provisioning`
  ([`LuaRedisServerConnection.kt:13`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisServerConnection.kt)).
  Password lives in `LuaRedisCredentialStore` (PasswordSafe), keyed by `id`
  ([`LuaRedisCredentialStore.kt:15`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisCredentialStore.kt)).
- `LuaRedisConnectionSettings` — project `PersistentStateComponent`; `upsert(conn)`, `findById(id)`,
  `connections()`
  ([`LuaRedisConnectionSettings.kt:19`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionSettings.kt)).
- `LuaRedisConnectionsConfigurable` — the settings page; a `ToolbarDecorator` list with add/remove
  and a detail form (Name/Host/Port/Use TLS/Username/Password/Database/Test Connection). It
  hardcodes `provisioning = Remote`
  ([`LuaRedisConnectionsConfigurable.kt:237`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt)).
- `RespEndpoint(host, port, tls, database, username, password)`
  ([`RespClient.kt:36`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/resp/RespClient.kt));
  TLS is bare `SSLSocketFactory.getDefault().createSocket()` — system-default trust only, no
  custom CA / client cert / SNI ([`RespClient.kt:152`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/resp/RespClient.kt)).

**Why insufficient:** the native form has no TLS-trust config, no SSH tunnel, no URL, no cluster.
Rather than rebuild those, this feature imports a Database data source that already has them.

### Prior Art in This Repo
Searched `src/main/kotlin/net/internetisalie/lunar/redis/` and `src/main/resources/META-INF/`:
- **Connection model / store / settings page** — the three files above. This design **extends**
  `LuaRedisConnectionsConfigurable` (adds one import action) and **reuses** `LuaRedisConnectionSettings`
  / `LuaRedisCredentialStore` unchanged as the import sink. It creates **no** parallel connection
  model.
- **Optional config-file modules** — `lunar-terminal.xml` and `lunar-makefile.xml`
  (`src/main/resources/META-INF/`), each wired via `<depends optional="true"
  config-file="…">…</depends>` in
  [`plugin.xml`](../../../../src/main/resources/META-INF/plugin.xml) lines 29 / 34. This design
  **follows that exact pattern** for `lunar-database.xml`. `LuaShellExecOptionsCustomizer`
  (`toolchain/terminal/`) is the reference for an optional-module extension class.
- **No existing `com.intellij.database` integration** exists (grep `com.intellij.database` over
  `src/main` → no hits). Greenfield for the DB seam.

### Target State
A new optional module contributes a single action to the existing Redis settings page. The action
enumerates Redis data sources, extracts one endpoint, and writes it through the *existing*
connection store. The always-loaded connection stack is untouched except for one small, reflectively
gated hook point on the settings page. Component sketch:

```
[settings page]  LuaRedisConnectionsConfigurable  ──(optional hook)──▶  LuaRedisDatabaseImportAction
                                                                              │ (optional module)
                        LuaRedisDataSourceReader ◀── DbPsiFacade / LocalDataSource / DatabaseCredentials
                                                                              │
                        LuaRedisJdbcUrlParser (pure, unit-tested)             │
                                                                              ▼
                        LuaRedisConnectionSettings.upsert  +  LuaRedisCredentialStore.setPassword
```

## 2. Core Components

All classes below except the two existing sinks are **[NEW]**. The reader/action classes live in a
new package `net.internetisalie.lunar.redis.database` and are referenced **only** from
`lunar-database.xml` (never from always-loaded `plugin.xml`), satisfying REDIS-07-01.

### 2.1 net.internetisalie.lunar.redis.database.LuaRedisJdbcUrlParser [NEW]
- **Responsibility**: pure parse of a `jdbc:redis[:s][:cluster]://…` URL into endpoint primitives.
  **No `com.intellij.database` dependency** — lives in the always-loadable code so it is
  unit-testable without the plugin (the only REDIS-07 class not gated behind the optional module).
- **Threading**: none (pure function).
- **Collaborators**: `RespEndpoint`
  ([`RespClient.kt:36`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/resp/RespClient.kt)).
- **Key API**:
  ```kotlin
  data class LuaRedisJdbcParse(val endpoint: RespEndpoint, val cluster: Boolean, val urlPassword: String?)

  object LuaRedisJdbcUrlParser {
      // Returns null when [url] is not a jdbc:redis URL at all.
      fun parse(url: String): LuaRedisJdbcParse?
  }
  ```
  Note: `endpoint.password` is always null here; the URL-embedded password (if any) is returned
  separately as `urlPassword` so the caller prefers the credential store (§3.3).

### 2.2 net.internetisalie.lunar.redis.database.LuaRedisDataSourceReader [NEW]
- **Responsibility**: enumerate Redis data sources and extract one endpoint + password. This is the
  **only** class that touches `com.intellij.database` types.
- **Threading**: `enumerate` may run on the EDT (fast, in-memory list). `readEndpoint` performs the
  credential read and is called **off the EDT** (REDIS-07-07).
- **Collaborators**: `com.intellij.database.psi.DbPsiFacade`,
  `com.intellij.database.psi.DbDataSource`, `com.intellij.database.dataSource.LocalDataSource`,
  `com.intellij.database.access.DatabaseCredentials`, `LuaRedisJdbcUrlParser` (§2.1).
- **Key API**:
  ```kotlin
  data class LuaRedisDataSourceRef(val displayName: String, val localDataSource: LocalDataSource)
  data class LuaRedisImportEndpoint(
      val name: String, val endpoint: RespEndpoint, val password: String?,
      val cluster: Boolean, val hasSshTunnel: Boolean,
  )

  class LuaRedisDataSourceReader(private val project: Project) {
      fun enumerateRedisDataSources(): List<LuaRedisDataSourceRef>   // §3.1
      fun readEndpoint(ref: LuaRedisDataSourceRef): LuaRedisImportEndpoint  // §3.2, §3.3 — off-EDT
  }
  ```

### 2.3 net.internetisalie.lunar.redis.database.LuaRedisDatabaseImportAction [NEW]
- **Responsibility**: the settings-page hook — show a picker of Redis data sources, run the reader
  off-EDT, reject clusters, and write the snapshot.
- **Threading**: invoked on the EDT; the credential read is dispatched to the project coroutine
  scope via `LunarCoroutineScopeService` (grounded: used at
  [`LuaRedisConnectionsConfigurable.kt:21`](../../../../src/main/kotlin/net/internetisalie/lunar/redis/connection/LuaRedisConnectionsConfigurable.kt)),
  with `withContext(Dispatchers.EDT)` to marshal the result back. Wraps user-visible work in
  `withBackgroundProgress` (the same idiom as the existing "Test Connection", `Configurable.kt:7`).
- **Collaborators**: `LuaRedisDataSourceReader` (§2.2), `LuaRedisConnectionSettings.upsert`,
  `LuaRedisCredentialStore.setPassword`, `com.intellij.openapi.ui.Messages`.
- **Key API**:
  ```kotlin
  class LuaRedisDatabaseImportAction {
      // Returns the id of the newly-created connection, or null if cancelled/aborted.
      suspend fun importInto(scope: LuaRedisImportSink): String?   // §3.4
  }
  ```
  `LuaRedisImportSink` is a tiny interface (`fun onImported(id: String)`) the settings page passes
  so the module never holds a hard `Configurable` reference; see §7 for the reflective wiring.

### 2.4 net.internetisalie.lunar.redis.connection.LuaRedisConnectionsConfigurable (EXISTING — extended)
- **Change**: add one "Import from Database data source…" button to the toolbar/detail area, gated
  on the optional module being loaded. The button is only added when
  `PluginManagerCore.getPlugin(PluginId.getId("com.intellij.database"))?.isEnabled == true`
  (grounded platform API), and it invokes the import action **through an
  `ExtensionPointName`-registered provider** so `Configurable` (always-loaded) never references a
  `com.intellij.database`-touching class directly. See §7.

## 3. Algorithms

### 3.1 Redis data-source classification (REDIS-07-02)
- **Input → Output**: `Project` → `List<LuaRedisDataSourceRef>`.
- **Steps**:
  1. `val sources = DbPsiFacade.getInstance(project).dataSources` (grounded:
     `DbPsiFacade.getDataSources(): List<DbDataSource>`).
  2. For each `DbDataSource ds`, obtain its `LocalDataSource`: `ds.delegate as? LocalDataSource`
     (`DbDataSource.getDelegate(): RawDataSource`; the concrete local data source implements it —
     **[SPIKE-ASSUMPTION]** confirmed by DR-2; fallback: `LocalDataSourceManager.getInstance(project)
     .dataSources` and match by `getUniqueId()`).
  3. Keep only those where `localDataSource.dbms.name == "REDIS"` (grounded:
     `LocalDataSource.getDbms(): com.intellij.database.Dbms`; `Dbms.getName(): String`;
     `RedisDbms.REDIS` registered as `<dbms id="REDIS">`). **String compare** deliberately — do
     **not** reference `RedisDbms.REDIS` (dialect-module class; may be absent).
  4. Map to `LuaRedisDataSourceRef(displayName = localDataSource.name, localDataSource)`.
- **Edge handling**: empty list when no Redis data sources ⇒ the picker shows an
  "No Redis data sources found" message and no import occurs.

### 3.2 Endpoint extraction (REDIS-07-03)
- **Input → Output**: `LocalDataSource` → `RespEndpoint` + cluster flag + ssh flag.
- **Steps**:
  1. `val url = localDataSource.url` (grounded: `LocalDataSource.getUrl(): String`).
  2. `val parse = LuaRedisJdbcUrlParser.parse(url)` (§4.1). If null, abort this data source with an
     "unrecognized Redis URL" message.
  3. Start from `parse.endpoint`. Override TLS to `true` if **either** `parse.endpoint.tls` is true
     **or** `localDataSource.sslCfg?.isEnabled == true` (grounded:
     `LocalDataSource.getSslCfg(): DataSourceSslConfiguration`; `.isEnabled`). This is the
     `tls: Boolean` value only — custom-CA config is intentionally dropped (documented limitation).
  4. Override `username` with `localDataSource.username` when non-blank (grounded:
     `LocalDataSource.getUsername(): String`); else keep the URL-parsed user.
  5. `hasSshTunnel = localDataSource.sshConfiguration?.isEnabled == true` (grounded:
     `LocalDataSource.getSshConfiguration(): DataSourceSshTunnelConfiguration`).
- **Rules / edge handling**: missing port ⇒ 6379 (URL default, §4.1). Missing db segment ⇒ 0.

### 3.3 Password extraction (REDIS-07-04)
- **Input → Output**: `LocalDataSource` (as `DasDataSource`) → `String?`.
- **Steps**:
  1. `val secret = DatabaseCredentials.getInstance().getPassword(localDataSource)` (grounded:
     `DatabaseCredentials.getInstance(): DatabaseCredentials`;
     `getPassword(DasDataSource): OneTimeString`; `LocalDataSource` **is** a `DasDataSource` via
     `DbDataSource extends DasDataSource` — **[SPIKE-ASSUMPTION]** that `LocalDataSource` also
     satisfies the `DasDataSource` receiver, confirmed by DR-1).
  2. Return `secret?.toString(true)` (OneTimeString consumed once), or null. **No modal is shown.**
  3. Fallback if `getPassword` throws / returns null / requires interaction (**DR-1 outcome**):
     return null, and §3.4 shows a "set the password in the imported connection" notice.
- **Rules**: never log the password; the `OneTimeString` is read exactly once and not retained.

### 3.4 Import flow (REDIS-07-05, -06)
- **Input → Output**: chosen `LuaRedisDataSourceRef` → new connection id (or null).
- **Steps**:
  1. Off-EDT: `val import = reader.readEndpoint(ref)`.
  2. If `import.cluster`: `Messages.showErrorDialog("Cluster Redis connections are not
     supported…")`; return null (REDIS-07-06). **No** connection written.
  3. If `import.hasSshTunnel`: append an SSH-tunnel-not-carried notice to the confirmation
     (REDIS-07-08) — non-blocking.
  4. `val id = UUID.randomUUID().toString()` (matches `LuaRedisConnectionDraft.newDefault`,
     `Configurable.kt:257`).
  5. Build `LuaRedisServerConnection(id, name = import.name, host, port, tls, database, username,
     provisioning = LuaRedisProvisioning.Remote)` from `import.endpoint`.
  6. `LuaRedisConnectionSettings.getInstance(project).upsert(connection)`.
  7. `LuaRedisCredentialStore.setPassword(id, import.password)` (no-op when null).
  8. `onImported(id)` so the settings page selects the new row.
- **Rules**: import always creates a fresh id (never overwrites). Steps 6–7 run on the EDT after the
  off-EDT read (state-component writes are cheap, non-I/O).

## 4. External Data & Parsing

### 4.1 `jdbc:redis://…` URL (from the bundled `redis-drivers.xml`)
The URL templates ship in `intellij.database.dialects.redis.jar` →
`databaseDrivers/redis-drivers.xml` (grounded, extracted verbatim):

```
standalone: jdbc:redis://[[{user}:]{password}@]{host::localhost}[:{port::6379}][/{database:.../0}?][?<params>]
cluster:    jdbc:redis:cluster://[[{user}:]{password}@]<host[:port],…>[/{database}?][?<params>]
```

Observed concrete forms (what `getUrl()` returns after the user fills the dialog):
- `jdbc:redis://localhost:6379/0`
- `jdbc:redis://alice:s3cr3t@redis.example:6380/3`
- `jdbc:redis:s://host:6379/0`   (TLS scheme variant)
- `jdbc:redis:cluster://n1:6379,n2:6379/0`

- **Parse strategy** (`LuaRedisJdbcUrlParser.parse`, deterministic; no regex backtracking):
  1. If the string does not start with `jdbc:redis`, return null.
  2. Strip the `jdbc:redis` prefix. The remaining scheme modifiers are read in order:
     `:s` ⇒ `tls = true`; `:cluster` ⇒ `cluster = true`. Then require `://`.
  3. Split the authority off at the first `/` after `://` (or end of string) → `authority`,
     optional `pathAndQuery`.
  4. In `authority`, if it contains `@`, split at the **last** `@`: left = `user[:password]`,
     right = `hostport`. `user` = before first `:`; `urlPassword` = after it (may be null).
     For cluster, `hostport` may be a comma list — take the **first** node only (Lunar is
     single-node; the cluster flag already triggers rejection at §3.4).
  5. `hostport`: host = before last `:`; port = after it as Int, default **6379** if absent/unparsable.
  6. `pathAndQuery`: strip a leading `/`, take up to `?`, parse as Int database index, default **0**.
- **Maps to**: `RespEndpoint(host, port, tls, database, username = user, password = null)` +
  `cluster` + `urlPassword`.
- **Failure handling**: any structural failure (no `://`, empty host) ⇒ `parse` returns null ⇒
  §3.2 step 2 aborts that import with a user-visible message; never throws to the EDT.

## 5. Data Flow

### Example 1: Import a standalone TLS data source
User opens **Settings ▸ Languages ▸ Lua ▸ Redis Connections**, clicks
**Import from Database data source…** ▸ picks "prod-cache". The action calls
`reader.enumerateRedisDataSources()` (already done to build the picker) then off-EDT
`reader.readEndpoint(ref)`: `getUrl()` = `jdbc:redis:s://cache.prod:6379/0`, `getSslCfg().isEnabled`
= true, `getUsername()` = "default", `DatabaseCredentials.getPassword` = "hunter2". Result
`RespEndpoint(host="cache.prod", port=6379, tls=true, database=0, username="default")`, password
"hunter2". Back on the EDT: a new `LuaRedisServerConnection` id `u1` is `upsert`-ed;
`LuaRedisCredentialStore.setPassword("u1","hunter2")`; the row is selected. The user then runs a
Redis Script run config against it — REDIS-01/02 consume it via `findById("u1")` unchanged.

### Example 2: Cluster data source rejected
Picked data source `getUrl()` = `jdbc:redis:cluster://a:6379,b:6379/0`. `parse.cluster == true` ⇒
`Messages.showErrorDialog`; nothing persisted.

### Example 3: Database plugin absent (Community IDE / CI)
`lunar-database.xml` is not loaded; the `redisDataSourceImporter` EP has no registered provider;
`LuaRedisConnectionsConfigurable` sees an empty EP list and does **not** add the import button. No
`com.intellij.database` class is loaded (REDIS-07-01 / TC-10).

## 6. Edge Cases
- **Password requires interaction (DR-1 negative).** `getPassword` may not return a saved secret
  without a connection attempt. Fallback: import with null password + notice (§3.3 step 3).
- **`delegate` is not a `LocalDataSource`.** Some data sources are not local (rare for Redis).
  `as? LocalDataSource` yields null ⇒ that entry is skipped in enumeration (§3.1 step 2).
- **URL-only connection type** (dialog "URL only"): `getUrl()` still returns a `jdbc:redis://` URL,
  so §4.1 parses it identically.
- **Duplicate import.** Importing the same data source twice creates two independent connections
  (fresh ids); acceptable — the user can delete one.
- **Non-numeric port / db in URL.** Defaulted to 6379 / 0 (§4.1 steps 5–6), never throws.

## 7. Integration Points

`platformBundledPlugins` in `gradle.properties` gains `com.intellij.database` (grounded: the
property is consumed by `build.gradle.kts:74` `bundledPlugins(...)`). `plugin.xml` gains the
optional dependency (following the `lunar-terminal.xml` pattern at `plugin.xml:29`):

```xml
<!-- plugin.xml (always-loaded) -->
<depends optional="true" config-file="lunar-database.xml">com.intellij.database</depends>

<!-- always-loaded EP so the settings page can reach the optional importer without a hard ref -->
<extensionPoints>
  <extensionPoint name="redisDataSourceImporter"
                  interface="net.internetisalie.lunar.redis.database.LuaRedisDataSourceImporter"
                  dynamic="true"/>
</extensionPoints>
```

```xml
<!-- lunar-database.xml (loaded only when com.intellij.database is present) -->
<idea-plugin>
  <extensions defaultExtensionNs="net.internetisalie.lunar">
    <redisDataSourceImporter
        implementation="net.internetisalie.lunar.redis.database.LuaRedisDatabaseImporter"/>
  </extensions>
</idea-plugin>
```

- `LuaRedisDataSourceImporter` (always-loaded **interface**, `redis/database/`) declares
  `fun canImport(project): Boolean` and `suspend fun importInto(project, sink): String?`. Its **only**
  implementation, `LuaRedisDatabaseImporter`, lives behind the optional module and is the class that
  touches `com.intellij.database`. `LuaRedisConnectionsConfigurable` queries
  `LuaRedisDataSourceImporter.EP_NAME.extensionList.firstOrNull()`; when present it adds the import
  button (REDIS-07-01 — the interface has no `com.intellij.database` types in its signature).
- No new settings keys; the import writes through the existing `lunar-redis.xml`
  `PersistentStateComponent` and PasswordSafe.
- Minimum since-build: pin to the platform baseline already required by the plugin (the Database
  API is unversioned/closed — DR-4 records the stability caveat).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| REDIS-07-01 | M | §2.4, §7 (optional module + always-loaded EP interface) |
| REDIS-07-02 | M | §2.2, §3.1 |
| REDIS-07-03 | M | §2.1, §3.2, §4.1 |
| REDIS-07-04 | M | §3.3 |
| REDIS-07-05 | M | §3.4 |
| REDIS-07-06 | M | §3.4 step 2, §4.1 |
| REDIS-07-07 | M | §2.2, §2.3 (off-EDT read) |
| REDIS-07-08 | S | §3.2 step 5, §3.4 step 3 |

## 9. Alternatives Considered
- **(A) Import-snapshot (chosen).** One-time copy into a native `LuaRedisServerConnection`.
  Pro: no live coupling to the Database plugin's credential lifecycle; the native connection is
  self-contained and works exactly like a hand-typed one downstream; simplest to make truly optional.
  Con: does not track later edits to the data source (documented; re-import re-syncs).
- **(B) Live reference by data-source id.** A `LuaRedisProvisioning`-like source that re-reads the
  data source on each run. Rejected for the MVP: it couples every run to a closed-source credential
  read (re-triggering DR-1 at run time on a background/debug thread), and it leaks the "paid IDE
  only" boundary into REDIS-01/02 run paths. Parked as future work in
  [risks-and-gaps.md](risks-and-gaps.md).
- **String Dbms match vs. `RedisDbms.REDIS` ref.** String match chosen to avoid a hard reference to
  the *dialect* module, which is a distinct jar and could be disabled independently of the base
  Database plugin (§3.1).

## 10. Open Questions

_None — feature has cleared the planning bar._

## 11. Spike-gated unknowns (not open questions)

The two runtime unknowns (DR-1 credential readability, DR-2 endpoint surfacing) are **not** open
design questions: they are tracked as gating de-risking tasks in
[risks-and-gaps.md](risks-and-gaps.md), and each already has a documented fallback folded into §3.
Post-spike phases in [implementation-plan.md](implementation-plan.md) are conditional on their outcome.
