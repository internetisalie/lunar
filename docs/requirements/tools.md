# TOOL Feature Specification

## Overview
The TOOL feature extends the IDE's capability to manage and inventory external tool binaries (e.g., `luarocks`, `lua-format`, `luacheck`) similar to how Lua interpreters are currently managed. This enables seamless integration of LuaRocks-dependent features (like the ROCKS feature) by ensuring the IDE can locate, validate, and invoke the correct tool versions per project or globally.

## Motivation
Currently, the IDE maintains an inventory of configured Lua interpreter binaries but lacks equivalent support for other essential Lua ecosystem tools. The ROCKS feature (for LuaRocks integration) requires access to the `luarocks` binary for operations such as:
- Searching for packages (`luarocks search`)
- Installing dependencies (`luarocks install`)
- Upgrading packages (`luarocks upgrade` - if implemented via CLI)
- Generating rockspecs (`luarocks new_version`)
- Running formatters/linters (`lua-format`, `luacheck`)

Without a centralized tool inventory, the IDE would rely on PATH resolution or hardcoded assumptions, leading to:
- Inconsistent behavior across projects
- Difficulty managing multiple tool versions
- Failed operations when tools are not in PATH
- Inability to enforce specific tool versions for reproducibility

## Key Use Cases

### 1. Tool Inventory Management
- **Global Tool Registration**: Define and label tool installations (e.g., "LuRocks 3.8.0", "LuaFormatter 1.4.0")
- **Per-Project Tool Binding**: Bind specific tool versions to projects (e.g., Project A uses LuaRocks 3.7.0, Project B uses 3.8.0)
- **Auto-Detection**: Scan common installation paths for known tools (LuaRocks, LuaFormatter, Luacheck, etc.)
- **Manual Registration**: Allow users to explicitly register tool binaries via file picker

### 2. Tool Validation & Compatibility
- **Version Checking**: Verify tool version meets minimum requirements for features (e.g., ROCKS feature requires LuaRocks >= 3.0.0)
- **Compatibility Matrix**: Ensure tool versions are compatible with selected Lua interpreter (e.g., LuaRocks 3.x for Lua 5.1-5.5)
- **Health Checks**: Validate tool executables are functional (e.g., `luarocks --version` returns expected output)

### 3. IDE Integration
- **PATH Augmentation**: Automatically prepend tool binary directories to PATH for integrated terminals and build tasks
- **Context-Aware Invocation**: Use project-bound tool versions when running IDE actions (e.g., "Format Document" uses project's lua-format)
- **Fallback Mechanism**: Fall back to global/default tool if project-bound version is unavailable
- **Diagnostic Reporting**: Surface tool version and path information in IDE settings/logs

### 4. User Experience
- **Settings UI**: Dedicated section in IDE preferences for managing tools (similar to Lua interpreters)
- **Project Settings Overlay**: View/bind tool versions in project properties
- **Notifications**: Alert users when:
  - A required tool is missing
  - A tool version is outdated
  - A tool executable is not found or invalid

## Benefits
- **Reproducibility**: Ensures consistent tool versions across developer machines and CI/CD pipelines
- **Productivity**: Reduces time spent debugging "tool not found" or version mismatch issues
- **Safety**: Prevents accidental use of incompatible tool versions
- **Extensibility**: Framework easily supports new Lua ecosystem tools (e.g., ldoc, luacov)
- **Consistency**: Aligns tool management with existing Lua interpreter workflow

## Implementation Considerations

### Data Model
Extend existing tool inventory structure to support:
```json
{
  "tools": [
    {
      "id": "luarocks-3.8.0",
      "name": "LuaRocks",
      "version": "3.8.0",
      "path": "/usr/local/bin/luarocks",
      "type": "package_manager",
      "lua_versions": ["5.1", "5.2", "5.3", "5.4", "5.5"],
      "is_global": false,
      "project_id": "proj-123"
    }
  ]
}
```

### Integration Points
- **Lua Interpreter Inventory**: Reuse existing UI patterns and storage mechanisms
- **ROCKS Feature**: Consume tool inventory to locate `luarocks` binary for operations
- **Build System**: Inject tool paths into build task environments
- **Terminals**: Augment PATH for embedded terminals
- **File Watchers**: Monitor tool binary paths for changes (e.g., upgrades via system package manager)

### Constraints & Assumptions
- Does not manage tool installation (users install tools externally via package managers, binaries, etc.)
- Focuses on discovery, validation, and binding of pre-installed tool binaries
- Leverages existing configuration infrastructure (similar to lua interpreters)
- Supports per-project and global tool bindings
- Assumes tools follow standard `--version` output for version detection

## Dependencies
- None beyond existing IDE infrastructure for managing interpreter inventories
- ROCKS feature will depend on this TOOL feature for `luarocks` binary access

## Open Questions
1. Should we support tool version ranges (e.g., "luarocks >= 3.0.0") in project bindings?
2. How should we handle tools that require Lua interpreter version awareness (e.g., a formatter built for Lua 5.4 may not work with 5.1)?
3. Should we cache tool version metadata to reduce startup overhead?

## Success Metrics
- 90% reduction in "tool not found" errors related to LuaRocks/LuaFormatter/etc.
- 100% of ROCKS feature operations use the correct tool version as bound to project
- Positive user feedback on tool management UI in IDE settings

## Next Steps
1. Finalize technical design for tool inventory storage and UI
2. Implement core inventory management (CRUD operations for tools)
3. Integrate with ROCKS feature for `luarocks` binary resolution
4. Extend to other tools (lua-format, luacheck) as needed
5. Add validation and health check mechanisms