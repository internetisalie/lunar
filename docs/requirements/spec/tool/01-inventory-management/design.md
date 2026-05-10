# Technical Design: Tool Registry & Discovery (`TOOL-01`)

## Overview
This document outlines the technical implementation for discovering, registering, and validating external Lua tools.

## Architecture

### 1. Data Models

#### `LuaToolType` (Enum)
Defines the categories of tools supported.
- `LUAROCKS`
- `LUA_FORMAT`
- `LUACHECK`
- `STYLUA`
- `GENERIC` (for custom tools or scripts)

#### `LuaTool` (Data Class)
Represents a registered tool instance.
- `id`: Unique UUID.
- `type`: `LuaToolType`.
- `name`: User-defined label or auto-generated name.
- `path`: Absolute path to the executable.
- `version`: Extracted version string (e.g., "3.8.0") or `"Unknown"`.
- `luaVersion`: Optional best-effort detected Lua version the tool is built for.
- `lastModified`: Last modification time of the binary (used for cache validation).
- `isValid`: Boolean flag indicating if the path is still valid.

### 2. Storage

#### Global Inventory (`LuaApplicationSettings`)
The `LuaApplicationSettings.State` stores the global list of registered tools.
```kotlin
class State {
    var toolInventory: MutableList<LuaTool> = mutableListOf()
}
```

### 3. Services

#### `LuaToolManager` (Application Service)
Central service for interacting with the inventory.
- `registerTool(path: String): LuaTool`: Validates the binary, extracts metadata, and adds it to the inventory.
- `unregisterTool(id: String)`: Removes a tool and cleans up associated bindings.
- `getTool(id: String): LuaTool?`: Retrieves a tool by ID.

#### `LuaToolDiscoveryService` (Utility)
Scans system environment for tools.
- `discoverKnownTools(): List<Path>`: Searches `PATH` and common OS-specific directories for known filenames.

#### `LuaToolValidator` (Utility)
Handles CLI interaction for verification.
- `extractVersion(path: String, type: LuaToolType): String?`: Executes `path --version` (merging stdout/stderr) and applies regex to find the version string.

## Implementation Details

### Version Extraction Patterns
The following tools are supported out-of-the-box:

| Tool | Type | Version Regex | OS Search Patterns |
| :--- | :--- | :--- | :--- |
| **LuaRocks** | `LUAROCKS` | `luarocks (\d+\.\d+\.\d+)` | `luarocks`, `luarocks.bat`, `luarocks.exe` |
| **Luacheck** | `LUACHECK` | `(\d+\.\d+\.\d+)` | `luacheck`, `luacheck.bat` |
| **StyLua** | `STYLUA` | `stylua (\d+\.\d+\.\d+)` | `stylua`, `stylua.exe` |
| **Lua-format** | `LUA_FORMAT` | `(\d+\.\d+\.\d+)` | `lua-format`, `lua-format.exe` |

### Validation and Compatibility
- **Minimum Version Enforcement**: Semantic version comparison for requirements.
- **Lua Interpreter Compatibility**: Best-effort detection of linked Lua version (e.g., path analysis or config command output). Mismatches result in warnings.
- **Cache Validation**: The `lastModified` timestamp is checked during health checks. If the file on disk is newer than the cached metadata, a full validation is re-run.

## Testing Strategy
- **Unit Tests**: Test regex version extraction against various CLI output mocks.
- **Integration Tests**: Verify settings persistence of the `toolInventory`.
- **Cross-Platform E2E Tests**: Use Docker Windows Server containers and Linux containers to validate tool discovery, PATH injection, and execution across Windows and Linux platforms. Tests should cover:
  - OS-specific filename pattern resolution (e.g., luarocks.exe vs luarocks)
  - PATH augmentation in both CMD/PowerShell and Bash/Zsh environments
  - Health check execution and validation in containerized environments
  - Tool execution through IDE actions (formatting, linting) in cross-platform scenarios
