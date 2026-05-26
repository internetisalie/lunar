---
id: "ROCKS-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-01"
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/01-project-initialization/requirements|requirements]]"
---

# Implementation Plan: Project Initialization & Setup (ROCKS-01)

## Phase 1: Templates & Resources [Must]
- [ ] Add `setup.lua` template for loader setup.
- [ ] Add `spec/` placeholder file for Busted configuration.
- [ ] Add basic Makefile template.
- [ ] Add workspace configuration file template (e.g., `workspace.lua`).
- [ ] Implement `LuaRocksScaffolder.generateSetupLua(version)`.
- [ ] Implement `LuaRocksScaffolder.generateSpecPlaceholder()`.
- [ ] Implement `LuaRocksScaffolder.generateMakefile()`.
- [ ] Implement `LuaRocksScaffolder.generateWorkspaceConfig(workspaceName, initialRocks)`.

## Phase 2: CLI Integration [Must]
- [ ] Implement `LuaRocksScaffolder.init(projectPath, luaVersions)` using `GeneralCommandLine`.
- [ ] Add logic to update `.gitignore` automatically.

## Phase 3: Project Wizard [Must]
- [ ] Implement `LuaRocksProjectGenerator` for the "New Project" dialog.
- [ ] Create the UI for Project Kind selection (Single Rock vs Workspace).
- [ ] If Single Rock selected:
    - Create the UI for Project Type selection (Application vs Library).
    - Create the UI for Optional Components (Loader Setup, Busted Configuration, Makefile) with appropriate enable/disable logic (Loader Setup only for Application).
- [ ] If Workspace selected:
    - Create the UI for Workspace Name.
    - Create the UI for initial rock count and names (optional).
- [ ] Link the wizard to the `LuaRocksScaffolder`.

## Verification Tasks
- [ ] **Integration Test**: Scaffolding test that runs `init` for single rock and asserts file existence based on selected options.
- [ ] **Integration Test**: Scaffolding test for workspace that asserts workspace config file and gitignore are created.
- [ ] **Manual Test**: Create a new "Application" project with all options and verify the structure.
- [ ] **Manual Test**: Create a new "Library" project with only Makefile and verify the structure.
- [ ] **Manual Test**: Create a new workspace with initial rocks and verify the structure.
