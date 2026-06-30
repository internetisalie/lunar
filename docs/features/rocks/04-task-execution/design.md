---
id: ROCKS-04-DESIGN
title: "Technical Design"
type: design
parent_id: ROCKS-04
priority: "high"
folders:
  - "[[features/rocks/04-task-execution/requirements|requirements]]"
---

# Technical Design: Task Execution & Run Configurations (ROCKS-04)

## 1. Architecture Overview

### Current State
The plugin already ships a working Run Configuration for the Lua interpreter
(`net.internetisalie.lunar.run.LuaRunConfiguration` + `LuaRunConfigurationType` +
`LuaRunConfigurationOptions` + `LuaRunSettingsEditor`), registered via
`<configurationType implementation="…LuaRunConfigurationType"/>`. It uses
`ConfigurationTypeBase`, `RunConfigurationOptions` with `StoredProperty` delegates,
`CommandLineState.startProcess()`, and `ProcessHandlerFactory.getInstance()
.createColoredProcessHandler(commandLine)`. **This feature clones that exact pattern** for
`luarocks` commands. There is **no** `LuaToolManager`; the binary path comes from a new
`LuaRocksSettings` app service modeled on `net.internetisalie.lunar.analysis.luacheck
.LuaCheckSettings`.

### Target State
A second configuration type, "LuaRocks", lets users save and run `luarocks <command>` tasks.
Because it is a real `RunConfiguration`, it is automatically usable as a platform **Before
Launch** step (no custom provider) and streams to the Run tool window with process controls.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.run.LuaRocksSettings`
- **Responsibility**: Persist the `luarocks` executable path (application-wide).
- **Threading**: settings service; any thread reads.
- **Pattern**: exact clone of `LuaCheckSettings` (`@Service(Service.Level.APP)`,
  `SimplePersistentStateComponent<State>`, storage `lunar.xml`).
- **Key API**:
  ```kotlin
  @Service(Service.Level.APP)
  @State(name = "LuaRocksSettings", storages = [Storage("lunar.xml")])
  class LuaRocksSettings : SimplePersistentStateComponent<LuaRocksSettings.State>(State()) {
      class State : BaseState() { var executablePath by string("luarocks") }
      val executablePath: String get() = state.executablePath ?: "luarocks"
      companion object { fun getInstance(): LuaRocksSettings = service() }
  }
  ```
- Registered: `<applicationService serviceImplementation="…LuaRocksSettings"/>`.

### 2.2 `net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationType`
- **Responsibility**: Register the configuration type + factory.
- **Key API**:
  ```kotlin
  class LuaRocksRunConfigurationType : ConfigurationTypeBase(
      ID, "LuaRocks", "LuaRocks task run configuration",
      NotNullLazyValue.createValue { LuaIcons.ROCKET }) {
      init { addFactory(LuaRocksRunConfigurationFactory(this)) }
      companion object { const val ID = "LuaRocksRunConfiguration" }
  }
  ```

### 2.3 `net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationFactory`
  ```kotlin
  class LuaRocksRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
      override fun getId() = LuaRocksRunConfigurationType.ID
      override fun createTemplateConfiguration(project: Project): RunConfiguration =
          LuaRocksRunConfiguration(project, this, "LuaRocks")
      override fun getOptionsClass() = LuaRocksRunConfigurationOptions::class.java
  }
  ```

### 2.4 `net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationOptions`
- **Responsibility**: Persisted state. Each `provideDelegate(this, "<tag>")` second argument is
  the **XML option tag** (§4.1).
- **Key API** (mirrors `LuaRunConfigurationOptions`):
  ```kotlin
  class LuaRocksRunConfigurationOptions : RunConfigurationOptions() {
      private val myCommand = string("make").provideDelegate(this, "command")
      private val myArguments = string("").provideDelegate(this, "arguments")
      private val myRockspecPath = string("").provideDelegate(this, "rockspecPath")
      private val myGlobalFlags = string("").provideDelegate(this, "globalFlags")
      private val myEnvironmentVariables = map<String, String>().provideDelegate(this, "environmentVariables")
      private val myEnvironmentProcess = string("true").provideDelegate(this, "environmentProcess")
      private val myEnvironmentFile = string("").provideDelegate(this, "environmentFile")
      // var accessors for each (get/set via getValue/setValue), as in LuaRunConfigurationOptions
  }
  ```
  Note `environmentProcess` defaults to `"true"` → **pass parent env by default** (needed for
  the C toolchain, ROCKS-04-08).

### 2.5 `net.internetisalie.lunar.rocks.run.LuaRocksRunConfiguration`
- **Responsibility**: Build and run the command line.
- **Threading**: `getState` runs on a pooled execution thread (platform-managed).
- **Key API**:
  ```kotlin
  class LuaRocksRunConfiguration(project, factory, name)
      : RunConfigurationBase<LuaRocksRunConfigurationOptions?>(project, factory, name) {
      override fun getOptions(): LuaRocksRunConfigurationOptions
      // typed accessors: command, arguments, rockspecPath, globalFlags,
      //   environmentVariables: EnvironmentVariablesData? (the 3-prop bridge as in LuaRunConfiguration)
      override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> =
          LuaRocksRunSettingsEditor(project)
      override fun getState(executor, environment): RunProfileState  // §3.1
  }
  ```

### 2.6 `net.internetisalie.lunar.rocks.run.LuaRocksRunSettingsEditor`
- **Responsibility**: Settings UI. `SettingsEditor<LuaRocksRunConfiguration>` built with
  `FormBuilder` (cloned from `LuaRunSettingsEditor`).
- **Fields**:
  - **Command**: `ComboBox<String>` editable, items = `LUAROCKS_COMMANDS` (ROCKS-04-02) but
    `isEditable = true` for free text.
  - **Arguments**: `RawCommandLineEditor`.
  - **Rockspec**: `TextFieldWithBrowseButton` (single `.rockspec` file, ROCKS-04-03).
  - **Global flags**: `RawCommandLineEditor` (e.g. `--tree`, `--local`, `--server`, ROCKS-04-05).
  - **Environment**: `EnvironmentVariablesTextFieldWithBrowseButton` (ROCKS-04-08 vars + DEBUG).
- **Constants**:
  ```kotlin
  val LUAROCKS_COMMANDS = listOf("make","build","install","test","upload","list","show","remove")
  ```

## 3. Algorithms

### 3.1 `getState` / `startProcess` — command-line construction
- **Input → Output**: executor + environment → `CommandLineState` whose `startProcess()`
  returns a colored `ProcessHandler`.
- **Steps** (in `startProcess`):
  1. `exe = LuaRocksSettings.getInstance().executablePath` (default `"luarocks"`).
  2. `cmd = GeneralCommandLine(exe)`.
  3. `cmd.withParameters(ParametersListUtil.parse(globalFlags.orEmpty()))` (before the
     subcommand, e.g. `--tree foo`).
  4. `cmd.withParameters(command.orEmpty())` (the subcommand; default `make`).
  5. `cmd.withParameters(ParametersListUtil.parse(arguments.orEmpty()))`.
  6. If `command ∈ {"make","build"}` and `rockspecPath` is non-blank →
     `cmd.withParameters(rockspecPath)`.
  7. `cmd.withWorkDirectory(workingDir)` where `workingDir = project.basePath` (or the
     rockspec's parent if set).
  8. `environmentVariables?.configureCommandLine(cmd, true)` — applies the user env **and**
     parent env when `isPassParentEnvs` (default true → ROCKS-04-08: the system `cc`/`make`
     and `PATH` reach luarocks for C-module builds).
  9. `val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(cmd)`;
     `ProcessTerminatedListener.attach(handler)`; return `handler` (ROCKS-04-04: Run tool
     window + stop/rerun controls come for free from `CommandLineState`).
- **Edge handling**: blank `command` → default `"make"`. `createColoredProcessHandler` throws
  `ExecutionException` if `exe` is not found → surfaced as the platform's run-error balloon.

### 3.2 Before-Launch integration (ROCKS-04-06)
- **No custom code.** Any registered `RunConfiguration` is selectable as a Before Launch step
  via the platform's built-in *Run Another Configuration* provider
  (`com.intellij.execution.impl.RunConfigurationBeforeRunProvider`). Documented + covered by a
  manual verification step; the only requirement is that the type be registered (§2.2, §6).

### 3.3 C-library build environment (ROCKS-04-08)
- **Rule**: the template config sets `environmentProcess = "true"` so `isPassParentEnvs` is on
  → the inherited shell environment (compiler, `PATH`, `CC`, `MAKE`) is available to `luarocks
  build`. Users add build-specific vars (`CC`, `LUA_INCDIR`, `LUA_LIBDIR`, `CFLAGS`) through the
  Environment field; these are merged by `EnvironmentVariablesData.configureCommandLine`.
- No compiler bundling — the design relies on the platform-provided toolchain (out of scope to
  install one; `ROCKS-04-DR-01`).

## 4. External Data & Parsing

This feature **produces** a command line and **streams** raw process output to the console
unparsed (the console renders ANSI via `createColoredProcessHandler`). It consumes no external
text format, so there is nothing to parse. (Structured parsing of `luarocks` output belongs to
ROCKS-02.)

### 4.1 Persisted XML schema (serialization round-trip)
Each `StoredProperty` serialises as an `<option name="<tag>" value="…"/>` under the
`<configuration>` element in the project's run-configuration store
(`.idea/runConfigurations/<name>.xml` for shared, or `workspace.xml`). Sample:
```xml
<configuration name="luarocks make" type="LuaRocksRunConfiguration"
               factoryName="LuaRocks">
  <option name="command" value="make" />
  <option name="arguments" value="" />
  <option name="rockspecPath" value="$PROJECT_DIR$/my-app-scm-1.rockspec" />
  <option name="globalFlags" value="--tree lua_modules" />
  <option name="environmentProcess" value="true" />
  <option name="environmentFile" value="" />
  <option name="environmentVariables">
    <map>
      <entry key="DEBUG" value="1" />
    </map>
  </option>
  <method v="2" />
</configuration>
```
- **Round-trip test**: set every field, write via `XmlSerializer`/the run manager, re-read,
  assert equality (mirrors how `LuaRunConfigurationOptions` round-trips).

## 5. Data Flow

### Example 1: `luarocks make` (TC-ROCKS-04-01)
User creates a "LuaRocks" config, command `make`. Run → `startProcess` builds
`luarocks make` (work dir = project base, parent env passed) → colored handler streams build
output to the Run console; on exit, the terminated listener shows the exit code.

### Example 2: env var (TC-ROCKS-04-02)
User adds `DEBUG=1`. `configureCommandLine` injects it alongside the parent env; the process
sees `DEBUG=1`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `luarocks` not on PATH and no path set | `createColoredProcessHandler` throws `ExecutionException` → run-error balloon "Cannot run program 'luarocks'". |
| Multiple rockspecs in project | Rockspec field lets the user pick; default empty → luarocks uses the cwd rockspec. |
| Used as Before Launch for a Lua run config | Platform runs this config first; failure (non-zero exit) aborts the launch per platform semantics. |
| Free-text command not in preset list | ComboBox `isEditable = true` accepts it verbatim. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<configurationType implementation="net.internetisalie.lunar.rocks.run.LuaRocksRunConfigurationType"/>
<applicationService serviceImplementation="net.internetisalie.lunar.rocks.run.LuaRocksSettings"/>
```
- Icon: `LuaIcons.ROCKET` (add field backed by `icons/rocket_16.png` if absent — shared with
  ROCKS-03 §7).
- Before Launch: no registration — uses the built-in provider (§3.2).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-04-01 LuaRocks Run Config | M | §2.2–2.5, §7 |
| ROCKS-04-02 Command Presets | M | §2.6 (`LUAROCKS_COMMANDS`, editable combo) |
| ROCKS-04-03 Rockspec Selector | S | §2.6 (Rockspec field), §3.1 step 6 |
| ROCKS-04-04 Execution Console | M | §3.1 step 9 (`CommandLineState` + colored handler) |
| ROCKS-04-05 Custom Flags | S | §2.6 (Global flags), §3.1 step 3 |
| ROCKS-04-06 Before Launch | M | §3.2 (platform built-in provider) |
| ROCKS-04-07 Interactive Input | C | console PTY input via `createColoredProcessHandler` (best-effort) |
| ROCKS-04-08 C-Library Build Env | M | §2.4 default `environmentProcess="true"`, §3.3 |

## 9. Alternatives Considered

- **Custom `BeforeRunTaskProvider`** vs **platform built-in**: built-in *Run Another
  Configuration* already lets any run config be a before-launch step; a custom provider would
  be redundant. Chosen: none.
- **Binary via TOOL epic** vs **`LuaRocksSettings`**: TOOL is unbuilt; a `LuaCheckSettings`-style
  app service is the existing, callable pattern. When TOOL lands, `LuaRocksSettings` can
  delegate to it.
- **`map<String,String>` env vs flattened string**: mirrors `LuaRunConfigurationOptions` so
  the `EnvironmentVariablesData` bridge and UI component are reused verbatim.

## 10. Open Questions

_None — feature has cleared the planning bar._
