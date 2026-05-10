# ROCKS-01: Project Initialization & Setup

## Scope
The Project Initialization feature enables users to scaffold a new Lua project with a LuaRocks-compatible structure. Users can create either a single rock project (with a standard src/ directory, optional application-specific setup, testing framework, and build automation) or a workspace that contains multiple rock projects. The IDE ensures that the selected components are correctly configured.

### In Scope
- Running `luarocks init` for single rock projects with configurable Lua versions.
- Automatic creation of `lua_modules/` for local dependencies (for single rock projects).
- Generation/Management of the `.rockspec` file (for single rock projects).
- Creation of `src/` directory for source code placement (for single rock projects).
- Optional creation of `src/setup.lua` for standardized module path resolution (application loader setup, for single rock projects).
- Optional creation of `spec/` directory for Busted testing configuration (for single rock projects).
- Optional creation of a `Makefile` for build automation (for single rock projects).
- Automatic injection of `LUA_INIT` logic for Run Configurations when loader setup is selected (for single rock projects).
- Creating a workspace configuration file for workspace projects.
- Initializing Git and appending standard exclusions to `.gitignore` for both project types.

### Out of Scope
- Managing multiple versions of the `luarocks` binary (handled by `TOOL-01`).
- Remote package searching and installation (covered in `ROCKS-02`).
- Code quality configuration files (`.luacheckrc`, `.stylua.toml`) (handled by separate code quality feature).
- Adding existing rocks to a workspace (handled by a separate workspace management feature).

## Syntax/Behavior

### Scaffolding Templates
The wizard provides templates for common Lua project layouts:
1. **Library/App (Standard)**:
     - Root: `[name]-scm-1.rockspec`.
     - `src/`: Core logic.
     - `spec/`: Busted tests (optional).
2. **Neovim Plugin**:
     - Root: `[name]-scm-1.rockspec`.
     - `lua/[name]/`: Plugin modules.
     - `plugin/`: Auto-load logic.
     - `tests/` or `spec/`: Testing suite (optional).

### Project Initialization Flow
When a user initializes a LuaRocks project:
1. The IDE executes `luarocks init --lua-versions "..."`.
2. A boilerplate `src/` directory is created.
3. If Loader Setup is selected (Application only): A boilerplate `src/setup.lua` is created.
4. If Busted Configuration is selected: A `spec/` directory is created with a placeholder spec file.
5. If Makefile is selected: A basic Makefile is created.
6. Root configuration files (`.luacheckrc`, `.stylua.toml`) are NOT generated (handled by separate code quality feature).
7. The `.rockspec` is populated with project metadata.
8. Git is initialized with standard LuaRocks exclusions added to `.gitignore`.

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

### TC-ROCKS-01-01: Minimal Single Rock Project Init (No Options)
- **Input**: User selects "Initialize LuaRocks Project", enters name "my-lib", selects kind "Single Rock", type "Library", and does not select any optional components.
- **Action**: IDE runs `luarocks init`.
- **Expected Output**: Directory contains `my-lib-scm-1.rockspec`, `src/`, and `.luarocks/`.

### TC-ROCKS-01-02: Single Rock Application with Loader Setup
- **Input**: User selects "Initialize LuaRocks Project", enters name "my-app", selects kind "Single Rock", type "Application", and selects "Loader Setup".
- **Action**: IDE runs `luarocks init` and generates `src/setup.lua`.
- **Expected Output**: Directory contains `my-app-scm-1.rockspec`, `.luarocks/`, `src/`, and `src/setup.lua`.

### TC-ROCKS-01-03: Single Rock Library with Busted Configuration
- **Input**: User selects "Initialize LuaRocks Project", enters name "my-lib", selects kind "Single Rock", type "Library", and selects "Busted Configuration".
- **Action**: IDE runs `luarocks init` and creates `spec/` directory with a placeholder.
- **Expected Output**: Directory contains `my-lib-scm-1.rockspec`, `.luarocks/`, `src/`, and `spec/` directory with `spec/placeholder.lua`.

### TC-ROCKS-01-04: Single Rock Application with All Options
- **Input**: User selects "Initialize LuaRocks Project", enters name "my-app", selects kind "Single Rock", type "Application", and selects all optional components (Loader Setup, Busted Configuration, Makefile).
- **Action**: IDE runs `luarocks init`, generates `src/setup.lua`, creates `spec/` directory, and creates `Makefile`.
- **Expected Output**: Directory contains `my-app-scm-1.rockspec`, `.luarocks/`, `src/`, `src/setup.lua`, `spec/` directory with `spec/placeholder.lua`, and `Makefile`.

### TC-ROCKS-01-05: Workspace Project Init
- **Input**: User selects "Initialize LuaRocks Project", enters workspace name "my-workspace", selects kind "Workspace", and specifies 2 initial rocks named "rock1" and "rock2".
- **Action**: IDE creates workspace configuration and directories for initial rocks.
- **Expected Output**: Directory contains workspace configuration file (e.g., `workspace.lua`), directories `rock1/` and `rock2/`, and `.gitignore` with appropriate exclusions.

### TC-ROCKS-01-06: Run Configuration Environment (with Loader Setup)
- **Input**: Run a Lua file in an initialized single rock project that has loader setup selected.
- **Action**: Inspect environment variables.
- **Expected Output**: `LUA_INIT` is set to point to the project's `src/setup.lua`.
