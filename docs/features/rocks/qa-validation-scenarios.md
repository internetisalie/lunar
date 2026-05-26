---
id: "ROCKS-QA"
title: "QA Verification Scenarios"
type: "spec"
parent_id: "ROCKS"
status: "planned"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# QA & User Validation Scenarios: LuaRocks Integration (ROCKS Epic)

This document defines high-level validation scenarios to ensure the LuaRocks integration meets user needs and functions correctly in real-world development workflows.

## 1. Project Lifecycle Scenarios (ROCKS-01)

### Scenario: New Library Scaffolding
- **Goal**: Verify a user can start a new Lua library with correct LuaRocks structure.
- **Pre-conditions**: IDE is open, no project loaded.
- **Steps**:
    1. Select **New Project** -> **Lua (LuaRocks)**.
    2. Enter name `my-lua-lib`.
    3. Select **Template**: `Standard Library/App`.
    4. Select **Lua Version**: `5.4`.
    5. Click **Create**.
- **Expected Results**:
    - Project created with `my-lua-lib-dev-1.rockspec` in root.
    - `src/setup.lua` exists with correct `package.path` logic.
    - `.luacheckrc` and `.stylua.toml` generated.
    - `.gitignore` includes `/lua_modules/` and `/.luarocks/`.

### Scenario: Neovim Plugin Scaffolding
- **Goal**: Verify specific structure for Neovim plugin developers.
- **Steps**:
    1. Select **New Project** -> **Lua (LuaRocks)**.
    2. Select **Template**: `Neovim Plugin`.
    3. Click **Create**.
- **Expected Results**:
    - Project contains `lua/my-plugin/` directory.
    - `plugin/` directory exists for auto-load scripts.
    - Rockspec correctly identifies the `lua/` folder as the module root.

---

## 2. Package Management Scenarios (ROCKS-02)

### Scenario: Dependency Discovery & Installation
- **Goal**: Find and install a package without leaving the IDE.
- **Steps**:
    1. Open the **LuaRocks** tool window.
    2. Go to the **Packages** tab.
    3. Type `inspect` in the search bar.
    4. Select the `inspect` rock from the results.
    5. Verify README and license (MIT) appear in the detail pane.
    6. Select version `3.1.0`.
    7. Click **Install**.
- **Expected Results**:
    - Background task shows installation progress.
    - `inspect` appears in the "Installed" list.
    - `lua_modules/share/lua/5.4/inspect.lua` is physically present on disk.

### Scenario: Multi-Repository Search
- **Goal**: Verify searching across custom rock servers.
- **Steps**:
    1. Open **Settings** -> **Lua** -> **LuaRocks**.
    2. Add a custom rock server URL.
    3. Search for a package only available on that server.
- **Expected Results**:
    - Package appears in the search results with correct server attribution.

---

## 3. Dependency Auditing Scenarios (ROCKS-03)

### Scenario: Visualizing Transitive Dependencies
- **Goal**: Understand the full dependency tree of a complex package.
- **Steps**:
    1. Open a project that depends on `busted`.
    2. Open the **LuaRocks** tool window -> **Dependencies** tab.
    3. Expand the `busted` node.
- **Expected Results**:
    - `say`, `luassert`, `mediator_lua`, etc., are shown as child nodes.
    - Transitive nodes are visually distinct (e.g., italics or subtle icon).

### Scenario: Detecting Version Conflicts
- **Goal**: Proactively identify versioning "hell".
- **Steps**:
    1. Manually edit the rockspec to require `inspect < 2.0`.
    2. Keep an existing dependency that requires `inspect >= 3.0`.
    3. Refresh the **Dependencies** tree.
- **Expected Results**:
    - `inspect` node is flagged with a red error icon.
    - Tooltip explains the conflict: "Conflict: Required < 2.0 (Direct) and >= 3.0 (via other-package)".

---

## 4. Execution & Build Scenarios (ROCKS-04)

### Scenario: Running a Custom Rock Task
- **Goal**: Execute `luarocks make` as a build step.
- **Steps**:
    1. Open **Run/Debug Configurations**.
    2. Add a new **LuaRocks** configuration.
    3. Set **Command** to `make`.
    4. Click **Run**.
- **Expected Results**:
    - Run tool window opens.
    - `luarocks make` executes using the project-bound binary.
    - Console shows colored output of the build process.

### Scenario: Before Launch Integration
- **Goal**: Ensure dependencies are installed before running tests.
- **Steps**:
    1. Open a **Busted** run configuration.
    2. In **Before Launch**, add a **LuaRocks** task with command `install --deps-only`.
    3. Run the Busted config.
- **Expected Results**:
    - The IDE first runs the LuaRocks installation task.
    - Upon success, it proceeds to launch the Busted tests.
