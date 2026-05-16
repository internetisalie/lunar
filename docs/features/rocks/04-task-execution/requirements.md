---
folders:
  - "[[features/rocks/requirements|requirements]]"
title: "04: Task Execution & Run Configurations"
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
1. Resolve the project's bound `luarocks` binary (via `TOOL-01/02`).
2. Execute the command in the IDE's execution console with proper ANSI color support.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-04-01** | **LuaRocks Run Config** | **Must** | **Pending** | Provide a Run Configuration type for LuaRocks commands. |
| **ROCKS-04-02** | **Command Presets** | **Must** | **Pending** | Dropdown with common commands (make, build, upload, test, install). |
| **ROCKS-04-03** | **Rockspec Selector** | **Should** | **Pending** | Allow selecting a specific .rockspec if multiple exist. |
| **ROCKS-04-04** | **Execution Console** | **Must** | **Pending** | Stream output to a standard IDE tool window with process controls. |
| **ROCKS-04-05** | **Custom Flags** | **Should** | **Pending** | Support global flags like `--tree`, `--local`, or `--server`. |
| **ROCKS-04-06** | **Before Launch Integration** | **Must** | **Pending** | Allow LuaRocks tasks to be run as "Before Launch" steps for other run configs. |
| **ROCKS-04-07** | **Interactive Input** | **Could** | **Pending** | Support basic interactive prompts (e.g., login credentials) in the console. |
| **ROCKS-04-08** | **C-Library Build Support** | **Must** | **Pending** | Ensure environment is set up for compiling C modules if required by the rockspec. |

## Test Cases

### TC-ROCKS-04-01: Build Configuration
- **Input**: Create a config for `luarocks make`. Click Run.
- **Action**: IDE executes binary with the current project as root.
- **Expected Output**: Local dependencies are built; success message shown in console.

### TC-ROCKS-04-02: Environment Variables
- **Input**: Add `DEBUG=1` to environment. Run.
- **Expected Output**: Process receives variable; verbose output seen if supported by tool.
