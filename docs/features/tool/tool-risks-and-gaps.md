---
folders:
  - "[[features/tool/requirements|requirements]]"
title: Design Gaps & De-risking
---

# Design Gaps & De-risking: Tool Inventory Management (TOOL Epic)

This document tracks identified technical risks, design gaps, and required de-risking actions for the Tool Inventory Management features.

## 1. Technical Risks

### R1: Terminal Environment Injection (High)
*   **Risk**: Prepending directories to the `PATH` in the built-in terminal is platform and shell-dependent. Standard extensions may not work uniformly across all OS versions or may be overridden by user shell configurations.
*   **Mitigation**: Prototype `com.intellij.terminal.TerminalCustomizer` and `LocalTerminalDirectRunner` extensions. Verify behavior on Windows (CMD/PowerShell) and POSIX (Bash/Zsh).

### R2: Settings Serialization (Medium)
*   **Risk**: Complex objects and `MutableMap` in `PersistentStateComponent` can lead to serialization issues or "leaky" settings in `lunar.xml`.
*   **Mitigation**: Use simple types in State classes (e.g., `Map<String, String>`) and verify persistence with integration tests that restart the settings component.

### R3: CLI Execution Overhead (Medium)
*   **Risk**: Frequent health checks or blocking discovery can lead to IDE UI freezes.
*   **Mitigation**: Ensure all `GeneralCommandLine` executions are wrapped in `Task.Backgroundable` and never run on the Event Dispatch Thread (EDT).

## 2. Design Gaps

### G1: OS-Specific Tool Descriptors
*   **Gap**: The current design lacks a mapping for tool filenames across operating systems (e.g., `luarocks` vs `luarocks.bat`).
*   **Requirement**: Define a `LuaToolDescriptor` that includes search patterns and preferred extensions per OS.

### G2: Stream Merging for Validation
*   **Gap**: Tool version info may be written to `stderr` instead of `stdout`.
*   **Requirement**: `LuaToolValidator` must merge output streams before applying regex patterns.

### G3: Cross-Platform E2E Testing
*   **Gap**: No established approach for end-to-end testing of tool inventory features on Windows environments.
*   **Requirement**: Implement E2E test infrastructure using Docker Windows Server containers to validate tool discovery, PATH injection, and execution across Windows and Linux platforms.

## 3. De-risking Action Items [Must]

| ID | Action Item | Priority | Target Feature | Status |
| :--- | :--- | :---: | :--- | :--- |
| **TOOL-DR-01** | Prototype Terminal `PATH` injection for Bash/CMD | High | `TOOL-02` | Pending |
| **TOOL-DR-02** | Define OS-specific filename patterns for `luarocks` | Medium | `TOOL-01` | Pending |
| **TOOL-DR-03** | Verify `PersistentStateComponent` map serialization | Medium | `TOOL-01/02` | Pending |
| **TOOL-DR-04** | Implement Async wrapper for `LuaProcessUtil` calls | Medium | `TOOL-01` | Pending |
| **TOOL-DR-05** | Implement E2E test infrastructure using Docker Windows Server containers | High | `TOOL Epic` | Pending |
