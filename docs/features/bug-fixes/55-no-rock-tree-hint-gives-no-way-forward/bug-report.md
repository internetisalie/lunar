---
id: "BUG-55"
title: "The packages browser says \"No project rock tree\" but gives no way to create one"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-55: The packages browser says "No project rock tree" but gives no way to create one

## 1. Reproduction
Open a project with no `lua_modules` or `.luarocks` directory (for example `~/Documents/src/lua/test`), then
open the *LuaRocks Packages* tool window. The strip reads "No project rock tree" and Install is disabled.

## 2. Expected vs Actual Behavior
- **Expected**: ROCKS-16 TC-04's hint, "No project rock tree; initialize a LuaRocks project", together
  with an action that creates the tree, equivalent to `luarocks init` in the project root, using the
  resolved LuaRocks.
- **Actual**: `LuaRocksBrowserPanel.refreshTreeStrip` (`LuaRocksBrowserPanel.kt:158-161`) shows only
  "No project rock tree", and offers no action. Lunar's only LuaRocks initialisation is the New
  Project generator (`LuaRocksProjectGenerator`), which cannot be applied to an existing project.

## 3. Root Cause
`LuaRocksBrowserPanel.refreshTreeStrip` and `PackageDetailPane`'s No-tree card only described the
missing tree. Nothing in the browser could create one: `LuaRocksInstallExecutor` ran only `install`
and `remove`, which both need an existing `--tree`.

## 4. Fix
- `LuaRocksInstallExecutor.initialize` runs `luarocks init` (`LuaRocksInstallCommand.buildInitArgs`)
  in the project root with the resolved LuaRocks, on a background task.
- `LuaRocksTreeInitializer.forProject` wraps it and refreshes the project directory in the VFS
  afterwards, so the created `lua_modules`, `.luarocks` and wrapper scripts appear.
- The tree strip shows an *Initialize LuaRocks project* link while no tree exists, and the No-tree
  card carries the same link. On success the strip re-reads the tree and the link hides.

`luarocks init` (3.8) also writes a rockspec template when the project has none, the `./lua` and
`./luarocks` wrapper scripts, and `.gitignore` entries. It has no options to skip them.

## 5. Test
`LuaRocksBrowserPanelInitTreeTest`: the link is offered with no tree, clicking it runs the
initializer and the strip then shows `Target tree: <base>/lua_modules` with the link hidden, and a
failed run leaves the link offered and enabled. Removing the strip refresh from the completion
callback turns the second test red.
