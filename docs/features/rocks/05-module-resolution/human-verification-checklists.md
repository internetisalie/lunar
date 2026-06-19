---
id: "ROCKS-05-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "todo"
parent_id: "ROCKS-05"
folders:
  - "[[features/rocks/05-module-resolution/requirements|requirements]]"
---

# Verification Checklists: ROCKS-05 — Rockspec Module Resolution

## 1. Require Navigation

### Scenario 1.1: Ctrl+Click on require() resolves to rockspec-mapped file
- **Setup**: Project with `rocks/adt/adt-1.0-1.rockspec` containing `build.modules = {["adt.orderedmap"] = "lua/adt/orderedmap.lua"}` and the file `rocks/adt/lua/adt/orderedmap.lua`.
- **Steps**:
  1. Open `src/main.lua` containing `require("adt.orderedmap")`.
  2. Ctrl+click on `"adt.orderedmap"`.
- **Expected**: Editor navigates to `rocks/adt/lua/adt/orderedmap.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: Require with init.lua module
- **Setup**: Rockspec with `build.modules = {["mymod"] = "lua/mymod/init.lua"}` and the file `lua/mymod/init.lua`.
- **Steps**:
  1. Open a file with `require("mymod")`.
  2. Ctrl+click on `"mymod"`.
- **Expected**: Editor navigates to `lua/mymod/init.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Cross-File Completion

### Scenario 2.1: Symbols from rockspec-mapped files appear in completion
- **Setup**: `rocks/adt/lua/adt/orderedmap.lua` exports a function `orderedmap.new()`. The rockspec maps the module.
- **Steps**:
  1. Open `src/main.lua` with `local om = require("adt.orderedmap")`.
  2. Type `om.` and trigger completion.
- **Expected**: `new` appears in the completion list with type text showing the source file.
- **Result**: ⬜ Pass / ⬜ Fail

## 3. Auto-Import

### Scenario 3.1: Auto-import suggests correct module path
- **Setup**: `rocks/adt/lua/adt/orderedmap.lua` exports `orderedmap`. Rockspec maps the module.
- **Steps**:
  1. In a new file, type `orderedmap` and accept the auto-import suggestion.
- **Expected**: `local orderedmap = require("adt.orderedmap")` is inserted at the top.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. External Libraries

### Scenario 4.1: Rockspec source directories appear in project tree
- **Setup**: Project with rockspec mapping modules to `rocks/adt/lua/`.
- **Steps**:
  1. Open the Project tool window.
  2. Look under "External Libraries" or the library roots section.
- **Expected**: `rocks/adt/lua/` (or a "Search Trees" library containing it) appears as a library root, with its files visible and searchable.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Multi-Rockspec Workspace

### Scenario 5.1: Multiple rockspecs contribute patterns
- **Setup**: Project with `rocks/adt/adt-1.0-1.rockspec` and `rocks/cmd/cmd-1.0-1.rockspec`, each mapping modules to their own `lua/` directories.
- **Steps**:
  1. Open a file with `require("adt.orderedmap")` and `require("cmd.cat")`.
  2. Ctrl+click each require.
- **Expected**: Both resolve to their respective files: `rocks/adt/lua/adt/orderedmap.lua` and `rocks/cmd/lua/cmd/cat.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

## 6. Invalidation

### Scenario 6.1: Adding a module to a rockspec updates resolution
- **Setup**: Project with `rocks/adt/adt-1.0-1.rockspec` already discovered. The file `rocks/adt/lua/adt/newmodule.lua` exists but is not in `build.modules`.
- **Steps**:
  1. Edit the rockspec to add `["adt.newmodule"] = "lua/adt/newmodule.lua"` to `build.modules`.
  2. Save the rockspec.
  3. Wait for re-indexing.
  4. Ctrl+click on `require("adt.newmodule")` in a Lua file.
- **Expected**: Navigation resolves to `rocks/adt/lua/adt/newmodule.lua`.
- **Result**: ⬜ Pass / ⬜ Fail

## 7. Graceful Degradation

### Scenario 7.1: No Lua interpreter configured
- **Setup**: Remove or un-configure the Lua interpreter in project settings.
- **Steps**:
  1. Open a project with rockspecs.
  2. Observe IDE log and behavior.
- **Expected**: No errors thrown to the user. Rockspec patterns are empty (log warning). User-configured source paths still work. IDE is not degraded beyond the missing rockspec resolution.
- **Result**: ⬜ Pass / ⬜ Fail
