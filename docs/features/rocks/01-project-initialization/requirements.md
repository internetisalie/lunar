---
id: ROCKS-01
title: "01: Project Initialization & Setup"
type: feature
parent_id: ROCKS
status: "done"
vf_icon: ✅
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-01: Project Initialization & Setup

## Scope
The Project Initialization feature enables users to scaffold a new Lua project with a LuaRocks-compatible structure. Users can create a rock project (with a standard src/ directory, optional application-specific setup, testing framework, and build automation). The IDE ensures that the selected components are correctly configured. (Note: prior to ROCKS-09 this supported a bespoke 'workspace.lua' scaffold; multi-rock workspaces are now created by scaffolding multiple rocks side-by-side or adding them to an existing directory.)

### In Scope
- Deterministic template-based scaffolding of a LuaRocks-compatible layout with a configurable runtime kind + Lua version. (*Corrected 2026-07-16:* `luarocks init` is **not** executed — `LuaRocksScaffolder` deliberately omits it because it requires process I/O outside the WriteAction; see `LuaRocksScaffolder.kt:14-16`.)
- Automatic creation of `lua_modules/` for local dependencies (for single rock projects).
- Generation/Management of the `.rockspec` file (for single rock projects).
- Creation of `src/` directory for source code placement (for single rock projects).
- Optional creation of `src/setup.lua` for standardized module path resolution (application loader setup, for single rock projects).
- Optional creation of `spec/` directory for Busted testing configuration (for single rock projects).
- Optional creation of a `Makefile` for build automation (for single rock projects).
- Automatic injection of `LUA_INIT` logic for Run Configurations when loader setup is selected (for single rock projects).
- Writing a `.gitignore` with standard LuaRocks exclusions. (*Corrected 2026-07-16:* the scaffolder does **not** run `git init` — it only writes the `.gitignore` file.)

### Out of Scope
- Managing multiple versions of the `luarocks` binary (handled by `TOOL-01`).
- Remote package searching and installation (covered in `ROCKS-02`).
- Code quality configuration files (`.luacheckrc`, `.stylua.toml`) (handled by separate code quality feature).
- Adding existing rocks to a workspace (handled by a separate workspace management feature).

## Syntax/Behavior

### Scaffolding Templates
*(Corrected 2026-07-16: an earlier draft described a "Neovim Plugin" template — it was never
implemented; the generator peer offers only the Library / Application project types
(`RockType` in `LuaRocksProjectSettings.kt`).)*

The wizard offers two project types (radio buttons in `LuaRocksGeneratorPeer`):
1. **Library**:
     - Root: `[name]-scm-1.rockspec`.
     - `src/[name].lua`: Core logic.
     - `spec/`: Busted tests (optional).
2. **Application**:
     - Root: `[name]-scm-1.rockspec`.
     - `src/main.lua`: Entry point.
     - `src/setup.lua`: Loader setup (optional).
     - `spec/`: Busted tests (optional).

### Project Initialization Flow
When a user initializes a LuaRocks project (`LuaRocksScaffolder.scaffold`, all template-based —
no `luarocks init` subprocess; *corrected 2026-07-16*):
1. The `.rockspec` is generated with project metadata (`LuaRocksTemplates.rockspec`).
2. A boilerplate `src/` directory is created with the main module.
3. If Loader Setup is selected (Application only): A boilerplate `src/setup.lua` is created.
4. If Busted Configuration is selected: A `spec/` directory is created with a placeholder spec file.
5. If Makefile is selected: A basic Makefile is created.
6. Root configuration files (`.luacheckrc`, `.stylua.toml`) are NOT generated (handled by separate code quality feature).
7. An empty `lua_modules/` directory is created.
8. A `.gitignore` with standard LuaRocks exclusions is written (no `git init` is run).
9. The wizard's interpreter choice is applied (and, if requested, an isolated environment is
   provisioned via the TOOLING-04 engine once the project opens).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| **ROCKS-01-01** | **Initialization Wizard** | **Must** | **Partial** | `DirectoryProjectGenerator` + `ProjectGeneratorPeer` panel; live wizard requires manual verification. |
| **ROCKS-01-02** | **Rockspec Generation** | **Must** | **Full** | `LuaRocksTemplates.rockspec()` generates valid rockspec; covered by scaffolder tests. |
| **ROCKS-01-03** | **Module Setup Script** | **Must** | **Full** | `LuaRocksTemplates.setupLua()` generates `src/setup.lua`; covered by scaffolder tests. |
| **ROCKS-01-04** | **Git Integration** | **Should** | **Partial** | `.gitignore` with all LuaRocks exclusions written by scaffolder; no `git init` is performed (corrected 2026-07-16 — earlier "optional/best-effort" wording overstated it). |
| **ROCKS-01-05** | **Run Config Patching** | **Must** | **Partial** | `LuaRocksScaffolder.patchRunConfigTemplate` sets `LUA_INIT`; patching only verifiable in a full IDE project context (no headless RunManager for template). |
| **ROCKS-01-06** | **Lua Version Selection** | **Should** | **Full** | `LuaRocksProjectSettings.kindId`/`luaVersion` selected via the peer's runtime-kind + version combos (post-TOOLING-05); drives the project Target and optional environment provisioning. (Row updated 2026-07-16 — the old "UI checkbox not wired" note was stale.) |

## Test Cases

### TC-ROCKS-01-01: Minimal Single Rock Project Init (No Options)
- **Input**: Generator settings name "my-lib", kind Single Rock, type Library, no options.
- **Action**: `LuaRocksScaffolder.scaffold` runs (template generation; no binary required).
- **Expected Output**: Directory contains `my-lib-scm-1.rockspec`, `src/my-lib.lua`,
  `lua_modules/`, and `.gitignore`. (No `.luarocks/` — `luarocks init` is not run; corrected
  2026-07-16.)

### TC-ROCKS-01-02: Single Rock Application with Loader Setup
- **Input**: name "my-app", Single Rock, type Application, "Loader Setup".
- **Action**: `scaffold` generates `src/setup.lua` and patches the run-config template.
- **Expected Output**: Directory contains `my-app-scm-1.rockspec`, `src/main.lua`,
  `src/setup.lua`, `lua_modules/`, `.gitignore`.

### TC-ROCKS-01-03: Single Rock Library with Busted Configuration
- **Input**: name "my-lib", Single Rock, type Library, "Busted Configuration".
- **Action**: `scaffold` creates `spec/`.
- **Expected Output**: Directory contains `my-lib-scm-1.rockspec`, `src/my-lib.lua`,
  `spec/my-lib_spec.lua`, `lua_modules/`, `.gitignore`.

### TC-ROCKS-01-04: Single Rock Application with All Options
- **Input**: name "my-app", Single Rock, type Application, all options (Loader Setup, Busted,
  Makefile).
- **Action**: `scaffold` generates all template files.
- **Expected Output**: Directory contains `my-app-scm-1.rockspec`, `src/main.lua`,
  `src/setup.lua`, `spec/my-app_spec.lua`, `Makefile`, `lua_modules/`, `.gitignore`.

### TC-ROCKS-01-05: Workspace Project Init (Obsolete)
- **Status**: OBSOLETE. As of ROCKS-09, the `workspace.lua` scaffolding path has been removed. Multi-rock workspaces are now discovered purely by the presence of multiple rockspecs in subdirectories, without requiring a root `workspace.lua`.

### TC-ROCKS-01-06: Run Configuration Environment (with Loader Setup)
- **Input**: A single-rock project initialized with Loader Setup (TC-ROCKS-01-02).
- **Action**: Read `RunManager.getInstance(project).getConfigurationTemplate(luaFactory)
  .configuration` env after scaffolding.
- **Expected Output**: the template's `LUA_INIT` env var equals `@<baseDir>/src/setup.lua`, so
  new Lua run configs inherit it.
