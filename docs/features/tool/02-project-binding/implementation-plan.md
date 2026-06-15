---
id: TOOL-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: TOOL-02
status: "planned"
priority: "high"
folders:
  - "[[features/tool/02-project-binding/requirements|requirements]]"
---

# Implementation Plan: Project Binding & Environment Integration (`TOOL-02`)

This plan covers the logic for binding tools to projects and making them available in the environment.

## Phase 1: Binding Storage [Must]
- [ ] Update `LuaApplicationSettings.State` to include `globalToolBindings: MutableMap<LuaToolType, String?>`.
- [ ] Update `LuaProjectSettings.State` to include `projectToolBindings: MutableMap<LuaToolType, String?>`.

## Phase 2: Resolution Logic [Must]
- [ ] Update `LuaToolManager` to include `getEffectiveTool(project, type)`.
- [ ] Implement inheritance logic: Project Binding > Global Binding > `null`.

## Phase 3: Run Configuration Integration [Must]
- [ ] Create `net.internetisalie.lunar.run.LuaToolEnvironmentProvider`.
- [ ] Implement `patchEnvironment(commandLine, project, type)` to prepend tool directory to `PATH`.
- [ ] Integrate this provider into `LuaRunConfiguration` or via a `RunConfigurationExtension`.

## Phase 4: Terminal Integration [Must]
- [ ] Implement a `LocalTerminalDirectRunner` extension and `TerminalCustomizer`.
- [ ] **Project-level Service**: Implement `LuaTerminalEnvironmentService` as a project-level service.
- [ ] **Reactive Invalidation**: Subscribe to the Message Bus to invalidate terminal environment cache on settings changes.
- [ ] **initCommands Injection**: Use `initCommands` in `TerminalCustomizer` to prepend `PATH` *after* shell initialization (prevents profile clobbering).
- [ ] **Shell-Aware Paths**: Detect shell type (CMD, PS, Bash) to determine the correct path separator and `export`/`set` syntax.
- [ ] Handle shell-specific PATH modification for CMD, PowerShell, Bash, and Zsh.
- [ ] Register extensions in plugin.xml.

## Verification Tasks
- [ ] **Integration Test**: Verify that project settings correctly override global settings for tool resolution.
- [ ] **Functional Test**: Launch a `LuaRunConfiguration` and verify `PATH` includes the bound tool directory.
- [ ] **Manual Test**: Open the Integrated Terminal and run `which luarocks` to verify path augmentation.
- [ ] **Cross-Platform E2E Test**: Use Docker Windows Server containers and Linux containers to validate tool discovery, PATH injection, and execution across Windows and Linux platforms (TOOL-00-05).
