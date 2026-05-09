# ROCKS-01: Project Initialization & Setup

## Scope
The Project Initialization feature enables users to scaffold a new Lua project with a standardized LuaRocks-compatible structure. It ensures that the IDE correctly configures dependency tracking and module resolution from the start.

### In Scope
- Running `luarocks init` with configurable Lua versions.
- Automatic creation of `lua_modules/` for local dependencies.
- Generation/Management of the `.rockspec` file.
- Creation of `src/setup.lua` for standardized module path resolution.
- Automatic injection of `LUA_INIT` logic for Run Configurations.
- Updating `.gitignore` with standard LuaRocks exclusions.

### Out of Scope
- Managing multiple versions of the `luarocks` binary (handled by `TOOL-01`).
- Remote package searching and installation (covered in `ROCKS-02`).

## Syntax/Behavior

### Scaffolding Templates
The wizard provides templates for common Lua project layouts:
1. **Library/App (Standard)**:
    - Root: `[name]-scm-1.rockspec`, `.luacheckrc`, `.stylua.toml`, `Makefile`.
    - `src/`: Core logic.
    - `spec/`: Busted tests.
2. **Neovim Plugin**:
    - Root: `[name]-scm-1.rockspec`, `.stylua.toml`.
    - `lua/[name]/`: Plugin modules.
    - `plugin/`: Auto-load logic.
    - `tests/` or `spec/`: Testing suite.

### Project Initialization Flow
When a user initializes a LuaRocks project:
1. The IDE executes `luarocks init --lua-versions "..."`.
2. A boilerplate `init.lua` and `src/setup.lua` are created (paths vary by template).
3. Root configuration files (`.luacheckrc`, `.stylua.toml`) are generated with sensible defaults.
4. The `.rockspec` is populated with project metadata.

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-01-01** | **Initialization Wizard** | **Must** | **Pending** | User can trigger initialization with Template selection (Library vs Plugin). |
| **ROCKS-01-02** | **Rockspec Generation** | **Must** | **Pending** | Generate a valid `.rockspec` file with user-provided metadata. |
| **ROCKS-01-03** | **Module Setup Script** | **Must** | **Pending** | Generate `src/setup.lua` to enable local module resolution. |
| **ROCKS-01-04** | **Git Integration** | **Should** | **Pending** | Automatically add `lua_modules`, `.luarocks`, and coverage files to `.gitignore`. |
| **ROCKS-01-05** | **Run Config Patching** | **Must** | **Pending** | Automatically set `LUA_INIT` or `-l setup` in project run configurations. |
| **ROCKS-01-06** | **Lua Version Selection** | **Should** | **Pending** | Allow users to select supported Lua versions during init. |
| **ROCKS-01-07** | **Standard Configs** | **Must** | **Pending** | Generate `.luacheckrc` and `.stylua.toml` during scaffolding. |

## Test Cases

### TC-ROCKS-01-01: Standard Project Init
- **Input**: User selects "Initialize LuaRocks Project", enters name "my-app".
- **Action**: IDE runs `luarocks init`.
- **Expected Output**: Directory contains `my-app-dev-1.rockspec`, `src/setup.lua`, and `.luarocks/`.

### TC-ROCKS-01-02: Run Configuration Environment
- **Input**: Run a Lua file in an initialized project.
- **Action**: Inspect environment variables.
- **Expected Output**: `LUA_INIT` is set to point to the project's `src/setup.lua`.
