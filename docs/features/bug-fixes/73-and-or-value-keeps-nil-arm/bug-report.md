---
id: "BUG-73"
title: "An a and b or c value keeps a nil arm"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-73: An a and b or c value keeps a nil arm

Found by MAINT-55 DR-07 (2026-11-09), which switched Lunar's possibly-nil check on `.f`, `:m` and calls to LuaLS's `need-check-nil` rule and swept the corpora. Lunar ships with that check off (MAINT-55 deviation `uncertain-nil`); this defect is one reason it cannot be turned on, and fixing it is a precondition for re-measuring.

## 1. Reproduction
```lua
---@type string?
local s
local v = s and s:upper() or "default"
print(v:lower())
```
The snippet is reduced from DR-07's cause list and has not been run; confirm it reproduces before fixing.

## 2. Expected vs Actual Behavior
- **Expected**: `v` is `string`; the `or` default removes the nil.
- **Actual**: `v` carries a nil arm and `v:lower()` is reported as possibly nil. One of the causes DR-07 recorded among its false samples.
