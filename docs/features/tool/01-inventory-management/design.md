---
id: TOOL-01-DESIGN
title: "Technical Design"
type: design
parent_id: TOOL-01
status: "done"
priority: "high"
folders:
  - "[[features/tool/01-inventory-management/requirements|requirements]]"
---

# Technical Design: Tool Registry & Discovery (`TOOL-01`)

## Overview
This document outlines the architecture for discovering and managing external Lua tools.

## Architecture

### 1. Data Model (`LuaTool`)
Stored in `LuaApplicationSettings`.
- **id**: Unique identifier (UUID).
- **type**: `LuaToolType` (LUAROCKS, LUACHECK, STYLUA, etc.).
- **path**: Absolute path to the binary.
- **version**: Extracted version string.
- **isValid**: Boolean flag for health status.

### 2. Services

#### `LuaToolManager` (Application Service)
- Manages the lifecycle of `LuaTool` objects.
- Uses `serviceCoroutineScope` for background tasks.
- **Lazy Re-validation**: Always verifies file existence on disk before returning a tool.

#### `LuaToolDiscoveryService`
- Cross-platform discovery using `PathEnvironmentVariableUtil.findInPath()`.
- Supports platform-specific extensions (`.bat`, `.cmd` on Windows).

#### `LuaToolValidator`
- Extracts versions via CLI.
- **Async Execution**: Uses Coroutines with `Dispatchers.IO`.
- **Stream Merging**: Captures both `stdout` and `stderr` to handle tools that print version info to `stderr`.

## Logic Flow

1. **Discovery**: `DiscoveryService` scans the system for known tool names.
2. **Validation**: `Validator` runs `tool --version` with a 10s timeout.
3. **Registration**: Valid tools are added to the global inventory with a canonical path.

## Performance & Threading
- All CLI operations are offloaded to `Dispatchers.IO`.
- Regex parsing is done on background threads.
- Settings are saved using IntelliJ's `PersistentStateComponent` mechanism.
