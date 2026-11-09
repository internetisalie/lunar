---
id: "BUG-70"
title: "A truthiness or early-return guard does not narrow away nil"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-70: A truthiness or early-return guard does not narrow away nil

Found by MAINT-55 DR-07 (2026-11-09), which switched Lunar's possibly-nil check on `.f`, `:m` and calls to LuaLS's `need-check-nil` rule and swept the corpora. Lunar ships with that check off (MAINT-55 deviation `uncertain-nil`); this defect is one reason it cannot be turned on, and fixing it is a precondition for re-measuring.

## 1. Reproduction
```lua
---@type table?
local t
if t then print(t.x) end
```
Corpus sites: `luarocks/src/luarocks/fetch/git.lua:71` (`pd:read` after `if not pd then return nil end`), `zerobrane/src/editor/menu_edit.lua:196` (`editor and editor:GetLength()`).

## 2. Expected vs Actual Behavior
- **Expected**: Inside `if t then`, after `if not t then return end`, and on the right of `t and …`, `t` is not nil.
- **Actual**: `t` keeps its nil arm. Only `x ~= nil` and `type(x) ==` narrow it (DR-07 probe `PROBE-P3`).
