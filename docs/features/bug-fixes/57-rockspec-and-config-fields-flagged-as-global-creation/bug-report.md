---
id: "BUG-57"
title: "Rockspec fields (and likely .luacheckrc / .busted settings) are flagged by the global-creation inspection"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-57: Rockspec fields are flagged by the global-creation inspection

## 1. Reproduction
Open `thirdparty/vendored_rock/vendored_rock-1.0-1.rockspec` in `~/Documents/src/lua/test`:
```lua
package = "vendored_rock"
version = "1.0-1"
source = { url = "" }
dependencies = { "lua >= 5.1" }
build = { type = "builtin", modules = { ["vendored_rock"] = "vendored_rock.lua" } }
```
There are 4 warnings, on `version`, `source`, `dependencies` and `build`, and none on `package`.

## 2. Expected vs Actual Behavior
- **Expected**: top-level assignments are how a rockspec, a `.luacheckrc` and a `.busted` file declare
  their content, so none of them warns.
- **Actual**: the warnings match `LuaGlobalCreationInspection`, whose only exemptions are standard
  globals, configured names and underscore-prefixed names (`LuaGlobalCreationInspection.kt:99-101`).
  `package` is exempt only because it is a standard global. This was inferred from the pattern, not
  confirmed by hovering; confirm the inspection's name first.

## 3. Fix Strategy (to be confirmed by plan-bug)
Skip files that the schema providers already identify as declarative config (`RockspecSchemaProvider`,
`LuacheckrcSchemaProvider`, `BustedSchemaProvider`). Better still, skip assignments to the keys their
schema declares, so that a misspelled field (`dependecies`) still warns.
