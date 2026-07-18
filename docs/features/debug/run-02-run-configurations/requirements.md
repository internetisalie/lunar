---
id: "RUN-02"
title: "RUN-02: Run Configurations"
type: "feature"
status: "done"
vf_icon: ✅
priority: "medium"
parent_id: "DEBUG/RUN"
folders: ["[[features/debug/requirements|requirements]]"]
---

# RUN-02: Run Configurations

## Overview

RUN-02 provides a first-class IntelliJ **run configuration type** for executing a Lua
script with a chosen interpreter. It lets a user create, edit, persist, and launch a
"Lua" configuration (script file, interpreter, working directory, arguments, environment
variables, source-path templates) and routes the same configuration through the debugger
when launched with the Debug executor. It is the execution surface that consumes the
interpreter model from [RUN-01](../run-01-lua-interpreter-sdk/requirements.md) and is the
host for the DBGp debug session in the DEBUG epic.

## Scope

### In Scope
- A registered run-configuration type ("Lua") creatable from the Run/Debug Configurations
  dialog (the `+` menu).
- A settings editor (form UI) to configure interpreter, script file, working directory,
  source-path templates, environment variables, interpreter arguments, and program
  arguments.
- Persistence (serialization) of all configured options across IDE restarts.
- Building a process command line from the configuration and launching it via a colored
  process handler (Run executor).
- `LUA_PATH` injection from the configuration's source-path templates, with a fallback to
  the project's configured source path.
- Debug executor integration: injecting the `mobdebug` preloader environment so the same
  configuration starts a debuggable process.
- REPL fallback: when no script file is set, launch the interpreter interactively
  (`-v -i`).

### Out of Scope
- **Context "create from element"** (right-click a `.lua` file → *Run* via a
  `RunConfigurationProducer`). Not implemented for the Lua run config; tracked in
  `risks-and-gaps.md` (Gap 2.1). The test-runner producer is a separate feature
  ([RUN-05](../run-05-test-runner/requirements.md)).
- The interpreter discovery/model itself — owned by [RUN-01](../run-01-lua-interpreter-sdk/requirements.md).
- The DBGp protocol / debug session mechanics — owned by the DEBUG epic
  (`LuaDebugRunner`, `LuaDebugProcess`, `LuaDebugConnection`). RUN-02 only injects the
  preloader env and defers to those.
- LuaRocks task run configs (ROCKS-04) and the interactive console
  ([RUN-03](../requirements.md)).

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| RUN-02-01 | **Configuration type registration** | M | Full | A "Lua" run-configuration type is registered and selectable in the Run/Debug Configurations dialog. |
| RUN-02-02 | **Editable options** | M | Full | The settings editor exposes interpreter, script file, working directory, source-path templates, environment, interpreter arguments, program arguments. |
| RUN-02-03 | **Option persistence** | M | Full | All configured options are serialized and restored across IDE restart / reopen of the dialog. |
| RUN-02-04 | **Script execution** | M | Full | Running the configuration builds a command line from the interpreter + script + arguments and launches a process. |
| RUN-02-05 | **Environment variables** | S | Full | Configured env vars (incl. pass-parent-envs and env file) are applied to the launched process. |
| RUN-02-06 | **LUA_PATH injection** | S | Full | Source-path templates set `LUA_PATH`; when empty, fall back to the project source path. |
| RUN-02-07 | **Debug executor integration** | S | Full | Launching under the Debug executor injects the `mobdebug` preloader env so the process is debuggable. |
| RUN-02-08 | **REPL fallback** | C | Full | With no script file, the interpreter is launched interactively (`-v -i`). |
| RUN-02-09 | **Interpreter resolution** | S | Full | The stored interpreter path resolves to a known `LuaInterpreter`, or a synthetic UNKNOWN-product interpreter when unregistered. |

## Detailed Specifications

### RUN-02-01: Configuration type registration
`LuaRunConfigurationType` extends `ConfigurationTypeBase` with id `"LuaRunConfiguration"`,
display name `"Lua"`, description `"Lua run configuration type"`, and icon
`LuaIcons.FILE`. It registers a single `LuaRunConfigurationFactory`. Registered in
`plugin.xml` under `<configurationType>`.

### RUN-02-02: Editable options
The editor (`LuaRunSettingsEditor`) is a `FormBuilder` panel with rows: *Interpreter*
(combo box of `LuaInterpreter`), *Script file* (file chooser), *Working directory* (folder
chooser), *Source path templates* (expandable `;`-separated field), *Environment*
(`EnvironmentVariablesTextFieldWithBrowseButton`), *Interpreter arguments*
(`RawCommandLineEditor`), *Program arguments* (`RawCommandLineEditor`).

### RUN-02-03: Option persistence
Options are stored in `LuaRunConfigurationOptions` (extends `RunConfigurationOptions`)
using `StoredProperty` delegates: `scriptName`, `interpreter` (path), `workingDirectory`,
`sourcePath`, `environmentVariables` (map), `environmentFile`, `environmentProcess`,
`programArguments`, `interpreterArguments`. The platform serializes these to the run
config XML automatically.

### RUN-02-04: Script execution
`LuaRunConfiguration.getState` returns a `CommandLineState` whose `startProcess` builds a
`GeneralCommandLine` from the resolved interpreter, appends interpreter args, the script
(or `-v -i`), program args, working directory, env, and source path, then creates a
colored process handler. See design §3.1 for the exact ordering.

### RUN-02-05: Environment variables
`environmentVariables` is exposed as `EnvironmentVariablesData` (envs map +
`isPassParentEnvs` from `environmentProcess` + `environmentFile`). Applied via
`EnvironmentVariablesData.configureCommandLine(commandLine, true)`.

### RUN-02-06: LUA_PATH injection
If the configuration's `sourcePath` is non-empty, set `LUA_PATH` to it. Otherwise read
`LuaProjectSettings.getInstance(project).state.expandSourcePath(project)`; if non-empty,
set `LUA_PATH` to that.

### RUN-02-07: Debug executor integration
When `executor.id == DefaultDebugExecutor.EXECUTOR_ID`, locate the plugin's bundled `lua`
directory and `debug.lua` preloader and inject: `LUNAR_LUA_PATH_TEMPLATE`,
`LUNAR_DEBUGGER_PACKAGE = "mobdebug"`, `LUA_INIT = "@<preloader path>"`. See design §3.2.

### RUN-02-08: REPL fallback
When `options.scriptName` is empty, append `-v -i` instead of a script path so the
interpreter starts an interactive REPL.

### RUN-02-09: Interpreter resolution
`LuaRunConfiguration.interpreter` getter: read `options.interpreter` path; if null/empty →
`null`; else `LuaApplicationSettings.findInterpreter(path)`, falling back to a synthetic
`LuaInterpreter(path = path, product = LuaInterpreterFamily.UNKNOWN_PRODUCT)`.

## Behavior Rules
- Command-line argument order is fixed: interpreter args → script (or `-v -i`) → program
  args (design §3.1).
- A `null` interpreter aborts launch with `ExecutionException("No Lua runtime is configured. Add one under Settings | Languages & Frameworks | Lua | Toolchain.")`;
  an interpreter whose executable cannot be located aborts with `"Interpreter is not found"`.
- Debug-only env vars are injected **only** under the Debug executor; the Run executor
  produces no `mobdebug` env.
- The configuration's `sourcePath` takes precedence over the project source path for
  `LUA_PATH`.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | RUN-02-03 | A `LuaRunConfiguration` with `scriptName="myscript.lua"`, `workingDirectory="/home/user/work"`, `programArguments="--arg1 val1"` | Read the properties back | Getters return exactly those values (covered by `TestLuaRunConfiguration.testOptionsPersistence`) |
| 2 | RUN-02-01 | A fresh project | Instantiate `LuaRunConfigurationType()` | `id == "LuaRunConfiguration"`, display name `"Lua"`, exactly one factory |
| 3 | RUN-02-09 | `options.interpreter = ""` | Read `LuaRunConfiguration.interpreter` | Returns `null` |
| 4 | RUN-02-09 | `options.interpreter = "/usr/bin/lua"` (unregistered) | Read `interpreter` | Returns a `LuaInterpreter` with `path="/usr/bin/lua"`, `product=UNKNOWN_PRODUCT` |
| 5 | RUN-02-04 | A config with no interpreter set | Build the state and call `startProcess` | Throws `ExecutionException("No Lua runtime is configured. Add one under Settings | Languages & Frameworks | Lua | Toolchain.")` |
| 6 | RUN-02-08 | A config with empty `scriptName`, valid interpreter | Build the command line | Parameters include `-v -i` and no script path |
| 7 | RUN-02-04 | A config with `scriptName="main.lua"`, `interpreterArguments="-W"`, `programArguments="a b"` | Build the command line | Parameter list is `[-W, main.lua, a, b]` in that order |
| 8 | RUN-02-07 | A config launched with the Debug executor | Inspect the command line environment | Contains `LUNAR_DEBUGGER_PACKAGE=mobdebug`, `LUA_INIT` starting with `@`, and `LUNAR_LUA_PATH_TEMPLATE` |
| 9 | RUN-02-06 | A config with `sourcePath="/lib/?.lua"` | Build the command line | Environment `LUA_PATH == "/lib/?.lua"` |
| 10 | RUN-02-02 | A new "Lua" run configuration open in the Run/Debug Configurations dialog | Manually populate every editor row (interpreter, script file, working directory, source-path templates, environment, interpreter arguments, program arguments) via `LuaRunSettingsEditor`, apply, then reopen the dialog | All seven rows are present and editable; values applied are shown again on reopen — **manual** test, steps in [`human-verification-checklists.md`](human-verification-checklists.md) (TC 10) |

> **Note**: TC 10 is a **manual / human-verification** case (UI settings editor); there is no
> headless assertion for editor-widget presence. It is executed via the checklist in
> [`human-verification-checklists.md`](human-verification-checklists.md) (TC 10), not by an
> automated test.

## Acceptance Criteria
- [x] RUN-02-01: "Lua" type appears in the Run/Debug Configurations dialog (`+` menu).
- [x] RUN-02-02: All listed fields are editable in the settings editor.
- [x] RUN-02-03: Options round-trip across dialog reopen / IDE restart.
- [x] RUN-02-04: Running launches the interpreter against the script and streams output.
- [x] RUN-02-05: Env vars (and pass-parent / env file) reach the process.
- [x] RUN-02-06: `LUA_PATH` reflects config source path, else project source path.
- [x] RUN-02-07: Debug launch injects the `mobdebug` preloader env.
- [x] RUN-02-08: Empty script launches the interactive REPL.
- [x] RUN-02-09: Interpreter path resolves (known or synthetic UNKNOWN).

## Non-Functional Requirements
- **Threading**: `startProcess` runs on the platform's execution thread (not EDT); it
  performs no PSI/VFS write. The settings editor's `createEditor`/`resetEditorFrom`/
  `applyEditorTo` run on the EDT and perform only UI mutations.
- **Memory**: the configuration holds only the `Project` passed by the platform (standard
  `RunConfigurationBase` contract); no extra hard refs to `Editor`/`PsiFile` are retained.
- **No blocking I/O on EDT**: plugin-directory / preloader lookup happens inside
  `startProcess`, off the EDT.

## Dependencies
- [RUN-01: Lua Interpreter SDK](../run-01-lua-interpreter-sdk/requirements.md) — supplies
  `LuaInterpreter`, `LuaInterpreterFamily`, `LuaApplicationSettings.findInterpreter`.
- `net.internetisalie.lunar.command.newLuaInterpreterCommandLine` — command-line factory.
- DEBUG epic — `LuaDebugRunner` consumes the injected `mobdebug` env for the debug session.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
</content>
