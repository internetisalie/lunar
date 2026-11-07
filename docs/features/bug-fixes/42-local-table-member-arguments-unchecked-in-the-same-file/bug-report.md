---
id: "BUG-42"
title: "Arguments to a function declared on a local table in the same file are not checked"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-42: Arguments to a function declared on a local table in the same file are not checked

Found by the [[MAINT-54]] false-negative audit (harness `28dfb5a2`, libvirt builder; cells and sites in [audit-results.md](../../maint/54-type-false-negative-audit/audit-results.md)). "Silent" means no `ERROR`/`WARNING` from `LuaTypesSnapshot.forFile(file).getErrors()` once `HYPOTHESIS` entries are dropped, which is what both type inspections show.

## 1. Reproduction
The snippets were run in a throwaway light-fixture probe (`IndexedBasePlatformTestCase`, `addFileToProject`, every error printed with its tier) on `28dfb5a2`, then deleted.

```lua
local M = {}
---@param s string
function M.f(s) end
M.f(true)   -- no diagnostic at any tier
M.f()       -- no diagnostic at any tier
---@param s string
function M:g(s) end
M:g(true)   -- no diagnostic at any tier
```
Corpus sites (audit edit, silent): lazynvim `lua/lazy/core/util.lua:266` `M.normname(topmod)` → `M.normname(true)`;
lazynvim `lua/lazy/core/meta.lua:146` `self:del(meta.name)` → `self:del(true)`.

## 2. Expected vs Actual Behavior
- **Expected**: the type and arity diagnostics a `local function` in the same file gets.
- **Actual**: nothing at any tier in the probe. In the corpus some sites on this route do report
  (lazynvim `lua/lazy/core/loader.lua:139` `M.packadd(path)` → `M.packadd(true)` reports at the argument), so
  the route is partly checked: audit 11 of 36 measured `SAME_FILE` `MEMBER`/`METHOD` sites reported.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: not established. MAINT-54 DR-01b measured the same: `local M = {} … function M.g(s) end
  M.g(true)`, no error.
- **Other Notes**: Cells: `SAME_FILE` `MEMBER` × `arg.array`, `arg.class`, `arg.function`, `arg.optional`,
  `arg.primitive`, `arity.tooFew`, `arity.tooMany`; `SAME_FILE` `METHOD` × `arg.class`, `arg.optional`,
  `arg.primitive`.
