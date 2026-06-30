---
id: TOOL-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: TOOL-02
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
- [ ] **Primary**: Patch the environment directly in `command/LuaCommandLine.kt`'s command-line builder —
      prepend the effective tool directories to `GeneralCommandLine.environment["PATH"]` (definitely
      available, no extra EP).
- [ ] **Alternative**: For configs not built through `LuaCommandLine`, integrate via a
      `RunConfigurationExtension.patchCommandLine(...)` that mutates `GeneralCommandLine.environment`.
      (There is no platform `EnvironmentProvider` interface — do not invent one.)

## Phase 4: Terminal Integration [Must]
- [ ] Register a `org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer` (EP
      `org.jetbrains.plugins.terminal.shellExecOptionsCustomizer`) that prepends the effective tool
      directories to the PATH entry of the exec options' environment in `customizeExecOptions(project, options)`.
      (Deprecated fallback: `LocalTerminalCustomizer.customizeCommandAndEnvironment(...)` injecting into the `envs` map.)
- [ ] **Project-level Service**: Implement `LuaTerminalEnvironmentService` as a project-level service.
- [ ] **Reactive Invalidation**: Subscribe to `LuaSettingsChangedListener.TOPIC` (`onSettingsChanged()`)
      to invalidate the terminal environment cache on settings changes.
- [ ] **Env-map Injection**: Prepend `PATH` by mutating the env map (no `export`/`set` init commands and
      no `initCommands` list — the env map is the single injection point, which also avoids profile clobbering).
- [ ] **Path Separator**: Use the OS path separator (`File.pathSeparator`) when joining tool directories.
- [ ] Register extensions in plugin.xml.

## Verification Tasks
- [ ] **Integration Test**: Verify that project settings correctly override global settings for tool resolution.
- [ ] **Functional Test**: Launch a `LuaRunConfiguration` and verify `PATH` includes the bound tool directory.
- [ ] **Manual Test**: Open the Integrated Terminal and run `which luarocks` to verify path augmentation.
- [ ] **Cross-Platform E2E Test**: Use Docker Windows Server containers and Linux containers to validate tool discovery, PATH injection, and execution across Windows and Linux platforms (TOOL-00-05).
