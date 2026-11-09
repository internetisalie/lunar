---
id: "BUG-72"
title: "An unannotated parameter takes nil from a caller in the same file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-72: An unannotated parameter takes nil from a caller in the same file

Found by MAINT-55 DR-07 (2026-11-09), which switched Lunar's possibly-nil check on `.f`, `:m` and calls to LuaLS's `need-check-nil` rule and swept the corpora. Lunar ships with that check off (MAINT-55 deviation `uncertain-nil`); this defect is one reason it cannot be turned on, and fixing it is a precondition for re-measuring.

## 1. Reproduction
```lua
local function f(x) return x:upper() end
f(nil)
f("a")
```
The snippet is reduced from DR-07's cause list and has not been run; confirm it reproduces before fixing.

## 2. Expected vs Actual Behavior
- **Expected**: An unannotated parameter is `any` (LuaLS's rule), so `x:upper()` is not a possibly-nil use.
- **Actual**: The nil passed by an in-file caller flows into `x`, and the use is reported. One of the causes DR-07 recorded among its false samples.
