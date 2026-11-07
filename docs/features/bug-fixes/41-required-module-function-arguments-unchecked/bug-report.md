---
id: "BUG-41"
title: "Arguments to a function or method of a `require`d project module are not type-checked"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-41: Arguments to a function or method of a `require`d project module are not type-checked

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction

Not reduced to a light fixture: the route needs `require` resolved through `sourcePath`. Corpus sites,
lazynvim (target `LUAJIT:2.1`, `sourcePath` = the checkout's `lua/?.lua;lua/?/init.lua`), each edit silent:

| File:line | Call | Edit |
|---|---|---|
| `lua/lazy/core/handler/ft.lua:12` | `Loader.ftdetect(plugin.dir)`, `Loader = require("lazy.core.loader")`, declared `string` | `plugin.dir` → `true` |
| `lua/lazy/view/render.lua:154` | `self:append(title, "LazyButtonActive", …)`, inherited `Text:append` (`---@class LazyRender:Text`, declared in `text.lua`), declared `string` | `title` → `true` |
| `lua/lazy/core/loader.lua:447` | `Util.walk(dir, function(path, name, t) … end)`, declared `fun(path: string, name:string, type:FileType)` | the function → `42` |

## 2. Expected vs Actual Behavior
- **Expected**: *boolean is not assignable to string* (or *integer is not assignable to fun(…)*) at the
  argument.
- **Actual**: nothing reported. Audit: 0 of 24 measured non-arity sites on `PROJECT_FILE`/`CHECKOUT_FILE`
  `MEMBER` reported, and 3 of 8 on `PROJECT_FILE` `METHOD`. A global table member in another file
  (`U = {}`, `function U.f(s)`, `U.f(true)`) and a global class's method (`Kk:m(true)`) do report in a light-fixture probe (`28dfb5a2`, since deleted),
  so the difference is how the module is reached, not that it is in another file.

## 3. Context / Environment
- **Lua Version**: LuaJIT 2.1 (lazynvim); `CHECKOUT_FILE` sites are modules found through `sourcePath` in
  the on-disk checkout rather than the project copy.
- **Relevant Files**: not established.
- **Other Notes**: `arg.array` cells on this route read silent rather than unmeasurable, because neither the
  wrong value nor the `{}` control raises anything here ([[BUG-481]]). Cells: `arg.array`/`arg.optional`/
  `arg.primitive` × `PROJECT_FILE`/`CHECKOUT_FILE` `MEMBER`; `arg.function` × `PROJECT_FILE` `MEMBER`;
  `arg.function`/`arg.optional`/`arg.primitive` × `PROJECT_FILE` `METHOD`.
