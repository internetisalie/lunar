---
id: MAINT-17
title: "MAINT-17: Test Coverage - Utilities & Command Line"
type: feature
parent_id: MAINT
status: done
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-17: Test Coverage - Utilities & Command Line

## Overview
Increase unit-test coverage for the process-runner, file, and thread utility layer plus the
Lua command-line builders. This is a **coverage-only** feature: no production behavior changes.
The targets are the pure/near-pure helpers in `util/` (`LuaProcessUtil`, `LuaFileUtil`,
`LuaTaskUtil`) and the command builders in `command/` (`LuaCommandLine.kt` free functions,
`LuaRunProfile`).

## Scope
* **In Scope**:
  * `LuaProcessUtil.capture()` / `LuaProcessUtil.listen()` — output capture, timeout handling,
    exception-to-exit-code mapping (`src/main/kotlin/net/internetisalie/lunar/util/LuaProcessUtil.kt`).
  * `LuaFileUtil` — plugin virtual-directory child resolution, recursive Lua-file discovery,
    PSI-file mapping (`src/main/kotlin/net/internetisalie/lunar/util/LuaFileUtil.kt`).
  * `LuaTaskUtil` — `newAppBackgroundTask` / `newProjectBackgroundTask` factory correctness and
    action delegation (`src/main/kotlin/net/internetisalie/lunar/util/LuaTaskUtil.kt`).
  * `LuaCommandLine.kt` — `newLuaInterpreterCommandLine` (system-binary vs `.jar` → `java -cp`),
    `LUA_PATH` injection via `newProjectLuaInterpreterCommandLine`, null-guards
    (`src/main/kotlin/net/internetisalie/lunar/command/LuaCommandLine.kt`).
  * `LuaRunProfile` — name/icon accessors and stored command-line pass-through
    (`src/main/kotlin/net/internetisalie/lunar/command/LuaRunProfile.kt`).
* **Out of Scope**:
  * OS-specific shell/terminal configuration.
  * Spawning real long-lived interpreter subprocesses; hermetic short commands only (see design).
  * Re-testing `LuaToolEnvironment.prependToolDirsToPath` (covered by its own tool tests) — only
    its invocation as a side effect of `newProjectLuaInterpreterCommandLine` is in scope.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-17-01 | **Process capture & exit-code mapping** | Must | Full | Verify `LuaProcessUtil.capture()` runs a `GeneralCommandLine`, returns captured stdout, and maps `TimeoutException` → exit code `PROCESS_TIMEOUT_EXCEPTION_CODE` (-1, `isTimeout`), and an unresolvable command → `PROCESS_EXECUTION_EXCEPTION_CODE` (-2). |
| MAINT-17-02 | **Plugin directory & Lua-file discovery** | Must | Full | Verify `LuaFileUtil.getPluginVirtualDirectoryChild(...)` returns null when a child is absent, `findLuaFilesInDir` returns only `.lua` files recursively, and `findPsiFiles` maps `VirtualFile`s to `PsiFile`s (skipping unmappable ones). |
| MAINT-17-03 | **Background-task factories** | Must | Full | Verify `newProjectBackgroundTask` / `newAppBackgroundTask` build `Task.Backgroundable` instances carrying the given title/project and that invoking `run(indicator)` calls the supplied action exactly once with the indicator. |
| MAINT-17-04 | **Interpreter command-line builders** | Must | Full | Verify `newLuaInterpreterCommandLine` builds `java -cp <jar> lua` for a `.jar` interpreter and `<exe>` for a system binary, returns null when `executable` is null, and that `newProjectLuaInterpreterCommandLine` injects `LUA_PATH` when the project source path expands non-empty. |
| MAINT-17-05 | **Run-profile accessors** | Should | Full | Verify `LuaRunProfile` exposes the stored command line, a non-blank localized name, a non-null icon, and returns a `LuaRunProfileState` from `getState`. |

## Test Cases
| TC | Given | When | Then |
|---|---|---|---|
| TC-01 | A `GeneralCommandLine` for a portable echo-style command that terminates immediately | `LuaProcessUtil.capture(cmd)` is called off the EDT | Returns a `ProcessOutput` whose `exitCode == 0` and whose `stdout` contains the echoed text |
| TC-02 | A `GeneralCommandLine` for a command that sleeps longer than the timeout | `capture(cmd, timeout = <small>)` is called | Returns `ProcessOutput` with `exitCode == PROCESS_TIMEOUT_EXCEPTION_CODE` (-1) and `isTimeout == true` |
| TC-03 | A `GeneralCommandLine` whose exePath is a non-existent binary | `capture(cmd)` is called | Returns `ProcessOutput` with `exitCode == PROCESS_EXECUTION_EXCEPTION_CODE` (-2) and `isTimeout == false` |
| TC-04 | A temp dir tree with `a.lua`, `sub/b.lua`, and `c.txt`, loaded as `VirtualFile`s | `LuaFileUtil.findLuaFilesInDir(root)` is called | Returns exactly the two `.lua` files (recursively), excluding `c.txt` |
| TC-05 | The plugin virtual directory (or its parent) | `getPluginVirtualDirectoryChild("no-such-child-xyz")` is called | Returns `null` (missing child short-circuits without throwing) |
| TC-06 | A configured Lua fixture file mapped to a `VirtualFile`, plus a bogus non-file `VirtualFile` in the same collection | `LuaFileUtil.findPsiFiles(project, files)` is called | Returns a list containing the real file's `PsiFile` and omitting the unmappable entry |
| TC-07 | A description string, a project, and a counting action lambda | `newProjectBackgroundTask(desc, project, action)` then `task.run(indicator)` | `task.title == desc`, `task.project === project`, and the action is invoked exactly once with `indicator` |
| TC-08 | A description string and a counting action | `newAppBackgroundTask(desc, action)` then `task.run(indicator)` | Returns a `Task.Backgroundable` bound to the default project whose `run` invokes the action once |
| TC-09 | A `LuaInterpreter(path = "/tmp/fake/lua")` pointing at an existing temp executable file | `newLuaInterpreterCommandLine(interpreter)` | Returns a `GeneralCommandLine` whose `exePath` ends in `lua` and whose parameter list does **not** contain `-cp` |
| TC-10 | A `LuaInterpreter` whose `path` ends in `.jar` and points at an existing temp file | `newLuaInterpreterCommandLine(interpreter)` | Returns a `GeneralCommandLine` with `exePath == "java"` and parameters `["-cp", <jarPath>, "lua"]` |
| TC-11 | A `LuaInterpreter` with `path = null` (or a non-existent path so `executable` is null) | `newLuaInterpreterCommandLine(interpreter)` | Returns `null` |
| TC-12 | A project whose `LuaProjectSettings` `interpreter` is set to a valid temp interpreter and `sourcePath` expands to a non-empty string | `newProjectLuaInterpreterCommandLine(project)` | Returns a `GeneralCommandLine` whose `environment["LUA_PATH"]` equals the expanded source path |
| TC-13 | A project whose `LuaProjectSettings.interpreter` is `null` | `newProjectLuaInterpreterCommandLine(project)` | Returns `null` |
| TC-14 | A `LuaRunProfile(cmd)` constructed from a known `GeneralCommandLine` | Accessing `commandLine`, `getName()`, `getIcon()`, `getState(executor, env)` | `commandLine === cmd`, name is non-blank, icon is non-null, and `getState` returns a `LuaRunProfileState` |

## Acceptance Criteria
* **AC-17-01**: MAINT-17-01 satisfied by TC-01, TC-02, TC-03.
* **AC-17-02**: MAINT-17-02 satisfied by TC-04, TC-05, TC-06.
* **AC-17-03**: MAINT-17-03 satisfied by TC-07, TC-08.
* **AC-17-04**: MAINT-17-04 satisfied by TC-09, TC-10, TC-11, TC-12, TC-13.
* **AC-17-05**: MAINT-17-05 satisfied by TC-14.
</content>
</invoke>
