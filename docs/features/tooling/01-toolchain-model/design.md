---
id: "TOOLING-01-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-01"
folders:
  - "[[features/tooling/01-toolchain-model/requirements|requirements]]"
---

# Technical Design: TOOLING-01 — Unified Toolchain Model & Registry

Class/package names follow the binding [architecture contract](../tooling-architecture.md)
(§1 package layout, §2 core model, §4 events, §7 persistence, §10 threading). The contract is
authoritative; where a symbol previously differed here it has been reconciled to match.

## 1. Architecture Overview

### Current State

Two parallel stacks model external binaries:

- **Tools** (`tool/`): `LuaToolType` closed enum (`tool/LuaToolDescriptor.kt:19-25`),
  `LuaToolDescriptor` filename candidates (`:27-54`), `LuaTool` mutable inventory bean
  (`tool/LuaTool.kt:15-60`) with the ambiguous `isValid` (`:41`), `LuaToolValidator` probe
  (`tool/LuaToolValidator.kt`), `LuaToolDiscoveryService` PATH/well-known scanner
  (`tool/LuaToolDiscoveryService.kt`), and `LuaToolManager` APP service
  (`tool/LuaToolManager.kt`) whose `registerTool` (`:49`) fires **no event** and whose
  `getTools()` (`:110-122`) **mutates health during a read**.
- **Interpreters** (`platform/` + `settings/`): `LuaInterpreterFamily` data table with
  per-family levelers (`platform/LuaInterpreter.kt:100-140`), `LuaInterpreterService`
  APP service with its own directory scan (`platform/LuaInterpreterService.kt:21-33`),
  env-var substitution (`:117-128`), dir-glob expansion (`:205-243`), `-v` probing
  (`identify`, `:79-115`), and `Banner` parsing (`:172-201`); inventory persisted as
  `LuaApplicationSettings.State.interpreters` (`settings/LuaApplicationSettings.kt:39`).

Duplication: two scanners, two probes, two inventories, two health notions, zero shared code.

### Prior Art in This Repo

Every component below is **REPLACED** by this feature (new code lands dark; physical deletion
is TOOLING-05). Verified by grep:

| Legacy component (file:line) | Replaced by |
|---|---|
| `LuaToolType` enum + `LuaToolDescriptor.candidates()` (`tool/LuaToolDescriptor.kt:19-25`, `:34-39`) | `LuaToolKind` + platform candidate expansion (§2.1, §3.2) |
| `LuaTool` bean incl. `isValid` `:41`, `lastCheckedMtime` `:51`, `lastCheckReason` `:59` (`tool/LuaTool.kt`) | `LuaRegisteredTool` + `LuaToolHealth` (§2.3) |
| `LuaToolValidator` — regexes `:32,:38,:44,:50,:56,:62`, min version `:68`, timeout `:22`, merge `:154-160`, flags `:174-180`, `SemanticVersion` `:183-208` (`tool/LuaToolValidator.kt`) | `LuaToolProbe` + per-kind `ProbeSpec` + `model.SemanticVersion` (§2.2, §3.4, §3.6) |
| `LuaToolDiscoveryService` — extra dirs `:27-41`, canonical dedup `:58-67`, candidate loop `:78-99` (`tool/LuaToolDiscoveryService.kt`) | `LuaToolDiscovery` (§2.6, §3.2) |
| `LuaToolManager` — `registerTool` `:49` (no event), dedup-by-path `:69`, mutating `getTools` `:110-122`, `autoDiscover` `:137-146`, `inferType` `:204-217`, `displayNameFor` `:219-225` (`tool/LuaToolManager.kt`) | `LuaToolchainRegistry` + `LuaToolKindRegistry.inferKind` (§2.4, §2.5, §3.1, §3.5) — note `getEffectiveTool` (`:163`) precedence is **TOOLING-02**, not here |
| `LuaInterpreterService.findInterpreters` `:21-33`, `PATHS_UNIX` `:133-147`, `PATHS_WINDOWS` `:149-152`, `substituteEnvVars` `:117-128`, `expandSearchPath` `:205-243`, glob helpers `:245-269` (`platform/LuaInterpreterService.kt`) | `LuaToolDiscovery` (§3.2) — glob helpers reused by move/copy into `toolchain.discovery` |
| `LuaInterpreterService.identify` `:79-115` + `Banner` `:172-201` (`VERSION_PATTERN` `:178`, stderr-preference `:190-199`) | `LuaToolProbe` RUNTIME handling (§3.4 steps 6-8) |
| `LuaInterpreterFamily.FAMILIES` + levelers (`platform/LuaInterpreter.kt:100-140`; Lua leveler `:109-118`, LuaJIT `:128`, Tarantool `:138`) | RUNTIME kinds + `LanguageLevelRule` (§2.2, §4.1) |
| `LuaApplicationSettings.State.interpreters` `:39`, `toolInventory` `:45`, `globalToolBindings` `:53` (`settings/LuaApplicationSettings.kt`) | `LuaToolchainAppState` (§2.4, §6) — legacy fields deleted in TOOLING-05, per the clean-break decision |

**Reused as-is** (grounded, not replaced): `LuaProcessUtil.capture(cmd, timeoutMs)` and its
sentinel exit codes (`util/LuaProcessUtil.kt:13-14,:17`); `PathEnvironmentVariableUtil`
(platform API — `findInPath` / `findAllExeFilesInPath` / `getPathDirs`, verified at
`intellij-community/platform/platform-util-io/src/com/intellij/execution/configurations/PathEnvironmentVariableUtil.java:40,77,115`);
`LuaLanguageLevel` (`lang/LuaLanguageLevel.kt:19-25`); `LuaPlatform`
(`platform/LuaPlatform.kt:3-10`); `PlatformVersionRegistry`
(`platform/target/PlatformVersionRegistry.kt:15-41` — consumed later by TOOLING-02/05 for
`Target` derivation via `resolveTarget`, `:81-86`; unchanged here); XML-state precedents
`LuaApplicationSettings` (`settings/LuaApplicationSettings.kt:29-34`) and the
`TargetState` serializable-wrapper pattern (`settings/LuaProjectSettings.kt:30-48`); enum
persistence precedent (`LuaProjectSettings.State.languageLevel`/`interpreterMode`,
`settings/LuaProjectSettings.kt:50,:62`); project-level topic precedent
`LuaSettingsChangedListener.TOPIC` (`settings/LuaSettingsChangedEvent.kt:29-32`).

### Target State

```
toolchain.model      LuaToolKind, ProbeSpec, RuntimeProbeSpec, LanguageLevelRule, Capability,
                     ProvisioningSpec (shape), LuaRegisteredTool, LuaToolHealth,
                     LuaRuntimeInfo, SemanticVersion
toolchain.registry   LuaToolchainRegistry (APP service + PersistentStateComponent<LuaToolchainAppState>),
                     LuaToolchainAppState (top-level state class), LuaToolKindRegistry
                     (built-in kind data), LuaToolchainListener (topic)
toolchain.discovery  LuaToolDiscovery (the one scanner) + glob utilities
toolchain.probe      LuaToolProbe (the one probe engine)
```

Data flow: `LuaToolDiscovery` finds candidate binaries per kind → `LuaToolchainRegistry`
registers each via `LuaToolProbe` → immutable `LuaRegisteredTool`s persist as beans in
`lunar.xml` → every mutation publishes `LuaToolchainListener.TOPIC`. No consumer reads any of
it until TOOLING-05.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.model.LuaToolKind` (+ probe/provisioning specs)

- **Responsibility**: pure-data descriptor of one tool kind (contract §2.1).
- **Threading**: immutable value objects; safe everywhere.
- **Collaborators**: none (leaf data).
- **Key API**:

```kotlin
data class LuaToolKind(
    val id: String,                            // stable key: "lua", "luajit", "luarocks", …
    val displayName: String,                   // "LuaRocks", "StyLua" — from tool/LuaToolManager.kt:219-225
    val binaryNames: List<String>,             // base names; may contain filename globs ("lua5.*")
    val probe: ProbeSpec,
    val capabilities: Set<Capability>,
    val minVersion: SemanticVersion? = null,   // enforced by the probe (fixes dead meetsMinimumVersion,
                                               // tool/LuaToolValidator.kt:119-124)
    val provisioning: List<ProvisioningSpec> = emptyList(),  // SHAPE only; populated by TOOLING-04
) {
    val isRuntime: Boolean get() = Capability.RUNTIME in capabilities
}

enum class Capability { RUNTIME, PACKAGE_MANAGER, LINTER, FORMATTER, TEST_RUNNER, COVERAGE }

data class ProbeSpec(
    val args: List<String>,                    // e.g. ["-v"], ["--version"], ["--help"]
    val versionRegex: Regex,                   // group(1) = version; applied to MERGED output (§3.4)
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,   // 10_000 — tool/LuaToolValidator.kt:22 precedent
    val luaVersionRegex: Regex? = null,        // luarocks "for Lua X.Y" — tool/LuaToolValidator.kt:62
    val runtime: RuntimeProbeSpec? = null,     // present iff the kind has Capability.RUNTIME
) { companion object { const val DEFAULT_TIMEOUT_MS: Int = 10_000 } }

data class RuntimeProbeSpec(
    val productToken: String,                  // first banner token must equal this ("Lua", "LuaJIT", "Tarantool")
    val platform: LuaPlatform,                 // platform/LuaPlatform.kt:3-10
    val languageLevel: LanguageLevelRule,
)

sealed interface LanguageLevelRule {
    data class Fixed(val level: LuaLanguageLevel) : LanguageLevelRule
    /** First entry whose key is a prefix of the probed version wins (declaration order);
     *  otherwise [fallback]. Reproduces the leveler at platform/LuaInterpreter.kt:109-118. */
    data class ByVersionPrefix(
        val prefixes: List<Pair<String, LuaLanguageLevel>>,
        val fallback: LuaLanguageLevel,
    ) : LanguageLevelRule
}
```

`ProvisioningSpec` — shape only (contract §6 names the three strategies; implementations,
URL templates, and build sequences are TOOLING-04, de-risked by TOOLING-00-01/-02/-03/-05):

```kotlin
sealed interface ProvisioningSpec {
    /** Prebuilt release asset per platform (TOOLING-00-02/-05 ground the template format). */
    data class ReleaseBinary(val urlTemplate: String, val checksumUrlTemplate: String?) : ProvisioningSpec
    /** Source tarball + host-toolchain build (TOOLING-00-01/-03). */
    data class SourceBuild(val sourceUrlTemplate: String) : ProvisioningSpec
    /** `luarocks install <rockName>` into an environment (TOOLING-04). */
    data class LuaRocksInstall(val rockName: String) : ProvisioningSpec
}
```

### 2.2 Built-in kinds — `net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry`

- **Responsibility**: hold the built-in `LuaToolKind` data list; lookup; kind inference.
- **Threading**: immutable object; safe everywhere.
- **Key API**:

```kotlin
object LuaToolKindRegistry {
    val BUILT_IN: List<LuaToolKind>            // exactly the §4.1 table, in that order
    fun all(): List<LuaToolKind> = BUILT_IN
    fun findById(id: String): LuaToolKind?     // map-backed, O(1)
    fun inferKind(fileName: String): LuaToolKind?   // §3.5
}
```

The complete built-in table (ids, names, candidates, probe specs) is normative in §4.1.
Adding a kind = appending one `LuaToolKind` literal (TOOLING-01-14; worked `redis-cli`
example in §4.2) — no other code changes.

### 2.3 `net.internetisalie.lunar.toolchain.model.LuaRegisteredTool`, `LuaToolHealth`, `LuaRuntimeInfo`

- **Responsibility**: the one immutable inventory-entry model (contract §2.2/§2.3).
- **Threading**: immutable; safe everywhere.
- **Key API**:

```kotlin
data class LuaRegisteredTool(
    val id: String,                    // UUID string
    val kindId: String,
    val path: String,                  // absolute path as registered
    val version: String?,              // probed; null if probe failed/never ran
    val luaVersion: String?,           // linked-Lua hint (contract §2.2); LuaRocks "for Lua X.Y"
    val runtime: LuaRuntimeInfo?,      // non-null only for RUNTIME kinds with a passing probe
    val origin: Origin,
    val environmentId: String?,        // set whenever the tool belongs to an environment
                                       // (adopted envs: origin=DISCOVERED with environmentId)
    val health: LuaToolHealth,
)

enum class Origin { DISCOVERED, MANUAL, PROVISIONED }

data class LuaToolHealth(
    val fileExists: Boolean,
    val executable: Boolean,
    val probeOk: Boolean?,             // null = never probed
    val probedAtMtime: Long?,          // mtime gate kept from TOOL-03 (tool/LuaTool.kt:51)
    val reason: String?,               // "OK 3.11.0" / "timed out" / "below minimum 3.0.0" …
)

val LuaRegisteredTool.isUsable: Boolean
    get() = health.fileExists && health.executable && health.probeOk != false

data class LuaRuntimeInfo(
    val product: String,               // "Lua" | "LuaJIT" | "Tarantool" (banner token 1)
    val version: String,               // banner token 2, e.g. "5.4.6"
    val languageLevel: LuaLanguageLevel,   // lang/LuaLanguageLevel.kt:19-25
    val platform: LuaPlatform,             // platform/LuaPlatform.kt:3-10
    val banner: String,                // the full banner line, for UI/diagnostics
)
```

> **`luaVersion` per contract §2.2:** `LuaRegisteredTool.luaVersion: String?` carries LuaRocks'
> "for Lua X.Y" compatibility hint (contract §2.2 field; contract §10.3 also carries it on
> `LuaToolProbeResult`), preserving the data today's `LuaTool.luaVersion` (`tool/LuaTool.kt:35`)
> holds and `LuaToolValidator.checkCompatibility` (`tool/LuaToolValidator.kt:110-113`) consumes,
> for TOOLING-02/07. Requirement TOOLING-01-15 (Should) captures the probe-side extraction.

### 2.4 `net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry`

- **Responsibility**: APP-level inventory CRUD + global-binding storage + persistence + event
  publication. The single writer of toolchain state (contract §2, §4, §7).
- **Threading**: `registerTool` / `refreshTool` / `autoDiscover` probe subprocesses →
  background-only, guarded by
  `ApplicationManager.getApplication().assertIsNonDispatchThread()`. All mutations
  `synchronized(stateLock)`; reads take an immutable snapshot; events publish synchronously.
- **Collaborators**: `LuaToolProbe`, `LuaToolDiscovery`, `LuaToolKindRegistry`,
  application message bus.
- **Key API**:

```kotlin
// -------- top-level state class (contract §7, §10.1) — NOT a nested .State --------
class LuaToolchainAppState {                            // XML bean — §6
    var tools: MutableList<RegisteredToolState> = mutableListOf()   // named `tools`, not `toolInventory`
    var globalBindings: MutableMap<String, String> = HashMap()      // kindId -> toolId
    var kindOptions: MutableMap<String, String> = HashMap()         // app-level kind options (§6, contract §7)
}

@Service(Service.Level.APP)
@State(
    name = "LuaToolchainRegistry",
    storages = [Storage("lunar.xml")],
    category = SettingsCategory.PLUGINS,
)  // mirrors settings/LuaApplicationSettings.kt:29-34
class LuaToolchainRegistry : PersistentStateComponent<LuaToolchainAppState> {

    // -------- reads: pure, never touch disk, never mutate (TOOLING-01-13) --------
    // Public surface is pinned by contract §10.1 (declared names/signatures below).
    fun tools(): List<LuaRegisteredTool>
    fun toolsOfKind(kindId: String): List<LuaRegisteredTool>   // NOT tools(kindId)
    fun tool(id: String): LuaRegisteredTool?                   // by-id lookup
    fun findByPath(path: String): LuaRegisteredTool?
    fun globalBindings(): Map<String, String>                  // kindId -> toolId
    fun kindOption(key: String): String?

    // -------- mutations: each fires LuaToolchainListener.TOPIC once; no-op fires nothing --------
    fun setGlobalBinding(kindId: String, toolId: String?)      // null clears; any thread
    /** Probe + persist. Returns null only when the kind cannot be determined (§3.1). BGT only. */
    fun registerTool(
        path: String,
        kindIdHint: String? = null,
        origin: Origin = Origin.MANUAL,
        environmentId: String? = null,
    ): LuaRegisteredTool?
    fun registerProvisioned(tool: LuaRegisteredTool)           // TOOLING-04 writes a provisioned entry
    fun unregisterTool(id: String): Boolean
    fun unregisterByEnvironment(environmentId: String)         // TOOLING-04 teardown
    /** Explicit re-probe of an existing entry (health is data; reads never do this). BGT only. */
    fun refreshTool(id: String)
    /** TOOLING-07 writes a caller-computed check result; fires the topic; no-op when unchanged. */
    fun updateToolCheck(
        toolId: String,
        health: LuaToolHealth,
        version: String?,
        luaVersion: String?,
        runtime: LuaRuntimeInfo?,
    )
    /** Discover (§3.2) + register every candidate. BGT only. */
    fun autoDiscover()
    fun setKindOption(key: String, value: String?)             // kindOptions (§6, contract §7)

    companion object { fun getInstance(): LuaToolchainRegistry }  // application service lookup,
        // as tool/LuaToolManager.kt:232-235 does today
}
```

The signatures above are the **declared public surface**, pinned by contract §10.1. Richer
internals (a by-id `globalBinding` resolution helper, snapshot mapping, etc.) remain private.
`refreshTool` and `updateToolCheck` coexist: `refreshTool` is the convenience path (registry
probes then writes); `updateToolCheck` lets TOOLING-07's off-thread checker persist a result it
already computed. Both fire the topic and **no-op silently when the value is unchanged**.

### 2.5 `net.internetisalie.lunar.toolchain.registry.LuaToolchainListener`

- **Responsibility**: the ONE app-level change topic (contract §4). Fired by **every** registry
  mutation — fixes the defect where `LuaToolManager.registerTool` fires nothing and the
  `LuaTerminalEnvironmentService` cache (subscribed only to `LuaSettingsChangedListener.TOPIC`,
  `tool/LuaTerminalEnvironmentService.kt:40-49`) goes stale.
- **Event shape: contract §4 is authoritative — declared once there, not redeclared here.**
  This feature uses exactly the contract's pinned shape: topic id `"lunar.toolchain.changed"`,
  enum `LuaToolchainChange` (`TOOL_REGISTERED`, `TOOL_UPDATED`, `TOOL_REMOVED`,
  `GLOBAL_BINDING_CHANGED`, `PROJECT_BINDING_CHANGED`, `ENVIRONMENT_ADDED`,
  `ENVIRONMENT_UPDATED`, `ENVIRONMENT_REMOVED`, `ACTIVE_ENVIRONMENT_CHANGED`,
  `KIND_OPTION_CHANGED`), and payload
  `LuaToolchainEvent(change, project?, kindId?, toolId?, environmentId?, optionKey?)`. TOOLING-01
  fires the inventory/health values (`TOOL_REGISTERED`/`TOOL_UPDATED`/`TOOL_REMOVED`),
  `GLOBAL_BINDING_CHANGED`, and `KIND_OPTION_CHANGED`; the environment/project-binding values are
  fired by TOOLING-02. The topic is published on the APPLICATION bus
  (`ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC)`); its default
  `TO_CHILDREN` broadcast direction means project-bus subscribers also receive it. `Topic.create`
  precedent: `settings/LuaSettingsChangedEvent.kt:29-32`.

### 2.6 `net.internetisalie.lunar.toolchain.discovery.LuaToolDiscovery`

- **Responsibility**: the ONE filesystem scanner (contract §1). Finds candidate binaries per
  kind; performs **no probing and no registration**.
- **Threading**: background only (same rule as both legacy scanners,
  `tool/LuaToolDiscoveryService.kt:17`, and `identify`'s process use). Pure `java.io`/`java.nio`
  — no VFS, no read action.
- **Collaborators**: `PathEnvironmentVariableUtil` (platform), glob utilities (moved from
  `platform/LuaInterpreterService.kt:245-269`).
- **Key API**:

```kotlin
object LuaToolDiscovery {
    data class DiscoveredBinary(val kind: LuaToolKind, val file: File)

    /** Scan PATH + well-known dirs for all [kinds]; §3.2. [extraRoots] exists for tests. */
    fun discoverAll(
        kinds: List<LuaToolKind> = LuaToolKindRegistry.all(),
        extraRoots: List<Path> = emptyList(),
    ): List<DiscoveredBinary>

    /** Windows/POSIX filename variants of one binaryName entry; §3.3. Pure, parameterized
     *  for tests like tool/LuaToolDescriptor.candidates(windows) is today (:34-39). */
    fun platformCandidates(name: String, windows: Boolean = SystemInfo.isWindows): List<String>

    internal val WELL_KNOWN_UNIX: List<String>      // §3.2 step 1 — merged legacy lists
    internal val WELL_KNOWN_WINDOWS: List<String>
}
```

### 2.7 `net.internetisalie.lunar.toolchain.probe.LuaToolProbe`

- **Responsibility**: the ONE version/banner probe (contract §1, §10.3). Runs the binary once,
  interprets output per the kind's `ProbeSpec`, returns a `LuaToolProbeResult`.
- **Type**: an **app service `interface`** with a `companion object { fun getInstance() }`
  (contract §10.3) — **not** a Kotlin `object`, so TOOLING-07 can inject a recording fake. The
  production implementation (`LuaToolProbeImpl`) is registered as the `applicationService`.
- **Threading**: synchronous; background threads only (as `LuaToolValidator`,
  `tool/LuaToolValidator.kt:15`).
- **Collaborators**: `LuaProcessUtil.capture` (`util/LuaProcessUtil.kt:17`).
- **Key API** (contract §10.3):

```kotlin
interface LuaToolProbe {
    /** Full probe: file checks + process run + interpretation (§3.4). (kind, Path) order. BGT only. */
    fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult

    companion object { fun getInstance(): LuaToolProbe }   // app-service lookup
}

data class LuaToolProbeResult(
    val ok: Boolean,                  // true iff the probe succeeded (health.probeOk == true)
    val version: String?,
    val luaVersion: String?,          // linked-Lua hint (LuaRocks "for Lua X.Y"); refreshes on re-probe
    val runtime: LuaRuntimeInfo?,     // RUNTIME kinds only
    val failure: String?,             // taxonomy (§3.4): "Timeout" / "Not executable"
                                      // / first non-blank merged-output line; null on success
)
```

The production impl derives `LuaToolHealth` (file/executable/mtime facts + `probeOk = ok`,
`reason = failure`) at the registry boundary (§3.1/§3.4); `LuaToolProbeResult` is the value the
probe returns and the fake records. Pure output interpretation (§3.4 steps 5-9) is a private
helper on `LuaToolProbeImpl` so tests run on static strings, as `LuaToolValidatorTest` does today.

### 2.8 `net.internetisalie.lunar.toolchain.model.SemanticVersion`

Promoted from the nested `LuaToolValidator.SemanticVersion`
(`tool/LuaToolValidator.kt:183-208`) with identical semantics (§3.6); the old one stays in
place until TOOLING-05 deletes the validator.

```kotlin
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int   // §3.6 rules
    override fun toString(): String                        // "major.minor.patch"
    companion object { fun parse(version: String): SemanticVersion? }  // §3.6 rules
}
```

## 3. Algorithms

### 3.1 Registration (`LuaToolchainRegistry.registerTool`)

- **Input → Output**: `(path: String, kindIdHint: String?, origin, environmentId)` →
  `LuaRegisteredTool?` (null only when no kind can be determined).
- **Steps**:
  1. Assert non-dispatch thread.
  2. `kind = kindIdHint?.let { LuaToolKindRegistry.findById(it) } ?: LuaToolKindRegistry.inferKind(File(path).name)`.
     If null → log warn, return `null` (no ProbeSpec to run — same contract as
     `tool/LuaToolManager.kt:56-59`). **No event.**
  3. `canonical = runCatching { File(path).canonicalPath }.getOrDefault(File(path).absolutePath)`
     (fallback rule from `tool/LuaToolDiscoveryService.kt:59-63`).
  4. `result = LuaToolProbe.getInstance().probe(kind, Path.of(path))` (outside the lock —
     probing is slow); derive `LuaToolHealth` from `result` + the file facts (§3.4).
  5. `synchronized(stateLock)`: look up an existing bean whose *canonical* path equals
     `canonical` **and** `kindId == kind.id`.
     - Found → overwrite its version/luaVersion/runtime/health fields in place (same `id`,
       origin/environmentId unchanged) → `change = TOOL_UPDATED`.
       (In-place refresh mirrors `tool/LuaToolManager.kt:69-77`; canonical keying fixes the
       symlink-twin gap of its `it.path == path` comparison.)
     - Not found → append a new bean (`id = UUID.randomUUID().toString()`, store the
       *as-given* absolute path, given origin/environmentId) → `change = TOOL_REGISTERED`.
  6. Outside the lock: publish `LuaToolchainEvent(change, tool.id, kind.id)` via
     `ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)`.
  7. Return the mapped immutable model.
- **Rules / edge handling**: probe failure still registers (health carries the reason,
  TOOLING-01-06); two kinds may legitimately register the same file only under different
  `kindId`s (step 5 keys on the pair); concurrent registrations of the same path serialize on
  the lock — the second becomes an update.

### 3.2 Discovery (`LuaToolDiscovery.discoverAll`) — replaces BOTH legacy scanners

- **Input → Output**: `(kinds, extraRoots)` → `List<DiscoveredBinary>` (deduplicated,
  deterministic order).
- **Search-root list construction** (ordered; earlier roots win dedup):
  1. PATH directories: `PathEnvironmentVariableUtil.getPathDirs(getPathVariableValue())`
     (verified platform API, `PathEnvironmentVariableUtil.java:115,153`).
  2. Well-known directories, the union of the two legacy lists:
     - `WELL_KNOWN_UNIX` = `/bin`, `/sbin`, `/usr/bin`, `/usr/sbin`, `/usr/local/bin`,
       `/usr/local/sbin`, `/opt/bin`, `/opt/sbin`, `/opt/local/bin`, `/opt/local/sbin`,
       `${HOME}/bin`, `${HOME}/sbin` (from `platform/LuaInterpreterService.kt:133-147`) ∪
       `/home/linuxbrew/.linuxbrew/bin` (from `tool/LuaToolDiscoveryService.kt:27-33`).
     - `WELL_KNOWN_WINDOWS` = `C:\Program Files\Lua 5.*`, `C:\Program Files (x86)\Lua 5.*`
       (from `platform/LuaInterpreterService.kt:149-152`) ∪ `C:\Program Files\LuaRocks`,
       `C:\Program Files\Lua`, `C:\ProgramData\chocolatey\bin`, `${APPDATA}\LuaRocks\bin`,
       `${USERPROFILE}\scoop\shims` (from `tool/LuaToolDiscoveryService.kt:35-41`).
  3. Each entry gets `${VAR}` env substitution (algorithm of
     `platform/LuaInterpreterService.kt:117-128`: repeatedly replace `${NAME}` with
     `System.getenv(NAME) ?: ""`), then **directory-glob expansion** (segment-wise frontier
     expansion, sorted by filename — the `expandSearchPath` algorithm of
     `platform/LuaInterpreterService.kt:205-243`, moved into `toolchain.discovery`).
  4. Append `extraRoots` (tests). Drop non-existent/non-directory roots.
- **Matching — two passes so exact names claim files before globs (TOOLING-01-09)**:
  - *Pass 1 (exact)*: for each kind (registry order), for each **non-glob** `binaryName`:
    - PATH: `PathEnvironmentVariableUtil.findAllExeFilesInPath(candidate)` for each platform
      candidate from §3.3 (returns all PATH matches, not just the first —
      `PathEnvironmentVariableUtil.java:77`).
    - Each non-PATH root: `File(root, candidate)`, kept if
      `exists() && isFile && canExecute()` (filter from `tool/LuaToolDiscoveryService.kt:102`).
  - *Pass 2 (glob)*: for each kind, for each **glob** `binaryName` (`isGlob` = contains
    `*`/`?`, `platform/LuaInterpreterService.kt:245-247`): list every root's files; a file
    matches when its name **stripped of a `.exe`/`.bat`/`.cmd` suffix (Windows only) and
    lowercased** matches `patternFromGlob(glob)` (`platform/LuaInterpreterService.kt:255-269`)
    and it passes the executable filter.
  - **Dedup**: one `LinkedHashSet<String>` of canonical paths across BOTH passes and all
    kinds — first claim wins (exact-before-glob, PATH-before-well-known, registry-order
    tiebreak). Canonical fallback to absolute on `IOException`
    (`tool/LuaToolDiscoveryService.kt:58-67`).
- **Output order**: pass-1 results before pass-2, each in (kind, root, candidate) iteration
  order — deterministic for tests.
- **Empty/error path**: unreadable directory → skip with debug log (as
  `platform/LuaInterpreterService.kt:239-242`); no PATH variable → PATH step contributes
  nothing.
- **Complexity**: O(roots × kinds × candidates) stat calls + one listing per root with globs;
  no subprocesses, no VFS.

### 3.3 Platform filename candidates (`platformCandidates`)

- **Input → Output**: `("luarocks", windows=true)` → `["luarocks.bat", "luarocks.exe",
  "luarocks.cmd", "luarocks"]`; `("luarocks", windows=false)` → `["luarocks"]`.
- **Rule**: exactly the order of `tool/LuaToolDescriptor.candidates`
  (`tool/LuaToolDescriptor.kt:34-39`) — `.bat` first (LuaRocks ships a `.bat` shim), then
  `.exe` (StyLua), `.cmd`, bare name. Glob names are **not** expanded here (they carry their
  own suffix-stripping match rule, §3.2 pass 2).

### 3.4 Probe (`LuaToolProbe.probe`) — replaces `LuaToolValidator` + `identify`/`Banner`

- **Input → Output**: `probe(kind, binaryPath)` → `LuaToolProbeResult` (contract §10.3). The
  `failure` string uses the fixed taxonomy: **`"Timeout"`** / **`"Not executable"`** / the first
  non-blank line of the merged probe output (for any other failure). `failure == null` iff `ok`.
  The registry derives `LuaToolHealth` (`probeOk = ok`, `reason = failure`, plus the file/mtime
  facts) at the boundary.
- **Steps**:
  1. `file = binaryPath.toFile()`. Not `exists()` → `LuaToolProbeResult(ok=false, version=null,
     luaVersion=null, runtime=null, failure="Not executable")` (file/executable facts recorded on
     the derived health: `fileExists=false, executable=false`) — **no process spawned**. Exists
     but not `canExecute()` → same `failure="Not executable"`, health `fileExists=true,
     executable=false`.
  2. Build `GeneralCommandLine(path)`, add `kind.probe.args`, `withWorkDirectory(file.parentFile)`
     (as `tool/LuaToolValidator.kt:138-141`).
  3. `output = LuaProcessUtil.capture(cmd, kind.probe.timeoutMs)`.
     - `exitCode == PROCESS_TIMEOUT_EXCEPTION_CODE` (`util/LuaProcessUtil.kt:13`) →
       `ok=false, failure="Timeout"`.
     - `exitCode == PROCESS_EXECUTION_EXCEPTION_CODE` (`:14`) →
       `ok=false, failure="Not executable"`.
     - Any other exit code (including non-zero) proceeds — luacov's `--help` and old
       interpreters may exit non-zero yet print the banner (rule from
       `tool/LuaToolValidator.kt:144-151`, which only rejects the two sentinels).
  4. **Stream merge** (exact algorithm of `tool/LuaToolValidator.kt:154-160`):
     `merged = trim(stdout) + ("\n" if both non-empty) + trim(stderr)` — stdout first.
     Empty merged output → `ok=false, failure` = first non-blank merged line (here none, so a
     fixed no-output marker for the empty case).
  5. Hand off to `interpret(merged, kind, file.lastModified())`:
  6. **Version**: `kind.probe.versionRegex.find(merged)` → `groupValues[1]`. No match →
     `ok=false`, `version=null`, `failure` = first non-blank line of `merged` (the tool's own
     output, per the taxonomy's fallback branch), stop.
  7. **Lua-compat capture** (order: after version, independent of it):
     `kind.probe.luaVersionRegex?.find(merged)?.groupValues?.get(1)` → `luaVersion` (or null).
  8. **Runtime** (only when `kind.probe.runtime != null`):
     a. `bannerLine` = the full line of `merged` containing the §-6 version match
        (`merged.lineSequence().first { versionRegex.containsMatchIn(it) }`). This subsumes
        legacy `Banner.create(ProcessOutput)`'s "prefer stderr, first line" heuristic
        (`platform/LuaInterpreterService.kt:190-199`) — the merged+match-line rule finds the
        banner wherever the tool printed it.
     b. `firstToken = bannerLine.trim().substringBefore(' ')` (token 1 of the legacy
        `Banner.VERSION_PATTERN ^(\S+)\s+(\S+).*$`, `platform/LuaInterpreterService.kt:178`).
     c. `firstToken != runtime.productToken` → `ok=false`, `runtime=null`, `failure` = first
        non-blank line of `merged` (the mismatching banner line, per the taxonomy fallback),
        stop. (Replaces the family product check at `platform/LuaInterpreterService.kt:105-108`;
        the expected-vs-actual detail is derivable from that banner line.)
     d. Else `runtimeInfo = LuaRuntimeInfo(productToken, version, levelOf(version), platform,
        bannerLine)` where `levelOf` evaluates the `LanguageLevelRule`: `Fixed` → its level;
        `ByVersionPrefix` → first `(prefix, level)` with `version.startsWith(prefix)` in
        declaration order, else `fallback`.
  9. **Minimum version**: if `kind.minVersion != null`:
     `parsed = SemanticVersion.parse(version)`; if `parsed == null` → **skip** the check
     (unparseable version cannot fail it; version string is still stored raw); else if
     `parsed < kind.minVersion` → `ok=false`, `version` still stored, `failure` = first
     non-blank line of `merged` (the tool's version banner, per the taxonomy fallback).
  10. Success → `LuaToolProbeResult(ok=true, version, luaVersion, runtime, failure=null)`; the
      registry derives `LuaToolHealth(fileExists=true, executable=true, probeOk=true,
      probedAtMtime=file.lastModified(), reason=null)`.
- **Rules / edge handling**: every failure path derives `fileExists`/`executable` truthfully on
  the boundary health; the interpretation helper is pure (String in → `LuaToolProbeResult` out)
  so TCs 1-8 run without processes.

### 3.5 Kind inference (`LuaToolKindRegistry.inferKind`)

- **Input → Output**: `"LUA5.4.EXE"` → `lua` kind; `"luarocks.bat"` → `luarocks`; `"foo"` → null.
- **Steps**:
  1. `base = fileName.lowercase()`, then strip **one** suffix, first match of
     `.exe` / `.bat` / `.cmd` (extends `tool/LuaToolManager.kt:204-217`, which handles the
     same three).
  2. Pass 1 (exact): first kind in `BUILT_IN` order with a **non-glob** `binaryName` whose
     lowercase equals `base`.
  3. Pass 2 (glob): first kind with a glob `binaryName` where
     `patternFromGlob(glob.lowercase()).matcher(base).matches()`.
  4. Else `null`.
- **Rules**: `BUILT_IN` order (§4.1) is the deterministic tiebreak. Exact-first guarantees
  `luajit` never falls into a `lua` glob (and the shipped lua globs `lua5.*`/`lua-5.*` cannot
  match `luajit` anyway).

### 3.6 Semantic-version parse & comparison (`SemanticVersion`)

Semantics identical to `tool/LuaToolValidator.kt:183-208` (covered today by
`LuaToolValidatorTest`), spelled out:

- **parse(s)**:
  1. `clean = s.substringBefore('-').trim()` — pre-release/build suffix after the first `-`
     is ignored (`"3.11.0-1"` → `"3.11.0"`).
  2. `parts = clean.split('.')`.
  3. `major = parts[0].toInt()`; absence or `NumberFormatException` (including Int overflow)
     → return `null`.
  4. `minor = parts.getOrNull(1)?.toInt() ?: 0`; `patch = parts.getOrNull(2)?.toInt() ?: 0`
     — missing components default to 0; a present-but-non-numeric component →
     `NumberFormatException` → `null`.
  5. Components beyond the third are ignored.
- **compareTo(other)**: compare `major` numerically; if equal, `minor`; if equal, `patch`.
  Purely numeric — `3.9.2 < 3.11.0` (never lexicographic).
- **Edge**: LuaJIT rolling versions (`2.1.1700008891`) parse while the patch fits `Int`
  (max 2 147 483 647); on overflow `parse` returns `null` and every consumer's null-path
  applies (min-version check skipped per §3.4 step 9). Only `luarocks` carries a
  `minVersion`, and LuaRocks versions are small — acceptable by construction.

### 3.7 Bean ↔ model mapping (persistence)

Deterministic two-way mapping between `RegisteredToolState` (§6) and `LuaRegisteredTool`:

- `""` string ↔ `null` for `version`, `luaVersion`, `environmentId`, `reason`,
  `banner`; `0L` ↔ `null` for `probedAtMtime`.
- `probeStatus` enum ↔ `probeOk`: `NEVER` ↔ `null`, `OK` ↔ `true`, `FAILED` ↔ `false`.
- `runtime`: reconstructed iff `product != ""` — then `languageLevel` /`platform` parse via
  `enumValueOf`, defaulting to `LUA54`/`STANDARD` on an unknown persisted name (forward
  compatibility), and `runtimeVersion`/`banner` fill the rest.
- `origin` persists as the `Origin` enum directly (enum-in-state precedent:
  `LuaProjectSettings.State.interpreterMode`, `settings/LuaProjectSettings.kt:62`).

## 4. External Data & Parsing

The probe consumes CLI output — the formats below are normative.

### 4.1 Built-in kind table (complete, normative)

Regex sources cited from the legacy code each replaces. All version regexes are applied with
`find()` against the **merged** output (§3.4 step 4); flags noted inline
(`RegexOption.IGNORE_CASE` = "CI"). `binaryNames` are base names — Windows variants come from
§3.3, glob matching from §3.2 pass 2. All 8 ship with `provisioning = emptyList()`.

| # | id | displayName | binaryNames | capabilities | probe args | timeout | versionRegex | runtime (product / platform / level rule) | minVersion | luaVersionRegex |
|---|----|-------------|-------------|--------------|-----------|---------|--------------|-------------------------------------------|------------|-----------------|
| 1 | `lua` | Lua | `lua`, `lua5.*`, `lua-5.*` | RUNTIME | `-v` | 10 s | `Lua\s+(\d+\.\d+(?:\.\d+)?)` | `Lua` / `STANDARD` / ByVersionPrefix `5.1→LUA51, 5.2→LUA52, 5.3→LUA53, 5.4→LUA54, 5.5→LUA55`, fallback `LUA50` (verbatim from `platform/LuaInterpreter.kt:109-118`) | — | — |
| 2 | `luajit` | LuaJIT | `luajit`, `luajit-2.*`, `luajit2.*` | RUNTIME | `-v` | 10 s | `LuaJIT\s+(\d[\w.]*)` | `LuaJIT` / `LUAJIT` / Fixed `LUA51` (leveler from `platform/LuaInterpreter.kt:128`; platform **deliberately changed** from the legacy family's `STANDARD` at `:127` to `LuaPlatform.LUAJIT`, which `PlatformVersionRegistry` fully supports at `platform/target/PlatformVersionRegistry.kt:23-26` — the legacy value loses the LuaJIT stdlib/luacheck mapping) | — | — |
| 3 | `tarantool` | Tarantool | `tarantool` | RUNTIME | `-v` | 10 s | `Tarantool\s+(\S+)` | `Tarantool` / `TARANTOOL` / Fixed `LUA51` (from `platform/LuaInterpreter.kt:130-139`; kept so replacing `findInterpreters` does not lose the family) | — | — |
| 4 | `luarocks` | LuaRocks | `luarocks` | PACKAGE_MANAGER | `--version` | 10 s | `LuaRocks\s+(\S+)` CI (from `tool/LuaToolValidator.kt:32`; CI so the `/usr/bin/luarocks 3.11.0` path-line also matches) | — | `3.0.0` (`tool/LuaToolValidator.kt:68`) | `for Lua\s+([\d.]+)` (`tool/LuaToolValidator.kt:62`) |
| 5 | `luacheck` | luacheck | `luacheck` | LINTER | `--version` | 10 s | `[Ll]uacheck[:\s]+(\S+)` (`tool/LuaToolValidator.kt:38`) | — | — | — |
| 6 | `stylua` | StyLua | `stylua` | FORMATTER | `--version` | 10 s | `stylua\s+(\S+)` CI (`tool/LuaToolValidator.kt:44`) | — | — | — |
| 7 | `luacov` | LuaCov | `luacov` | COVERAGE | `--help` (luacov has no `--version` — `tool/LuaToolValidator.kt:179`) | 10 s | `LuaCov\s+(\S+)` CI (`tool/LuaToolValidator.kt:56`) | — | — | — |
| 8 | `busted` | Busted | `busted` | TEST_RUNNER | `--version` | 10 s | `(?:busted\s+)?(\d[\w.\-]*)` CI — **hardened** from the legacy `(?:busted\s+)?(\S+)` (`tool/LuaToolValidator.kt:50`), whose bare `(\S+)` would capture the first word of *any* error text; the leading-digit anchor still matches busted's bare `2.2.0` output while rejecting prose | — | — | — |

Display names 4-8 from `tool/LuaToolManager.displayNameFor` (`tool/LuaToolManager.kt:219-225`);
1-3 from `LuaInterpreterFamily.interpreterName` (`platform/LuaInterpreter.kt:102,121,131`).
Sample outputs each regex must accept (used verbatim in TCs 1-8):

```
Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio          → 5.4.6   (stdout, 5.4+)
Lua 5.1.5  Copyright (C) 1994-2012 Lua.org, PUC-Rio          → 5.1.5   (stderr, ≤5.3)
LuaJIT 2.1.1700008891 -- Copyright (C) 2005-2023 Mike Pall.  → 2.1.1700008891
/usr/local/bin/luarocks 3.11.0                               → 3.11.0  (CI match on path line)
Luacheck: 0.26.0                                             → 0.26.0
stylua 0.20.0                                                → 0.20.0
LuaCov 0.15.0 - coverage analyzer for Lua                    → 0.15.0  (inside --help text)
2.2.0                                                        → 2.2.0   (busted prints bare)
```

### 4.2 Adding a future kind (TOOLING-01-14, worked example)

Append one literal to `LuaToolKindRegistry.BUILT_IN` — nothing else changes (discovery,
probing, inference, persistence, and events are all descriptor-generic):

```kotlin
LuaToolKind(
    id = "redis-cli", displayName = "Redis CLI",
    binaryNames = listOf("redis-cli"),
    probe = ProbeSpec(args = listOf("--version"),
        versionRegex = Regex("""redis-cli\s+(\S+)""", RegexOption.IGNORE_CASE)),
    capabilities = setOf(),   // add a Capability value only if a resolver must query it
)
```

A `lua-language-server` kind follows the same pattern
(`--version` → `3.9.3` bare token, regex `(\d[\w.\-]*)`). Adding a *Capability* enum value is a
one-line model change consumed only by TOOLING-02's resolver queries.

### 4.3 Persisted XML (`lunar.xml`)

Produced by the platform serializer from §6's beans — shape (informative):

```xml
<component name="LuaToolchainRegistry">
  <option name="tools">
    <list>
      <RegisteredToolState>
        <option name="id" value="7f3a…"/> <option name="kindId" value="lua"/>
        <option name="path" value="/usr/bin/lua5.4"/> <option name="version" value="5.4.6"/>
        <option name="product" value="Lua"/> <option name="languageLevel" value="LUA54"/>
        <option name="platform" value="STANDARD"/> <option name="origin" value="DISCOVERED"/>
        <option name="probeStatus" value="OK"/> <option name="probedAtMtime" value="1719…"/>
        …
      </RegisteredToolState>
    </list>
  </option>
  <option name="globalBindings"><map><entry key="stylua" value="9c1b…"/></map></option>
</component>
```

Failure handling: the serializer tolerates missing options (defaults apply); an unknown
persisted enum name maps per §3.7's forward-compat defaults.

## 5. Data Flow

### Example 1: Auto-discover on a Linux box with `/usr/bin/lua5.4`, `/usr/bin/luarocks`, and a `/usr/local/bin/luarocks` symlink to it

1. Caller (test now; Toolchain UI in TOOLING-06) invokes
   `LuaToolchainRegistry.getInstance().autoDiscover()` on a pooled thread.
2. `LuaToolDiscovery.discoverAll`: PATH pass-1 exact finds `luarocks` (claims canonical
   `/usr/bin/luarocks`; the `/usr/local/bin` symlink dedups away); pass-2 glob `lua5.*`
   claims `/usr/bin/lua5.4` for the `lua` kind.
3. For each candidate, `registerTool(path, kind.id, origin=DISCOVERED)`:
   `LuaToolProbe.probe` runs `lua5.4 -v` → merged `Lua 5.4.6  Copyright …` → version
   `5.4.6`, runtime `(Lua, 5.4.6, LUA54, STANDARD)`; `luarocks --version` → `3.11.0`.
4. Each registration appends a bean, then fires `TOOL_REGISTERED` on the app bus.
5. State persists to `lunar.xml` on the platform's save cycle.

### Example 2: Manual add of a broken binary

1. User path `/opt/oldrocks/luarocks` (exists, executable, prints `LuaRocks 2.4.4`).
2. `registerTool(path)` → inference (§3.5) → `luarocks` kind → probe → version `2.4.4`,
   `ok=false` (below minVersion 3.0.0), `failure="LuaRocks 2.4.4"` (first non-blank merged line,
   per the §3.4 taxonomy) → derived health `probeOk=false, reason="LuaRocks 2.4.4"`.
3. Tool **is** in the inventory (visible-but-unusable), `TOOL_REGISTERED` fired;
   `isUsable == false` keeps TOOLING-02's future resolver from picking it.

### Example 3: Binary deleted after registration

1. `tools()` keeps returning the last-probed health — no disk I/O on read (TOOLING-01-13).
2. An explicit `refreshTool(id)` (TOOLING-07's monitor, or the UI's Re-check) probes again →
   `fileExists=false, probeOk=null` per §3.4 step 1 → `TOOL_UPDATED` fired → subscribers
   (e.g. the terminal-env cache, once TOOLING-05 rewires it) invalidate.

## 6. Persistence Design

The top-level `LuaToolchainAppState` (contract §7, §10.1 — **not** a nested `.State`) holds
`tools: List<RegisteredToolState>` (the tag is named `tools`, **not** the legacy `toolInventory`,
so old/new components deserialize without same-name/different-shape clashes),
`globalBindings: Map<String,String>` (kindId → toolId), and `kindOptions: Map<String,String>`.
Its beans follow the repo's serializer rules — mutable fields with defaults (rule documented at
`tool/LuaTool.kt:8-11`), string-keyed maps (rationale comment at
`settings/LuaApplicationSettings.kt:48-53`), wrapper-bean-for-rich-model pattern
(`TargetState`, `settings/LuaProjectSettings.kt:30-48`):

`kindOptions: Map<String,String>` is part of `LuaToolchainAppState` per contract §7 (it ships in
this feature's state class, empty by default; accessors are `setKindOption(key, value?)` /
`kindOption(key)` per contract §10.1, firing `KIND_OPTION_CHANGED`); the concrete option keys,
values, and UI arrive with TOOLING-02/-06.

```kotlin
class RegisteredToolState {
    var id: String = ""
    var kindId: String = ""
    var path: String = ""
    var version: String = ""            // "" = null
    var luaVersion: String = ""
    var product: String = ""            // "" = no runtime info
    var runtimeVersion: String = ""
    var languageLevel: String = ""      // LuaLanguageLevel.name
    var platform: String = ""           // LuaPlatform.name
    var banner: String = ""
    var origin: Origin = Origin.MANUAL
    var environmentId: String = ""
    var fileExists: Boolean = false
    var executable: Boolean = false
    var probeStatus: ProbeStatus = ProbeStatus.NEVER   // NEVER | OK | FAILED  (↔ probeOk tri-state)
    var probedAtMtime: Long = 0
    var reason: String = ""
}
enum class ProbeStatus { NEVER, OK, FAILED }
```

Storage file `lunar.xml` (same file, distinct `@State(name)` → distinct `<component>` element;
`LuaCheckSettings` at `analysis/luacheck/LuaCheckSettings.kt:8-12` is the precedent for a
second component in that file). **Clean break** (PRD Resolved Decision 2): `loadState` never
reads the legacy `LuaApplicationSettings` fields (`interpreters` `:39`, `toolInventory` `:45`,
`globalToolBindings` `:53`) — no migration code exists, per de-risking item **TOOLING-00-06**
(clean-break serialization); those fields and their consumers
(`LuaApplicationSettings.findInterpreter`/`validInterpreters` `:71-77`,
`LuaInterpreterComponent.kt:37`, `LuaInterpretersTable.kt:156`) are deleted by TOOLING-05.

## 7. Edge Cases

- **Symlinked duplicates** — canonical-path dedup in both discovery (§3.2) and registration
  (§3.1 step 5); TC 12/16.
- **`lua` name that is actually LuaJIT** — glob/exact claims assign the `lua` kind, but the
  probe's product-token check fails it with an explanatory reason (§3.4 step 8c, TC 8);
  the user re-adds it with the `luajit` hint (UI affordance is TOOLING-06).
- **Same binary, two kinds** — allowed (distinct `(canonical, kindId)` keys); harmless and
  it makes hererocks-style multi-symlink layouts representable.
- **Spaces/unicode in paths** — `GeneralCommandLine` handles quoting; no string-splitting of
  paths anywhere in the design.
- **No PATH env var** — `getPathVariableValue()` null-safe; discovery degrades to well-known
  dirs.
- **Windows** — candidate expansion (§3.3) and suffix-stripped glob matching (§3.2) are pure
  functions parameterized on `windows:`, unit-tested without a Windows host (pattern proven by
  `LuaToolDescriptorTest`-style tests of `candidates(windows = true)`).
- **Probe writes garbage / multi-line banners** — regex `find` over merged text; the
  banner-line rule picks the matching line only (§3.4 step 8a).
- **Concurrent mutations** — single `stateLock`; events published outside the lock to avoid
  listener re-entrancy deadlocks.
- **Registry read during a probe** — reads snapshot the last committed state; a probe in
  flight is invisible until its `synchronized` commit.

## 8. Integration Points

New `plugin.xml` entries (exactly these — nothing else registers; discovery/probe/kind
registry are plain objects; the listener topic needs no declaration since subscribers connect
programmatically, as `LuaTerminalEnvironmentService` does today at
`tool/LuaTerminalEnvironmentService.kt:40-49`):

```xml
<!-- src/main/resources/META-INF/plugin.xml, inside
     <extensions defaultExtensionNs="com.intellij"> — placed next to the legacy registration
     it will replace (LuaToolManager, plugin.xml:421-423; precedent style plugin.xml:418-419) -->

<!-- Unified toolchain registry (TOOLING-01) -->
<applicationService
        serviceImplementation="net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry" />
```

`LuaToolchainRegistry` also carries `@Service(Service.Level.APP)` — matching how
`LuaToolManager` (`tool/LuaToolManager.kt:27-28` + `plugin.xml:421-423`) and
`LuaApplicationSettings` (`settings/LuaApplicationSettings.kt:29` + `plugin.xml:418-419`) are
registered today (annotation + explicit entry). No configurables, actions, startup activities,
or indexes in this feature (UI is TOOLING-06; monitoring is TOOLING-07).

## 9. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-01-01 kind descriptor | M | §2.1 |
| TOOLING-01-02 built-in registry | M | §2.2, §4.1, §3.5 |
| TOOLING-01-03 tool & health model | M | §2.3 |
| TOOLING-01-04 runtime info | M | §2.3, §3.4 steps 6-8, §4.1 runtime column |
| TOOLING-01-05 semantic version | M | §2.8, §3.6 |
| TOOLING-01-06 inventory CRUD | M | §2.4, §3.1 |
| TOOLING-01-07 global bindings | M | §2.4 (`setGlobalBinding`/`globalBindings`), §6 |
| TOOLING-01-08 change events | M | §2.5, §3.1 step 6 |
| TOOLING-01-09 unified discovery | M | §2.6, §3.2, §3.3 |
| TOOLING-01-10 unified probe | M | §2.7, §3.4, §4.1 |
| TOOLING-01-11 kind inference | M | §3.5 |
| TOOLING-01-12 persistence, clean break | M | §2.4 State, §3.7, §4.3, §6 |
| TOOLING-01-13 pure reads & threading | M | §2.4 threading, §5 Example 3, §7 concurrency |
| TOOLING-01-14 data-only extensibility | S | §4.2 |
| TOOLING-01-15 Lua-compat capture | S | §2.3 note, §3.4 step 7, §4.1 luarocks row |

## 10. Alternatives Considered

- **Extend `LuaToolManager`/`LuaToolType` instead of replacing** — rejected by the epic's
  Resolved Decision 4 (descriptor-driven kinds) and Motivation: the enum forces code changes
  in five files per kind and cannot express RUNTIME metadata.
- **Store new state inside `LuaApplicationSettings.State`** — rejected: the legacy field is
  literally named `toolInventory` (`settings/LuaApplicationSettings.kt:45`), so coexistence
  during Waves 01-04 would force awkward renames; a separate component keeps the clean break
  clean and lets TOOLING-05 delete legacy fields without touching the new state.
- **Make `LuaRegisteredTool` a mutable persisted bean (like `LuaTool`)** — rejected:
  immutability-first (engineering contract) and it is exactly the mutable-bean design that
  enabled today's mutating `getTools()`; the bean/model split (§3.7) costs one mapping
  function and buys pure reads.
- **Reject binaries whose probe fails** — rejected: invisible failures were a core TOOL-epic
  complaint (PRD Use Case 4); registering with failing health gives TOOLING-06/07 something
  to show and fix.
- **Per-kind Java `Pattern` + merged-stream logic left in one `when(type)`** — rejected:
  that *is* `LuaToolValidator.patternFor` (`tool/LuaToolValidator.kt:166-180`); moving the
  data into `ProbeSpec` is the whole point of descriptor-driven kinds.
- **Stubbing an EP (`com.intellij.…extensionPoint`) for third-party kinds now** — deferred:
  YAGNI until an external consumer exists; `BUILT_IN` as a data list already satisfies
  TOOLING-01-14, and an EP can wrap it later without model changes.

## Open Questions

_None — the planning bar is cleared. Provisioning specifics (download URLs, feeds, build sequences) are out of scope here and tracked as TOOLING-00-01…-06 de-risking items feeding TOOLING-04; §2.1's `ProvisioningSpec` is the only, strategy-agnostic, contact surface._