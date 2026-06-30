---
id: "DEBUG-RUN-API"
title: "Run Configuration API Reference"
type: "spec"
parent_id: "DEBUG/RUN"
priority: "low"
folders:
  - "[[features/debug/requirements|requirements]]"
---

# Lunar Run Configuration API Reference

## Overview

The Lunar plugin provides comprehensive Lua program execution support through the `LuaRunConfiguration` class. This document details all configuration options, properties, and usage patterns.

## LuaRunConfiguration Class

**Location**: `src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt`

**Base Class**: `RunConfigurationBase<LuaRunConfigurationOptions>`

**Purpose**: Defines how Lua programs are executed, including interpreter selection, script path, arguments, environment variables, and debugging configuration.

## Configuration Properties

### Script Configuration

#### `scriptName: String?`
The path to the Lua file to execute.

```kotlin
runConfig.scriptName = "/path/to/script.lua"
// or relative to project/working directory
runConfig.scriptName = "main.lua"
```

**Notes**:
- If empty or null, interpreter starts in interactive mode (-i -v)
- Required for non-interactive execution
- Can be absolute or relative path

### Interpreter Configuration

#### `interpreter: LuaInterpreter?`
The Lua interpreter to use for execution.

```kotlin
val interpreter = LuaApplicationSettings.findInterpreter("/usr/bin/lua")
runConfig.interpreter = interpreter
```

**Structure** (`LuaInterpreter`):
- `path: String` - Full path to interpreter executable
- `product: LuaInterpreterFamily` - Interpreter type (LUA_5_1, LUA_5_4, MLUA, etc.)
- `version: String?` - Version string if detected

**Available Products**:
- `LUA_5_1` - Lua 5.1
- `LUA_5_2` - Lua 5.2  
- `LUA_5_3` - Lua 5.3
- `LUA_5_4` - Lua 5.4
- `MLUA` - MLua (Rust FFI)
- `UNKNOWN_PRODUCT` - Unknown interpreter

**Notes**:
- If null, execution throws `ExecutionException("Interpreter is not defined")`
- Automatically found from project/application settings if not set
- Can be manually set to override defaults

### Path and Directory Configuration

#### `workingDirectory: String?`
The working directory for process execution.

```kotlin
runConfig.workingDirectory = "/home/user/projects/my-lua-project"
```

**Notes**:
- If not set, uses project base directory
- Affects where files are searched for with relative paths
- Environment variable expansion is supported

#### `sourcePath: String?`
Lua module search path (LUA_PATH).

```kotlin
runConfig.sourcePath = "?.lua;?/init.lua;/usr/share/lua/5.4/?.lua"
```

**Notes**:
- If not set, uses project settings
- Fallback to LuaProjectSettings if empty
- Supports standard Lua path format with ? placeholders
- Used for require() and module loading

### Arguments and Environment

#### `programArguments: String?`
Command-line arguments passed to the Lua script (accessible via `arg` table).

```kotlin
// Single arguments
runConfig.programArguments = "arg1 arg2 arg3"

// With spaces/quotes (parsed by ParametersListUtil)
runConfig.programArguments = "\"quoted arg\" unquoted arg"

// Access in Lua script:
// print(arg[0])  -- script name
// print(arg[1])  -- "arg1"
// print(arg[2])  -- "arg2"
```

**Notes**:
- Parsed using `ParametersListUtil.parse()`
- Preserves quoted arguments with spaces
- Accessible in Lua via `arg` table (0-indexed)

#### `interpreterArguments: String?`
Arguments passed to the Lua interpreter itself (before script name).

```kotlin
runConfig.interpreterArguments = "-O2 -w2"
```

**Common Lua Interpreter Flags**:
- `-e chunk` - Execute code
- `-i` - Interactive mode
- `-l name` - Load library
- `-v` - Print version
- `-E` - Ignore environment
- `-O[0-2]` - Optimization level (Lua 5.4+)
- `-W` - Warnings (Lua 5.4+)

**Notes**:
- Interpreter-specific flags may vary
- Parsed with `ParametersListUtil.parse()`
- Applied before script path

#### `environmentVariables: EnvironmentVariablesData?`
Environment variables for process execution.

```kotlin
val envData = EnvironmentVariablesData.create(
    mapOf(
        "LUA_PATH" to "?.lua",
        "MY_VAR" to "my_value"
    ),
    inheritParentEnvironment = true,
    envFile = null
)
runConfig.environmentVariables = envData
```

**Access in Lua**:
```lua
local my_var = os.getenv("MY_VAR")
print("MY_VAR = " .. (my_var or "not set"))
```

**Special Handling**:
- If `environmentProcess` is true, inherits parent environment
- If `environmentFile` is set, loads variables from file
- Standard environment variables always available

#### `environmentProcess: String?` (Internal)
Whether to inherit parent process environment.

```kotlin
runConfig.environmentProcess = "true"  // inherit
runConfig.environmentProcess = "false" // don't inherit
```

#### `environmentFile: String?` (Internal)
Path to file containing environment variables (format: KEY=VALUE per line).

```kotlin
runConfig.environmentFile = "/path/to/.env"
```

## Execution Methods

### `getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState`

**Returns**: `CommandLineState` that manages process execution

**Process Creation Flow**:
1. Validates interpreter is available
2. Builds command line with interpreter path
3. Adds interpreter arguments
4. Adds script name and program arguments
5. Configures working directory
6. Applies environment variables
7. Configures debugging if debug executor
8. Creates ProcessHandler

**Example Executor Types**:
- `DefaultRunExecutor` - Normal execution
- `DefaultDebugExecutor` - Debug execution with breakpoints
- `DebugExecutor.EXECUTOR_ID` - ID constant: "Debug"

### Command Line Construction

The command line is built as:
```
<interpreter_path> [interpreter_args] [script_name] [program_args]
```

**Example**:
```
/usr/bin/lua -O2 main.lua arg1 arg2
```

**Internal Process**:
```kotlin
val commandLine = newLuaInterpreterCommandLine(interpreter) ?: throw ExecutionException(...)
commandLine.withParameters(interpreterArgs)
if (scriptName.isNotEmpty()) {
    commandLine.withParameters(scriptName)
} else {
    commandLine.withParameters("-v", "-i")  // interactive mode
}
commandLine.withParameters(programArgs)
commandLine.withWorkDirectory(workingDirectory)
environmentVariables?.configureCommandLine(commandLine, true)

val processHandler = ProcessHandlerFactory.getInstance()
    .createColoredProcessHandler(commandLine)
ProcessTerminatedListener.attach(processHandler)
```

## Debugging Support

### Debug Execution

When executed with `DefaultDebugExecutor`:

1. **Debugger Preloader Setup**:
   - Locates `debug.lua` from plugin resources
   - Sets `LUA_INIT` to preloader file
   - Enables MOBDebug protocol support

2. **Environment Variables Set**:
   - `LUNAR_LUA_PATH_TEMPLATE` - Plugin Lua module path
   - `LUNAR_DEBUGGER_PACKAGE` - "mobdebug" package identifier
   - `LUA_INIT` - Path to debugger preloader

3. **Process Handler**:
   - Uses colored output processor
   - Attaches termination listener
   - Returns DBGp protocol handler for IDE

**Breakpoint Support**:
- Line breakpoints: `LuaLineBreakpointType`
- Handler: `LuaLineBreakpointHandler`
- Debug runner: `LuaDebugRunner`

**Variables/Values**:
- Parser: `LuaDebugValueParser` - Parses responses from debugger
- Variable types: `LuaDebugVariable`, `LuaDebugValue`
- Stack frames: `LuaStackFrame`, `LuaExecutionStack`

## LuaRunConfigurationType

**ID**: `"LuaRunConfiguration"`

**Display Name**: `"Lua"`

**Description**: `"Lua run configuration type"`

**Factory**: `LuaRunConfigurationFactory`

### Registration

In `plugin.xml`:
```xml
<extensions defaultExtensionNs="com.intellij">
    <configurationType implementation="net.internetisalie.lunar.run.LuaRunConfigurationType"/>
</extensions>
```

The plugin uses `ConfigurationTypeBase` for auto-registration.

## LuaRunConfigurationFactory

**Purpose**: Creates new `LuaRunConfiguration` instances

**Methods**:
- `createTemplateConfiguration(project)` - Creates blank configuration
- `getOptionsClass()` - Returns `LuaRunConfigurationOptions` class

**Usage**:
```kotlin
val factory = LuaRunConfigurationFactory(LuaRunConfigurationType())
val config = factory.createTemplateConfiguration(project) as LuaRunConfiguration
```

## LuaRunConfigurationOptions

**Base Class**: `RunConfigurationOptions`

**Stored Properties**:
- `scriptName: String?`
- `interpreter: String?` (path, not LuaInterpreter object)
- `workingDirectory: String?`
- `sourcePath: String?`
- `environmentVariables: Map<String, String>`
- `environmentProcess: String?`
- `environmentFile: String?`
- `programArguments: String?`
- `interpreterArguments: String?`

**Notes**:
- Options are persisted in project `.idea/runConfigurations/`
- Automatically serialized/deserialized by IDE
- String format for interpreter (path) not object (LuaInterpreter)

## Integration with RunManager

### Creating and Saving Configuration

```kotlin
val runManager = RunManager.getInstance(project)
val config = LuaRunConfiguration(project, factory, "My Lua Script")
config.scriptName = "main.lua"
config.interpreter = interpreter

val settings = RunConfigurationFactory.createSettings(config)
runManager.addConfiguration(settings)

// Set as selected/default
if (runManager.selectedConfiguration == null) {
    runManager.selectedConfiguration = settings
}
```

### Executing Configuration

```kotlin
val executor = DefaultRunExecutor.getRunExecutorInstance()
val environment = ExecutionEnvironmentBuilder(project, executor)
    .runConfiguration(config)
    .build()

val runner = environment.runner ?: throw Exception("No runner found")
val result = runner.execute(environment)
val processHandler = result.processHandler
```

### Finding Configuration by Name

```kotlin
val runManager = RunManager.getInstance(project)
val settings = runManager.findConfigurationByName("My Lua Script")
val config = settings?.configuration as? LuaRunConfiguration
```

## UI Configuration Editor

**Class**: `LuaRunSettingsEditor`

**Location**: `src/main/kotlin/net/internetisalie/lunar/run/LuaRunSettingsEditor.kt`

**Features**:
- Script file selector with browse button
- Interpreter dropdown with detection
- Working directory selector
- Source path editor
- Program arguments editor
- Interpreter arguments editor
- Environment variables editor (table with add/remove buttons)
- Environment file selector

## Constants and Defaults

```kotlin
// Debugger configuration
const val DEBUGGER_PRELOADER_FILE = "debug.lua"
const val DEBUGGER_PACKAGE = "mobdebug"
const val ENV_LUA_INIT = "LUA_INIT"
const val ENV_LUNAR_LUA_PATH_TEMPLATE = "LUNAR_LUA_PATH_TEMPLATE"
const val ENV_LUNAR_DEBUGGER_PACKAGE = "LUNAR_DEBUGGER_PACKAGE"

// Type ID
const val ID = "LuaRunConfiguration"

// Interactive mode (when no script specified)
// Lua starts with: lua -v -i
```

## Errors and Exceptions

### ExecutionException Conditions

1. **No Interpreter Defined**:
   ```kotlin
   throw ExecutionException("Interpreter is not defined")
   ```

2. **Interpreter Not Found**:
   ```kotlin
   throw ExecutionException("Interpreter is not found")
   ```

3. **Debug Setup Failed**:
   ```kotlin
   throw ExecutionException("Failed to locate plugin directory")
   throw ExecutionException("Failed to locate debugger preloader")
   ```

## Complete Usage Example

```kotlin
// Create configuration
val factory = LuaRunConfigurationFactory(LuaRunConfigurationType())
val config = factory.createTemplateConfiguration(project) as LuaRunConfiguration

// Configure
config.name = "Run main.lua"
config.scriptName = "main.lua"
config.interpreter = LuaApplicationSettings.findInterpreter("/usr/bin/lua5.4")
config.workingDirectory = "/home/user/my-project"
config.interpreterArguments = "-O2"
config.programArguments = "arg1 arg2"
config.environmentVariables = EnvironmentVariablesData.create(
    mapOf("MY_VAR" to "value"),
    true,
    null
)

// Save to run manager
val runManager = RunManager.getInstance(project)
runManager.addConfiguration(RunConfigurationFactory.createSettings(config))
runManager.selectedConfiguration = runManager.findConfigurationByName("Run main.lua")

// Execute
val executor = DefaultRunExecutor.getRunExecutorInstance()
val environment = ExecutionEnvironmentBuilder(project, executor)
    .runConfiguration(config)
    .build()

val result = environment.runner?.execute(environment)
val processHandler = result?.processHandler

// Capture output
processHandler?.addProcessListener(object : ProcessListener {
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        println(event.text)
    }
    
    override fun processTerminated(event: ProcessEvent) {
        println("Exit code: ${processHandler.exitCode}")
    }
    
    override fun startNotified(event: ProcessEvent) {}
    override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {}
})
```

## Related Classes

- `LuaDebugRunner` - Handles debug execution
- `LuaDebugProcess` - Manages debug session
- `LuaDebugConnection` - DBGp protocol handler
- `LuaDebuggerController` - Breakpoint and variable management
- `LuaLineBreakpointType` - Breakpoint definition
- `LuaRunSettingsEditor` - UI for configuration

## See Also

- [IDE Execution Guide](ide-execution-integration-tests.md)
- [LuaApplicationSettings](../../../src/main/kotlin/net/internetisalie/lunar/settings/LuaApplicationSettings.kt)
- [LuaProjectSettings](../../../src/main/kotlin/net/internetisalie/lunar/project/LuaProjectSettings.kt)
- [newLuaInterpreterCommandLine](../../../src/main/kotlin/net/internetisalie/lunar/command/InterpreterCommand.kt)
