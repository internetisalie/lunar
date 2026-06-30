---
id: TOOL-01
title: "01: Tool Registry & Discovery"
type: feature
parent_id: TOOL
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/tool/requirements|requirements]]"
---

# Tool Registry & Discovery (`TOOL-01`)

## Scope
The Tool Registry feature enables the IDE to discover, register, and validate external Lua ecosystem tool binaries (e.g., `luarocks`, `lua-format`, `luacheck`). It provides a centralized repository of tools.

### In Scope
- Automatic detection of tools in standard system paths.
- Manual registration of tool binaries via file path.
- Version extraction and validation via CLI (e.g., `tool --version`).
- Compatibility checking with Lua versions.

### Out of Scope
- Installation or uninstallation of the tools themselves.
- Project-level binding (covered in `TOOL-02`).
- UI for management (covered in `TOOL-03`).

## Syntax/Behavior

### Tool Registration
The IDE executes the tool with a version flag (typically `--version` or `-v`) to identify the tool type and version.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **TOOL-01-01** | **Global Tool Registration** | **M** | **Full** | `LuaToolManager.registerTool(path, hintType?)` validates, extracts version, and persists to `LuaApplicationSettings.State.toolInventory`. |
| **TOOL-01-02** | **Auto-Detection** | **M** | **Full** | `LuaToolManager.autoDiscover()` delegates to `LuaToolDiscoveryService.discoverKnownTools()`, scanning PATH + common platform directories. |
| **TOOL-01-03** | **Manual Registration** | **M** | **Full** | `LuaToolManager.registerTool(path)` accepts any absolute path; UI layer (TOOL-03) provides the file picker. |
| **TOOL-01-04** | **Version Checking** | **M** | **Full** | `LuaToolValidator.extractVersion(path, type)` runs `--version`, merges stdout+stderr, applies per-tool regex (LuaRocks/luacheck/StyLua). |
| **TOOL-01-05** | **Minimum Version Validation** | **M** | **Full** | `LuaToolValidator.meetsMinimumVersion(path, LUAROCKS)` compares parsed `SemanticVersion` against `3.0.0`. |
| **TOOL-01-06** | **Compatibility Matrix** | **M** | **Full** | `LuaToolValidator.checkCompatibility(tool, interpreterLuaVersion)` — LuaRocks version detected via `for Lua X.Y` pattern; major.minor match enforced. |

## Test Cases

### TC-TOOL-01: Successful LuaRocks Registration
- **Input**: User provides path `/usr/local/bin/luarocks`.
- **Action**: IDE runs `/usr/local/bin/luarocks --version`.
- **Expected Output**: IDE identifies tool as "LuaRocks", version as "3.8.0", and adds it to the inventory.

### TC-TOOL-02: Invalid Tool Path
- **Input**: User provides path `/tmp/not_a_tool`.
- **Action**: IDE attempts to execute and fails.
- **Expected Output**: Error indicating invalid executable.
