---
folders:
  - "[[features/tool/01-inventory-management/requirements|requirements]]"
title: Implementation Plan
---

# Implementation Plan: Tool Registry & Discovery (`TOOL-01`)

This plan covers the foundational logic for managing external Lua tools.

## Phase 1: Data Models & Storage [Must]
- [ ] Create `net.internetisalie.lunar.settings.LuaToolType` enum.
- [ ] Create `net.internetisalie.lunar.settings.LuaTool` data class with `id` (UUID), `type`, `name`, `path`, `version`, `luaVersion`, `lastModified`, `isValid`.
    - Ensure it has a no-arg constructor and use `@Tag` / `@AbstractCollection` for robust XML serialization.
- [ ] Update `LuaApplicationSettings.State` to include `toolInventory: MutableList<LuaTool>`.
- [ ] Implement `LuaApplicationSettings.getTool(id: String)` helper.

## Phase 2: Validation Logic [Must]
- [ ] Create `net.internetisalie.lunar.util.LuaToolValidator` utility.
- [ ] Implement `extractVersion(path, type)` using `GeneralCommandLine`.
    - **Robustness**: Use `withContext(Dispatchers.IO)` and `serviceCoroutineScope`.
    - **Stream Merging**: Merge `stdout` and `stderr` before applying regex.
    - **Timeouts**: Enforce a 10s timeout for all CLI calls.
- [ ] Add regex patterns for `luarocks`, `luacheck`, `stylua`, and `lua-format`.
- [ ] Implement `detectLuaVersion(path, type)` logic for best-effort compatibility checks.
- [ ] Implement `checkCompatibility(tool, interpreter)` logic.

## Phase 3: Discovery Service [Must]
- [ ] Create `net.internetisalie.lunar.util.LuaToolDiscoveryService`.
- [ ] Implement `discoverKnownTools()` using `PathEnvironmentVariableUtil.findInPath()`.
- [ ] **Windows Parity**: Ensure detection of `.bat`, `.cmd`, and `.exe` extensions and search standard Windows paths (Program Files, AppData).
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
- [ ] **Verification Task**: Verify `PersistentStateComponent` map serialization does not cause issues - use simple types where possible and validate with integration tests that restart settings component (TOOL-00-03).
