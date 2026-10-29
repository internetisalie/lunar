---
id: "BUG-16"
title: "An unresolved member of a union parameter type is dropped from the demand"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-16: An unresolved member of a union parameter type is dropped from the demand

Found while planning [[BUG-11]]. Observed only; root cause not investigated.

## 1. Reproduction

In one file, with no definition of `Unres.ns` anywhere:

```lua
---@param opts? Unres.ns | {buffer: boolean}
local function f(opts) end
f({ pattern = "x" })
```

## 2. Expected vs Actual
- **Expected**: no diagnostic. `Unres.ns` is unknown, so the argument may be one, and Lunar cannot
  tell that it is wrong. An unresolved type name on its own (`---@param opts? Unres.ns`) is already
  silent.
- **Actual**: *{ ... } is not assignable to union nil | { ... }* at ERROR, reported twice on line 2.
  The demand is the union without its unresolved member, so it is stricter than what was written.

## 3. Context
- Reproduced on main `8d8ab1b3` in the [[BUG-11]] planner's probe (case `L2-local-opt-unres-union`).
- With [[BUG-566]] applied, the required form (`---@param opts Unres.ns | {buffer: boolean}`) also
  reports *Missing required field 'buffer'* (case `L4-local-req-unres-union`). That case was not run
  on main.
- No lazynvim corpus hit has this shape; vim stubs that do not resolve would produce it.
- [[BUG-11]] is the cross-file, optional-parameter form, where the whole type becomes unknown
  instead. That bug lives in BUG-566's code; this one is on main.
