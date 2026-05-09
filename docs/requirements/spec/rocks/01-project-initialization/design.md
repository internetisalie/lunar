# Technical Design: Project Initialization & Setup (ROCKS-01)

## Overview
This feature provides the logic and UI for scaffolding new Lua projects. It leverages the \`luarocks init\` command and supplements it with standardized boilerplate for better IDE integration.

## Architecture

### 1. Wizard Implementation
- **LuaRocksProjectGenerator**: Implements \`com.intellij.ide.util.projectWizard.ModuleBuilder\`.
- Provides UI for:
  - Project Name (sanitized for rockspec).
  - Target Directory.
  - Template Selection (Library/App vs. Neovim Plugin).
  - Lua Version(s) support.

### 2. Scaffolding Service
- **LuaRocksScaffolder**: Handles the execution of CLI commands and file generation.
- **Workflow**:
  1. \`luarocks init\`.
  2. Generate \`src/setup.lua\` based on the selected Lua version.
  3. Generate \`.luacheckrc\` and \`.stylua.toml\` from bundled resources/templates.
  4. Create boilerplate \`init.lua\` or \`main.lua\`.
  5. Initialize Git and append standard entries to \`.gitignore\`.

## Implementation Details

### src/setup.lua Template
```lua
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
