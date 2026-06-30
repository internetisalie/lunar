---
id: "TOOL-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOL"
priority: "high"
folders:
  - "[[features/tool/requirements|requirements]]"
---

# Overall Implementation Plan: Tool Inventory Management (TOOL Epic)

This document provides the consolidated, chronological implementation sequence for the Tool Inventory Management features, integrating de-risking actions with functional phases.

## Phase 0: Foundations & De-risking [Must]
*Start here to ensure the technical architecture is sound and high-uncertainty items are addressed. See the [detailed de-risking requirements](00-de-risking/requirements.md) and [implementation plan](00-de-risking/implementation-plan.md) for specifics.*

- [ ] **TOOL-00-03**: Verify `PersistentStateComponent` map serialization (Critical for data persistence).
- [ ] **TOOL-00-02**: Define OS-specific filename patterns (Necessary for cross-platform validation).
- [ ] **TOOL-01 Phase 1**: Implement `LuaToolType` enum, `LuaTool` data class, and `LuaApplicationSettings.State` storage.
- [ ] **TOOL-00-04**: Implement Async wrapper for `GeneralCommandLine` executions (Required before any CLI calls).
- [ ] **TOOL-00-01**: Prototype Terminal `PATH` injection (De-risks the most complex part of `TOOL-02`).
- [ ] **TOOL-00-06**: Verify VFS listener impact on performance for deep tool paths (De-risks `TOOL-03`).

## Phase 1: Registry & Discovery (TOOL-01) [Must]
*Building the core services that manage the tool inventory.*

- [ ] **TOOL-01 Phase 2**: Implement `LuaToolValidator` (CLI version extraction, stream merging, regex).
- [ ] **TOOL-01 Phase 3**: Implement `LuaToolDiscoveryService` (Scanning system `PATH` and standard directories).
- [ ] **TOOL-01 Phase 4**: Implement `LuaToolManager` application service (Registration/Unregistration logic).
- [ ] **Verification**: Execute `LuaToolValidatorTest` and `LuaToolDiscoveryServiceTest`.

## Phase 2: Project Integration (TOOL-02) [Must]
*Connecting the registry to specific projects and environments.*

- [ ] **TOOL-02 Phase 1**: Update Project and App settings to store tool bindings.
- [ ] **TOOL-02 Phase 2**: Implement `LuaToolManager.getEffectiveTool` (Inheritance logic: Project > Global).
- [ ] **TOOL-02 Phase 3**: Patch `GeneralCommandLine.environment` in `command/LuaCommandLine.kt` (alternative: `RunConfigurationExtension.patchCommandLine`) to inject tool dirs into `PATH`.
- [ ] **TOOL-02 Phase 4**: Implement a `ShellExecOptionsCustomizer` (deprecated fallback `LocalTerminalCustomizer`) for Terminal `PATH` injection via the env map.

## Phase 3: User Interface & Experience (TOOL-03) [Must]
*Exposing the logic to the user via settings panels.*

- [ ] **TOOL-03 Phase 1**: `LuaToolInventoryPanel` (Global settings UI with Add/Edit/Auto-Detect).
- [ ] **TOOL-03 Phase 2**: `LuaProjectToolPanel` (Project settings UI for binding overrides).

## Phase 4: Reliability & Monitoring [Should]
*Ensuring the inventory remains healthy over time.*

- [ ] **TOOL-03 Phase 3**: `LuaToolHealthCheckActivity` (Background `mtime` and existence checks).
- [ ] **TOOL-03 Phase 4**: IDE Notifications and Diagnostics for missing/invalid tools.

## Phase 5: Final Verification [Must]
- [ ] **TOOL-00-05**: Implement E2E test infrastructure using Docker Windows/Linux containers.
- [ ] **Validation**: Run full integration and functional test suite across platforms.
