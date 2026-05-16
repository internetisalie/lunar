---
folders:
  - "[[features/tool/requirements|requirements]]"
title: "01: Tool Registry & Discovery"
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
| **TOOL-01-01** | **Global Tool Registration** | **M** | **Not Implemented** | Users can register a tool binary by providing its file path and optionally a label/version. |
| **TOOL-01-02** | **Auto-Detection** | **M** | **Not Implemented** | IDE scans standard paths (`/usr/bin`, `/usr/local/bin`, etc.) for known tools on first run or manual trigger. |
| **TOOL-01-03** | **Manual Registration** | **M** | **Not Implemented** | Manual registration via file picker is supported for custom tool locations. |
| **TOOL-01-04** | **Version Checking** | **M** | **Not Implemented** | The IDE validates a tool binary by checking its version output (e.g., `luarocks --version`) and parses the version string. |
| **TOOL-01-05** | **Minimum Version Validation** | **M** | **Not Implemented** | For the ROCKS feature, the IDE ensures the `luarocks` binary meets the minimum version required (e.g., >= 3.0.0). |
| **TOOL-01-06** | **Compatibility Matrix** | **M** | **Not Implemented** | The IDE checks compatibility between the bound Lua interpreter and the tool (e.g., LuaRocks 3.x supports Lua 5.1-5.5). |

## Test Cases

### TC-TOOL-01: Successful LuaRocks Registration
- **Input**: User provides path `/usr/local/bin/luarocks`.
- **Action**: IDE runs `/usr/local/bin/luarocks --version`.
- **Expected Output**: IDE identifies tool as "LuaRocks", version as "3.8.0", and adds it to the inventory.

### TC-TOOL-02: Invalid Tool Path
- **Input**: User provides path `/tmp/not_a_tool`.
- **Action**: IDE attempts to execute and fails.
- **Expected Output**: Error indicating invalid executable.
