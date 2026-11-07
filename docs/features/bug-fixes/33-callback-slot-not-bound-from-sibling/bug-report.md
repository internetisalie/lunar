---
id: "BUG-33"
title: "A lambda passed to a generic callback slot does not take its parameter type from a sibling argument"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-33: A lambda passed to a generic callback slot does not take its parameter type from a sibling argument

This is [[TYPE-14]]'s TYPE-14-16, the LuaLS #1883 shape. It was not delivered and is recorded
there as risks-and-gaps Gap 2.12.

## 1. Reproduction
```lua
---@generic T
---@param fn fun(a: T)
---@param t T[]
---@return T[]
local function filter(fn, t) return t end
---@type string[]
local xs
local r = filter(function(a) end, xs)
```
The result is the same with the arguments swapped (`---@param t T[]` first), and with `filter` as a
global in another file. With `---@param fn fun(k: K, v: V)` and `t: table<string, number>`, `k`
and `v` behave the same way.

## 2. Expected vs Actual Behavior
- **Expected**: the lambda's `a` is `string`. For the two-parameter form, `k` is `string` and `v`
  is `number`.
- **Actual**: `a` is `T` (`k: K`, `v: V`). `r` is correctly `string[]`.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaTypesVisitor.propagateExpectedLambdaParams` / `seedLambdaParams`,
  `LuaGenericInstantiation`, and `LuaTypeGraph.checkFunctionCompatibility`.
- **Other Notes**: two attempts were measured. Their results are in
  `docs/features/type/14-generics-baseline/risks-and-gaps.md` Gap 2.12.
  - Seeding from the call's binding alone gives `string | T`.
  - Also skipping the shape check's placeholder edge into a binding-seeded parameter gives `string`
    in every listed shape. But it turns the case where only the lambda mentions `T`
    (`call(function(a) end)` with `---@param fn fun(a: T)`) from `T` into `undefined`.
