---
id: "ROCKS-QA"
title: "QA Verification Scenarios"
type: "spec"
parent_id: "ROCKS"
priority: "high"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# QA & User Validation Scenarios: LuaRocks Integration (ROCKS Epic)

This document defines high-level validation scenarios to ensure the LuaRocks integration meets user needs and functions correctly in real-world development workflows.

## 1. Project Lifecycle Scenarios (ROCKS-01)

*(Corrected 2026-07-16 to the shipped wizard: there is no Template selector — the generator peer
offers Library/Application radio buttons plus runtime-kind and Lua-version combos; rockspecs are
named `-scm-1`, not `-dev-1`; `.luacheckrc`/`.stylua.toml` are explicitly out of scope.)*

### Scenario: New Library Scaffolding
- **Goal**: Verify a user can start a new Lua library with correct LuaRocks structure.
- **Pre-conditions**: IDE is open, no project loaded.
- **Steps**:
    1. Select **New Project** -> **LuaRocks**.
    2. Enter name `my-lua-lib`.
    3. Select **Project type**: `Library`.
    4. Select **Runtime**: `Lua`, **Lua version**: `5.4`.
    5. Click **Create**.
- **Expected Results**:
    - Project created with `my-lua-lib-scm-1.rockspec` in root.
    - `src/my-lua-lib.lua` exists (main module). (`src/setup.lua` is generated only for
      **Application** projects with **Loader Setup** checked.)
    - `lua_modules/` directory exists.
    - `.gitignore` includes the standard LuaRocks exclusions (e.g. `/lua_modules/`).
    - No `.luacheckrc`/`.stylua.toml` (out of scope) and no `.luarocks/` (`luarocks init` is
      not executed by the scaffolder).

### Scenario: Neovim Plugin Scaffolding
*(never implemented — removed 2026-07-16; the generator has only Library/Application project
types, no Neovim Plugin template ever shipped)*

---

## 2. Package Management Scenarios (ROCKS-02)

### Scenario: Dependency Discovery & Installation
*(Corrected 2026-07-16: the Package Browser is its own **LuaRocks Packages** tool window
(bottom), separate from the **LuaRocks** dependency tool window (right) — they are two windows,
not tabs of one. ROCKS-16 (planned 2026-07-16) will retitle them, not merge them.)*
- **Goal**: Find and install a package without leaving the IDE.
- **Steps**:
    1. Open the **LuaRocks Packages** tool window.
    2. Type `inspect` in the search bar.
    3. Select the `inspect` rock from the results.
    4. Verify the package metadata (description, license) appears in the detail pane.
    5. Select version `3.1.0`.
    6. Click **Install**.
- **Expected Results**:
    - Background task shows installation progress.
    - `inspect` carries the installed marker (✓) in the results list. *(Known issue as of
      2026-07-16: the list badge can go stale after install — tracked for fix in ROCKS-16.)*
    - `lua_modules/share/lua/5.4/inspect.lua` is physically present on disk.

### Scenario: Custom Rock Server Search
*(Corrected 2026-07-16: there is no "Settings → Lua → LuaRocks" page. The default server URL is
a LuaRocks kind option on the **Toolchain** page (Settings → Languages & Frameworks → Lua →
Toolchain), with a per-project override on the **Lua Project** page. A single resolved server is
used per search (`--server`); results are NOT attributed per-server in the list renderer.)*
- **Goal**: Verify searching against a custom rock server.
- **Steps**:
    1. Set a custom rock server URL (Toolchain page kind option, or the project override on the
       Lua Project page).
    2. Search for a package only available on that server.
- **Expected Results**:
    - Package appears in the search results (the search command was issued with
      `--server <url>`). No per-server attribution is shown in the list.

---

## 3. Dependency Auditing Scenarios (ROCKS-03)

### Scenario: Visualizing Transitive Dependencies
- **Goal**: Understand the full dependency tree of a complex package.
- **Steps**:
    1. Open a project that depends on `busted`.
    2. Open the **LuaRocks** tool window (the dependency-tree window, anchored right — separate
       from **LuaRocks Packages**; corrected 2026-07-16).
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
