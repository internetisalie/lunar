---
id: "TOOLING-ARCH"
title: "Architecture Contract"
type: "spec"
parent_id: "TOOLING"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING Architecture Contract

The cross-feature contract for the unified toolchain subsystem. Feature designs
(`01`–`07`) MUST use these names, packages, and responsibility boundaries; deviations
require updating this document first. Existing-code references cite the current (legacy)
implementation this epic replaces.

## 1. Package layout

All new code lives under `net.internetisalie.lunar.toolchain`:

| Package | Contents |
|---|---|
| `toolchain.model` | `LuaToolKind`, `LuaRegisteredTool`, `LuaToolHealth`, `LuaRuntimeInfo`, `LuaEnvironmentState` |
| `toolchain.registry` | `LuaToolchainRegistry` (APP service), `LuaToolchainListener` topic |
| `toolchain.discovery` | `LuaToolDiscovery` — the single PATH/well-known-dir scanner; environment detection/adoption |
| `toolchain.probe` | `LuaToolProbe` — the single version/banner probe engine |
| `toolchain.resolve` | `LuaToolResolver` — the single "which binary for kind X in project P" |
| `toolchain.exec` | `LuaToolExecutionService`, `LuaExecutionEnvironmentBuilder` (PATH/LUA_PATH/LUA_CPATH) |
| `toolchain.provision` | `LuaToolProvisioner` + strategies (source build, release binary, luarocks install) |
| `toolchain.health` | health checker/monitor/banners (evolves `tool/health/*`) |
| `toolchain.ui` | consolidated configurables |
| `toolchain.terminal` | shell customizer (evolves `tool/terminal/LuaShellExecOptionsCustomizer`) |

Legacy packages deleted by TOOLING-05: `tool/` (all), `platform/LuaInterpreterService`
(deleted outright — 05 verified `Target`/`PlatformVersionRegistry` don't reference it; its
family/banner/leveler data survives in TOOLING-01 kind descriptors), `util/LuaProcessUtil`
(absorbed into `toolchain.exec`), `rocks/env/` hererocks classes,
`LuaCheckSettings.executablePath`, `LuaRocksSettings.executablePath`,
`command/LuaCommandLine` env patching (absorbed into `toolchain.exec`). Surviving
components relocate: matrix runner → `rocks/matrix` (consumes TOOLING-02 environments);
the env status-bar widget → `toolchain.ui` (rewired consumer, not deleted).

## 2. Core model

### 2.1 `LuaToolKind` — descriptor, not enum

Replaces `LuaToolType` (closed enum, `tool/LuaToolDescriptor.kt:19`) and the interpreter
`LuaInterpreterFamily` (`platform/LuaInterpreter.kt`). A kind is **data**:

```kotlin
data class LuaToolKind(
    val id: String,                      // stable key; built-ins: lua, luajit, tarantool,
                                         // luarocks, luacheck, stylua, luacov, busted
    val displayName: String,
    val binaryNames: List<String>,       // base names; platform variants derived (.exe/.bat/.cmd)
    val probe: ProbeSpec,                // args (e.g. ["--version"]), version regex, banner regex
    val capabilities: Set<Capability>,   // RUNTIME, PACKAGE_MANAGER, LINTER, FORMATTER, TEST_RUNNER, ...
    val minVersion: SemanticVersion?,    // enforced at registration (fixes dead meetsMinimumVersion)
    val provisioning: List<ProvisioningSpec>, // ordered strategies; empty = discover/manual only
)
```

Built-in kinds ship as a data list in `LuaToolKindRegistry`; the set is extensible without
touching registry/UI/resolver code. Interpreters are kinds with `Capability.RUNTIME` whose
probe fills `LuaRuntimeInfo` (product/version/languageLevel/platform → feeds the existing
`Target` model, which this epic does NOT change).

### 2.2 `LuaRegisteredTool` — one inventory entry type

Replaces both `LuaTool` (`tool/LuaTool.kt`) and `LuaInterpreter`-as-inventory-entry
(`LuaApplicationSettings.State.interpreters`):

```kotlin
data class LuaRegisteredTool(
    val id: String,                  // UUID
    val kindId: String,
    val path: String,
    val version: String?,            // probed
    val luaVersion: String?,         // linked-Lua hint (LuaRocks "for Lua X.Y"; ProbeSpec.luaVersionRegex)
    val runtime: LuaRuntimeInfo?,    // RUNTIME kinds only
    val origin: Origin,              // DISCOVERED | MANUAL | PROVISIONED
    val environmentId: String?,      // set when the tool belongs to an environment
                                     // (adopted envs: origin=DISCOVERED with environmentId)
    val health: LuaToolHealth,
)
```

### 2.3 `LuaToolHealth` — no more ambiguous `isValid`

Separates the states today conflated in `LuaTool.isValid` (`tool/LuaTool.kt:41`):

```kotlin
data class LuaToolHealth(
    val fileExists: Boolean,
    val executable: Boolean,
    val probeOk: Boolean?,           // null = never probed
    val probedAtMtime: Long?,        // mtime gate (kept from TOOL-03)
    val reason: String?,
)
val LuaRegisteredTool.isUsable get() = health.fileExists && health.executable && health.probeOk != false
```

Reads never mutate health (fixes the mutating `getTools()` getter,
`tool/LuaToolManager.kt:110-120`); re-checks are explicit and background-only.

### 2.4 `LuaEnvironmentState` — provisioned toolchain set

Replaces `HererocksEnvState` + `interpreterMode` + `explicitInterpreter/explicitTarget`
overlays (`settings/LuaProjectSettings.kt`, ROCKS-14/15/16):

```kotlin
data class LuaEnvironmentState(
    val id: String,
    val name: String,
    val rootDir: String,
    val toolIds: List<String>,       // registered tools provisioned into this env
)
```

**The ROCKS-16 mode state machine is deleted.** Precedence (§3) makes it unnecessary: an
*active* environment simply wins resolution; deactivating it falls back to explicit
bindings. No stashed overlays.

## 3. Resolution — exactly one precedence, one implementation

`LuaToolResolver.resolve(project: Project?, kindId: String): LuaRegisteredTool?`, plus a
capability-based `resolveRuntime(project): LuaRegisteredTool?` (first RUNTIME-capability
kind per the same precedence — consumed by run configs, console, and the interpreter
dropdown) and a strict per-environment overload `resolveIn(env, kindId): LuaRegisteredTool?`
(no fallback — matrix rows fail loudly; see §10.4):

1. Active environment's tool of that kind (project state `activeEnvironmentId`).
2. Project binding (`bindings[kindId]`) if usable.
3. Global binding if usable.
4. First usable inventory entry of the kind.
5. `null` (callers surface a kind-specific "configure" hint; no hardcoded default paths —
   deletes `LuaCheckSettings` default `/usr/local/bin/luacheck`).

Every consumer goes through this — including the four that bypass today's model:
luacheck (`analysis/luacheck/LuaCheckCommandLine.kt:17`), luarocks run configs
(`rocks/run/LuaRocksRunConfiguration.kt:208`), hererocks locator, and the run-config
interpreter dropdown (which lists RUNTIME-kind tools).

## 4. Events

One app-level topic `LuaToolchainListener.TOPIC` fired by **every** registry/binding/env
mutation (fixes: `registerTool` today fires nothing → stale
`LuaTerminalEnvironmentService` cache). Project-level `LuaSettingsChangedListener.TOPIC`
(`settings/LuaSettingsChangedEvent.kt`) remains for non-toolchain settings; toolchain
consumers subscribe to the new topic only.

**Pinned shape (authoritative — TOOLING-01 and -02 both use exactly this, declared once
here):**

```kotlin
// package net.internetisalie.lunar.toolchain.registry
const val TOPIC_ID = "lunar.toolchain.changed"       // lunar-prefixed, matches plugin id

enum class LuaToolchainChange {
    TOOL_REGISTERED, TOOL_UPDATED, TOOL_REMOVED,                          // inventory + health
    GLOBAL_BINDING_CHANGED, PROJECT_BINDING_CHANGED,                      // bindings
    ENVIRONMENT_ADDED, ENVIRONMENT_UPDATED, ENVIRONMENT_REMOVED,          // environments
    ACTIVE_ENVIRONMENT_CHANGED,
    KIND_OPTION_CHANGED,                                                  // kindOptions
}

data class LuaToolchainEvent(
    val change: LuaToolchainChange,
    val project: Project?,        // null for app-scoped changes
    val kindId: String? = null,
    val toolId: String? = null,
    val environmentId: String? = null,
    val optionKey: String? = null,
)

interface LuaToolchainListener {
    fun toolchainChanged(event: LuaToolchainEvent)
    companion object { val TOPIC = Topic.create(TOPIC_ID, LuaToolchainListener::class.java) }
}
```

Mutators fire a `LuaToolchainEvent` with the matching `change` and populated ids; a mutation
that changes nothing (no-op) fires no event.

## 5. Execution & injection

- `LuaToolExecutionService` — the only subprocess entry point (absorbs
  `util/LuaProcessUtil`, direct `CapturingProcessHandler` in stylua, `ProcessHandlerFactory`
  in matrix runner). Capture and stream modes; explicit timeout classes (PROBE=10s,
  COMMAND=15s default, FORMAT=30s, NETWORK=120s, INSTALL=600s — values grounded in the
  legacy constants they replace); EDT guard; cancellation. Outcomes are an explicit enum
  (COMPLETED/TIMED_OUT/START_FAILED/CANCELLED), replacing `LuaProcessUtil`'s −1/−2
  exit-code sentinels.
- `LuaExecutionEnvironmentBuilder.forProject(project)` → `{PATH prepend dirs, LUA_PATH,
  LUA_CPATH}` computed once, consumed by: Lua run configs, LuaRocks run configs (fixes
  missing PATH prepend), test/console/coverage states, external annotators, and the
  terminal customizer (`prependEntryToPATH` mechanics unchanged).

## 6. Provisioning (native hererocks replacement)

`LuaToolProvisioner.provision(project, request)` where `request` names an environment
(name, rootDir) and a set of `(kindId, versionSpec)`. Per-kind `ProvisioningSpec`
strategies, tried in order:

- `ReleaseBinaryStrategy` — download prebuilt asset (URL template + checksum per platform),
  extract via platform `Decompressor`.
- `SourceBuildStrategy` — download source tarball, compile (PUC Lua: direct compiler
  invocation à la hererocks; LuaJIT: make/msvcbuild). Requires host toolchain detection.
- `LuaRocksInstallStrategy` — `luarocks install <rock>` into the environment tree (requires
  the env's PACKAGE_MANAGER tool; used for luacheck/busted/luacov).

Downloads via `com.intellij.util.io.HttpRequests` with `ProgressIndicator`; shared cache dir
for downloaded artifacts. Windows convention: a provisioned env's LuaRocks config lives at
`<rootDir>/luarocks-config.lua`, derivable by TOOLING-03's env builder (`LUAROCKS_CONFIG`). Exact URL templates, version feeds, build command sequences, and
the Windows story are specified in TOOLING-04's design, informed by the hererocks dossier
(research doc) and de-risked by TOOLING-00 spikes.

## 7. Persistence (clean break)

- State classes: **`LuaToolchainAppState`** (app — held by `LuaToolchainRegistry`, which is
  itself the `PersistentStateComponent<LuaToolchainAppState>`; a NEW component, not fields
  on `LuaApplicationSettings`, so old/new can coexist until the TOOLING-05 deletion) and
  **`LuaToolchainProjectState`** (project), with nested serializable `RegisteredToolState`
  / `ToolEnvironmentState` records (per TOOLING-00/-01 deviation review, 2026-07-05).
  `LuaToolKindRegistry` lives in `toolchain.registry`.
- **App** (`lunar.xml`): `tools: List<RegisteredToolState>` (named `tools`, NOT reusing the
  legacy `toolInventory` tag name — avoids same-name/different-shape deserialization
  clashes; TOOLING-00-06 verifies stale-tag tolerance), `globalBindings:
  Map<String,String>` (kindId → toolId). Legacy `interpreters`, `toolInventory`,
  `globalToolBindings` fields are **deleted**, not migrated. App state also carries
  `kindOptions: Map<String,String>` (keys like `luacheck.arguments`, `luarocks.serverUrl`)
  — the app-level defaults surfaced on the Toolchain page (§8).
- **Project** (`.idea/lunar.xml`): `bindings: Map<String,String>`, `environments:
  List<LuaEnvironmentState>`, `activeEnvironmentId: String?`, plus retained non-path
  options (`luacheckArguments`, `rocksServerUrl` — relocated, kind-scoped). Legacy
  `interpreter`, `interpreterMode`, `explicitInterpreter/Target`, `hererocksEnv(s)`,
  `activeEnvId`, `projectToolBindings` fields deleted; migration code
  (`migrateLegacyEnv`, `migrateInterpreterMode`) deleted.
- The project `interpreter` concept becomes: resolved RUNTIME tool (via §3), from which
  `Target`/language-level derivation continues to work as today
  (`platform/target/Target.kt` unchanged).

## 8. Settings UI target

```
Settings
└─ Languages & Frameworks
   └─ Lua                    (app: language options, as today)
      ├─ Toolchain           (app: one inventory table — all kinds incl. runtimes;
      │                       add / auto-discover / provision / re-check; kind options
      │                       sections: luacheck args, rocks default server)
      └─ Lua Project         (project: active environment, per-kind bindings,
                              rocks server override, source path — as today minus
                              interpreter combo, which becomes a RUNTIME binding)
```

Diagram order is illustrative — the platform sorts sibling configurables alphabetically
("Lua Project" renders above "Toolchain"). Classes: `toolchain.ui.LuaToolchainConfigurable`
(app) and `toolchain.ui.LuaProjectConfigurable` (project — the page's class/ID move out of
`settings/`; the old `LuaProjectSettingsConfigurable` ID is retired, nothing navigates to
it). TOOLING-07 banner links target these new IDs.

Deleted pages: *Lua Tools* (`tool/ui/LuaToolsConfigurable`), *LuaRocks*
(`rocks/run/LuaRocksSettingsConfigurable`), *LuaCheck* executable field
(`analysis/luacheck/LuaCheckSettingsPanel` — its non-path options move under Toolchain).

## 9. Consumer inventory (migration checklist for TOOLING-05)

| Consumer | Today | Target |
|---|---|---|
| Luacheck annotator | `LuaCheckSettings.executablePath` (`analysis/luacheck/LuaCheckCommandLine.kt:17`) | resolver(`luacheck`) + exec service |
| Stylua | `getEffectiveTool(STYLUA)` (`lang/formatting/external/StyluaFormattingService.kt:19`) | resolver(`stylua`), exec service (replaces raw `CapturingProcessHandler`) |
| Busted tests | `getEffectiveTool(BUSTED)` (`run/test/LuaTestCommandLineState.kt:61`) | resolver(`busted`) + env builder |
| LuaRocks browser/actions | `LuaRocksEnvironment.resolveExecutable` (`rocks/LuaRocksEnvironment.kt:49-58`) | resolver(`luarocks`); `withServer` logic retained |
| LuaRocks run config | `LuaRocksSettings.executablePath` (`rocks/run/LuaRocksRunConfiguration.kt:208`) — **bypasses bindings, no PATH prepend** | resolver(`luarocks`) + env builder |
| Lua run config / console / coverage | project `interpreter` + `LuaCommandLine` | resolver(RUNTIME) + env builder |
| Debugger (DBGp attach uses run config) | via run config | unchanged consumers of env builder |
| Terminal | `LuaTerminalEnvironmentService` cache | env builder + new topic |
| Matrix runner | per-env `luarocksExe()` (`rocks/env/matrix/MatrixRunner.kt:46-48`) | per-environment resolution (env's own tools) via resolver overload |
| Rocks browser actions | `LuaRocksSettings.executablePath` (`rocks/browser/LuaRocksActionHandler.kt:33,57`) | resolver(`luarocks`) + exec service |
| Rocks metadata service | `LuaRocksSettings.executablePath` (`rocks/browser/LuaRocksMetadataService.kt:30`) | resolver(`luarocks`) + exec service |
| Workspace build runner | `LuaRocksSettings.executablePath` (`rocks/build/WorkspaceBuildRunner.kt:29`) | resolver(`luarocks`) + env builder |
| New Project wizard | `HererocksProvisioner` + `InterpreterMode` (`rocks/init/LuaRocksGeneratorPeer` / `LuaRocksInterpreterInitializer`) | TOOLING-04 provision request + TOOLING-02 activation |
| Hererocks locator/provisioner/binder/detector | `rocks/env/*` | deleted; replaced by `toolchain.provision` |

## 10. Cross-feature API signatures (authoritative — designs MUST match)

Pinned 2026-07-05 and reconciled across all eight Step-9 reviews. Where a design named a
symbol differently, **this table wins** and the design is corrected. All classes are app or
project **light services** acquired via `getInstance()` / `getInstance(project)` unless
noted.

### 10.1 `toolchain.registry.LuaToolchainRegistry` (APP service — owner: 01)
```kotlin
fun tools(): List<LuaRegisteredTool>
fun toolsOfKind(kindId: String): List<LuaRegisteredTool>   // NOT tools(kindId)
fun tool(id: String): LuaRegisteredTool?                   // by-id lookup (NOT findTool)
fun findByPath(path: String): LuaRegisteredTool?
fun globalBindings(): Map<String, String>                  // kindId -> toolId
fun setGlobalBinding(kindId: String, toolId: String?)
fun registerTool(path: String, kindIdHint: String? = null,
                 origin: Origin = Origin.MANUAL, environmentId: String? = null): LuaRegisteredTool?
fun registerProvisioned(tool: LuaRegisteredTool)           // 04
fun unregisterTool(id: String): Boolean
fun unregisterByEnvironment(environmentId: String)         // 04
fun refreshTool(id: String)                                // re-probe internally + write (fires topic)
fun updateToolCheck(toolId: String, health: LuaToolHealth, // 07 writes a caller-computed result
                    version: String?, luaVersion: String?, runtime: LuaRuntimeInfo?)
fun autoDiscover()
fun setKindOption(key: String, value: String?)             // kindOptions (§7)
fun kindOption(key: String): String?
```
`refreshTool` and `updateToolCheck` coexist: `refreshTool` is the convenience path (registry
probes then writes); `updateToolCheck` lets TOOLING-07's off-thread checker write the result
it already computed. Both fire the topic and **no-op silently when the value is unchanged**.

### 10.2 `toolchain.registry.LuaToolKindRegistry` (APP service — owner: 01)
```kotlin
fun all(): List<LuaToolKind>                 // NOT kinds()
fun findById(id: String): LuaToolKind?       // NOT find()
fun inferKind(binaryName: String): LuaToolKind?
```

### 10.3 `toolchain.probe.LuaToolProbe` (APP service, INTERFACE — owner: 01)
Must be an `interface` (or open class) with `getInstance()`, **not** a Kotlin `object` —
TOOLING-07's tests inject a recording fake.
```kotlin
fun probe(kind: LuaToolKind, binaryPath: Path): LuaToolProbeResult   // (kind, Path) order

data class LuaToolProbeResult(
    val ok: Boolean,
    val version: String?,
    val luaVersion: String?,          // linked-Lua hint (LuaRocks "for Lua X.Y"); refreshes on re-probe
    val runtime: LuaRuntimeInfo?,     // RUNTIME kinds only
    val failure: String?,             // taxonomy: "Timeout" / "Not executable" / first non-blank merged-output line
)
```

### 10.4 `toolchain.resolve.LuaToolResolver` (APP service — owner: 02)
```kotlin
fun resolve(project: Project?, kindId: String): LuaRegisteredTool?
fun resolveDetailed(project: Project?, kindId: String): LuaToolResolution   // Resolved(tool, source) | Unresolved(kindId, skipped)
fun resolveRuntime(project: Project?): LuaRegisteredTool?
fun resolveRuntimeDetailed(project: Project?): LuaToolResolution
fun resolveIn(env: LuaEnvironmentState, kindId: String): LuaRegisteredTool?  // strict, NO fallback (matrix); returns the tool (05 uses ?.path)
fun notConfiguredMessage(kindId: String): String
```

### 10.5 `toolchain.registry.LuaToolchainProjectSettings` (PROJECT service — owner: 02)
A `PersistentStateComponent<LuaToolchainProjectState>` (top-level state class — **not** a
nested `.State`), persisting per §7.
```kotlin
val state: LuaToolchainProjectState          // bindings, environments, activeEnvironmentId, kindOptions
fun bindings(): Map<String, String>
fun setBinding(kindId: String, toolId: String?)
fun environments(): List<LuaEnvironmentState>
fun activeEnvironment(): LuaEnvironmentState?
fun activateEnvironment(environmentId: String): Boolean
fun deactivateEnvironment()
fun upsertEnvironmentAndActivate(env: LuaEnvironmentState): LuaEnvironmentState   // 04; instance is project-scoped
fun removeEnvironment(environmentId: String)                                     // 04
fun setKindOption(key: String, value: String?)
fun kindOption(key: String): String?
```

### 10.6 `toolchain.exec.LuaToolExecutionService` (APP service — owner: 03)
```kotlin
fun capture(cmd: GeneralCommandLine, timeout: LuaExecTimeout,
            stdin: String? = null,                    // fed to the process stdin (stylua --stdin-filepath)
            indicator: ProgressIndicator? = null): LuaExecResult
fun stream(cmd: GeneralCommandLine, listener: ProcessListener,
           timeout: LuaExecTimeout, colored: Boolean = false, indicator: ProgressIndicator? = null)
```
Timeout enum is **`LuaExecTimeout`** (NOT `TimeoutClass`): `PROBE`=10s, `COMMAND`=15s,
`FORMAT`=30s, `NETWORK`=120s, `INSTALL`=600s. `capture` accepts an optional `stdin` string
(written to the process's stdin then closed) — required by the stylua formatter, which pipes
the document text (`StyluaFormattingTask` today uses a raw `CapturingProcessHandler` for
exactly this; TOOLING-05 migrates it onto `capture(cmd, FORMAT, stdin = documentText)`).
Environment map + working directory ride on the `GeneralCommandLine` (`withEnvironment` /
`withWorkDirectory`); there is no `execute(env, workDir)` facade — provisioning (04) builds
the command line with both set.

### 10.7 `toolchain.ui` configurables (owner: 06)
`toolchain.ui.LuaToolchainConfigurable` (app), `toolchain.ui.LuaProjectConfigurable`
(project — replaces `settings.LuaProjectSettingsConfigurable`; old id retired). TOOLING-07
banner links target `LuaToolchainConfigurable`.

### 10.8 Kind-option keys (`toolchain.registry.LuaKindOptionKeys` — owner: 02)
`LUACHECK_ARGUMENTS = "luacheck.arguments"`, `LUAROCKS_SERVER_URL = "luarocks.serverUrl"`.

## 11. Threading & platform rules (binding on all designs)

Unchanged from the engineering contract: discovery/probing/provisioning/health run on
background threads only; UI mutations on EDT; the terminal customizer runs on a background
thread with no read action; registry state reads are cheap and thread-safe; no hard refs to
`Project` in app-level services (project passed per-call, as `LuaToolManager` does today).
