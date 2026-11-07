---
id: "BUG-40"
title: "A `table`-, class- or record-typed parameter declared in another file accepts a function"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-40: A `table`-, class- or record-typed parameter declared in another file accepts a function

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
-- caller.lua, bundled stdlib callees: no diagnostic at any tier
for k in pairs(function() end) do end
table.insert(function() end, 1)
local s = coroutine.status(function() end)
```
```lua
-- d.lua
---@class Bx
---@field n number
Bx = {}
---@param t table
function gT(t) end
---@param b Bx
function gC(b) end
-- caller.lua: no diagnostic at any tier
gT(function() end)
gC(function() end)
```
Corpus sites (audit edit, silent): luacheck `src/luacheck/options.lua:235` `table.sort(new_fields, …)` →
`table.sort(function() end, …)`; lazynvim `lua/lazy/core/loader.lua:71` `Plugin.has_errors(plugin)` (`LazyPlugin`)
→ `Plugin.has_errors(function() end)`.

## 2. Expected vs Actual Behavior
- **Expected**: *fun() is not assignable to table* (or to the class) at the argument, as for a primitive
  parameter of the same callee (`pStr(true)` across files reports).
- **Actual**: nothing at any tier. Audit: 64 of 65 measured sites silent (one reported under another rule);
  `arg.class` reports 4 of 4 for a `local function` in the caller's file.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: the [type system survey](../../../type-system-survey.md)'s mechanism A names
  `detachedDemand`, which leaves a parameter whose type holds nodes untyped ([[BUG-19]] §5 "table demands").
  That is the survey's reading, not established here.
- **Other Notes**: `coroutine.status`'s `thread` parameter resolves as a class, so the audit counts it as
  `arg.class`. Cells: `arg.table` × `LIBRARY` `GLOBAL`/`MEMBER`, `PROJECT_FILE` `MEMBER`/`METHOD`; `arg.class` ×
  `LIBRARY`/`PROJECT_FILE`/`CHECKOUT_FILE` `MEMBER`, `PROJECT_FILE` `METHOD`; `arg.record` × `PROJECT_FILE` `METHOD`.
