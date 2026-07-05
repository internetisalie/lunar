---
id: "TOOLING-02-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "TOOLING-02"
folders:
  - "[[features/tooling/02-resolution-and-environments/requirements|requirements]]"
---

# Technical Design: TOOLING-02 — Resolution, Binding & Environments

Conforms to the binding [architecture contract](../tooling-architecture.md) (§2.4, §3, §4,
§7). All new code lives under `net.internetisalie.lunar.toolchain`.

## 1. Architecture Overview

### Current State

Four resolution patterns coexist, plus a mode state machine:

- `LuaToolManager.getEffectiveTool(project, type)` (`tool/LuaToolManager.kt:163-177`):
  project binding > global > first valid, with stale ids skipped against the "valid" list;
  `getTools()` mutates health inline (`:110-122`). Bindings are stored as
  `projectToolBindings: MutableMap<String,String>` (`settings/LuaProjectSettings.kt:101`)
  and `globalToolBindings` (`settings/LuaApplicationSettings.kt:53`).
- Luacheck bypasses it entirely: `LuaCheckSettings.State.executablePath` (default
  `/usr/local/bin/luacheck`, `analysis/luacheck/LuaCheckSettings.kt:15`) consumed at
  `analysis/luacheck/LuaCheckCommandLine.kt:17`; its `arguments` field (`:16`) at
  `LuaCheckCommandLine.kt:26`.
- LuaRocks run configs bypass it too: `LuaRocksSettings.State.executablePath`
  (`rocks/run/LuaRocksSettings.kt:30`) consumed at
  `rocks/run/LuaRocksRunConfiguration.kt:208`; the registry server lives twice —
  app `LuaRocksSettings.serverUrl` (`:31`) and project
  `LuaProjectSettings.State.rocksServerUrl` (`settings/LuaProjectSettings.kt:109`), merged
  by `LuaRocksEnvironment.resolveServer` (project non-blank > app non-blank > none,
  `rocks/LuaRocksEnvironment.kt:33`).
- The interpreter is a parallel subsystem: `LuaProjectSettings.State.interpreter`
  (`settings/LuaProjectSettings.kt:53`), consumed by `command/LuaCommandLine.kt:20`,
  `run/test/LuaTestRunConfiguration.kt:183`, `rocks/RockspecBridge.kt:35`, etc.
- **The ROCKS-16 mode machine** owns who writes that interpreter:
  `InterpreterMode` (`settings/LuaProjectSettings.kt:409-412`), the
  `explicitInterpreter`/`explicitTarget` stash (`:76-78`), the mode switch + stash/restore
  logic `setInterpreterModeAndNotify` (`:323-346`) / `restoreExplicitOverlay` (`:353-359`),
  the one-shot `migrateInterpreterMode` (`:185-191`), and `HererocksEnvBinder.bind`
  (`rocks/env/HererocksEnvBinder.kt:39-67`), which conditionally repoints interpreter +
  target (deriving the target via `HererocksEnvState.toTarget`,
  `rocks/env/HererocksEnvState.kt:45-50`, and reloading platform libraries on level change,
  `HererocksEnvBinder.kt:56-59`). Environment records live in
  `hererocksEnvs`/`activeEnvId` (`settings/LuaProjectSettings.kt:124,130`) with lifecycle
  helpers `upsertAndActivate` (`:236`), `setActiveEnvAndNotify` (`:260`), `addEnv`/`removeEnv`
  (`:217,:222`), detection in `HererocksEnvDetector` (`rocks/env/HererocksEnvDetector.kt`)
  + `HererocksDetectStartup` (`rocks/env/HererocksDetectStartup.kt:17`).

### Prior Art in This Repo

Verified components this design **replaces** (deletion itself lands in TOOLING-05):

| Legacy | Location | Fate |
|---|---|---|
| `LuaToolManager.getEffectiveTool` / `getAllValidTools` / `setGlobalBinding` | `tool/LuaToolManager.kt:163,184,188` | Replaced by §2.1 `LuaToolResolver` + §2.9 registry binding mutators; the tier-skip fallback behavior is retained (§3.1) |
| `projectToolBindings` + `setProjectToolBindingAndNotify` | `settings/LuaProjectSettings.kt:101,283` | Replaced by §2.2 state + `setBinding` |
| `InterpreterMode` machine (mode, stash, migration, mode switch) | `settings/LuaProjectSettings.kt:62-78,185-191,323-359,409-412` | **Deleted with no replacement state** — semantics in §3.3/§3.5/§6 |
| `HererocksEnvBinder.bind`/`unbind`/`normalizeDir` | `rocks/env/HererocksEnvBinder.kt:39,76,94` | bind → resolution (§3.1) + target sync (§3.5); unbind → deactivate/remove (§3.4); normalizeDir carried over verbatim |
| `HererocksEnvState` (+ `toTarget`, `normalizedVersionLabel`) | `rocks/env/HererocksEnvState.kt:19,45,58` | Replaced by contract `LuaEnvironmentState`; the target mapping moves to §3.5 keyed on `LuaRuntimeInfo` |
| Env set ops `upsertAndActivate`/`setActiveEnvAndNotify`/`addEnv`/`removeEnv`/`activeEnv`/`resolveAllEnvs` | `settings/LuaProjectSettings.kt:236,260,217,222,213,210` (callers: `rocks/env/HererocksEnvSet.kt:18`, `rocks/env/LuaEnvStatusBarWidget.kt:89`, `rocks/env/HererocksProvisioner.kt:72`) | Replaced by §2.2 lifecycle API (§3.4) |
| `HererocksEnvDetector` + `HererocksDetectStartup` | `rocks/env/HererocksEnvDetector.kt:17-64`, `rocks/env/HererocksDetectStartup.kt:17` | Replaced by §2.6/§2.7/§2.8 (heuristics generalized, §3.6/§3.7) |
| `LuaCheckSettings.arguments`, `LuaRocksSettings.serverUrl`, `LuaProjectSettings.State.rocksServerUrl` | `analysis/luacheck/LuaCheckSettings.kt:16`, `rocks/run/LuaRocksSettings.kt:31`, `settings/LuaProjectSettings.kt:109` | Values relocate to kind-scoped options (§2.4, §3.8); precedence mirrors `LuaRocksEnvironment.resolveServer` (`rocks/LuaRocksEnvironment.kt:33`) |
| `LuaSettingsChangedListener.TOPIC` as the toolchain-cache invalidator | `settings/LuaSettingsChangedEvent.kt:29`, subscriber `tool/LuaTerminalEnvironmentService.kt:41-48` | Toolchain mutations move to `LuaToolchainListener.TOPIC` (§2.3); the project topic remains for non-toolchain settings and is still fired by `setTargetAndNotify` (`settings/LuaProjectSettings.kt:273`) |

**Reused as-is** (grounded, unchanged): `Target` + `getImplicitLanguageLevel`
(`platform/target/Target.kt:16,37`), `PlatformVersionRegistry.findVersion`/`resolveTarget`
(`platform/target/PlatformVersionRegistry.kt:68,81`), `PlatformLibraryIndex.reload`
(`project/PlatformLibraryProvider.kt:105,134`), `LuaProjectSettings.setTargetAndNotify`
(`settings/LuaProjectSettings.kt:273`), `FileUtil.toCanonicalPath` normalization
(`rocks/env/HererocksEnvBinder.kt:94-95`), pooled-thread `FileUtil.delete`
(`rocks/env/HererocksEnvBinder.kt:86-90`), notification group
`notification.group.lunar.tools` (`plugin.xml:543`), test-isolation base pattern
(`src/test/kotlin/net/internetisalie/lunar/rocks/env/EnvSettingsTestCase.kt:13`).

**From TOOLING-01** (contract §2, consumed here): `LuaToolKind` (`id`, `binaryNames`,
`capabilities`), `Capability.RUNTIME`/`PACKAGE_MANAGER`, `LuaToolKindRegistry` (built-in
kind list), `LuaRegisteredTool` (`id`, `kindId`, `path`, `runtime`, `origin`,
`environmentId`, `health`, `isUsable`), `LuaRuntimeInfo` (must expose
`platform: LuaPlatform` and `version: String` — required by §3.5),
`LuaToolchainRegistry` (`tool`, `tools`, `registerTool`, `unregisterTool`,
`unregisterByEnvironment`, app-state `globalBindings: MutableMap<String,String>`).

### Target State

```
                 ┌──────────────────────────────┐  app (lunar.xml)
                 │ LuaToolchainRegistry (T-01)   │  inventory, globalBindings, kindOptions
                 └──────┬───────────────────────┘
   fires LuaToolchainListener.TOPIC (app bus) ── every mutation
                 ┌──────┴───────────────────────┐  project (.idea/lunar.xml)
                 │ LuaToolchainProjectSettings   │  bindings, environments,
                 │  (state + lifecycle + events) │  activeEnvironmentId, kindOptions
                 └──────┬───────────────────────┘
                 ┌──────┴───────────┐
                 │ LuaToolResolver  │  the ONE precedence (stateless, APP)
                 └──────┬───────────┘
      ┌─────────────────┼──────────────────────┐
 consumers (T-03/05)  LuaTargetSynchronizer   matrix runner (resolveIn)
                      (runtime → Target/level)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.toolchain.resolve.LuaToolResolver`
- **Responsibility**: the single precedence implementation (contract §3) and its
  capability/runtime and per-environment variants.
- **Threading**: any thread; reads in-memory registry/project state only — no disk/VFS/PSI,
  never mutates health (fixes the mutating `getTools()`, `tool/LuaToolManager.kt:110-122`).
- **Collaborators**: `LuaToolchainRegistry`, `LuaToolKindRegistry`,
  `LuaToolchainProjectSettings`.
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  class LuaToolResolver {
      fun resolve(project: Project?, kindId: String): LuaRegisteredTool?          // §3.1
      fun resolveDetailed(project: Project?, kindId: String): LuaToolResolution   // §3.1
      fun resolveIn(environment: LuaEnvironmentState, kindId: String): LuaRegisteredTool? // §3.2 strict, NO fallback
      fun resolveRuntime(project: Project?): LuaRegisteredTool?                   // §3.3
      fun resolveRuntimeDetailed(project: Project?): LuaToolResolution            // §3.3
      fun resolveAll(project: Project?): Map<String, LuaRegisteredTool>           // §3.1, per kind
      fun notConfiguredMessage(kindId: String): String                            // §2.1 below
      companion object { fun getInstance(): LuaToolResolver }
  }
  ```
- Result types (same file/package):
  ```kotlin
  sealed interface LuaToolResolution {
      data class Resolved(val tool: LuaRegisteredTool, val source: ResolutionSource) : LuaToolResolution
      data class Unresolved(val kindId: String, val skipped: List<SkippedBinding>) : LuaToolResolution
  }
  enum class ResolutionSource { ACTIVE_ENVIRONMENT, PROJECT_BINDING, GLOBAL_BINDING, INVENTORY_FALLBACK }
  data class SkippedBinding(val tier: ResolutionSource, val toolId: String, val reason: SkipReason)
  enum class SkipReason { NOT_IN_INVENTORY, WRONG_KIND, UNUSABLE }
  ```
- `notConfiguredMessage(kindId)` returns
  `"No usable <displayName> configured. Add or bind one under Settings | Languages & Frameworks | Lua | Toolchain."`
  where `displayName` = `LuaToolKindRegistry.findById(kindId)?.displayName ?: kindId`. Callers
  (TOOLING-03/05/07) render it; nothing here hardcodes fallback paths.

### 2.2 `net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings`
- **Responsibility**: the project half of contract §7 — persisted bindings, environments,
  active environment, kind options — plus all lifecycle mutators; every mutator fires §2.3
  events.
- **Decision — new component, not `LuaProjectSettings.State` fields**: (a) clean break —
  new state never coexists with the legacy fields in one class, so TOOLING-02 can land while
  the legacy mode machine still drives legacy consumers, and TOOLING-05 deletes legacy fields
  without touching this component; (b) package hygiene — toolchain state lives under
  `toolchain.registry`; (c) `LuaProjectSettings` keeps only non-toolchain concerns
  (`languageLevel`, `target`, `sourcePath`, globals, rockspec globs, auto-import) and its
  `LuaSettingsChangedListener` topic for them; (d) no `loadState` migration hooks (contract
  §7 forbids migration). Same storage file (`.idea/lunar.xml`) — distinct component root tag.
- **Threading**: state reads any thread; mutators any thread (platform-synchronized state
  component, same guarantee `LuaToolManager` relies on today); events delivered on the
  mutating thread.
- **Key API**:
  ```kotlin
  // Top-level state class (contract §7/§10.5 — NOT a nested `.State`)
  class LuaToolchainProjectState {
      var bindings: MutableMap<String, String> = HashMap()          // kindId → toolId
      var environments: MutableList<LuaEnvironmentState> = mutableListOf()
      var activeEnvironmentId: String = ""                          // "" = none
      var kindOptions: MutableMap<String, String> = HashMap()       // §2.4 keys
  }

  @Service(Service.Level.PROJECT)
  @State(name = "LuaToolchainProjectSettings", storages = [Storage("lunar.xml")],
         category = SettingsCategory.PLUGINS)
  class LuaToolchainProjectSettings(private val project: Project) :
      PersistentStateComponent<LuaToolchainProjectState> {

      fun setBinding(kindId: String, toolId: String?)                   // §3.9 events, §3.3 invariant
      fun environments(): List<LuaEnvironmentState>
      fun activeEnvironment(): LuaEnvironmentState?                     // null when id blank/dangling
      fun upsertEnvironment(spec: LuaEnvironmentState): LuaEnvironmentState   // §3.4
      fun upsertEnvironmentAndActivate(spec: LuaEnvironmentState): LuaEnvironmentState   // §3.4
      fun activateEnvironment(envId: String): Boolean                   // §3.4; unknown id → false
      fun deactivateEnvironment()                                       // §3.4
      fun removeEnvironment(envId: String, deleteDir: Boolean)          // §3.4
      fun setKindOption(key: String, value: String?)                    // §3.8
      fun effectiveKindOption(key: String): String                      // §3.8
      companion object {
          fun getInstance(project: Project): LuaToolchainProjectSettings
          fun normalizeDir(directory: String): String =
              FileUtil.toCanonicalPath(File(directory).absolutePath)    // carried from HererocksEnvBinder.normalizeDir (rocks/env/HererocksEnvBinder.kt:94)
      }
  }
  ```
- `LuaEnvironmentState` (contract §2.4, `toolchain.model`, created by TOOLING-01; if 01 has
  not yet created it, this feature does — serializer-friendly form, the sanctioned
  `var`+default exception, de-risked by TOOLING-00-06):
  ```kotlin
  data class LuaEnvironmentState(
      var id: String = "",
      var name: String = "",
      var rootDir: String = "",
      var toolIds: MutableList<String> = mutableListOf(),
  )
  ```

### 2.3 `net.internetisalie.lunar.toolchain.registry.LuaToolchainListener` (+ event)
- **Responsibility**: the one app-level topic fired by **every** registry/binding/env
  mutation (contract §4). TOOLING-01 fires the `TOOL_*` changes; TOOLING-02 fires the rest.
  Whichever feature lands first creates this file; the shape per contract §4 (authoritative).
- **Key API**:
  ```kotlin
  interface LuaToolchainListener {
      fun toolchainChanged(event: LuaToolchainEvent)
      companion object {
          @JvmField
          val TOPIC: Topic<LuaToolchainListener> =
              Topic.create("lunar.toolchain.changed", LuaToolchainListener::class.java)
      }
  }

  data class LuaToolchainEvent(
      val change: LuaToolchainChange,
      val project: Project? = null,        // null = application-scoped change
      val kindId: String? = null,
      val toolId: String? = null,
      val environmentId: String? = null,
      val optionKey: String? = null,
  )

  enum class LuaToolchainChange {
      TOOL_REGISTERED, TOOL_UPDATED, TOOL_REMOVED,                  // fired by TOOLING-01
      GLOBAL_BINDING_CHANGED, PROJECT_BINDING_CHANGED,              // §3.9
      ENVIRONMENT_ADDED, ENVIRONMENT_UPDATED, ENVIRONMENT_REMOVED,  // §3.4
      ACTIVE_ENVIRONMENT_CHANGED,                                   // §3.4 (activate/switch/deactivate)
      KIND_OPTION_CHANGED,                                          // §3.8
  }
  ```
- **Publishing**: always
  `ApplicationManager.getApplication().messageBus.syncPublisher(LuaToolchainListener.TOPIC)`
  — app bus even for project-scoped changes (the `project` field scopes them). The event
  object is transient; holding `Project` in it is safe (no retention).
- **Subscriber contract** (normative for TOOLING-03's terminal cache and all others):
  1. Subscribe on the application bus with a `Disposable`
     (`ApplicationManager.getApplication().messageBus.connect(disposable)`).
  2. Delivery is synchronous on the mutating thread (EDT *or* background) — handlers must be
     cheap: no I/O, PSI, VFS, or probing; marshal heavy work.
  3. Project-scoped caches invalidate when `event.project == null || event.project == myProject`
     (app-scoped changes affect every project).
  4. Pattern: drop-and-lazily-recompute, exactly as
     `LuaTerminalEnvironmentService` does today (`tool/LuaTerminalEnvironmentService.kt:37-49`
     — `@Volatile` cache field nulled in the handler); that service resubscribes to this
     topic in TOOLING-03.

### 2.4 `net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys`
- **Responsibility**: the closed list of kind-scoped option keys (format
  `"<kindId>.<option>"`), replacing the relocated fields.
- **Key API**:
  ```kotlin
  object LuaKindOptionKeys {
      const val LUACHECK_ARGUMENTS = "luacheck.arguments"   // from LuaCheckSettings.State.arguments (analysis/luacheck/LuaCheckSettings.kt:16)
      const val LUAROCKS_SERVER_URL = "luarocks.serverUrl"  // from LuaRocksSettings.State.serverUrl (rocks/run/LuaRocksSettings.kt:31)
  }                                                         //  + LuaProjectSettings.State.rocksServerUrl (settings/LuaProjectSettings.kt:109)
  ```
- The **app-level** defaults map (`kindOptions: MutableMap<String,String>`) lives on
  `LuaToolchainRegistry`'s app state with mutator
  `LuaToolchainRegistry.setKindOption(key: String, value: String?)` firing
  `KIND_OPTION_CHANGED(project = null)`. This is a one-field addition to contract §7's app
  list (flagged to the epic owner; see §9). Consumers cut over in TOOLING-05
  (`LuaCheckCommandLine.kt:26` → `effectiveKindOption(LUACHECK_ARGUMENTS)`;
  `LuaRocksEnvironment.resolveServer`, `rocks/LuaRocksEnvironment.kt:33` →
  `effectiveKindOption(LUAROCKS_SERVER_URL)`).

### 2.5 `net.internetisalie.lunar.toolchain.resolve.LuaTargetSynchronizer`
- **Responsibility**: derive the project `Target`/language level from the *effective*
  runtime whenever it changes (replaces `HererocksEnvBinder.bind`'s managed-mode cascade,
  `rocks/env/HererocksEnvBinder.kt:49-59`, and the ROCKS-16 stash/restore).
- **Threading**: receives events on any thread; computes resolution inline (cheap); applies
  settings + `PlatformLibraryIndex.reload()` via `invokeLater` (reload must run on the EDT,
  see `LuaProjectSettings.restoreExplicitOverlay` doc, `settings/LuaProjectSettings.kt:349-353`).
- **Collaborators**: `LuaToolResolver`, `LuaProjectSettings.setTargetAndNotify`
  (`settings/LuaProjectSettings.kt:273`), `PlatformVersionRegistry.resolveTarget`
  (`platform/target/PlatformVersionRegistry.kt:81`), `PlatformLibraryIndex.reload`
  (`project/PlatformLibraryProvider.kt:134`).
- **Key API**:
  ```kotlin
  @Service(Service.Level.PROJECT)
  class LuaTargetSynchronizer(private val project: Project) : Disposable {
      // init { app messageBus connect(this) subscribe(LuaToolchainListener.TOPIC) { onEvent(it) } }
      fun ensureSynchronized()                    // §3.5, called once from LuaTargetSyncStartup
      internal fun onEvent(event: LuaToolchainEvent)   // §3.5
      override fun dispose() {}
      companion object { fun getInstance(project: Project): LuaTargetSynchronizer }
  }

  class LuaTargetSyncStartup : ProjectActivity {   // same package
      override suspend fun execute(project: Project) {
          LuaTargetSynchronizer.getInstance(project).ensureSynchronized()
      }
  }
  ```

### 2.6 `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentDetector`
- **Responsibility**: pure detection of env-shaped directories (generalizes
  `HererocksEnvDetector`, `rocks/env/HererocksEnvDetector.kt:17-64`).
- **Threading**: off-EDT; VFS reads inside `runReadAction` (as `detect` does today,
  `HererocksEnvDetector.kt:21`).
- **Key API**:
  ```kotlin
  object LuaEnvironmentDetector {
      val CONVENTIONAL_NAMES: List<String> // = [".lua", "lua_env", "hererocks", ".hererocks", "_lua"] (carried from HererocksEnvDetector.kt:18)
      fun detect(project: Project): String?                            // §3.6
      fun isEnvShaped(dir: VirtualFile): Boolean                       // §3.6
      fun isKnownDirectory(project: Project, directory: String): Boolean  // §3.6
  }
  ```

### 2.7 `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentAdopter`
- **Responsibility**: turn a detected directory into registered tools + an active
  environment record (replaces `HererocksEnvDetector.descriptorFromDir` +
  `upsertAndActivate`-with-bind, `rocks/env/HererocksEnvDetector.kt:29-42`,
  `settings/LuaProjectSettings.kt:236`).
- **Threading**: background only (registration probes run in TOOLING-01's `registerTool`).
- **Key API**:
  ```kotlin
  object LuaEnvironmentAdopter {
      fun adopt(project: Project, directory: String): LuaEnvironmentState?   // §3.7; null if nothing registered
  }
  ```

### 2.8 `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentDetectionStartup`
- **Responsibility**: on project open, offer one-click **Adopt** for a detected, unknown
  env dir (replaces `HererocksDetectStartup`, `rocks/env/HererocksDetectStartup.kt:17`).
- **Key API**: `class LuaEnvironmentDetectionStartup : ProjectActivity` — `execute` runs
  `detect` → `isKnownDirectory` guard → notification (group
  `notification.group.lunar.tools`, `plugin.xml:543`) with an **Adopt**
  `NotificationAction` that queues `LuaEnvironmentAdopter.adopt` on a background task
  (`newProjectBackgroundTask`, same idiom as `HererocksDetectStartup.kt:39-42`).
- **Registration timing**: class ships in TOOLING-02 (unit-tested by invoking `execute`
  logic directly); its `plugin.xml` line is added in TOOLING-05 in the same commit that
  removes the `HererocksDetectStartup` line (`plugin.xml:434-435`) — otherwise both
  activities would offer duplicate notifications for the same directory during the
  transition (§7).

### 2.9 `LuaToolchainRegistry` additions (extends TOOLING-01's service)
- **Responsibility**: global-binding + app kind-option mutators, symmetric with §2.2 and
  firing §2.3 events (replaces `LuaToolManager.setGlobalBinding`/`getGlobalBinding`,
  `tool/LuaToolManager.kt:188-197`).
- **Key API** (added by this feature if TOOLING-01 lands without them):
  ```kotlin
  fun setGlobalBinding(kindId: String, toolId: String?)   // §3.9; §3.3 runtime invariant
  fun globalBindings(): Map<String, String>
  fun setKindOption(key: String, value: String?)          // §3.8
  fun kindOption(key: String): String                     // "" when absent
  ```

## 3. Algorithms

### 3.1 `resolveDetailed(project, kindId)` — the precedence (TOOLING-02-01/02/11)
- **Input → Output**: `(Project?, String)` → `LuaToolResolution`.
- **Steps** (`registry` = `LuaToolchainRegistry`, `ps` =
  `LuaToolchainProjectSettings.getInstance(project)` when `project != null`):
  1. `skipped = mutableListOf<SkippedBinding>()`.
  2. **Tier 1 — active environment** (skip when `project == null`):
     `env = ps.activeEnvironment()`; if non-null, for each `toolId` in `env.toolIds`
     (list order):
     a. `tool = registry.tool(toolId)`; if null →
        `skipped += SkippedBinding(ACTIVE_ENVIRONMENT, toolId, NOT_IN_INVENTORY)`, continue.
     b. if `tool.kindId != kindId` → continue (not recorded — the env legitimately holds
        other kinds).
     c. if `!tool.isUsable` →
        `skipped += SkippedBinding(ACTIVE_ENVIRONMENT, toolId, UNUSABLE)`, continue.
     d. else return `Resolved(tool, ACTIVE_ENVIRONMENT)`.
  3. **Tier 2 — project binding** (skip when `project == null`):
     `boundId = ps.state.bindings[kindId]`; if non-null:
     a. `tool = registry.tool(boundId)`; null →
        skip `(PROJECT_BINDING, boundId, NOT_IN_INVENTORY)`;
     b. `tool.kindId != kindId` → skip `(PROJECT_BINDING, boundId, WRONG_KIND)`;
     c. `!tool.isUsable` → skip `(PROJECT_BINDING, boundId, UNUSABLE)`;
     d. else return `Resolved(tool, PROJECT_BINDING)`.
  4. **Tier 3 — global binding**: identical to step 3 against
     `registry.globalBindings()[kindId]`, tier tag `GLOBAL_BINDING`.
  5. **Tier 4 — inventory fallback**:
     `registry.tools().firstOrNull { it.kindId == kindId && it.isUsable }`
     → `Resolved(tool, INVENTORY_FALLBACK)` (inventory persistence order — matches
     `valid.first()` today, `tool/LuaToolManager.kt:176`).
  6. **Tier 5**: `Unresolved(kindId, skipped)`.
- **Rules / edge handling**: `resolve` = `(resolveDetailed as? Resolved)?.tool`. Usability
  is the recorded `tool.isUsable` (contract §2.3) — **no lazy disk re-validation** (differs
  deliberately from `getTools()`'s inline mutation, `tool/LuaToolManager.kt:110-122`; health
  refresh is TOOLING-01/07's explicit background job). The skip-and-fall-through behavior
  matches `getEffectiveTool` (TOOL-02-06 fallback, `tool/LuaToolManager.kt:157-160`).
  `resolveAll(project)` = for each kind in `LuaToolKindRegistry.all()`, `resolve(project,
  kind.id)`, collecting non-null into `Map<kindId, tool>` (successor of
  `getAllValidTools`, `tool/LuaToolManager.kt:184-185`).
- **Complexity**: O(|env.toolIds| + |inventory|) in-memory scans; no caching.

### 3.2 `resolveIn(environment, kindId)` — matrix-row resolution (TOOLING-02-03)
- **Input → Output**: `(LuaEnvironmentState, String)` → `LuaRegisteredTool?` (contract §10.4:
  strict, returns the tool or `null`; TOOLING-05 consumes `?.path`).
- **Steps**: exactly tier 1 of §3.1 run against the *given* environment — for each `toolId`
  in `environment.toolIds`, return the first tool whose kind matches `kindId` and that
  `isUsable`; with **no** tier 2/3/4 fallback, return `null` when none matches.
- **Rationale**: a matrix row must use that row's toolchain or fail loudly (successor of
  `MatrixRunner.commandLineFor`'s `env.luarocksExe()`, `rocks/env/matrix/MatrixRunner.kt:47-48`;
  the runner migrates in TOOLING-05 and renders `notConfiguredMessage` in the
  row output when `resolveIn` returns `null`).

### 3.3 `resolveRuntime(project)` + single-runtime invariant (TOOLING-02-04)
- **Input → Output**: `Project?` → `LuaToolResolution` (`resolveRuntimeDetailed`) /
  `LuaRegisteredTool?` (`resolveRuntime`).
- Let `runtimeKinds = LuaToolKindRegistry.all().filter { Capability.RUNTIME in it.capabilities }`
  (declaration order — `lua` before `luajit`).
- **Steps**:
  1. **Tier 1**: for each `toolId` in the active env's `toolIds` (steps a–d of §3.1 tier 1,
     with the kind test replaced by *"tool's kind has `Capability.RUNTIME`"*).
  2. **Tier 2**: for each `k` in `runtimeKinds`: apply §3.1 tier-2 checks to
     `ps.state.bindings[k.id]`; first `Resolved` wins.
  3. **Tier 3**: same over `registry.globalBindings()`.
  4. **Tier 4**: for each `k` in `runtimeKinds`: first usable inventory tool of kind `k`.
  5. `Unresolved("runtime-capability", skipped)` — the pseudo kind id is only used in
     messages ("No usable Lua runtime configured…").
- **Invariant** (enforced in `setBinding` / `setGlobalBinding`): when the kind being bound
  has `Capability.RUNTIME` and `toolId != null`, remove every *other* RUNTIME-kind entry
  from that scope's map inside the same mutation (one event fires — the one for the set
  binding). This keeps tiers 2/3 deterministic: at most one runtime binding per scope.
- **Consumers**: "the project interpreter" = `resolveRuntime(project)`; the run-config
  dropdown lists RUNTIME-capable inventory tools; cutover of
  `command/LuaCommandLine.kt:20`, `run/test/LuaTestRunConfiguration.kt:183`,
  `rocks/RockspecBridge.kt:35` etc. is TOOLING-05.

### 3.4 Environment lifecycle (TOOLING-02-07/08/13)
All mutate `LuaToolchainProjectState` and fire §2.3 events; none touch `bindings`,
none provision, none probe.

- **`upsertEnvironment(spec)`** (dedupe rule carried from the legacy `upsertAndActivate`,
  `settings/LuaProjectSettings.kt:236-252`):
  1. `normalized = normalizeDir(spec.rootDir)`.
  2. `existing = environments.firstOrNull { it.id == spec.id || normalizeDir(it.rootDir) == normalized }`.
  3. If `existing == null`: if `spec.id.isBlank()` assign `UUID.randomUUID().toString()`;
     append; fire `ENVIRONMENT_ADDED(project, envId = spec.id)`; return `spec`.
  4. Else: `merged = spec.copy(id = existing.id.ifBlank { spec.id })`; replace in place at
     `existing`'s index; fire `ENVIRONMENT_UPDATED(project, envId = merged.id)`; return
     `merged`. (No event if `merged == existing` field-for-field.)
- **`upsertEnvironmentAndActivate(spec)`**: `resolved = upsertEnvironment(spec)`; then if
  `activeEnvironmentId != resolved.id` → set it and fire
  `ACTIVE_ENVIRONMENT_CHANGED(project, envId = resolved.id)`; return `resolved`.
- **`activateEnvironment(envId)`**: unknown id → return `false`, no mutation, no event
  (matches `setActiveEnvAndNotify`'s no-op, `settings/LuaProjectSettings.kt:260-264`);
  already active → return `true`, no event; else set + fire `ACTIVE_ENVIRONMENT_CHANGED`.
  Switching is `activateEnvironment(otherId)` — never re-provisions.
- **`deactivateEnvironment()`**: if `activeEnvironmentId.isEmpty()` → no-op; else set `""`
  and fire `ACTIVE_ENVIRONMENT_CHANGED(project, envId = null)`. **This is the whole
  "restore"**: bindings were never modified, so the next `resolve` call lands on tier 2/3/4
  by construction. No stash, no mode, no overlay (deletes the ROCKS-16 semantics of
  `setInterpreterModeAndNotify`/`restoreExplicitOverlay`,
  `settings/LuaProjectSettings.kt:323-359`).
- **`removeEnvironment(envId, deleteDir)`**:
  1. `env = environments.firstOrNull { it.id == envId } ?: return` (no-op).
  2. Capture `wasActive = (activeEnvironmentId == envId)` and `rootDir = env.rootDir`.
  3. Remove from `environments`; if `wasActive` → `activeEnvironmentId = ""` and fire
     `ACTIVE_ENVIRONMENT_CHANGED(project, envId = null)`.
  4. `registry.unregisterByEnvironment(envId)` — unregisters every inventory tool with
     `environmentId == envId` (each fires `TOOL_REMOVED` per TOOLING-01) —
     the environment owns its tools; a kept-on-disk dir can be re-adopted (§3.7).
  5. Fire `ENVIRONMENT_REMOVED(project, envId)`.
  6. If `deleteDir`: `ApplicationManager.getApplication().executeOnPooledThread { FileUtil.delete(File(rootDir)) }`
     (verbatim from `HererocksEnvBinder.unbind`, `rocks/env/HererocksEnvBinder.kt:86-90`).
- **Event order** is exactly the step order above.

### 3.5 Target derivation & synchronization (TOOLING-02-09)
- **`targetFor(info: LuaRuntimeInfo): Target`**:
  1. `label = Regex("""(\d+\.\d+)""").find(info.version)?.groupValues?.get(1) ?: info.version`
     (verbatim from `HererocksEnvState.normalizedVersionLabel`,
     `rocks/env/HererocksEnvState.kt:58-59` — handles "5.4.6", "2.1.0-beta3").
  2. `return PlatformVersionRegistry.resolveTarget(info.platform, label)`
     (`platform/target/PlatformVersionRegistry.kt:81-86`; graceful fallback to platform
     default then `Target.default()` — supersedes the PUC→STANDARD / LUAJIT→LUAJIT mapping
     in `HererocksEnvState.toTarget`, `rocks/env/HererocksEnvState.kt:45-50`, because
     `info.platform` is already a `LuaPlatform` from the TOOLING-01 probe).
- **`LuaTargetSynchronizer.onEvent(event)`**:
  1. If `event.project != null && event.project != project` → return.
  2. If `event.change ∉ {TOOL_REGISTERED, TOOL_UPDATED, TOOL_REMOVED,
     GLOBAL_BINDING_CHANGED, PROJECT_BINDING_CHANGED, ENVIRONMENT_UPDATED,
     ENVIRONMENT_REMOVED, ACTIVE_ENVIRONMENT_CHANGED}` → return (`KIND_OPTION_CHANGED` and
     `ENVIRONMENT_ADDED`-without-activation cannot change the effective runtime).
  3. `resolution = LuaToolResolver.getInstance().resolveRuntimeDetailed(project)`.
  4. If `resolution` is `Resolved` with
     `source == INVENTORY_FALLBACK` → treat as no effective runtime (**the fallback tier
     never drives the target** — it is a convenience for execution, not user intent; this
     also keeps the synchronizer inert until the user binds/activates something, preserving
     manual `Target` choices exactly like today's `EXPLICIT` mode).
  5. `newId = (resolution as? Resolved)?.tool?.id` (with step-4 filtering).
     If `newId == lastAppliedRuntimeId` (a `@Volatile` field, initially the sentinel
     `UNINITIALIZED`) → return.
  6. `lastAppliedRuntimeId = newId`. If `newId == null` or `tool.runtime == null` → return
     — **target stickiness**: nothing resolves, the last target/level stays (no legacy
     "restore stashed target"; there is no stash).
  7. `target = targetFor(tool.runtime)`; `invokeLater`:
     `settings = LuaProjectSettings.getInstance(project)`;
     if `settings.state.getTarget() == target` → return; else
     `previousLevel = settings.state.languageLevel`;
     `settings.setTargetAndNotify(target)` (`settings/LuaProjectSettings.kt:273` — fires
     the existing `LuaSettingsChangedListener.TOPIC`, so language-level consumers keep
     working, contract §4); if `settings.state.languageLevel != previousLevel` →
     `PlatformLibraryIndex.reload()` (`project/PlatformLibraryProvider.kt:134`; mirrors
     `HererocksEnvBinder.bind`, `rocks/env/HererocksEnvBinder.kt:56-59`).
- **`ensureSynchronized()`**: runs steps 3–7 once at project open (`LuaTargetSyncStartup`),
  so a VCS-pulled `activeEnvironmentId` change applies without an in-session event.

### 3.6 Environment detection (TOOLING-02-14)
- **`detect(project)`** (structure carried from `HererocksEnvDetector.detect`,
  `rocks/env/HererocksEnvDetector.kt:21-26`):
  ```
  runReadAction {
    base = project.guessProjectDir() ?: return null
    candidates = base.children.filter { it.isDirectory } +
                 CONVENTIONAL_NAMES.mapNotNull { base.findChild(it) }
    return candidates.distinct().firstOrNull { isEnvShaped(it) }?.path
  }
  ```
- **`isEnvShaped(dir)`** = `hasCapabilityBinary(dir, RUNTIME) && hasCapabilityBinary(dir, PACKAGE_MANAGER)`.
- **`hasCapabilityBinary(dir, capability)`**: for each kind in
  `LuaToolKindRegistry.all()` with `capability`, for each `base` in `kind.binaryNames`:
  `dir.findFileByRelativePath("bin/$base") != null || dir.findChild("$base.exe") != null ||
  dir.findChild("$base.bat") != null` — generalizes the hardcoded `hasLua`/`hasLuarocks`
  checks (`rocks/env/HererocksEnvDetector.kt:58-63`; POSIX `bin/` layout, Windows
  root-with-extension layout as in `HererocksEnvState.binDir`,
  `rocks/env/HererocksEnvState.kt:27`).
- **`isKnownDirectory(project, directory)`**: any `env` in
  `LuaToolchainProjectSettings.getInstance(project).environments()` with
  `normalizeDir(env.rootDir) == normalizeDir(directory)` (carried from
  `HererocksEnvDetector.isKnownDirectory`, `rocks/env/HererocksEnvDetector.kt:50-54`).

### 3.7 Adoption (TOOLING-02-14)
- **`LuaEnvironmentAdopter.adopt(project, directory)`** (background thread):
  1. `envId = UUID.randomUUID().toString()`; `toolIds = mutableListOf<String>()`.
  2. For each `kind` in `LuaToolKindRegistry.all()` (all kinds — a luacheck/stylua/busted
     installed inside the env is adopted too, unlike the legacy lua+luarocks-only bind):
     for each `base` in `kind.binaryNames`, for each candidate path in
     `["$directory/bin/$base", "$directory/$base.exe", "$directory/$base.bat"]`
     (first existing executable file wins per kind):
     `tool = registry.registerTool(path, kind.id, origin = Origin.DISCOVERED, environmentId = envId)`;
     if non-null → `toolIds += tool.id` and stop scanning this kind. (`registerTool`
     performs the probe and fires `TOOL_REGISTERED` — TOOLING-01.)
  3. If `toolIds.isEmpty()` → return null (notification stays; nothing recorded).
  4. `return LuaToolchainProjectSettings.getInstance(project).upsertEnvironmentAndActivate(
        LuaEnvironmentState(id = envId, name = File(directory).name, rootDir = directory,
                            toolIds = toolIds))`.
  - Adopted tools carry `origin = DISCOVERED` **with** `environmentId` set — contract §2.2's
    comment ("set when origin == PROVISIONED") is relaxed to "set when the tool belongs to
    an environment" (flagged, §9). Target/level then follow automatically via §3.5 (the
    activation event) — no explicit bind step exists.
- **Startup flow** (`LuaEnvironmentDetectionStartup.execute`, mirrors
  `rocks/env/HererocksDetectStartup.kt:18-47`): `detect` → null-return or
  `isKnownDirectory` → return; else notification ("Lua environment detected at <dir>",
  group `notification.group.lunar.tools`) with **Adopt** action queuing
  `newProjectBackgroundTask("Adopting Lua environment", project) { adopt(project, dir) }`
  and expiring the notification.

### 3.8 Kind-scoped option resolution (TOOLING-02-12)
- **`effectiveKindOption(key)`** (precedence mirrors `LuaRocksEnvironment.resolveServer`,
  `rocks/LuaRocksEnvironment.kt:33`):
  1. `project = state.kindOptions[key]?.trim()`; if non-blank → return it.
  2. `app = LuaToolchainRegistry.getInstance().kindOption(key).trim()`; if non-blank →
     return it.
  3. Return `""` (callers treat `""` as "none" — e.g. no `--server` flag emitted).
- **`setKindOption(key, value)`** (both scopes): `value == null || value.isBlank()` removes
  the entry; unchanged value → no event; else fire
  `KIND_OPTION_CHANGED(project = <project or null>, optionKey = key)`.

### 3.9 Event firing rules (TOOLING-02-06/10)
- **`setBinding(kindId, toolId)`**: if `toolId == null` → remove entry (no event if it was
  absent); else apply the §3.3 runtime invariant, then put (no event if the value is
  unchanged *and* the invariant removed nothing). Fire exactly one
  `PROJECT_BINDING_CHANGED(project, kindId, toolId)`.
  `setGlobalBinding` identical with `GLOBAL_BINDING_CHANGED(project = null)`.
- **Mutation → event table** (normative enumeration; "—" = field null):

| Mutation | Change | project | kindId | toolId | environmentId | optionKey |
|---|---|---|---|---|---|---|
| `setBinding(k, t)` | PROJECT_BINDING_CHANGED | P | k | t/— | — | — |
| `LuaToolchainRegistry.setGlobalBinding(k, t)` | GLOBAL_BINDING_CHANGED | — | k | t/— | — | — |
| `upsertEnvironment` (new) | ENVIRONMENT_ADDED | P | — | — | id | — |
| `upsertEnvironment` (merge) | ENVIRONMENT_UPDATED | P | — | — | id | — |
| `activateEnvironment` / `upsertEnvironmentAndActivate` / `deactivateEnvironment` / removal-of-active | ACTIVE_ENVIRONMENT_CHANGED | P | — | — | id/— (— = none active) | — |
| `removeEnvironment` | ENVIRONMENT_REMOVED (after `unregisterByEnvironment` fires per-tool TOOL_REMOVED) | P | — | — | id | — |
| `setKindOption` (project) | KIND_OPTION_CHANGED | P | — | — | — | key |
| `LuaToolchainRegistry.setKindOption` (app) | KIND_OPTION_CHANGED | — | — | — | — | key |

- Fixes the "silent cache staleness" defect class (contract §4: legacy `registerTool` fires
  nothing, `tool/LuaToolManager.kt:49-90`): with TOOLING-01's `TOOL_*` events, **every**
  toolchain mutation is observable on one topic.

## 4. External Data & Parsing

None. This feature consumes no CLI output, files, or network responses — it operates purely
on in-memory/persisted plugin state. Binary probing (the only external input in the
subsystem) is TOOLING-01's `LuaToolProbe`; directory detection (§3.6) checks only VFS file
*existence*, parsing nothing.

## 5. Data Flow

### Example 1: Activate an environment (no mode machine)
1. UI/TOOLING-04 calls `upsertEnvironmentAndActivate(env)` → state row upserted,
   `ACTIVE_ENVIRONMENT_CHANGED` fired. `bindings` untouched (TC 12).
2. `LuaTargetSynchronizer.onEvent`: `resolveRuntimeDetailed` → `Resolved(envLua,
   ACTIVE_ENVIRONMENT)`; runtime id changed → `targetFor(envLua.runtime)` →
   `setTargetAndNotify` (+ `PlatformLibraryIndex.reload` on level change).
3. Any consumer's next `resolve(project, "luacheck")` returns the env's luacheck (tier 1);
   TOOLING-03's caches were invalidated by the same event.

### Example 2: Deactivate → implicit restore
1. `deactivateEnvironment()` → `ACTIVE_ENVIRONMENT_CHANGED(envId = null)`.
2. `resolve(project, kind)` now lands on the *unchanged* project/global bindings (tier 2/3)
   — restoration is a consequence of never having overwritten them.
3. Synchronizer re-derives the target from the now-effective bound runtime; if nothing is
   bound, the target stays put (target stickiness, §3.5 step 6).

### Example 3: Adopt on project open
1. `LuaEnvironmentDetectionStartup` (background): `detect` finds `<base>/.lua` with
   `bin/lua` + `bin/luarocks`; not known → **Adopt** notification.
2. User clicks Adopt → background `adopt`: two `registerTool` calls (probed, indexed,
   `TOOL_REGISTERED` each) → `upsertEnvironmentAndActivate` → events → Example 1 steps 2–3.

## 6. Edge Cases

| Case | Handling |
|---|---|
| Active env id dangling (env removed on another machine, state merged via VCS) | `activeEnvironment()` returns null → tier 1 skipped entirely; resolution proceeds at tier 2 |
| Env `toolIds` reference tools registered on another machine (app inventory is per-machine) | Tier-1 skips with `NOT_IN_INVENTORY` (surfaced via `Unresolved.skipped` / TOOLING-07); re-adoption of the same dir re-registers and refreshes the record (dir-dedupe keeps one row) |
| Bound tool deleted from disk but health not yet re-checked | Resolver returns it (reads never touch disk); execution error handling (TOOLING-03) and health monitor (TOOLING-07) own the failure — deliberate, documented trade for thread-safe reads |
| Two environments over the same directory | Impossible via API: `upsertEnvironment` dedupes by normalized dir (carried from ROCKS-15 remediation defect A, `settings/LuaProjectSettings.kt:229-235`) |
| Binding both `lua` and `luajit` | Impossible via API: single-runtime invariant (§3.3); pre-existing hand-edited XML with both → tier order (kind declaration order) decides deterministically |
| `project == null` (app-context callers, e.g. global settings UI preview) | Tiers 1–2 skipped; global binding → fallback → null |
| Deactivate with nothing bound | Resolution `Unresolved`; target keeps last value (§3.5 step 6); consumers show `notConfiguredMessage` |
| Event fired while another mutation is in flight on a different thread | State component access is platform-synchronized (same guarantee `LuaToolManager` documents, `tool/LuaToolManager.kt:20-22`); handlers read a coherent snapshot via the normal state getters |
| `removeEnvironment(deleteDir = true)` while a tool from that env is running | Deletion is best-effort on a pooled thread (as today, `rocks/env/HererocksEnvBinder.kt:86-90`); running processes keep their open handles (POSIX) or fail visibly (Windows) — unchanged from legacy behavior |
| Legacy state files with old fields (`interpreterMode`, `hererocksEnvs`, …) | Ignored — different component tags; fields deleted in TOOLING-05; no migration (contract §7, TOOLING-00-06) |

## 7. Integration Points

```xml
<!-- plugin.xml — added by TOOLING-02 -->
<extensions defaultExtensionNs="com.intellij">
    <!-- Initial target/runtime reconciliation on project open (design §2.5) -->
    <postStartupActivity
            implementation="net.internetisalie.lunar.toolchain.resolve.LuaTargetSyncStartup" />
</extensions>
```

- `LuaToolResolver`, `LuaToolchainProjectSettings`, `LuaTargetSynchronizer` are
  `@Service`-annotated **light services** — no `plugin.xml` entries (same pattern as
  `HererocksProvisioner`, documented in `docs/features/rocks/14-hererocks-environment/design.md`
  §5). `LuaToolchainProjectSettings` persistence comes from its `@State` annotation
  (component name `LuaToolchainProjectSettings`, storage `lunar.xml` → `.idea/lunar.xml`).
- `LuaToolchainListener.TOPIC` is a code-declared `Topic` (like
  `LuaSettingsChangedListener.TOPIC`, `settings/LuaSettingsChangedEvent.kt:29`) — no
  `plugin.xml` declaration; publish/subscribe via the **application** message bus.
- Notifications reuse the existing group `notification.group.lunar.tools`
  (`plugin.xml:543`) — no new `notificationGroup` element.
- **Deferred to TOOLING-05** (one atomic swap, avoiding duplicate detect notifications):
  ```xml
  <!-- remove --> <postStartupActivity implementation="net.internetisalie.lunar.rocks.env.HererocksDetectStartup" />  <!-- plugin.xml:434-435 -->
  <!-- add    --> <postStartupActivity implementation="net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentDetectionStartup" />
  ```
- **Not touched here**: `LuaToolManager`'s `applicationService` entry (`plugin.xml:422-423`)
  and all legacy consumers — they keep running on the legacy path until TOOLING-05.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TOOLING-02-01 Precedence resolution | M | §2.1, §3.1 |
| TOOLING-02-02 Outcome reporting | M | §2.1 (result types, `notConfiguredMessage`), §3.1 |
| TOOLING-02-03 Per-environment resolution | M | §2.1, §3.2 |
| TOOLING-02-04 Runtime resolution | M | §2.1, §3.3 |
| TOOLING-02-05 Project persistence | M | §2.2, §7 (`@State`) |
| TOOLING-02-06 Binding mutation API | M | §2.2, §2.9, §3.9 |
| TOOLING-02-07 Environment lifecycle | M | §2.2, §3.4 |
| TOOLING-02-08 Mode machine replaced | M | §3.4 (deactivate), §3.5, §5 Ex. 1–2, §1 (deleted inventory) |
| TOOLING-02-09 Target synchronization | M | §2.5, §3.5 |
| TOOLING-02-10 Change events | M | §2.3, §3.9 |
| TOOLING-02-11 Stale-binding fallback | M | §3.1 (skip rules), §3.2, §3.3 |
| TOOLING-02-12 Kind-scoped options | M | §2.4, §3.8 |
| TOOLING-02-13 Removal cleanup | S | §3.4 (`removeEnvironment`) |
| TOOLING-02-14 Detection & adoption | M | §2.6–2.8, §3.6, §3.7 |

## 9. Alternatives Considered

- **Extend `LuaProjectSettings.State` instead of a new component** — rejected: mixes new
  fields with the nine legacy fields TOOLING-05 deletes, requires coordinating both features
  in one class, and re-couples toolchain state to the legacy notify helpers (§2.2 decision).
- **A `runtime` pseudo-binding key** (`bindings["runtime"] = toolId`) instead of the
  single-runtime invariant — rejected: breaks the "keys are kind ids" contract shape (§7),
  needs special-casing in every map consumer, and the invariant achieves the same
  determinism with plain kind keys.
- **Stash/restore the user target across activation** (ROCKS-16 style) — rejected by the
  contract (§2.4: "No stashed overlays"). Consequence accepted and documented (target
  stickiness, §3.5 step 6): after deactivating with no bound runtime, the level stays at the
  env's last value instead of snapping back — no hidden state, user can rebind/re-pick
  anytime.
- **Let the inventory-fallback tier drive the target** — rejected: tier 4 is not user
  intent; it would stomp manually chosen targets the moment TOOLING-01 discovery finds any
  runtime on PATH (§3.5 step 4).
- **Fall back to project bindings inside `resolveIn`** — rejected: a matrix row silently
  running the wrong `luarocks` is precisely the bug class this epic kills (§3.2).
- **Auto-adopt detected environments without asking** — rejected: registering binaries and
  switching the active toolchain on project open without consent is surprising; keep the
  legacy opt-in notification flow (`rocks/env/HererocksDetectStartup.kt:28-47`).
- **Per-mutation listener methods** (`bindingChanged(...)`, `environmentAdded(...)`) instead
  of one event object — rejected: every new mutation kind becomes a breaking interface
  change; a sealed-ish enum payload keeps subscribers forward-compatible.

## 10. Open Questions

_None — feature has cleared the planning bar._
