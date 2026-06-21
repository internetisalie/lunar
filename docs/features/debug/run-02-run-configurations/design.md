---
id: "RUN-02-DESIGN"
title: "Technical Design"
type: "design"
status: "done"
parent_id: "RUN-02"
folders:
  - "[[features/debug/run-02-run-configurations/requirements|requirements]]"
---

# Technical Design: RUN-02 — Run Configurations

## 1. Architecture Overview

### Current State
The feature is implemented. All types live in a single file,
`src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt`, registered via
`plugin.xml`. This document reverse-specifies the shipped design to the planning bar.

### Prior Art in This Repo
- **`LuaRunConfiguration.kt`** (`run/LuaRunConfiguration.kt:38-353`) — the implementation
  itself: `LuaRunConfigurationType`, `LuaRunConfigurationFactory`,
  `LuaRunConfigurationOptions`, `LuaRunConfiguration`, `LuaRunSettingsEditor`.
- **`LuaTestRunConfiguration`** (`run/test/LuaTestRunConfiguration.kt:169`) — a *sibling*
  configuration type (RUN-05) that mirrors the same pattern (Type/Factory/Options/State)
  for busted test runs. RUN-02 is **not** extended or replaced by it; they coexist as two
  registered `<configurationType>`s. The synthetic-UNKNOWN interpreter fallback in
  RUN-02-09 is the same idiom used there.
- **`LuaRocksRunConfiguration`** (`rocks/run/LuaRocksRunConfiguration.kt`) — a third,
  independent configuration type for LuaRocks tasks (ROCKS-04). No overlap.
- **`newLuaInterpreterCommandLine`** (`command/LuaCommandLine.kt:32-45`) — the shared
  command-line factory; RUN-02 reuses it (does not reimplement command-line construction).
- **No `RunConfigurationProducer`** exists for the Lua run config — only
  `LuaTestRunConfigurationProducer` (`plugin.xml:410-411`) for the test type. Context
  creation is therefore out of scope (see `risks-and-gaps.md` Gap 2.1).

### Target State
Five classes in one file: a `ConfigurationTypeBase` type that registers one
`ConfigurationFactory`; a `RunConfigurationOptions` subclass holding the serialized state;
a `RunConfigurationBase` that exposes typed accessors over those options and produces a
`CommandLineState`; and a `SettingsEditor` form. The state's `startProcess` is the only
non-trivial logic (command-line assembly + debug env injection).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.run.LuaRunConfigurationType`
- **Responsibility**: Register the "Lua" configuration type and its factory.
- **Threading**: platform-managed; constructed at registration.
- **Collaborators**: `ConfigurationTypeBase`, `LuaIcons.FILE`
  (`lang/LuaIcons`), `LuaRunConfigurationFactory`.
- **Key API**:
  ```kotlin
  class LuaRunConfigurationType : ConfigurationTypeBase(
      ID, "Lua", "Lua run configuration type",
      NotNullLazyValue.createValue { LuaIcons.FILE }) {
      init { addFactory(LuaRunConfigurationFactory(this)) }
      companion object { const val ID: String = "LuaRunConfiguration" }
  }
  ```

### 2.2 `net.internetisalie.lunar.run.LuaRunConfigurationFactory`
- **Responsibility**: Create template configs and bind the options class.
- **Threading**: platform-managed.
- **Collaborators**: `ConfigurationFactory`, `LuaRunConfiguration`,
  `LuaRunConfigurationOptions`.
- **Key API**:
  ```kotlin
  class LuaRunConfigurationFactory(type: ConfigurationTypeBase) : ConfigurationFactory(type) {
      override fun getId(): String = LuaRunConfigurationType.ID
      override fun createTemplateConfiguration(project: Project): RunConfiguration =
          LuaRunConfiguration(project, this, "Lua")
      override fun getOptionsClass(): Class<out BaseState> =
          LuaRunConfigurationOptions::class.java
  }
  ```

### 2.3 `net.internetisalie.lunar.run.LuaRunConfigurationOptions`
- **Responsibility**: Serialized state for one configuration.
- **Threading**: platform serialization; plain data.
- **Collaborators**: `RunConfigurationOptions`, `StoredProperty`.
- **Key API** (every property is a `StoredProperty` delegate; map for env):
  ```kotlin
  class LuaRunConfigurationOptions : RunConfigurationOptions() {
      var scriptName: String?            // key "scriptName"
      var interpreter: String?           // key "interpreter" (path)
      var workingDirectory: String?      // key "workingDirectory"
      var sourcePath: String?            // key "sourcePath"
      var environmentVariables: MutableMap<String, String>  // key "environmentVariables"
      var environmentFile: String?       // key "environmentFile"
      var environmentProcess: String?    // key "environmentProcess" (boolean-as-string)
      var programArguments: String?      // key "programArguments"
      var interpreterArguments: String?  // key "interpreterArguments"
  }
  ```

### 2.4 `net.internetisalie.lunar.run.LuaRunConfiguration`
- **Responsibility**: Typed view over the options; produces the run state.
- **Threading**: `getState`/`startProcess` on the execution thread; no PSI write.
- **Collaborators**: `RunConfigurationBase<LuaRunConfigurationOptions?>`,
  `LuaApplicationSettings.findInterpreter` (`settings/LuaApplicationSettings.kt:71`),
  `LuaInterpreter` / `LuaInterpreterFamily.UNKNOWN_PRODUCT`
  (`platform/LuaInterpreter.kt:52,97`), `newLuaInterpreterCommandLine`
  (`command/LuaCommandLine.kt:32`), `LuaProjectSettings.…expandSourcePath`
  (`settings/LuaProjectSettings.kt:65`), `LuaFileUtil.getPluginVirtualDirectoryChild`
  (`util/LuaFileUtil.kt:22`), `DefaultDebugExecutor.EXECUTOR_ID`.
- **Key API**:
  ```kotlin
  class LuaRunConfiguration(project: Project, factory: ConfigurationFactory?, name: String?) :
      RunConfigurationBase<LuaRunConfigurationOptions?>(project, factory, name) {
      override fun getOptions(): LuaRunConfigurationOptions
      var scriptName: String?
      var interpreter: LuaInterpreter?       // resolves path → interpreter (RUN-02-09)
      var workingDirectory: String?
      var sourcePath: String?
      var environmentVariables: EnvironmentVariablesData?
      var programArguments: String?
      var interpreterArguments: String?
      override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> =
          LuaRunSettingsEditor(project)
      override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState
      companion object {
          const val DEBUGGER_PRELOADER_FILE = "debug.lua"
          const val DEBUGGER_PACKAGE = "mobdebug"
          const val ENV_LUA_INIT = "LUA_INIT"
          const val ENV_LUNAR_LUA_PATH_TEMPLATE = "LUNAR_LUA_PATH_TEMPLATE"
          const val ENV_LUNAR_DEBUGGER_PACKAGE = "LUNAR_DEBUGGER_PACKAGE"
      }
  }
  ```

### 2.5 `net.internetisalie.lunar.run.LuaRunSettingsEditor`
- **Responsibility**: Form UI; reset/apply between options and widgets.
- **Threading**: EDT; UI-only mutations.
- **Collaborators**: `SettingsEditor<LuaRunConfiguration>`, `FormBuilder`,
  `ComboBox<LuaInterpreter>` via `customizeLuaInterpreterComboBox`
  (`platform/LuaInterpreterComponent.kt:18`), `TextFieldWithBrowseButton`,
  `ExpandableTextField` with `PathConfiguration.TEMPLATE_SEPARATOR` (`";"`,
  `lang/path/SourcePathPattern.kt:16`), `EnvironmentVariablesTextFieldWithBrowseButton`,
  `RawCommandLineEditor`, `FileChooserDescriptorFactory`.
- **Key API**:
  ```kotlin
  class LuaRunSettingsEditor(project: Project) : SettingsEditor<LuaRunConfiguration>() {
      override fun resetEditorFrom(runConfiguration: LuaRunConfiguration)
      override fun applyEditorTo(runConfiguration: LuaRunConfiguration)
      override fun createEditor(): JComponent
  }
  ```

## 3. Algorithms

### 3.1 Command-line assembly (`CommandLineState.startProcess`)
- **Input → Output**: `LuaRunConfiguration` state + `Executor` → `ProcessHandler`.
- **Steps** (exact order; `run/LuaRunConfiguration.kt:226-279`):
  1. `interpreter = this.interpreter ?: throw ExecutionException("Interpreter is not defined")`.
  2. `commandLine = newLuaInterpreterCommandLine(interpreter) ?: throw ExecutionException("Interpreter is not found")`.
  3. `commandLine.withParameters(ParametersListUtil.parse(interpreterArguments.orEmpty()))`.
  4. `scriptName = options.scriptName.orEmpty()`; if non-empty → `withParameters(scriptName)`;
     else → `withParameters("-v", "-i")` (RUN-02-08).
  5. `commandLine.withParameters(ParametersListUtil.parse(programArguments.orEmpty()))`.
  6. `commandLine.withWorkDirectory(workingDirectory)`.
  7. `environmentVariables?.configureCommandLine(commandLine, true)` (RUN-02-05).
  8. If debug executor → apply §3.2.
  9. Apply `LUA_PATH` per §3.3.
  10. `processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)`;
      `ProcessTerminatedListener.attach(processHandler)`; return it.
- **Rules / edge handling**: empty interpreter args / program args parse to an empty list
  (no params added). A `null` working directory leaves the command line's default working
  dir (the interpreter's parent, set by `newLuaInterpreterCommandLine`).
- **Complexity / bounds**: O(args); single process spawn.

### 3.2 Debug env injection
- **Input → Output**: plugin VFS dir → three environment entries.
- **Steps** (`run/LuaRunConfiguration.kt:250-260`):
  1. `pluginLuaPath = LuaFileUtil.getPluginVirtualDirectoryChild("lua") ?: throw ExecutionException("Failed to locate plugin directory")`.
  2. `debuggerPreloaderFile = pluginLuaPath.findChild("debug.lua") ?: throw ExecutionException("Failed to locate debugger preloader")`.
  3. `withEnvironment(ENV_LUNAR_LUA_PATH_TEMPLATE, "${pluginLuaPath.path}/?/init.lua;${pluginLuaPath.path}/?.lua")`.
  4. `withEnvironment(ENV_LUNAR_DEBUGGER_PACKAGE, "mobdebug")`.
  5. `withEnvironment(ENV_LUA_INIT, "@${debuggerPreloaderFile.path}")`.
- **Rules / edge handling**: applied only when
  `executor.id == DefaultDebugExecutor.EXECUTOR_ID`; otherwise skipped entirely.

### 3.3 LUA_PATH resolution
- **Input → Output**: config `sourcePath` + project settings → at most one `LUA_PATH` entry.
- **Steps** (`run/LuaRunConfiguration.kt:262-272`):
  1. `sourcePath = this.sourcePath ?: ""`.
  2. If `sourcePath.isNotEmpty()` → `withEnvironment("LUA_PATH", sourcePath)` and stop.
  3. Else `luaPath = LuaProjectSettings.getInstance(project).state.expandSourcePath(project)`;
     if `luaPath.isNotEmpty()` → `withEnvironment("LUA_PATH", luaPath)`.
- **Rules / edge handling**: if both are empty, `LUA_PATH` is left unset (interpreter
  default applies).

### 3.4 Interpreter resolution (`LuaRunConfiguration.interpreter` getter)
- **Input → Output**: `options.interpreter: String?` → `LuaInterpreter?`.
- **Steps** (`run/LuaRunConfiguration.kt:169-178`):
  1. `path = options.interpreter ?: return null`.
  2. `if (path.isEmpty()) return null`.
  3. `return LuaApplicationSettings.findInterpreter(path) ?: LuaInterpreter(path = path, product = LuaInterpreterFamily.UNKNOWN_PRODUCT)`.

### 3.5 EnvironmentVariablesData mapping
- **Input → Output**: stored map + `environmentProcess` + `environmentFile` ↔
  `EnvironmentVariablesData`.
- **Getter** (`:192-197`): `EnvironmentVariablesData.create(options.environmentVariables,
  options.environmentProcess.toBoolean(), options.environmentFile)`.
- **Setter** (`:198-208`): non-null → copy envs map, `environmentProcess =
  isPassParentEnvs.toString()`, `environmentFile = data.environmentFile`; null → reset all.

## 4. External Data & Parsing
Argument strings (interpreter/program arguments) are parsed with the platform's
`ParametersListUtil.parse(String): List<String>` (shell-like tokenization). No bespoke
parser. No other external/unstructured input is consumed; the `mobdebug` preloader is a
bundled file located by VFS lookup, not parsed.

## 5. Data Flow

### Example 1: Run a script
User picks interpreter + `main.lua` + program args `a b` → presses Run → platform calls
`getState(RunExecutor, env)` → `startProcess` builds `[<interpreterArgs>, main.lua, a, b]`,
sets work dir + env + `LUA_PATH` → colored process handler streams stdout/stderr to the
Run console.

### Example 2: Debug the same config
Same config under the Debug executor → §3.2 injects `LUNAR_LUA_PATH_TEMPLATE`,
`LUNAR_DEBUGGER_PACKAGE=mobdebug`, `LUA_INIT=@…/debug.lua` → the preloader starts the
`mobdebug` server, which `LuaDebugRunner` (DEBUG epic) connects to over DBGp.

### Example 3: REPL fallback
Config with no script → `startProcess` appends `-v -i` → an interactive interpreter session
runs in the console.

## 6. Edge Cases
- **No interpreter** → `ExecutionException("Interpreter is not defined")` (TC 5).
- **Interpreter path not executable** → `newLuaInterpreterCommandLine` returns null →
  `ExecutionException("Interpreter is not found")`.
- **Plugin dir / preloader missing under Debug** → `ExecutionException` with the
  corresponding message; Run executor unaffected.
- **Empty source path and empty project source path** → `LUA_PATH` unset.
- **Unregistered interpreter path** → synthetic UNKNOWN-product interpreter (TC 4).
- **`.jar` interpreter** → `newLuaInterpreterCommandLine` rewrites exe to `java -cp <jar> lua`
  (`command/LuaCommandLine.kt:39-42`).

## 7. Integration Points

```xml
<!-- plugin.xml (src/main/resources/META-INF/plugin.xml:406-407) -->
<extensions defaultExtensionNs="com.intellij">
  <configurationType
      implementation="net.internetisalie.lunar.run.LuaRunConfigurationType"/>
</extensions>
```

- The debug path is consumed by `<programRunner implementation="net.internetisalie.lunar.run.LuaDebugRunner"/>`
  (`plugin.xml:452`) and `<xdebugger.breakpointType implementation="net.internetisalie.lunar.run.LuaLineBreakpointType"/>`
  (`plugin.xml:453`) — DEBUG epic, not registered by RUN-02.
- Settings keys read at launch: `LuaProjectSettings` state (`expandSourcePath`),
  `LuaApplicationSettings` interpreters. No new settings keys are introduced.
- No `runConfigurationProducer` is registered for this type (see Gap 2.1).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| RUN-02-01 | M | §2.1, §2.2, §7 |
| RUN-02-02 | M | §2.5 |
| RUN-02-03 | M | §2.3, §3.5 |
| RUN-02-04 | M | §2.4, §3.1 |
| RUN-02-05 | S | §3.1 (step 7), §3.5 |
| RUN-02-06 | S | §3.3 |
| RUN-02-07 | S | §3.2 |
| RUN-02-08 | C | §3.1 (step 4) |
| RUN-02-09 | S | §3.4 |

## 9. Alternatives Considered
- **Separate `CommandLineState` subclass** vs. the anonymous `CommandLineState` inside
  `getState`: the anonymous form keeps the small launch logic local; a named class would be
  warranted only if a producer/context-creation path needed to reuse it (deferred — Gap 2.1).
- **`SimpleProgramRunner`/custom runner** vs. relying on the platform default Run +
  `LuaDebugRunner` for Debug: the platform default already streams to the console; only the
  debug session needs the dedicated runner, which the DEBUG epic owns.

## 10. Open Questions

_None — feature has cleared the planning bar._
</content>
