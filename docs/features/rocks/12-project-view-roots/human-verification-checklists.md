---
id: "ROCKS-12-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "ROCKS-12"
folders:
  - "[[features/rocks/12-project-view-roots/requirements|requirements]]"
---

# Verification Checklists: ROCKS-12 — Project-View Roots & Marking

> Run in the containerized GoLand per the `verify-in-ide` skill. Use a project that has both
> installed rocks and a first-party rock source root.

## 1. Installed-Rock Library (Piece A)

### Scenario 1.1: Installed rocks appear under External Libraries
- **Setup**: A Lua project whose base contains `lua_modules/share/lua/5.4/<some-rock>/init.lua`
  (e.g. run `luarocks install --tree lua_modules luassert`), interpreter target = Standard 5.4.
- **Steps**:
  1. Open the project in the IDE.
  2. Expand **External Libraries** in the Project view.
  3. Expand the **Installed Rocks** node.
- **Expected**: An "Installed Rocks" node is present and expands to the installed rock files under
  `share/lua/5.4` (and `lib/lua/5.4` if any C modules were installed).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Installed tree is not first-party source
- **Setup**: Same as 1.1.
- **Steps**:
  1. Open a `.lua` file under `lua_modules/share/lua/5.4/...`.
  2. Check the file is treated as a library file (it sits under External Libraries, not under the
     project content root in the Project view; "Find in Files → Scope: Project Files" excludes it).
- **Expected**: The file is library-scoped (excluded from the project-source scope), not indexed as
  first-party project source.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.3: No installed tree → no node
- **Setup**: A Lua project with no `lua_modules` and no `.luarocks` directory.
- **Steps**:
  1. Open the project; expand External Libraries.
- **Expected**: No "Installed Rocks" node is shown.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. First-Party Source-Root Marking (Piece B)

### Scenario 2.1: First-party src root is marked
- **Setup**: A LuaRocks project whose rockspec `build.modules` maps modules under `src/` (so
  ROCKS-05 derives `<base>/src/` as a source root), with ROCKS-05/09 enabled.
- **Steps**:
  1. Open the project in the IDE.
  2. Locate the `src` folder in the Project view content tree.
- **Expected**: The `src` folder node shows the grayed " rock source root" suffix label.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Vendored / installed folders are NOT marked
- **Setup**: Same project, additionally containing a `thirdparty/` folder and the `lua_modules`
  installed tree.
- **Steps**:
  1. Inspect the `thirdparty` folder node and the `lua_modules` folder node in the Project view.
- **Expected**: Neither `thirdparty` nor `lua_modules` shows the "rock source root" label
  (`lua_modules` instead appears under External Libraries → Installed Rocks).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: Combined real-flow check
- **Setup**: A scaffolded rock project (ROCKS-01) with `luarocks install --tree lua_modules`
  having populated `lua_modules`, and a first-party `src/` source root.
- **Steps**:
  1. Open the project.
  2. Confirm installed rocks appear under External Libraries → Installed Rocks (Scenario 1.1).
  3. Confirm the first-party `src` root is badged (Scenario 2.1).
- **Expected**: Both behaviors hold simultaneously in one project.
- **Result**: ⬜ Pass / ⬜ Fail
