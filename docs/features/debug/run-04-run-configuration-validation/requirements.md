---
id: "RUN-04"
title: "RUN-04: Run Configuration Validation"
type: "feature"
status: "done"
priority: "medium"
parent_id: "DEBUG/RUN"
folders: ["[[features/debug/requirements|requirements]]"]
---

# RUN-04: Run Configuration Validation

<!-- SRS reverse-engineered from the shipped implementation in
     run/LuaRunConfiguration.kt. Behavior reflects what the code actually does, not an
     idealized spec. -->

## Overview

A Lua run/debug configuration ([`LuaRunConfiguration`](../../../../src/main/kotlin/net/internetisalie/lunar/run/LuaRunConfiguration.kt)) can be created with an
unset or unresolvable interpreter, or launched under the Debug executor while the bundled
debugger assets are missing. This feature guards execution: before a process is spawned, the
configuration is validated and any blocking condition aborts the launch with a clear, user-facing
error instead of a broken process or an IDE crash. It is the run-side counterpart of the
debug-target check (`DEBUG-06`) and part of the `RUN`/`DEBUG` epic.

## Scope

### In Scope
- Pre-execution validation performed inside `LuaRunConfiguration.getState()` →
  `CommandLineState.startProcess()` for the **main** Lua run configuration.
- Rejecting launch when no interpreter is configured.
- Rejecting launch when the configured interpreter cannot be resolved into a command line.
- Rejecting **debug** launches when the bundled debugger preloader assets are missing.
- Surfacing all failures as `com.intellij.execution.ExecutionException` so the platform shows
  an error and aborts the run without crashing the IDE.

### Out of Scope
- `checkConfiguration()`-style editor-form validation (red error banner in the Run/Debug
  Configurations dialog). The main run config does **not** override `checkConfiguration()`;
  that pattern is implemented separately for the test runner (see `RUN-05`,
  `LuaTestRunConfiguration.checkConfiguration()`).
- **Script-file existence checking.** The current code does not verify the script path exists;
  an empty script name intentionally falls back to interactive mode (`lua -v -i`). Deferred —
  see [risks-and-gaps.md](risks-and-gaps.md).
- Interpreter SDK discovery/registration (`RUN-01`) and the configuration form UI (`RUN-02`).

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| RUN-04-01 | **Interpreter-defined check** | M | When no interpreter is configured, `startProcess()` aborts with `ExecutionException("Interpreter is not defined")`. |
| RUN-04-02 | **Interpreter-resolvable check** | M | When an interpreter is configured but no command line can be built from it, `startProcess()` aborts with `ExecutionException("Interpreter is not found")`. |
| RUN-04-03 | **Debug preloader availability** | M | Under the Debug executor only, abort with `ExecutionException("Failed to locate plugin directory")` / `"Failed to locate debugger preloader"` when the bundled `lua` directory or `debug.lua` is absent. |
| RUN-04-04 | **Empty-script fallback** | S | An empty/blank script name is not an error; the launch proceeds in interactive mode (`-v -i`). |
| RUN-04-05 | **Empty-source-path fallback** | S | An empty source path is not an error; the launch falls back to the project source path (`LuaProjectSettings.expandSourcePath`). |
| RUN-04-06 | **Non-crashing failure surface** | M | All validation failures are thrown as `ExecutionException`, which the platform renders as a run error dialog rather than crashing the host IDE. |

## Detailed Specifications

### RUN-04-01: Interpreter-defined check
The `interpreter` getter returns `null` when `options.interpreter` is `null` **or** empty
(`LuaRunConfiguration.kt:170-175`). `startProcess()` does
`val interpreter = interpreter ?: throw ExecutionException("Interpreter is not defined")`
(`LuaRunConfiguration.kt:229-230`). This is the first statement of `startProcess()`, so it
fires before any command line is constructed.

### RUN-04-02: Interpreter-resolvable check
A non-null interpreter is passed to `newLuaInterpreterCommandLine(interpreter)`
(`command/LuaCommandLine.kt:32`), which returns `null` when a command line cannot be produced
(e.g. the interpreter family/binary is not usable). `startProcess()` does
`newLuaInterpreterCommandLine(interpreter) ?: throw ExecutionException("Interpreter is not found")`
(`LuaRunConfiguration.kt:231-232`).

### RUN-04-03: Debug preloader availability
Only when `executor.getId() == DefaultDebugExecutor.EXECUTOR_ID`
(`LuaRunConfiguration.kt:250`):
1. `LuaFileUtil.getPluginVirtualDirectoryChild("lua")` (`util/LuaFileUtil.kt:22`) must return a
   non-null `VirtualFile`; otherwise `ExecutionException("Failed to locate plugin directory")`
   (`LuaRunConfiguration.kt:251-252`).
2. `pluginLuaPath.findChild(DEBUGGER_PRELOADER_FILE)` (where
   `DEBUGGER_PRELOADER_FILE = "debug.lua"`, `LuaRunConfiguration.kt:284`) must be non-null;
   otherwise `ExecutionException("Failed to locate debugger preloader")`
   (`LuaRunConfiguration.kt:253-254`).

A **Run** (non-debug) launch skips this block entirely.

### RUN-04-04 / RUN-04-05: Fallbacks (not errors)
- Empty script (`options.scriptName.orEmpty().isEmpty()`): the command line is given
  `-v -i` instead of a script path (`LuaRunConfiguration.kt:237-239`).
- Empty `sourcePath`: the code reads
  `LuaProjectSettings.getInstance(project).state.expandSourcePath(project)`
  (`settings/LuaProjectSettings.kt:65`) and sets `LUA_PATH` only if that is non-empty
  (`LuaRunConfiguration.kt:262-272`).

## Behavior Rules
- Validation order in `startProcess()`: (1) interpreter-defined → (2) interpreter-resolvable →
  argument/env assembly → (3) debug-only preloader checks. The first failing check throws and
  no process is started.
- Validation is **execution-time**, not edit-time: failures appear when the user presses Run/Debug,
  not while editing the configuration form.
- All thrown exceptions are `ExecutionException` (checked, platform-handled).

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | RUN-04-01 | A `LuaRunConfiguration` with `options.interpreter = ""` (or null) | `getState(runExecutor, env).startProcess()` is invoked | Throws `ExecutionException` with message `"Interpreter is not defined"`; no process handler created |
| 2 | RUN-04-02 | A `LuaRunConfiguration` whose interpreter path resolves to an `UNKNOWN_PRODUCT` interpreter for which `newLuaInterpreterCommandLine` returns null | `startProcess()` is invoked | Throws `ExecutionException` with message `"Interpreter is not found"` |
| 3 | RUN-04-03 | A valid interpreter, launched under `DefaultDebugExecutor` (id = `Debug`), with the bundled `lua/debug.lua` preloader absent | `startProcess()` is invoked | Throws `ExecutionException` (`"Failed to locate plugin directory"` or `"Failed to locate debugger preloader"`) |
| 4 | RUN-04-04 | A valid interpreter and empty `scriptName`, run (non-debug) | `startProcess()` is invoked | No exception; command line contains `-v -i` (interactive) |
| 5 | RUN-04-06 | Any of the failing conditions in #1–#3 | `startProcess()` throws | The IDE shows a run-error notification and aborts; the host IDE does not crash |

## Acceptance Criteria
- [x] RUN-04-01: launching with no interpreter aborts with `"Interpreter is not defined"`.
- [x] RUN-04-02: launching with an unresolvable interpreter aborts with `"Interpreter is not found"`.
- [x] RUN-04-03: debug launch without the bundled preloader aborts with a locating error.
- [x] RUN-04-04 / RUN-04-05: empty script and empty source path are handled as fallbacks, not errors.
- [x] RUN-04-06: every failure path throws `ExecutionException` (no `!!`, no IDE crash).

## Non-Functional Requirements
- **Threading**: validation runs on whatever thread the platform invokes `startProcess()` on;
  it performs only cheap field reads and a VFS `findChild` lookup (no blocking I/O loops), so it
  does not violate the EDT/slow-operation contract.
- **Memory**: no hard references to `Project`/`Editor` retained beyond the configuration's own
  platform-managed lifetime; the `Project` used is the configuration's own.
- **Error bounding**: failures are `ExecutionException`, never uncaught runtime exceptions
  (engineering contract §2 — "no IDE crashes").

## Dependencies
- `RUN-01` (Lua Interpreter SDK) — `LuaApplicationSettings.findInterpreter`, `LuaInterpreter`.
- `RUN-02` (Run Configurations) — `LuaRunConfiguration`, `LuaRunConfigurationOptions`.
- `DEBUG-05` (Remote Debugging) — the bundled `debug.lua` preloader validated by RUN-04-03.

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
</content>
</invoke>
