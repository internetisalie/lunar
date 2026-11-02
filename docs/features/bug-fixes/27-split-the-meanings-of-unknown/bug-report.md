---
id: "BUG-27"
title: "`LuaPrimitiveType.UNKNOWN` carries several meanings, so no producer can be re-spelled alone"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-27: `LuaPrimitiveType.UNKNOWN` carries several meanings, so no producer can be re-spelled alone

A chore, found while planning [[MAINT-52]] (its Risk 1.1).

## 1. Reproduction

`LuaPrimitiveType.UNKNOWN` is what each of these yields, and `LuaGraphType.fromLuaType` maps it to
`LuaGraphType.Undefined` in every case:

```lua
---@param a Unres          -- an unresolved type name
---@param b unknown        -- a written `unknown`
local function f(a, b) end
function noret() end      -- a function with no declared return (funcTypeFromStub)
```

The round trip of `Undefined` through `graphTypeToLuaType` produces it as well.

## 2. Expected vs Actual Behavior
- **Expected**: "cannot model" (an unresolved name, a written `unknown`) and "nothing known" (an
  absent declared return, an uninferred value) are distinct, so a producer of the first can be
  spelled `any` without changing the second.
- **Actual**: one constant covers all of them. MAINT-52 measured mapping `UNKNOWN` to `any` in
  `fromLuaType` (its variant V1, DR-05): lazynvim `inspection.LuaTypeAssignability` moved 78 → 80,
  three golden tests failed, and a generic method's diagnostics changed.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaPrimitiveType.UNKNOWN`; `LuaGraphType.fromLuaType`;
  `LuaTypeManagerImpl.resolveType` and `funcTypeFromStub`.
- **Other Notes**: a prerequisite for spelling "cannot model" as `any` at the producers.
