---
id: "ROCKS-14-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ROCKS-14"
folders:
  - "[[features/rocks/14-hererocks-environment/requirements|requirements]]"
---

# Technical Design: ROCKS-14 — Hererocks Environment Lifecycle

## 1. Architecture Overview

### Prior art in this repo (verified)

- **Tool inventory & resolution** — `LuaToolManager` (`tool/LuaToolManager.kt`) exposes
  `registerTool(path: String, hintType: LuaToolType?): LuaTool?` (`:49`), which validates the
  binary, de-dups by path, appends to `LuaApplicationSettings.instance.state.toolInventory`, and
  returns a `LuaTool` with a stable `id`. `getEffectiveTool(project, type)` (`:163`) applies
  project-binding > global > first-valid. `LuaToolType` (`tool/LuaToolDescriptor.kt:19`) includes
  `LUAROCKS`. **Reused as-is** — hererocks registers a tool through it, no new resolver.
- **Project bindings & notify** — `LuaProjectSettings` (`settings/LuaProjectSettings.kt`,
  `@Service(PROJECT)`, `Storage("lunar.xml")`). `State` (`:44`) holds `interpreter: LuaInterpreter?`
  (`:50`), `projectToolBindings: MutableMap<String, String>` (`:73`), `rocksServerUrl` (`:81`).
  `setProjectToolBindingAndNotify(typeName, toolId)` (`:144`) writes the binding and publishes
  `LuaSettingsChangedListener.TOPIC`. **Extended** with one nested descriptor field + one notify
  helper for the interpreter.
- **Interpreter model** — `LuaInterpreter` (`platform/LuaInterpreter.kt:11`, data class,
  `path/banner/product/version/platform/languageLevel`, `constructor(executable: Path)` at `:27`).
  `LuaInterpreterService` (`platform/LuaInterpreterService.kt`, `@Service(APP)`) exposes
  `identify(interpreter)` (`:79`, probes the banner to fill product/version/languageLevel) and
  `getInstance()` (`:156`). The project interpreter is written today via
  `LuaProjectSettingsPanel` (`settings/LuaProjectSettingsPanel.kt:105`: `state.interpreter = …`).
  **Reused** — provisioning builds a `LuaInterpreter(Path)`, calls `identify`, stores it.
- **Background CLI pattern** — `LuaProcessUtil.capture(GeneralCommandLine, timeoutMs)` run inside
  `ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) …)`
  is the established idiom (`rocks/publish/PublishRockAction.kt:61-65`,
  `rocks/build/BuildWorkspaceAction.kt:62`, `rocks/browser/LuaRocksActionHandler.kt:38,77`).
  **Reused verbatim.**
- **Rocks consumers** — `LuaRocksEnvironment` (`rocks/LuaRocksEnvironment.kt:21`) resolves the
  `luarocks` executable via TOOL-02. **Untouched** — once the binding points at the hererocks
  `luarocks`, search/install/publish/build all follow with no code change.

### Target state

A new package `net.internetisalie.lunar.rocks.env` holds the descriptor, a locator, a provisioner,
a binder, and a detector, plus four `AnAction`s and one create dialog. Descriptor state lives on
`LuaProjectSettings.State`. No existing resolver is modified.

## 2. Core Components

### 2.1 `rocks.env.HererocksEnvState`

Serializable descriptor (ROCKS-14-01), stored on the project state exactly like `LuaInterpreter`.

```kotlin
enum class HererocksFlavor { PUC, LUAJIT }

data class HererocksEnvState(
    var id: String = "",            // UUID assigned at create; "" == unset
    var directory: String = "",     // absolute path to the env root
    var flavor: HererocksFlavor = HererocksFlavor.PUC,
    var luaVersion: String = "5.4",
    var luarocksVersion: String = "latest",
    var label: String = "",
) {
    fun binDir(): String = if (SystemInfo.isWindows) directory else "$directory/bin"
    fun luaExe(): String = "${binDir()}/${if (flavor == HererocksFlavor.LUAJIT) "luajit" else "lua"}${if (SystemInfo.isWindows) ".exe" else ""}"
    fun luarocksExe(): String = "${binDir()}/luarocks${if (SystemInfo.isWindows) ".bat" else ""}"
    fun displayLabel(): String = label.ifBlank { "${flavor.name} $luaVersion" }
}
```

### 2.2 `settings.LuaProjectSettings.State` (extended)

```kotlin
// added to State (settings/LuaProjectSettings.kt:44), defaulted for the XML serializer:
var hererocksEnv: HererocksEnvState? = null   // ROCKS-14-01; null = none
```

And one notify helper on `LuaProjectSettings` (mirrors `setProjectToolBindingAndNotify`, `:144`):

```kotlin
fun setInterpreterAndNotify(interpreter: LuaInterpreter?) {
    state.interpreter = interpreter
    project?.messageBus?.syncPublisher(LuaSettingsChangedListener.TOPIC)?.onSettingsChanged()
}
```

### 2.3 `rocks.env.HererocksLocator`

```kotlin
object HererocksLocator {
    /** ROCKS-14-02. Command prefix to invoke hererocks, or null if unavailable. */
    fun resolvePrefix(): List<String>?
}
```

Algorithm (§3.1). Uses `com.intellij.execution.configurations.PathEnvironmentVariableUtil.findInPath(name)`
(already used by `LuaToolManager`/discovery) for the `PATH` lookup and `LuaProcessUtil.capture`
for the `import hererocks` probe. Pure resolution — call off the EDT (it may run a probe process).

### 2.4 `rocks.env.HererocksProvisioner` (`@Service(Service.Level.PROJECT)`)

```kotlin
@Service(Service.Level.PROJECT)
class HererocksProvisioner(private val project: Project) {
    /** ROCKS-14-03/06/07. Provisions [spec] (create/upgrade/recreate per [mode]) on a
     *  Task.Backgroundable, then binds on success via HererocksEnvBinder. */
    fun provision(spec: HererocksEnvState, mode: Mode)
    enum class Mode { CREATE, UPGRADE, RECREATE }
    companion object { fun getInstance(project: Project): HererocksProvisioner }
}
```

- Builds args via `argsFor(prefix, spec)` (§3.2), runs `LuaProcessUtil.capture(GeneralCommandLine(args), PROVISION_TIMEOUT_MS)` (timeout 600_000 ms — env builds are slow).
- **Concurrency guard (ROCKS-14-09):** a `private val active = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()` keyed by `spec.directory`; `provision` returns early with a "provisioning already in progress" balloon if `!active.add(directory)`; removed in a `finally`.
- `RECREATE` deletes `spec.directory` (`com.intellij.openapi.util.io.FileUtil.delete(File(directory))`) before running.
- On exit code 0 → `HererocksEnvBinder.bind(project, spec.copy(id = existingOrNewUuid))`. On non-zero → error balloon with the last 20 lines of stderr; descriptor/bindings untouched.

### 2.5 `rocks.env.HererocksEnvBinder`

```kotlin
object HererocksEnvBinder {
    /** ROCKS-14-04. Registers bin/luarocks + bin/lua, binds them, stores the descriptor,
     *  and fires the settings-changed topic. Must run inside a write-safe/EDT context for
     *  the settings mutation; VFS refresh of the env dir first. */
    fun bind(project: Project, spec: HererocksEnvState)
    /** ROCKS-14-08. Clears bindings + descriptor; deletes the dir when [deleteDir]. */
    fun unbind(project: Project, deleteDir: Boolean)
}
```

`bind` (§3.3): refresh VFS for the dir, `registerTool(spec.luarocksExe(), LuaToolType.LUAROCKS)`,
`setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, tool.id)`, build+`identify` the
interpreter, `setInterpreterAndNotify(it)`, then `settings.state.hererocksEnv = spec`.

### 2.6 `rocks.env.HererocksEnvDetector`

```kotlin
object HererocksEnvDetector {
    /** ROCKS-14-05. Returns the first hererocks-shaped dir in [project], or null. */
    fun detect(project: Project): String?
}
```

Plus a `ProjectActivity` (`rocks.env.HererocksDetectStartup`) that calls `detect` off the EDT and,
when it finds an unbound env, shows a `Notification` on the **existing** LuaRocks notification
group `notification.group.lunar.luarocks` (declared `plugin.xml:544`, consumed via the
`NOTIFICATION_GROUP` constant in `rocks/publish/PublishRockAction.kt:85` and
`rocks/browser/LuaRocksActionHandler.kt:24` — reuse that constant, do **not** declare a new group)
with a **Bind** action → `HererocksEnvBinder.bind(project, descriptorFromDir(dir))`. Detection is
skipped when
`settings.state.hererocksEnv?.directory` already equals the found dir.

### 2.7 Actions & dialog (`rocks.env`)

- `CreateHererocksEnvAction : AnAction` → opens `CreateHererocksEnvDialog` (a `DialogWrapper` with:
  directory `TextFieldWithBrowseButton` defaulting to `<projectBase>/.lua`; flavor combo;
  Lua-version combo `5.1/5.2/5.3/5.4` (or `2.1` for LuaJIT); LuaRocks-version text default
  `latest`). On OK → `HererocksProvisioner.getInstance(project).provision(spec, CREATE)`.
- `UpgradeHererocksEnvAction` → same dialog pre-filled from the stored descriptor → `UPGRADE`.
- `RecreateHererocksEnvAction` → confirm → `provision(stored, RECREATE)`.
- `RemoveHererocksEnvAction` → confirm (with "also delete directory?" checkbox) →
  `HererocksEnvBinder.unbind(project, deleteDir)`.
- `Upgrade/Recreate/Remove` are `update()`-gated on `settings.state.hererocksEnv != null`.

## 3. Algorithms

### 3.1 `HererocksLocator.resolvePrefix()` (ROCKS-14-02)

```
name = if (SystemInfo.isWindows) "hererocks" else "hererocks"   // findInPath adds PATHEXT on Win
findInPath(name)?.let { return listOf(it.absolutePath) }
for (py in listOf("python3", "python")):
    exe = findInPath(py) ?: continue
    out = LuaProcessUtil.capture(GeneralCommandLine(exe, "-c", "import hererocks"), PROBE_TIMEOUT_MS)
    if (out != null && out.exitCode == 0) return listOf(exe, "-m", "hererocks")
return null
```

### 3.2 `argsFor(prefix, spec)` (ROCKS-14-03/06/07)

```
flavorFlag = if (spec.flavor == LUAJIT) listOf("--luajit", spec.luaVersion)
             else                        listOf("--lua",    spec.luaVersion)
return prefix + listOf(spec.directory) + flavorFlag + listOf("--luarocks", spec.luarocksVersion)
```

Example (TC-4): `["hererocks","/p/.lua","--lua","5.4","--luarocks","latest"]`.

### 3.3 `HererocksEnvBinder.bind` (ROCKS-14-04)

```
LocalFileSystem.getInstance().refreshAndFindFileByPath(spec.directory)   // make bin/ visible
val tool = LuaToolManager.getInstance().registerTool(spec.luarocksExe(), LuaToolType.LUAROCKS)
           ?: return errorBalloon("luarocks not found in provisioned env")
val settings = LuaProjectSettings.getInstance(project)
settings.setProjectToolBindingAndNotify(LuaToolType.LUAROCKS.name, tool.id)
val interp = LuaInterpreter(Path.of(spec.luaExe())).also { LuaInterpreterService.getInstance().identify(it) }
settings.setInterpreterAndNotify(interp)
settings.state.hererocksEnv = spec.copy(id = spec.id.ifBlank { UUID.randomUUID().toString() })
```

`setProjectToolBindingAndNotify` and `setInterpreterAndNotify` each fire TOPIC; acceptable (TC-6
asserts "fired" — callers coalesce). If a single event is required, inline both mutations then a
single publish; the test asserts ≥1.

### 3.4 `HererocksEnvDetector.detect` (ROCKS-14-05)

```
base = project.guessProjectDir() ?: return null
candidates = base.children.filter { it.isDirectory } +
             listOf(".lua","lua_env","hererocks",".hererocks","_lua").mapNotNull { base.findChild(it) }
for (dir in candidates.distinct()):
    if (hasLua(dir) && hasLuarocks(dir)) return dir.path
return null
hasLua(d)      = d.findFileByRelativePath("bin/lua") != null || d.findChild("lua.exe") != null ||
                 d.findFileByRelativePath("bin/luajit") != null || d.findChild("luajit.exe") != null
hasLuarocks(d) = d.findFileByRelativePath("bin/luarocks") != null || d.findChild("luarocks.bat") != null
```

`descriptorFromDir(dir)` infers `flavor` from which lua exe exists and leaves versions blank
(`luaVersion = ""`, `luarocksVersion = "latest"`) — detection binds without re-provisioning, so
versions are informational; `identify` fills the real Lua version from the banner.

## 4. External Data & Parsing

hererocks emits human-readable progress to stdout/stderr; **we parse none of it** — success is the
process exit code (0/non-zero). On failure the last 20 stderr lines are shown verbatim. No format
spec is required.

## 5. Integration Points (`plugin.xml`)

```xml
<extensions defaultExtensionPointName="com.intellij">
  <postStartupActivity implementation="net.internetisalie.lunar.rocks.env.HererocksDetectStartup"/>
  <!-- No new notificationGroup: reuse the existing "notification.group.lunar.luarocks"
       (plugin.xml:544) via the NOTIFICATION_GROUP constant. -->
</extensions>

<actions>
  <group id="net.internetisalie.lunar.rocks.env.HererocksEnvGroup" text="Lua Environment" popup="true">
    <add-to-group group-id="ToolsMenu" anchor="last"/>
    <action id="Lunar.Hererocks.Create"   class="net.internetisalie.lunar.rocks.env.CreateHererocksEnvAction"   text="Create Isolated Lua Environment…"/>
    <action id="Lunar.Hererocks.Upgrade"  class="net.internetisalie.lunar.rocks.env.UpgradeHererocksEnvAction"  text="Change Lua/LuaRocks Version…"/>
    <action id="Lunar.Hererocks.Recreate" class="net.internetisalie.lunar.rocks.env.RecreateHererocksEnvAction" text="Recreate Environment"/>
    <action id="Lunar.Hererocks.Remove"   class="net.internetisalie.lunar.rocks.env.RemoveHererocksEnvAction"   text="Remove Environment"/>
  </group>
</actions>
```

`HererocksProvisioner` is a `@Service(PROJECT)` (registered by annotation; no `<projectService>`
entry needed). `HererocksEnvState`/nested field need no registration — serialized with the existing
`LuaProjectSettings` state.

## 6. Threading

- Locator probe, provisioning, and detection scan run **off the EDT** (`Task.Backgroundable` /
  startup coroutine). VFS reads in detection wrap in `runReadAction`.
- Settings mutations in `bind`/`unbind` run on the EDT via `invokeLater` after provisioning
  completes (they are cheap state writes + message-bus publish, no write-command needed as
  `LuaProjectSettings` is a plain state component).

## 7. Requirement Coverage

| Requirement | Implemented by |
|-------------|----------------|
| ROCKS-14-01 | §2.1, §2.2 |
| ROCKS-14-02 | §2.3, §3.1 |
| ROCKS-14-03 | §2.4, §3.2 |
| ROCKS-14-04 | §2.5, §3.3 |
| ROCKS-14-05 | §2.6, §3.4 |
| ROCKS-14-06 | §2.4 (UPGRADE), §2.7 |
| ROCKS-14-07 | §2.4 (RECREATE), §2.7 |
| ROCKS-14-08 | §2.5 `unbind`, §2.7 |
| ROCKS-14-09 | §2.4 concurrency guard |

## 8. Alternatives Considered

- **New `LuaToolType.HEREROCKS`** — rejected; hererocks is not a resolvable runtime tool, it is a
  one-shot provisioner. The env's *products* (`lua`, `luarocks`) are what the IDE binds.
- **Store the descriptor in application settings** — rejected; the env is project-scoped and
  VCS-shared alongside `rocksServerUrl`/`interpreter`, so it belongs in `.idea/lunar.xml`.
- **List of environments now** — deferred to ROCKS-15; ROCKS-14 keeps a single descriptor so the
  resolver contract stays single-valued. ROCKS-15's list is a strict superset.
- **Parse hererocks output for version confirmation** — rejected; `LuaInterpreterService.identify`
  already derives the authoritative Lua version from the interpreter banner after binding.

## 9. Open Questions

_None — the feature has cleared the planning bar._
