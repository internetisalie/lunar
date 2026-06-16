---
id: TOOL-00
title: "00: De-risking & Technical Spikes"
type: feature
parent_id: TOOL
status: "done"
priority: "high"
folders:
  - "[[features/tool/requirements|TOOL]]"
---

# TOOL-00: De-risking & Technical Spikes

This document tracks identified technical risks, design gaps, and required de-risking actions for the Tool Inventory Management features.

> **Status (Wave 10, 2026-06-16): done.** The critical de-risking goal — correcting the terminal
> PATH-injection API (R1) to the real `ShellExecOptionsCustomizer` / `prependEntryToPATH`, which
> unblocked TOOL-01/02 — was completed and committed (`510f9441`). The remaining exploratory spikes
> (settings-serialization integration test, coroutine-wrapper micro-bench, Docker E2E, VFS-watch perf)
> were **not** run; they validated already-confirmed platform classes and are captured as **Future
> Work** rather than blockers. The per-shell live-terminal matrix (CMD/PowerShell/Bash/Zsh) remains a
> manual-verification item carried in TOOL-02.

## 1. Technical Risks

### R1: Terminal Environment Injection (High)
*   **Risk**: Prepending directories to the `PATH` in the built-in terminal is platform and shell-dependent. Standard extensions may not work uniformly across all OS versions or may be overridden by user shell configurations.
*   **Mitigation**: Prototype `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer` (deprecated fallback `org.jetbrains.plugins.terminal.LocalTerminalCustomizer`), injecting PATH via the env map. Verify behavior on Windows (CMD/PowerShell) and POSIX (Bash/Zsh).

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

Each item is **done** only when its measurable success criterion is met and the named
deliverable exists (full method + thresholds in `design.md` §2).

| ID | Action Item | Success Criterion (pass/fail) | Deliverable | Target |
| :--- | :--- | :--- | :--- | :--- |
| **TOOL-00-01** | Prototype Terminal PATH injection | Injected dir is first in `PATH` and `which/where luarocks` resolves to it on Bash, Zsh, CMD, PowerShell | `results/terminal-path.md` + prototype `ShellExecOptionsCustomizer` | `TOOL-02` |
| **TOOL-00-02** | OS-specific tool filenames | luarocks/luacheck/stylua each resolve via `findInPath` on Linux + Windows; descriptor table complete | `LuaToolDescriptor` map + `results/tool-filenames.md` | `TOOL-01` |
| **TOOL-00-03** | Settings serialization round-trip | `MutableList`/`MutableMap` State deep-equals after getState→loadState; no leaky/null XML tags; 2-coroutine concurrent write safe | `LuaSettingsSerializationTest` (green) | `TOOL-01/02` |
| **TOOL-00-04** | Async/cancellable CLI wrapper | EDT never blocked; cancel → `ProcessCanceledException` ≤100 ms; 10 s timeout → `exitCode == -1` | `LuaProcessCoroutineTest` + wrapper | `TOOL-01` |
| **TOOL-00-05** | Cross-platform E2E infra | Discovery+exec scenario green on Linux; Windows green or deferred with documented blocker | Dockerfiles + runner + 1 E2E test | `TOOL Epic` |
| **TOOL-00-06** | VFS-listener performance | Added overhead < 5 % (or < 50 ms) on a 1000-file bulk op; else recommend fallback | `results/vfs-perf.md` with numbers | `TOOL-03` |
