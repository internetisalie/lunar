---
folders:
  - "[[features/rocks/01-project-initialization/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: Project Initialization & Setup (ROCKS-01)

## Overview
This feature provides the logic and UI for scaffolding new Lua projects. It leverages the \`luarocks init\` command and supplements it with standardized boilerplate for better IDE integration.

## Architecture

### 1. Wizard Implementation
- **LuaRocksProjectGenerator**: Implements `com.intellij.ide.util.projectWizard.ModuleBuilder`.
- Provides UI for:
  - Project Name (sanitized for rockspec).
  - Target Directory.
  - Project Kind: Single Rock or Workspace.
  - If Single Rock:
    - Project Type (Application vs Library).
    - Lua Version(s) support.
    - Optional Components:
      - Loader Setup (for Application only): Creates `src/setup.lua` to enable local module resolution.
      - Busted Configuration: Creates `spec/` directory with a placeholder spec file.
      - Makefile: Creates a basic Makefile with common targets.
  - If Workspace:
    - Workspace Name (for the workspace configuration file).
    - Initial rock count (optional, with ability to add more later).

### 2. Scaffolding Service
- **LuaRocksScaffolder**: Handles the execution of CLI commands and file generation.
- **Workflow for Single Rock**:
  1. \`luarocks init\`.
  2. Create `src/` directory.
  3. Generate \`src/setup.lua\` for loader setup (if selected and project type is Application).
  4. Create `spec/` directory with a placeholder for Busted configuration (if selected).
  5. Create a basic Makefile (if selected).
  6. Initialize Git and append standard entries to \`.gitignore\`.
- **Workflow for Workspace**:
  1. Create workspace configuration file (e.g., `workspace.lua` or similar) with workspace name and initial rocks array.
  2. Create directory for each initial rock (if specified) without initializing each as a separate luarocks project (to be done later via add-rock).
  3. Initialize Git and append standard entries to \`.gitignore\` (including exclusions for rock directories).

## Implementation Details

### src/setup.lua Template (for loader setup)
```lua
-- setup.lua: Configure Lua module paths for locally installed rocks
local version = _VERSION:match("%d+%.%d+")
local share_path = "lua_modules/share/lua/" .. version .. "/?.lua;" ..
                  "lua_modules/share/lua/" .. version .. "/?/init.lua;"
local lib_path = "lua_modules/lib/lua/" .. version .. "/?.so;"

package.path = share_path .. package.path
package.cpath = lib_path .. package.cpath
```

## Testing Strategy
- **Integration Tests**: Verify that the generator creates all expected files in a temporary directory.
- **Manual Verification**: Run the wizard in a sandboxed IDE and verify the project structure.
