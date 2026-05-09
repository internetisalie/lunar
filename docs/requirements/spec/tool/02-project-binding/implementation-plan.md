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
- [ ] Implement a `LocalTerminalDirectRunner` extension or `TerminalCustomizer`.
- [ ] On terminal session creation, identify bound tools for the current project.
- [ ] Prepend tool binary directories to the `PATH` environment variable of the terminal process.

## Verification Tasks
- [ ] **Integration Test**: Verify that project settings correctly override global settings for tool resolution.
- [ ] **Functional Test**: Launch a `LuaRunConfiguration` and verify `PATH` includes the bound tool directory.
- [ ] **Manual Test**: Open the Integrated Terminal and run `which luarocks` to verify path augmentation.
