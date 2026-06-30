---
id: "ROCKS-INVESTIGATION-PROJECTS"
title: "Project Layout Investigation"
type: "spec"
parent_id: "ROCKS"
priority: "low"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# Open-Source Lua Project Layout Investigation

This document summarizes common directory structures and configuration patterns found in popular open-source Lua projects (e.g., Kong, Lapis, Busted, Neovim plugins). These findings inform the requirements for the **ROCKS-01: Project Initialization** feature.

## 1. Common Layout Patterns

Projects generally follow one of two major patterns depending on their target environment.

### Pattern A: Standard Lua/LuaRocks (Libraries & Apps)
Used for general-purpose libraries and CLI applications.
- **Source Code**: Often in a directory named after the project (e.g., `kong/`, `lapis/`) or inside a `src/` directory.
- **Tests**: Almost universally in a `spec/` directory, following the **Busted** testing framework standard. Large projects often use subdirectories like `spec/01-unit/`.
- **Executables**: CLI tools and binaries are stored in `bin/`.
- **Metadata**: The `.rockspec` file is located in the root directory.
- **Documentation**: Found in `docs/` or `doc/`.

### Pattern B: Neovim Plugins (Modern Lua)
Specific to the Neovim ecosystem.
- **Source Code**: Strictly uses a `lua/` directory. Modules must be in `lua/<plugin_name>/` to be discoverable.
- **Entry Points**: The `plugin/` directory contains code that runs automatically on Neovim startup.
- **Documentation**: Uses `doc/` for Vimhelp files (`.txt`).
- **Tests**: Often uses `tests/` or `spec/`.

## 2. Standard Configuration Files

Professional Lua projects include a consistent set of root-level configuration files:

| File | Purpose | Industry Standard |
| :--- | :--- | :--- |
| **`.luacheckrc`** | Static Analysis | **Luacheck** |
| **`.stylua.toml`** | Code Formatting | **StyLua** |
| **`.luacov`** | Code Coverage | **LuaCov** |
| **`Makefile`** | Task Orchestration | POSIX Make |
| **`.editorconfig`** | Editor Consistency | EditorConfig |
| **`.gitignore`** | Version Control | Standard exclusions (see below) |

### Common `.gitignore` Exclusions
- `lua_modules/` (Local dependencies)
- `*.rock` (Packaged rocks)
- `luacov.*` (Coverage reports)
- `.luarocks/` (Local LuaRocks config)

## 3. Implications for Project Initialization

To align with these industry standards, the "Initialize LuaRocks Project" feature should support the following:

- **Templates**: Provide choices for "Library/App" (Pattern A) vs "Neovim Plugin" (Pattern B).
- **Default Scaffolding**:
    - Automatic creation of `spec/` with a boilerplate test.
    - Generation of `.luacheckrc` and `.stylua.toml` with sensible defaults.
    - Generation of a Scscm-1 rockspec.
- **Dependency Handling**: Creation of `src/setup.lua` (as identified in previous investigation) to bridge the gap between `lua_modules/` and standard Lua `require`.
