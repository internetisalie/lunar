---
id: TOOL-00-PLAN
title: "Implementation Plan: De-risking"
type: plan
parent_id: TOOL-00
status: "planned"
priority: "high"
folders:
  - "[[features/tool/00-de-risking/requirements|requirements]]"
---

# Implementation Plan: De-risking (`TOOL-00`)

This plan covers the critical de-risking actions required to validate the technical architecture of the Tool Inventory Management system.

## Phase 1: Environment & Execution [Must]
*De-risks R1 (Terminal PATH) and R3 (CLI Execution).*

- [ ] **TOOL-00-01: Prototype Terminal env-map PATH injection**
    - Create a minimal `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer` implementation
      (`customizeExecOptions(project, options: MutableShellExecOptions)`; EP `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`;
      `@ApiStatus.Experimental`, runs on a background thread).
    - Inject PATH by mutating the environment in the exec options (no `export` init commands — the
      env map is the injection point). Verify the prepended tool dir appears on PATH in the spawned shell.
    - As a comparison/fallback, prototype the deprecated `org.jetbrains.plugins.terminal.LocalTerminalCustomizer`
      (`customizeCommandAndEnvironment(project, workingDirectory, command, envs)`; EP `…localTerminalCustomizer`),
      injecting PATH into the `envs` map; confirm which one GoLand 2026.1 honors.
    - Test on Linux (Bash) and Windows (CMD, PowerShell).
- [ ] **TOOL-00-04: Implement Async/Coroutine wrapper for CLI calls**
    - Create a utility method using `serviceCoroutineScope` and `withContext(Dispatchers.IO)`.
    - Ensure it handles `ProcessCanceledException` and timeouts correctly.
    - Integrate with `GeneralCommandLine` and `OSProcessHandler`.

## Phase 2: Configuration & Persistence [Must]
*De-risks R2 (Settings Serialization).*

- [ ] **TOOL-00-03: Verify `PersistentStateComponent` serialization with Coroutines**
    - Implement a test settings component using a `MutableList` of data classes.
    - Verify that data is correctly persisted to XML and reloaded.
    - Test concurrent access via multiple coroutines.
- [ ] **TOOL-00-02: Define OS-specific filename patterns for `luarocks`**
    - Document the mapping of tool names to binary filenames (e.g., `luarocks` -> `luarocks.bat` on Windows).
    - Implement a helper to resolve the correct filename based on `SystemInfo`.

## Phase 3: Infrastructure & Monitoring [Should]
*De-risks G3 (E2E Testing) and performance of VFS listeners.*

- [ ] **TOOL-00-05: Implement E2E test infrastructure using Docker containers**
    - Scaffold Dockerfiles for a minimal Windows Server (with Lua installed) and Linux.
    - Set up a test runner that can execute commands inside these containers.
- [ ] **TOOL-00-06: Verify VFS listener impact on performance for deep tool paths**
    - Profile the `VirtualFileListener` when registered on common tool paths (e.g., `/usr/local/bin`).
    - Ensure it doesn't cause overhead during bulk file operations in those directories.

## Verification Tasks
- [ ] Run `LuaTerminalInjectionTest` (to be created during TOOL-00-01).
- [ ] Run `LuaProcessCoroutineTest` (to be created during TOOL-00-04).
- [ ] Run `LuaSettingsSerializationTest` (to be created during TOOL-00-03).
