---
id: "BUG-55"
title: "The packages browser says \"No project rock tree\" but gives no way to create one"
type: "bug"
parent_id: "BUG"
status: "todo"
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
