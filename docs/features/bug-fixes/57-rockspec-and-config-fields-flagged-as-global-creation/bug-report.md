---
id: "BUG-57"
title: "Rockspec fields (and likely .luacheckrc / .busted settings) are flagged by the global-creation inspection"
type: "bug"
parent_id: "BUG"
status: "done"
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
  `package` is exempt only because it is a standard global. Confirmed: the warnings read
  *Global creation 'version'* and so on.

## 3. Fix Strategy
`LuaGlobalCreationInspection` returns an empty visitor for a file that any registered
`LuaSchemaFileProvider` claims (`LuaSchemaProviderFactory.EP_NAME`). Skipping only the keys a schema
declares was not needed: the rockspec schemas set `additionalProperties: false`, so
`LuaJsonSchemaCompliance` already reports a misspelled field, while the luacheck and busted schemas
allow arbitrary top-level keys by design.

## 4. Test Strategy
`LuaGlobalCreationConfigFileTest`: the same two assignments are flagged in a `.lua` file and not in a
`.rockspec`, `.luacheckrc` or `.busted` file. With the exemption disabled, the three config-file
cases fail.
