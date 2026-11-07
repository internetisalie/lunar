---
id: "BUG-31"
title: "A generic function called with `.` on a class is not instantiated per call"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-31: A generic function called with `.` on a class is not instantiated per call

Remainder of [[TYPE-14]]'s TYPE-14-04, recorded there as risks-and-gaps Gap 2.10.

## 1. Reproduction
```lua
---@class Box
local Box = {}
---@generic T
---@param v T
---@return T
function Box.make(v) return v end
local m = Box.make(1)
```

## 2. Expected vs Actual Behavior
- **Expected**: `m` is `number`, the same as the `:` form (`Box:echo(1)` is `number`).
- **Actual**: `m` is `T | number`. This holds both before and after TYPE-14 Phase 5 (Phase 5
  review probe). No diagnostic depends on it.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaTypesVisitor.visitFuncCall` and `calleeTemplate`.
- **Other Notes**: the visitor has no receiver node for a `.` call (`methodExpr` is null), and the
  callee is the member expression's own node. The `:` route through
  `LuaClassType.genericMethod` therefore does not apply as is. See
  `docs/features/type/14-generics-baseline/risks-and-gaps.md` Gap 2.10.
