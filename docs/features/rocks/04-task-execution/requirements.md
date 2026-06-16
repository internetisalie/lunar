---
id: ROCKS-04
title: "04: Task Execution & Run Configurations"
type: feature
parent_id: ROCKS
status: "done"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-04: Task Execution & Run Configurations

## Scope
This feature enables users to define, save, and execute LuaRocks CLI commands as native IDE Run Configurations. This provides a repeatable way to perform build, install, and administrative tasks.

### In Scope
- Create a new `LuaRocksRunConfigurationType`.
- Support standard LuaRocks commands: `make`, `build`, `install`, `test`, `upload`.
- Configuration UI for command arguments, flags, and custom environment variables.
- Integration with the "Before Launch" task sequence.
- Persistence of configurations in `.idea/runConfigurations`.

### Out of Scope
- Automatic parsing of `Makefile` targets (unless explicitly requested).
- Direct integration with remote CI providers (CI systems should use the generated CLI commands).

## Syntax/Behavior

### Configuration Template
The configuration dialog will include:
- **Command**: A dropdown of common LuaRocks actions.
- **Arguments**: A text field for additional parameters.
- **Rockspec**: Selection of which `.rockspec` file to use (defaults to project root).
- **Environment**: Key-value pairs for variables like `LUAROCKS_CONFIG`.

### Execution
Running the configuration will:
1. Resolve the `luarocks` binary from `LuaRocksSettings.executablePath` (default: `luarocks` on `PATH`). *(Future: source it from the project-bound TOOL-01/02 registry once that integration lands — ROCKS does not depend on the TOOL track for Wave 10.)*
2. Execute the command in the IDE's execution console with proper ANSI color support.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-04-01** | **LuaRocks Run Config** | **Must** | **Full** | Provide a Run Configuration type for LuaRocks commands. |
| **ROCKS-04-02** | **Command Presets** | **Must** | **Full** | Dropdown with common commands (make, build, upload, test, install). |
| **ROCKS-04-03** | **Rockspec Selector** | **Should** | **Full** | Allow selecting a specific .rockspec if multiple exist. |
| **ROCKS-04-04** | **Execution Console** | **Must** | **Full** | Stream output to a standard IDE tool window with process controls. |
| **ROCKS-04-05** | **Custom Flags** | **Should** | **Full** | Support global flags like `--tree`, `--local`, or `--server`. |
| **ROCKS-04-06** | **Before Launch Integration** | **Must** | **Full** | Allow LuaRocks tasks to be run as "Before Launch" steps for other run configs (platform built-in provider). |
| **ROCKS-04-07** | **Interactive Input** | **Could** | **Partial** | Console PTY input via colored process handler; full interactivity best-effort. |
| **ROCKS-04-08** | **C-Library Build Support** | **Must** | **Full** | Environment set up for compiling C modules (pass-parent-env on by default). |

## Test Cases

### TC-ROCKS-04-01: Build Configuration
- **Input**: Create a config for `luarocks make`. Click Run.
- **Action**: IDE executes binary with the current project as root.
- **Expected Output**: Local dependencies are built; success message shown in console.

### TC-ROCKS-04-02: Environment Variables
- **Input**: Add `DEBUG=1` to environment. Run.
- **Expected Output**: Process receives variable; verbose output seen if supported by tool.

### TC-ROCKS-04-03: Configuration Round-Trip (serialization — design §4.1)
- **Input**: A config with command `make`, globalFlags `--tree lua_modules`, rockspecPath set,
  env `{DEBUG=1}`, passParentEnvs on.
- **Action**: Serialize via the run manager, then deserialize a fresh instance.
- **Expected Output**: every field equals the original; the XML contains
  `<option name="command" value="make"/>`, `<option name="globalFlags" .../>`, and an
  `environmentVariables` `<map>` entry for `DEBUG`.

### TC-ROCKS-04-04: Command-Line Assembly (unit — design §3.1)
- **Input**: command `build`, globalFlags `--local`, arguments `--no-doc`, rockspecPath
  `app-1.rockspec`.
- **Action**: Invoke command-line construction.
- **Expected Output**: argv = `luarocks --local build --no-doc app-1.rockspec`, work dir =
  project base.

### TC-ROCKS-04-05: Before Launch (manual — ROCKS-04-06)
- **Input**: Add the `luarocks make` config as a "Before Launch" step of a Lua run config.
- **Action**: Run the Lua config.
- **Expected Output**: `luarocks make` runs first; on its failure the Lua launch is aborted.

### TC-ROCKS-04-06: C-Library Build Env (manual — ROCKS-04-08)
- **Input**: A rockspec with a C `build.type = "builtin"` module; default config (pass parent
  env on); system C compiler present.
- **Action**: Run `luarocks make`.
- **Expected Output**: the C module compiles (the inherited `PATH`/`CC` reaches luarocks).
