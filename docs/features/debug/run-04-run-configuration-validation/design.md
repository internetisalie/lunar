---
id: "RUN-04-DESIGN"
title: "Technical Design"
type: "design"
status: "done"
parent_id: "RUN-04"
folders:
  - "[[features/debug/run-04-run-configuration-validation/requirements|requirements]]"
---

# Technical Design: RUN-04 — Run Configuration Validation

<!-- Reverse-engineered TDD. Every symbol below is grounded to a real file:line in this repo. -->

## 1. Architecture Overview

### Current State
Validation is **already implemented** and lives entirely inside the main run configuration's
process-launch path. There is no separate validator class and no `checkConfiguration()` override
on `LuaRunConfiguration`; instead, `getState()` returns an anonymous `CommandLineState` whose
`startProcess()` performs the checks and throws `ExecutionException` on failure. This matches the
project's own status note ("Interpreter + script validation in `getState()`",
`docs/status-detail.md:162`).

### Prior Art in This Repo

| Component | File:line | Disposition |
|-----------|-----------|-------------|
| `LuaRunConfiguration.getState()` (anonymous `CommandLineState.startProcess()`) | `run/LuaRunConfiguration.kt:226-281` | **This is the feature** — documented as-is, not duplicated |
| `interpreter` getter (null/empty → null) | `run/LuaRunConfiguration.kt:169-178` | REUSE — the null-coalescing source for RUN-04-01 |
| `newLuaInterpreterCommandLine(interpreter)` | `command/LuaCommandLine.kt:32` | REUSE — returns nullable command line for RUN-04-02 |
| `LuaApplicationSettings.findInterpreter(path)` | `settings/LuaApplicationSettings.kt:71` | REUSE — interpreter resolution |
| `LuaFileUtil.getPluginVirtualDirectoryChild(vararg)` | `util/LuaFileUtil.kt:22` | REUSE — bundled-asset lookup for RUN-04-03 |
| `LuaProjectSettings.expandSourcePath(project)` | `settings/LuaProjectSettings.kt:65` | REUSE — source-path fallback (RUN-04-05) |
| `LuaTestRunConfiguration.checkConfiguration()` | `run/test/LuaTestRunConfiguration.kt:247-254` | SEPARATE — belongs to `RUN-05`; the edit-time validation pattern, **not** part of RUN-04 |

> Grounding note: the original feature brief described the validation as living in
> `LuaRunConfiguration.checkConfiguration()`. No such override exists on the main run config
> (`grep checkConfiguration src` returns only the test config and its test). The real location is
> `startProcess()`.

### Target State
No change required — the design records the shipped behavior. Future hardening (script-file
existence, edit-time `checkConfiguration()`) is captured in [risks-and-gaps.md](risks-and-gaps.md).

## 2. Core Components

### 2.1 `net.internetisalie.lunar.run.LuaRunConfiguration`
- **Responsibility**: Hosts the run/debug configuration and, via its `getState()` →
  `CommandLineState.startProcess()`, validates and launches the Lua process.
- **Threading**: `startProcess()` is invoked by the platform's program runner; it performs only
  cheap field reads plus one VFS `findChild` (debug path), no blocking loops.
- **Collaborators**: `newLuaInterpreterCommandLine` (`command/LuaCommandLine.kt:32`),
  `LuaApplicationSettings.findInterpreter` (`settings/LuaApplicationSettings.kt:71`),
  `LuaFileUtil.getPluginVirtualDirectoryChild` (`util/LuaFileUtil.kt:22`),
  `LuaProjectSettings.getInstance(project).state.expandSourcePath`
  (`settings/LuaProjectSettings.kt:65`), `DefaultDebugExecutor.EXECUTOR_ID`.
- **Key API** (existing, grounded `run/LuaRunConfiguration.kt:226`):
  ```kotlin
  override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
      object : CommandLineState(environment) {
          override fun startProcess(): ProcessHandler { /* validation + launch */ }
      }

  val interpreter: LuaInterpreter?  // null when options.interpreter is null or empty
  ```
- **Relevant constants** (`run/LuaRunConfiguration.kt:283-289`):
  `DEBUGGER_PRELOADER_FILE = "debug.lua"`, `DEBUGGER_PACKAGE = "mobdebug"`,
  `ENV_LUA_INIT`, `ENV_LUNAR_LUA_PATH_TEMPLATE`, `ENV_LUNAR_DEBUGGER_PACKAGE`.

## 3. Algorithms

### 3.1 `startProcess()` validation gate
- **Input → Output**: `(executor: Executor, options: LuaRunConfigurationOptions)` →
  `ProcessHandler` on success, or throws `ExecutionException` on any failing check.
- **Steps** (exact order, `run/LuaRunConfiguration.kt:228-260`):
  1. `val interpreter = interpreter ?: throw ExecutionException("Interpreter is not defined")`
     — the getter yields null when `options.interpreter` is null/empty (RUN-04-01).
  2. `val commandLine = newLuaInterpreterCommandLine(interpreter) ?: throw ExecutionException("Interpreter is not found")`
     (RUN-04-02).
  3. Append parsed `interpreterArguments` via `ParametersListUtil.parse(...)`.
  4. If `options.scriptName.orEmpty().isEmpty()` → add `-v -i`; else add the script name (RUN-04-04).
  5. Append parsed `programArguments`; set working directory; apply environment variables.
  6. **Debug-only** (`if (executor.getId() == DefaultDebugExecutor.EXECUTOR_ID)`):
     a. `pluginLuaPath = LuaFileUtil.getPluginVirtualDirectoryChild("lua") ?: throw ExecutionException("Failed to locate plugin directory")`.
     b. `debuggerPreloaderFile = pluginLuaPath.findChild("debug.lua") ?: throw ExecutionException("Failed to locate debugger preloader")` (RUN-04-03).
     c. Inject `LUNAR_LUA_PATH_TEMPLATE`, `LUNAR_DEBUGGER_PACKAGE`, `LUA_INIT=@<preloader path>`.
  7. Source path (RUN-04-05): if `sourcePath` non-empty → `LUA_PATH=<sourcePath>`; else read
     `LuaProjectSettings...expandSourcePath(project)` and set `LUA_PATH` only if non-empty.
  8. Create the process handler, attach `ProcessTerminatedListener`, return it.
- **Rules / edge handling**: first failing check short-circuits via `throw`; non-debug launches
  skip step 6 entirely; empty script and empty source path are fallbacks, never failures.
- **Complexity / bounds**: O(1) — constant field reads + one VFS child lookup.

## 4. External Data & Parsing
The feature consumes **no external/unstructured text**. The only "parsing" is splitting the
user's argument strings with the platform helper `ParametersListUtil.parse(...)`
(`run/LuaRunConfiguration.kt:234,241`), which is a platform API, not a custom parser. Bundled
assets are located by exact filename (`"lua"`, `"debug.lua"`) via VFS, not parsed.

## 5. Data Flow

### Example 1: Run with no interpreter (RUN-04-01)
User presses **Run** → platform calls `getState(...).startProcess()` → `interpreter` getter
returns null → `ExecutionException("Interpreter is not defined")` propagates → platform shows
the run-error dialog; no process spawned.

### Example 2: Debug with a valid interpreter (happy path + RUN-04-03)
User presses **Debug** → interpreter resolves, command line built → executor id == `Debug` →
`getPluginVirtualDirectoryChild("lua")` and `findChild("debug.lua")` both resolve → debugger env
vars injected → process handler returned and launched.

## 6. Edge Cases
- `options.interpreter == ""` (blank, not null): getter still returns null → RUN-04-01 fires.
- Interpreter path resolves to a real path but `LuaInterpreterFamily.UNKNOWN_PRODUCT` for which
  `newLuaInterpreterCommandLine` returns null → RUN-04-02 fires.
- Run (non-debug) with missing preloader: **no** error — debug-only block is skipped.
- Empty script under Debug: still launches interactive (`-v -i`) with debugger env injected.

## 7. Integration Points
No new registration is required — the run configuration types are already registered. The
existing declaration (`src/main/resources/META-INF/plugin.xml:406-409`):

```xml
<!-- plugin.xml (existing) -->
<configurationType
        implementation="net.internetisalie.lunar.run.LuaRunConfigurationType"/>
<configurationType
        implementation="net.internetisalie.lunar.run.test.LuaTestRunConfigurationType"/>
```

`LuaRunConfigurationType.ID = "LuaRunConfiguration"` (`run/LuaRunConfiguration.kt:46`). Validation
is internal to the configuration's `getState()` and needs no extension point of its own.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| RUN-04-01 | M | §2.1, §3.1 step 1 |
| RUN-04-02 | M | §3.1 step 2 |
| RUN-04-03 | M | §3.1 step 6 |
| RUN-04-04 | S | §3.1 step 4 |
| RUN-04-05 | S | §3.1 step 7 |
| RUN-04-06 | M | §3.1 (all throws are `ExecutionException`); §5 |

## 9. Alternatives Considered
- **Edit-time `checkConfiguration()`** (red banner in the dialog before launch): the platform's
  preferred UX and what `LuaTestRunConfiguration` does. Not used by the main run config today;
  recorded as a future enhancement in risks-and-gaps. The shipped approach validates at launch
  time via `ExecutionException`, which still blocks a broken run but only once Run/Debug is pressed.

## 10. Open Questions

_None — this design records already-shipped behavior. Outstanding enhancements (script-file
existence check, edit-time validation) are tracked in [risks-and-gaps.md](risks-and-gaps.md)._
</content>
