---
folders:
  - "[[features/tool/01-inventory-management/requirements|requirements]]"
title: Implementation Plan
---

# Implementation Plan: Tool Registry & Discovery (`TOOL-01`)

This plan covers the foundational logic for managing external Lua tools.

## Phase 1: Data Models & Storage [Must]
- [ ] Create `net.internetisalie.lunar.settings.LuaToolType` enum.
- [ ] Create `net.internetisalie.lunar.settings.LuaTool` data class with `id`, `type`, `name`, `path`, `version`, `luaVersion`, `lastModified`, `isValid`.
- [ ] Update `LuaApplicationSettings.State` to include `toolInventory: MutableList<LuaTool>`.
- [ ] Implement `LuaApplicationSettings.getTool(id: String)` helper.

## Phase 2: Validation Logic [Must]
- [ ] Create `net.internetisalie.lunar.util.LuaToolValidator` utility.
- [ ] Implement `extractVersion(path, type)` using `GeneralCommandLine` (merging streams) and regex.
- [ ] Add regex patterns for `luarocks`, `luacheck`, `stylua`, and `lua-format`.
- [ ] Implement `detectLuaVersion(path, type)` logic for best-effort compatibility checks.
- [ ] Implement `checkCompatibility(tool, interpreter)` logic.
- [ ] **Implement async wrapper for GeneralCommandLine executions** - Ensure all CLI calls are wrapped in `Task.Backgroundable` and never run on EDT to prevent UI freezes (TOOL-DR-04).

## Phase 3: Discovery Service [Must]
- [ ] Create `net.internetisalie.lunar.util.LuaToolDiscoveryService`.
- [ ] Implement `discoverKnownTools()` to scan standard OS paths (`PATH`, `/usr/local/bin`, etc.).
- [ ] Filter discovered paths by checking if they are executable.

## Phase 4: Core Management Service [Must]
- [ ] Create `net.internetisalie.lunar.settings.LuaToolManager` (Application Service).
- [ ] Implement `registerTool(path: String)`:
    - Validate binary and extract version.
    - Check for duplicates.
    - Add to `LuaApplicationSettings`.
- [ ] Implement `unregisterTool(id: String)`.

## Verification Tasks
- [ ] **Unit Test**: `LuaToolValidatorTest` to verify regex against mock CLI outputs.
- [ ] **Unit Test**: `LuaToolDiscoveryServiceTest` (using mock environment).
- [ ] **Integration Test**: Verify `LuaApplicationSettings` persistence of the `toolInventory`.
- [ ] **Verification Task**: Verify `PersistentStateComponent` map serialization does not cause issues - use simple types where possible and validate with integration tests that restart settings component (TOOL-DR-03).
