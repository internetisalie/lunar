---
id: "REDIS-01-DESIGN"
title: "Technical Design"
type: "design"
status: "todo"
parent_id: "REDIS-01"
folders:
  - "[[features/redis/01-connections-run-config/requirements|requirements]]"
---

# Technical Design: REDIS-01 — Connections & Script Run Configuration

Realizes every acceptance criterion in [requirements.md](requirements.md). All symbols named
below are grounded to `file:line` in this repo or explicitly marked **[NEW]**. Package root is
`net.internetisalie.lunar` (verified: `src/main/kotlin/net/internetisalie/lunar/`). New code
lives under `net.internetisalie.lunar.redis` (**[NEW]** package) except run-configuration classes,
which follow the `run/test` and `rocks/run` sibling-package convention and live under
`net.internetisalie.lunar.redis.run`.

## 1. Architecture Overview

### Current State (Prior Art in This Repo)

Grounded searches (real, with `file:line`):

- **Run configurations** — three existing families, all `RunConfigurationBase<Options>` +
  `ConfigurationTypeBase` + `ConfigurationFactory` + `SettingsEditor`, options persisted via
  `RunConfigurationOptions` `StoredProperty` delegates:
  - `run/test/LuaTestRunConfiguration.kt:42-322` (`LuaTestRunConfigurationType`, `…Factory`,
    `…Options`, `LuaTestRunConfiguration`, `LuaTestSettingsEditor`).
  - `rocks/run/LuaRocksRunConfiguration.kt:54-283` (same shape; `getState` returns an inline
    `CommandLineState`).
  - `run/LuaRunConfiguration.kt` (standard Lua run config; registered
    `plugin.xml:466-467`).
  - **This design EXTENDS the pattern** (a fourth configuration type) — it does **not** replace
    any of them. The Redis producer is additive (see §7, RISK-R12).
- **Run-configuration producer** — `run/test/LuaTestRunConfigurationProducer.kt:17` extends
  `LazyRunConfigurationProducer<LuaTestRunConfiguration>`; registered
  `plugin.xml:470-471`. REDIS-01's producer follows this exact shape.
- **Credential storage** — `rocks/publish/LuaRocksApiKeyStore.kt:20-51` is the reference
  `PasswordSafe` pattern (`CredentialAttributes` + `generateServiceName(SUBSYSTEM, key)`);
  REDIS-01's `LuaRedisCredentialStore` **[NEW]** mirrors it (this is the "ROCKS-06 pattern" the
  requirements name).
- **Toolchain resolution (server binary)** — `toolchain/resolve/LuaToolResolver.kt:17-156`
  (`resolve(project, kindId)`, `getInstance()`); tool kinds declared in
  `toolchain/registry/LuaToolKindRegistry.kt` (`id = "lua"|"luajit"|"tarantool"|"luarocks"|…`,
  `LuaToolKind` model `toolchain/model/LuaToolKind.kt:6-14`, `Capability` enum lines 18-25).
  **No `redis-server`/`valkey-server` kind exists** — grep of `id = "` in the registry lists
  only the eight kinds above → REDIS-01 adds two **[NEW]** kinds (§2.9).
- **Project target** — `platform/LuaPlatform.kt` has `REDIS("Redis", "redis")` (line verified;
  `VALKEY` is REDIS-03, not this feature). Current target read via
  `LuaProjectSettings.getInstance(project).state.getTarget().platform`
  (`settings/LuaProjectSettings.kt:90` `getTarget(): Target`, `:140-144` `getInstance`;
  `platform/target/Target.kt:16-18` `data class Target(platform, version)`).
- **Coroutine scope** — `util/LunarCoroutineScopeService.kt:18-23`
  (`@Service(PROJECT)`, `val scope: CoroutineScope`, `getInstance(project)`); DBGp transport
  `run/LuaDebugConnection.kt` is the reference reader-loop/`Mutex`/`CompletableDeferred` pattern
  the RESP client mirrors (but shares **no code** — RISK-R09).
- **Console hyperlink filter** — grep for `com.intellij.execution.filters.Filter` /
  `OpenFileHyperlinkInfo` in `src/main/kotlin` returns **no hits** → the error-line filter is
  **[NEW]** (built on the platform `Filter` API, §2.7).
- **Reply-tree console** — no existing RESP/JSON tree console; the tree component is **[NEW]**
  (built on platform `com.intellij.ui.treeStructure.Tree`, §2.6).

### Target State

```
LuaRedisRunConfiguration (run config)
  └─ getState() → LuaRedisRunProfileState (RunProfileState)
        ├─ resolves LuaRedisServerConnection from LuaRedisConnectionSettings (project svc)
        ├─ starts session server if provisioning ≠ REMOTE (LuaRedisServerLauncher)
        ├─ opens RespClient (pooled coroutine on session childScope)
        ├─ executes script per LuaRedisExecMode → RespValue reply
        └─ builds ConsoleView: RespReplyTreeConsole + LuaRedisErrorLinkFilter
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.redis.resp.RespValue` **[NEW]**
- **Responsibility**: Immutable sealed model of a decoded RESP2/RESP3 reply.
- **Threading**: Pure data — thread-agnostic.
- **Collaborators**: produced by `RespCodec` (§2.3); consumed by `RespReplyTreeModel` (§2.6).
- **Key API**:
  ```kotlin
  sealed interface RespValue {
      data class Simple(val text: String) : RespValue                 // +OK\r\n  and RESP3 =…\r\n
      data class Error(val klass: String, val message: String) : RespValue  // -ERR msg / -WRONGTYPE msg
      data class Integer(val value: Long) : RespValue                 // :123\r\n
      data class Bulk(val bytes: ByteArray?) : RespValue {            // $len\r\n…  ($-1 → bytes=null)
          fun asString(): String? = bytes?.toString(Charsets.UTF_8)
      }
      data class Array(val items: List<RespValue>?) : RespValue       // *len\r\n…  (*-1 → items=null)
      data class Map(val entries: List<Pair<RespValue, RespValue>>) : RespValue // RESP3 %len
      data class Double(val value: kotlin.Double) : RespValue         // RESP3 ,3.14
      data class Bool(val value: Boolean) : RespValue                 // RESP3 #t / #f
      object Null : RespValue                                         // RESP3 _\r\n
  }
  ```
  `Error.klass` = first whitespace-delimited token of the error line (§3.4); `message` = remainder.

### 2.2 `net.internetisalie.lunar.redis.resp.RespCodec` **[NEW]**
- **Responsibility**: Byte-accurate RESP2/RESP3 encode (`encodeCommand`) and decode (`decode`).
- **Threading**: Called only on the pooled reader coroutine (never EDT). Pure/stateless object.
- **Collaborators**: `RespClient` (§2.3) feeds it a buffered `InputStream`.
- **Key API**:
  ```kotlin
  object RespCodec {
      fun encodeCommand(args: List<ByteArray>): ByteArray            // RESP array-of-bulk (§3.2)
      fun decode(input: PushbackInputStream): RespValue              // one reply (§3.3)
  }
  ```
  Encoding is always RESP2 array-of-bulk-strings (accepted by every server regardless of the
  negotiated reply protocol). Decoding dispatches on the first byte and covers the RESP3 markers
  in §2.1 (§3.3).

### 2.3 `net.internetisalie.lunar.redis.resp.RespClient` **[NEW]**
- **Responsibility**: One TCP (optionally TLS) connection to a server; sends commands, reads
  replies, handles `HELLO`/`AUTH`/`SELECT` handshake. `Disposable`.
- **Threading**: All I/O on the caller's pooled coroutine; `command()` is `suspend`. **No EDT I/O**
  (contract §1). Reader uses a raw `InputStream`/`PushbackInputStream` (explicit bytes → UTF-8),
  **not** `BufferedReader.readLine` (RISK-R09).
- **Collaborators**: `RespCodec` (§2.2), `LuaRedisServerConnection` (§2.4) for endpoint/TLS/auth,
  `ProgressIndicator` for cancellation.
- **Key API**:
  ```kotlin
  class RespClient private constructor(
      private val socket: Socket,
      private val handshake: RespProtocol,  // RESP2 | RESP3
  ) : Disposable {
      suspend fun command(args: List<ByteArray>): RespValue
      suspend fun command(vararg args: String): RespValue      // UTF-8 convenience
      val protocol: RespProtocol
      override fun dispose()                                    // closes socket
      companion object {
          // Phase 2 shipped an endpoint-typed seam (below) rather than taking a LuaRedisServerConnection
          // directly, so Phase 2 does not forward-depend on the unbuilt Phase-3 connection/credential
          // types. Phase 3 adds the thin connection→endpoint adapter (LuaRedisServerConnection.toEndpoint,
          // resolving the secret from LuaRedisCredentialStore), so the caller-side ergonomics are unchanged.
          suspend fun open(endpoint: RespEndpoint, timeouts: RespTimeouts = RespTimeouts(), indicator: ProgressIndicator? = null): RespClient
      }
  }
  enum class RespProtocol { RESP2, RESP3 }
  data class RespTimeouts(val connectMs: Int = 5_000, val readMs: Int = 30_000)
  // Phase-2 value object carrying only the handshake primitives; Phase 3's
  // LuaRedisServerConnection.toEndpoint(password) folds a connection + its PasswordSafe secret into it.
  data class RespEndpoint(val host: String, val port: Int, val tls: Boolean = false, val database: Int = 0, val username: String? = null, val password: String? = null)
  ```
  `open` performs the handshake in §3.1; `command` writes `encodeCommand(args)` then returns
  `RespCodec.decode(...)`. Socket read timeout = `readMs`; a `SocketTimeoutException` from either the
  connect or a read maps to a `RespException.Timeout` (§2.10) — unit-tested by **TC-TIMEOUT-1**
  (feed a socket that throws `SocketTimeoutException`; assert `RespException.Timeout` with the op
  name). Between the write and the blocking read, and inside the length-prefixed read loop (§3.3),
  the client honours cancellation via `ProgressManager.checkCanceled()` / `ensureActive()`
  (contract §2); a cancelled `ProgressIndicator` aborts the in-flight connect/command — unit-tested
  by **TC-CANCEL-1** (§6, cancellation edge).

### 2.4 `net.internetisalie.lunar.redis.connection.LuaRedisServerConnection` **[NEW]**
- **Responsibility**: Immutable value model of one named server connection (persistable state,
  no secret).
- **Threading**: Pure data.
- **Collaborators**: stored in `LuaRedisConnectionSettings` (§2.5); credential fetched from
  `LuaRedisCredentialStore` (§2.9) at connect time (never persisted here).
- **Key API**:
  ```kotlin
  data class LuaRedisServerConnection(
      val id: String,                 // UUID, stable key for PasswordSafe + run-config ref
      val name: String,
      val host: String,
      val port: Int,
      val tls: Boolean,
      val database: Int,              // SELECT index; default 0
      val username: String?,          // ACL user; null → legacy AUTH (password only)
      val provisioning: LuaRedisProvisioning,
  )
  sealed interface LuaRedisProvisioning {
      object Remote : LuaRedisProvisioning
      data class LocalBinary(val toolKindId: String) : LuaRedisProvisioning  // "redis-server" | "valkey-server"
      data class Docker(val image: String) : LuaRedisProvisioning           // "redis:8" | "valkey/valkey:8"
  }
  ```

### 2.5 `net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings` **[NEW]**
- **Responsibility**: Project-level `PersistentStateComponent` holding the connection list; stored
  in `.idea/lunar-redis.xml` (VCS-shareable per skill Best Practices).
- **Threading**: State access on any thread; mutations fire a message-bus topic on EDT.
- **Collaborators**: mirrors `LuaProjectSettings` shape (`settings/LuaProjectSettings.kt:18`
  `PersistentStateComponent<State>`, `:140` `getInstance`).
- **Key API**:
  ```kotlin
  @State(name = "LunarRedisConnections", storages = [Storage("lunar-redis.xml")])
  @Service(Service.Level.PROJECT)
  class LuaRedisConnectionSettings : PersistentStateComponent<LuaRedisConnectionSettings.State> {
      class State { var connections: MutableList<ConnectionState> = mutableListOf() }
      class ConnectionState {                     // XML-serializable mirror of §2.4 (no secret)
          var id: String = ""; var name: String = ""; var host: String = "127.0.0.1"
          var port: Int = 6379; var tls: Boolean = false; var database: Int = 0
          var username: String? = null; var provisioningKind: String = "REMOTE"
          var toolKindId: String? = null; var dockerImage: String? = null
      }
      fun connections(): List<LuaRedisServerConnection>
      fun findById(id: String): LuaRedisServerConnection?
      fun upsert(connection: LuaRedisServerConnection)
      fun remove(id: String)
      companion object { fun getInstance(project: Project): LuaRedisConnectionSettings = project.service() }
  }
  ```

### 2.6 `net.internetisalie.lunar.redis.console.RespReplyTreeConsole` **[NEW]**
- **Responsibility**: Renders a `RespValue` reply as an expandable tree; wraps a text `ConsoleView`
  for scalar/error lines and process output.
- **Threading**: Tree/console mutations on EDT (`withContext(Dispatchers.EDT)`); building the
  `RespReplyTreeModel` off-EDT.
- **Collaborators**: platform `com.intellij.ui.treeStructure.Tree` (grounded, `ide-*.jar`) and a
  text `com.intellij.execution.ui.ConsoleView` built via
  `com.intellij.execution.filters.TextConsoleBuilderFactory.getInstance().createBuilder(project)`
  (both grounded, `execution-*.jar`); `RespReplyTreeModel` **[NEW]** (§3.5 shaping rules).
- **Key API**:
  ```kotlin
  class RespReplyTreeConsole(project: Project) : Disposable {
      val component: JComponent
      fun showReply(reply: RespValue)          // arrays/maps → tree nodes; scalar → inline row
      fun showError(error: RespValue.Error)    // "(error) <klass> <message>" line
      fun attachProcessOutput(handler: ProcessHandler)  // server stdout/stderr passthrough
      override fun dispose()
  }
  ```

### 2.7 `net.internetisalie.lunar.redis.console.LuaRedisErrorLinkFilter` **[NEW]**
- **Responsibility**: `com.intellij.execution.filters.Filter` that hyperlinks
  `user_script:<N>` / `@user_script: <N>` references to the run's script file at line N.
- **Threading**: `applyFilter` called by platform on a pooled thread; returns immutable `Result`.
- **Collaborators**: platform `Filter`, `OpenFileHyperlinkInfo`, the script `VirtualFile` captured
  by URL (contract §4: no hard `VirtualFile` field — store the URL string).
- **Key API**:
  ```kotlin
  class LuaRedisErrorLinkFilter(
      private val project: Project,
      private val scriptFileUrl: String,
  ) : Filter {
      override fun applyFilter(line: String, entireLength: Int): Filter.Result?  // §3.6
  }
  ```

### 2.8 `net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration` **[NEW]**
- **Responsibility**: The "Redis Script" run configuration — persists script path, connection id,
  exec mode, read-only flag, KEYS, ARGV; validates in `checkConfiguration`.
- **Threading**: EDT (editor/settings); `getState` returns the profile state (§2.11).
- **Collaborators**: extends `RunConfigurationBase<LuaRedisRunConfigurationOptions>` exactly like
  `LuaRocksRunConfiguration` (`rocks/run/LuaRocksRunConfiguration.kt:136-137`).
- **Key API**:
  ```kotlin
  class LuaRedisRunConfigurationType : ConfigurationTypeBase(
      ID, "Redis Script", "Run a Lua script against a Redis/Valkey server",
      NotNullLazyValue.createValue { LuaIcons.ROCKET }) {
      init { addFactory(LuaRedisRunConfigurationFactory(this)) }
      companion object { const val ID = "LuaRedisRunConfiguration"; fun getInstance(): LuaRedisRunConfigurationType }
  }
  class LuaRedisRunConfigurationOptions : RunConfigurationOptions() {
      // Each field is a `string()` StoredProperty delegate, exactly like
      // LuaRocksRunConfigurationOptions (rocks/run/LuaRocksRunConfiguration.kt:81-103) and
      // LuaTestRunConfigurationOptions (run/test/LuaTestRunConfiguration.kt:68-102) — the only
      // scalar delegate the repo's run configs use besides map().
      var scriptPath: String?  // string("")
      var connectionId: String? // string("")
      var execMode: String?    // string("EVAL") — "EVAL" | "EVALSHA" ; "FCALL" reserved (REDIS-05)
      var readOnly: String?    // string("false") — "true"|"false" (StoredProperty is String-typed, cf. myEnvironmentProcess)
      // KEYS / ARGV are List<String> at the model level but persisted as newline-delimited
      // string() StoredProperties (keysRaw / argvRaw). The repo has NO list() delegate — its
      // only collection delegate is map() (rocks/run/LuaRocksRunConfiguration.kt:93-96); rather
      // than misuse map(), each list persists as one `string("")` field joined on '\n'.
      var keysRaw: String?     // string("") — KEYS joined by '\n'
      var argvRaw: String?     // string("") — ARGV joined by '\n'
  }
  class LuaRedisRunConfiguration(project, factory, name)
      : RunConfigurationBase<LuaRedisRunConfigurationOptions>(project, factory, name) {
      var scriptPath: String?; var connection: LuaRedisServerConnection?  // resolves via §2.5 by id
      var execMode: LuaRedisExecMode; var readOnly: Boolean
      // keys/argv getters split options.keysRaw/argvRaw on '\n' (dropping blank lines);
      // setters join on '\n'. This is the List<String> ↔ string() bridge over the persisted
      // keysRaw/argvRaw StoredProperties above.
      var keys: List<String>; var argv: List<String>
      override fun checkConfiguration()                       // §3.7
      override fun getConfigurationEditor(): SettingsEditor<*> // LuaRedisSettingsEditor [NEW]
      override fun getState(executor, environment): RunProfileState // LuaRedisRunProfileState §2.11
  }
  enum class LuaRedisExecMode { EVAL, EVALSHA, FCALL }   // FCALL rejected in checkConfiguration until REDIS-05
  ```

### 2.9 `net.internetisalie.lunar.redis.connection.LuaRedisCredentialStore` **[NEW]** + server tool kinds
- **Responsibility**: AUTH password storage in `PasswordSafe`, keyed by connection `id`
  (mirrors `LuaRocksApiKeyStore` `rocks/publish/LuaRocksApiKeyStore.kt:20-51`).
- **Threading**: any thread (`PasswordSafe.instance` is thread-safe).
- **Key API**:
  ```kotlin
  object LuaRedisCredentialStore {
      const val SUBSYSTEM = "Lunar Redis"
      fun getPassword(connectionId: String): String?
      fun setPassword(connectionId: String, password: String?)   // blank → clears
  }
  ```
- **Tool kinds [NEW]**: two `LuaToolKind` entries added to `LuaToolKindRegistry`
  (`toolchain/registry/LuaToolKindRegistry.kt`), resolved via `LuaToolResolver.resolve(project, id)`:
  - `id = "redis-server"`, `binaryNames = listOf("redis-server")`, `probe` = `ProbeSpec(args=["--version"],
    versionRegex=Regex("""v=(\d[\w.]*)"""))`, `capabilities = emptySet()` (not a runtime).
  - `id = "valkey-server"`, `binaryNames = listOf("valkey-server")`, same probe shape
    (`valkey-server --version` prints `Valkey server v=…`). Valkey provisioning is REDIS-03's
    concern; the kind is registered here so LocalBinary can target it.

### 2.10 `net.internetisalie.lunar.redis.resp.RespException` **[NEW]**
- **Responsibility**: Typed failure surface for the client (no `!!`, contract §1).
- **Key API**:
  ```kotlin
  sealed class RespException(message: String, cause: Throwable? = null) : Exception(message, cause) {
      class Timeout(op: String) : RespException("Redis $op timed out")
      class Protocol(detail: String) : RespException("Malformed RESP reply: $detail")
      class Io(cause: Throwable) : RespException("Redis connection I/O error", cause)
      class ServerVersion(required: String) : RespException("This server does not support this operation (requires $required)")
  }
  ```

### 2.11 `net.internetisalie.lunar.redis.run.LuaRedisRunProfileState` **[NEW]**
- **Responsibility**: `RunProfileState.execute` — orchestrates launch → connect → execute → console
  (§5 flow). Splits work into ≤30-line helpers (contract §3).
- **Threading**: `execute` on EDT returns immediately; server launch + RESP I/O on a session
  `childScope` derived from `LunarCoroutineScopeService.getInstance(project).scope`
  (`util/LunarCoroutineScopeService.kt:19`); UI marshalled with `withContext(Dispatchers.EDT)`.
- **Collaborators**: `LuaRedisServerLauncher` **[NEW]** (§2.12), `RespClient` (§2.3),
  `LuaRedisScriptExecutor` **[NEW]** (§3.8), `RespReplyTreeConsole` (§2.6).
- **Key API**:
  ```kotlin
  class LuaRedisRunProfileState(
      private val config: LuaRedisRunConfiguration,
      environment: ExecutionEnvironment,
  ) : RunProfileState {
      override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult  // §5
  }
  ```

### 2.12 `net.internetisalie.lunar.redis.connection.LuaRedisServerLauncher` **[NEW]**
- **Responsibility**: Starts/stops a session-scoped server for `LocalBinary` / `Docker`
  provisioning; returns the endpoint the `RespClient` connects to. `Disposable` (stop on dispose).
- **Threading**: pooled coroutine; process via `GeneralCommandLine` + `OSProcessHandler`
  (grounded platform types, used across the repo, e.g.
  `rocks/run/LuaRocksRunConfiguration.kt:188`) — the same platform process API the existing run
  configs use.
- **Key API**:
  ```kotlin
  class LuaRedisServerLauncher(private val project: Project) {
      suspend fun launch(provisioning: LuaRedisProvisioning): LaunchedServer   // §3.9
  }
  data class LaunchedServer(val host: String, val port: Int, val stop: () -> Unit)
  ```

## 3. Algorithms

### 3.1 Connection handshake (`RespClient.open`)
- **Input → Output**: `(LuaRedisServerConnection, RespTimeouts)` → open `RespClient`.
- **Steps**:
  1. Open `Socket` (or TLS socket via `SSLSocketFactory.getDefault()` when `tls`), apply
     `connectMs`/`readMs`.
  2. Fetch password: `LuaRedisCredentialStore.getPassword(connection.id)`.
  3. If password or `username` present, send `HELLO 3 AUTH <user|"default"> <password>`.
     - On `RespValue.Map`/`Array` reply → `protocol = RESP3`.
     - On `RespValue.Error` whose `klass`/message indicates unknown command or wrong arity
       (`ERR`/`WRONGARGS`/contains `unknown command`) → fall back: send `AUTH …` (if creds) then
       treat `protocol = RESP2`.
  4. If no creds: send `HELLO 3`; same success→RESP3 / error→RESP2 fallback (bare `PING` for
     liveness on the RESP2 path).
  5. If `database != 0`, send `SELECT <db>`; a non-`+OK` reply → `RespException.Protocol`.
- **Edge handling**: any `SocketTimeoutException` → `RespException.Timeout("connect")`; auth error
  (`-WRONGPASS`/`-NOAUTH`) surfaces to the caller as the `RespValue.Error` (Test Connection shows it).

### 3.2 Command encoding (`RespCodec.encodeCommand`)
- **Input → Output**: `List<ByteArray>` → `ByteArray`.
- **Steps** (RESP2 array-of-bulk, ASCII framing, payload bytes verbatim):
  1. Write `*<count>\r\n` (ASCII digits).
  2. For each arg: `$<byteLength>\r\n`, the raw arg bytes, `\r\n`.
- **Rules**: `byteLength` is `arg.size` (bytes, never `String.length`) — this is the multi-byte
  UTF-8 correctness point (TC-RESP-3).

### 3.3 Reply decoding (`RespCodec.decode`)
- **Input → Output**: `PushbackInputStream` → one `RespValue`.
- **Steps**:
  1. Read one type byte. Dispatch:
     `+`→Simple, `-`→Error, `:`→Integer, `$`→Bulk, `*`→Array, `%`→Map, `,`→Double, `#`→Bool,
     `_`→Null(read trailing `\r\n`), `=`→Simple(verbatim, RESP3).
  2. Simple/Error/Integer/Double/Bool: read bytes up to `\r\n`, parse.
  3. Bulk (`$`): read length `L` up to `\r\n`. If `L == -1` → `Bulk(null)`. Else read exactly `L`
     bytes (loop until `L` read or EOF), then consume trailing `\r\n`.
  4. Array (`*`): read count `N`. If `N == -1` → `Array(null)`. Else recurse `decode` N times.
  5. Map (`%`): read pair-count `N`; recurse `decode` 2·N times, pair them.
- **Rules / edge handling**: line terminator is exactly `\r\n`; a lone `\r` or EOF mid-frame →
  `RespException.Protocol`. **Partial reads** (TC-RESP-4): the length-prefixed read loops until the
  declared byte count is satisfied, so a fragmented stream is reassembled (this is why decode takes
  a stream, not a pre-read line). Depth is bounded by reply nesting; no explicit cap in scope.

### 3.4 Error classification (`RespValue.Error` split)
- **Input → Output**: raw error line (after `-`, before `\r\n`) → `Error(klass, message)`.
- **Steps**: `klass = line.substringBefore(' ')`; `message = line.substringAfter(' ', "")`.
- **Rules**: covers `ERR`, `WRONGTYPE`, `NOSCRIPT`, `NOAUTH`, `WRONGPASS`, etc. — the class is the
  console's shown error tag (TC-CON-2).

### 3.5 Reply-tree shaping (`RespReplyTreeModel`)
- **Input → Output**: `RespValue` → Swing `TreeModel` root + children.
- **Steps**: scalar (`Simple`/`Integer`/`Bulk`/`Double`/`Bool`/`Null`) → single leaf, rendered inline;
  `Array`/`Map` → an expandable node with one child per element (Map child label `key = value`);
  nested arrays/maps recurse. `Bulk(null)`/`Array(null)`→ leaf "(nil)".
- **Rules**: node label = `"[<index>] <typeGlyph> <preview>"`; `Bulk` preview via `asString()` or
  `<binary N bytes>` when not valid UTF-8. No truncation in REDIS-01 (LDB `maxlen` is REDIS-02).

### 3.6 Error-link filtering (`LuaRedisErrorLinkFilter.applyFilter`)
- **Input → Output**: `(line, entireLength)` → `Filter.Result?`.
- **Regex** (grounded — `Filter` API): `Regex("""(?:@?user_script:?\s*)(\d+)""")`.
- **Steps**:
  1. Find first match; capture group 1 = 1-based server line `N`.
  2. `targetOffset = entireLength - line.length + match.range.first`.
  3. Resolve the script file with an explicit null-checked lookup (no `!!`), mirroring the
     grounded pattern in `lang/doc/LuaDocSearchItem.kt:25`
     (`VirtualFileManager.getInstance().findFileByUrl(fileUrl) ?: return`):
     ```kotlin
     val file = VirtualFileManager.getInstance().findFileByUrl(scriptFileUrl) ?: return null
     val hyperlink = OpenFileHyperlinkInfo(project, file, N - 1)  // 1-based server line → 0-based editor line (TC-CON-3)
     ```
     A missing/unresolvable file returns `null` (no hyperlink), never a `!!` dereference (contract §1, no-`!!` rule).
  4. Return `Filter.Result(targetOffset, targetOffset + match.value.length, hyperlink)`.
- **Rules**: matches both `user_script:12:` and `@user_script: 12` (optional `@`, optional colon,
  optional whitespace before the digits).

### 3.7 `checkConfiguration` validation
- **Steps** (throw `RuntimeConfigurationException`, cf. `LuaTestRunConfiguration.kt:257-264`):
  1. `scriptPath` null/blank → "Script path is not defined".
  2. resolved `connection == null` (id missing or not in settings) → "No Redis connection selected".
  3. `execMode == FCALL` → "FCALL mode is not available until REDIS-05".
  4. `readOnly && execMode == EVALSHA/EVAL` is allowed; version gate is enforced at run time (§3.8),
     not here (we don't have a live connection at edit time).

### 3.8 Script execution (`LuaRedisScriptExecutor`) — EVAL / EVALSHA / read-only / NOSCRIPT
- **Input → Output**: `(RespClient, LuaRedisRunConfiguration, scriptBody)` → `RespValue`.
- **Command selection**:
  | execMode | readOnly | command |
  |----------|----------|---------|
  | EVAL | false | `EVAL <body> <#keys> <keys…> <argv…>` |
  | EVAL | true | `EVAL_RO …` |
  | EVALSHA | false | `EVALSHA <sha> <#keys> <keys…> <argv…>` |
  | EVALSHA | true | `EVALSHA_RO …` |
- **Steps**:
  1. Compute `sha = sha1Hex(scriptBody.toByteArray(UTF_8))`.
  2. Read-only gate: if `readOnly`, probe server version once via `INFO server` →
     `redis_version`/`valkey_version`; if `< 7.0.0` → `RespException.ServerVersion("Redis 7 / Valkey")`
     (TC-RO-1). Compare with `toolchain/model/SemanticVersion.kt` (grounded model).
  3. EVAL mode: send the EVAL command directly.
  4. EVALSHA mode: look up cached sha in `LuaRedisScriptShaCache` **[NEW]** keyed by
     `(connectionId, sha)`; if absent, `SCRIPT LOAD <body>` first, cache the returned sha. Send
     `EVALSHA`. On `RespValue.Error` with `klass == "NOSCRIPT"` → re-`SCRIPT LOAD` once, evict+re-cache,
     retry `EVALSHA` (TC-SHA-1). A second `NOSCRIPT` surfaces the error.
- **`sha1Hex`**: `MessageDigest.getInstance("SHA-1")` (JDK), lowercase hex — matches server SHA.

### 3.9 Server launch (`LuaRedisServerLauncher.launch`)
- **Steps by provisioning**:
  - `Remote` → return the connection's own host/port; no process.
  - `LocalBinary(toolKindId)`: `LuaToolResolver.getInstance().resolve(project, toolKindId)`; null →
    `ExecutionException` "Redis/Valkey server binary not found — register it under Settings |
    Languages & Frameworks | Lua | Toolchain, or use Docker" (RISK: "neither available" criterion).
    Else `GeneralCommandLine(binary, "--port", "<freePort>", "--save", "")`, start via
    `OSProcessHandler`; `freePort` from `com.intellij.util.net.NetUtils.findAvailableSocketPort()`
    (grounded platform util). Poll `PING` until `+PONG` or 5 s timeout.
  - `Docker(image)`: locate `docker` on PATH via `PathEnvironmentVariableUtil.findInPath("docker")`
    (grounded platform util); null → `ExecutionException` "Docker is not available…". Run
    `docker run --rm -d -p <freePort>:6379 <image>`; capture container id from stdout; `stop = { docker rm -f <id> }`.
    Poll `PING`.
- **Edge**: `stop` is idempotent and invoked from `dispose()` and on process termination
  (session lifecycle, epic constraint).

## 4. External Data & Parsing

### 4.1 RESP2/RESP3 wire protocol
- **Format / Parse strategy**: fully specified in §3.2 (encode) and §3.3 (decode) with the byte-level
  framing. A sample RESP3 map reply (`HELLO 3`):
  ```
  %7\r\n$6\r\nserver\r\n$5\r\nredis\r\n$7\r\nversion\r\n$5\r\n7.4.0\r\n…
  ```
- **Maps to**: `RespValue` (§2.1). **Failure handling**: §3.3 edge rules → `RespException.Protocol`.

### 4.2 `redis-server` / `valkey-server --version`
- **Format**: `Redis server v=7.4.0 sha=… malloc=… bits=64 build=…` (Valkey: `Valkey server v=8.0.0 …`).
- **Parse strategy**: `ProbeSpec.versionRegex = Regex("""v=(\d[\w.]*)""")` (grounded `ProbeSpec`
  model `toolchain/model/LuaToolKind.kt:27-37`).
- **Maps to**: `LuaRegisteredTool` version via the existing probe pipeline. **Failure**: no match →
  tool unresolved (existing toolchain behavior).

### 4.3 `INFO server` reply (version + flavor for Test Connection / read-only gate)
- **Format**: bulk string of `key:value\r\n` lines, e.g. `redis_version:7.4.0`,
  `redis_mode:standalone` (Valkey adds `valkey_version:…`).
- **Parse strategy**: split on `\r\n`, then `substringBefore(':')`/`substringAfter(':')`; read
  `redis_version` (fallback `valkey_version`). Flavor = presence of `valkey_version` → "Valkey"
  else "Redis" (REDIS-03 replaces this heuristic with `SERVER_NAME`; REDIS-01 uses `INFO`).
- **Maps to**: the Test-Connection result string and the §3.8 version gate.

## 5. Data Flow

### Example 1: EVAL against a remote connection
1. User runs config → `getState` → `LuaRedisRunProfileState.execute` (EDT) launches a coroutine on
   the session childScope and returns a `DefaultExecutionResult` wrapping `RespReplyTreeConsole`.
2. Coroutine: `LuaRedisServerLauncher.launch(Remote)` → host/port; `RespClient.open` handshake (§3.1).
3. `LuaRedisScriptExecutor` reads the script body via `readAction { }`, sends `EVAL` (§3.8).
4. Reply `RespValue` → `withContext(Dispatchers.EDT) { console.showReply(reply) }`; errors →
   `showError`; `user_script:N` lines linked by the filter (§3.6). Client + launcher disposed.

### Example 2: Local Docker + EVALSHA + read-only
1. `launch(Docker("redis:8"))` starts a container (§3.9), returns mapped port.
2. read-only gate probes `INFO server` (§3.8 step 2). If `< 7` → fail fast with `ServerVersion`.
3. `SCRIPT LOAD` → sha cached; `EVALSHA_RO` → reply tree. On teardown, `stop()` removes the container.

## 6. Edge Cases
- **Nil bulk / null array** (`$-1`, `*-1`) → `Bulk(null)`/`Array(null)` → "(nil)" leaf.
- **Binary bulk payload** (not UTF-8) → `<binary N bytes>` preview (§3.5).
- **HELLO unsupported** (Redis < 6) → RESP2 fallback (§3.1 step 3/4).
- **Neither Docker nor binary** → explicit `ExecutionException` message (§3.9; requirement bullet 3).
- **NOSCRIPT after SCRIPT LOAD** → single retry then surface (§3.8).
- **Cancellation** (TC-CANCEL-1): `ProgressManager.checkCanceled()` / `ensureActive()` in the read
  loop and between commands (contract §2); a cancelled `ProgressIndicator` aborts the in-flight
  connect/command with a `ProcessCanceledException`, and cancelling the run disposes the client and
  stops the server. Unit test: drive `command()` under a `ProgressIndicator` that reports
  `isCanceled == true`; assert the call aborts (throws `ProcessCanceledException`) without completing
  the read.
- **Connect/read timeout** (TC-TIMEOUT-1): a `SocketTimeoutException` on connect or read → mapped to
  `RespException.Timeout` (§2.3, §2.10), never propagated raw.
- **Producer on non-Redis target** → producer returns `false` (§7), standard Lua config remains.

## 7. Integration Points

```xml
<!-- plugin.xml (append near the existing run-config block, plugin.xml:466-514) -->
<extensions defaultExtensionNs="com.intellij">
  <configurationType
      implementation="net.internetisalie.lunar.redis.run.LuaRedisRunConfigurationType"/>
  <runConfigurationProducer
      implementation="net.internetisalie.lunar.redis.run.LuaRedisRunConfigurationProducer"/>
  <projectService
      serviceImplementation="net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings"/>
  <projectConfigurable
      parentId="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
      instance="net.internetisalie.lunar.redis.connection.LuaRedisConnectionsConfigurable"
      id="net.internetisalie.lunar.redis.connection.LuaRedisConnectionsConfigurable"
      displayName="Redis Connections"
      nonDefaultProject="true"/>
</extensions>
```

- `LuaRedisRunConfigurationProducer` **[NEW]** extends
  `LazyRunConfigurationProducer<LuaRedisRunConfiguration>` (shape from
  `run/test/LuaTestRunConfigurationProducer.kt:17`); `setupConfigurationFromContext` returns `false`
  unless `file.fileType.name == "Lua"` **and**
  `LuaProjectSettings.getInstance(project).state.getTarget().platform == LuaPlatform.REDIS`
  (grounded; RISK-R12 — additive, never replaces the standard producer).
- `LuaRedisConnectionsConfigurable` **[NEW]** = `Configurable` under the existing Lua settings tree
  (parent id grounded `plugin.xml:461-462`); hosts the connection list UI + Test Connection button.
- **Tool kinds**: `LuaToolKindRegistry` **[EDIT]** gains `redis-server` + `valkey-server` (§2.9).
- **Credential subsystem**: `"Lunar Redis"` in `PasswordSafe` (§2.9); no plugin.xml.
- **Gradle [EDIT]**: a `redisIntegrationTest` task (RISK-R10 / DR-04) — see implementation-plan §Phase 6.

## 8. Requirement Coverage

| Acceptance criterion (requirements.md order) | Priority | Implemented by |
|----------------------------------------------|----------|----------------|
| RESP client (RESP2 + HELLO/RESP3 fallback, byte framing, timeouts, cancellable, no EDT I/O) | M | §2.2, §2.3, §3.1–§3.3; tests TC-RESP-1..4, **TC-TIMEOUT-1** (`SocketTimeoutException → RespException.Timeout`), **TC-CANCEL-1** (cancelled `ProgressIndicator` aborts in-flight connect/command) |
| Connection settings UI (named, host/port/TLS/auth/db, PasswordSafe, Test Connection + flavor/version) | M | §2.4, §2.5, §2.9, §4.3, §7 |
| Launch-local variants (binary + Docker, session-bound, error if neither) | M | §2.12, §3.9 |
| `LuaRedisRunConfiguration` (path, connection ref, mode, KEYS, ARGV persisted; checkConfiguration) | M | §2.8, §3.7 |
| Run-config producer (editor context menu, gated on Redis/Valkey target) | M | §7 |
| Console reply tree (scalar inline, errors with class) | M | §2.6, §3.4, §3.5 |
| Console error-link filter (`user_script:N` → editor line, 1→0-based) | M | §2.7, §3.6 |
| SCRIPT LOAD sha caching per (connection, script hash) + NOSCRIPT fallback | M | §3.8 |
| Read-only toggle → `EVAL_RO`/`EVALSHA_RO`; fail fast on Redis < 7 | M | §3.8 |
| Unit tests (encode/decode incl. multi-byte, partial reads) + Docker integration test | M | impl-plan §Phase 1/6 |

## 9. Alternatives Considered
- **Shell out to `redis-cli`** — rejected (epic rationale): no structured replies for the tree, PTY
  handling, and it cannot be reused by the REDIS-02 debug adapter, which needs the shared client.
- **Jedis/Lettuce dependency** — rejected: bundling a coroutine-incompatible client bloats the plugin
  and duplicates the platform's networking; RESP2 is ~120 lines to implement precisely (§3.2/§3.3).
- **RESP3-only** — rejected: many managed/older servers reject `HELLO 3`; RESP2 with negotiation is
  the compatible floor (RISK-R11 scope).

## 10. Open Questions

_None — feature has cleared the planning bar. Deferred decisions (Docker CI provisioning DR-04,
Valkey provisioning specifics) are tracked in [redis-risks-and-gaps.md](../redis-risks-and-gaps.md)
and the feature-level [risks-and-gaps.md](risks-and-gaps.md)._
