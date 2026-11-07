---
id: "BUG-32"
title: "A method that uses only its class's type parameter is untyped on an instantiation"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-32: A method that uses only its class's type parameter is untyped on an instantiation

Remainder of [[TYPE-14]]'s TYPE-14-09, recorded there as risks-and-gaps Gap 2.11.

## 1. Reproduction
```lua
---@class Box<T>
---@field value T
local Box = {}
---@return T
function Box:get() end
---@type Box<string>
local b
local v = b.value
local g = b:get()
```

## 2. Expected vs Actual Behavior
- **Expected**: `g` is `string`, the same as `v`.
- **Actual**: `g` is `undefined`. `v` is `string`. The same happens in one file and across files,
  and through a subclass `Kid : Box<string>` with `k:get()`. `b:set(1)` against `---@param v T`
  reports nothing. No diagnostic depends on it (TYPE-14 Phase 5 review probe iv).

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaTypesVisitor.visitFuncCall` (the `containsGeneric` gate) and
  `LuaGenericSubstitution.instantiate`.
- **Other Notes**: see `docs/features/type/14-generics-baseline/risks-and-gaps.md` Gap 2.11.
