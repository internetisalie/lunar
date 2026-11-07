---
id: "BUG-37"
title: "A generic class's type parameter is not recognised inside an in-file method's annotations"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-37: A generic class's type parameter is not recognised inside an in-file method's annotations

Remainder of [[TYPE-14]]'s TYPE-14-01 (requirements status row): the class head's parameters parse
in its `@field` types, its parent types and its methods' call templates, but not where
`LuaTypeGraphBridge` builds an in-file method's own signature, because the bridge does not receive
the class's names there (design §4.8, Phase 5 note).

## 1. Reproduction
Not yet measured; this is the shape the design note describes.
```lua
---@class Box<T>
---@field value T
local Box = {}
---@param v T
function Box:set(v)
  local x = v
end
```

## 2. Expected vs Actual Behavior
- **Expected**: inside `Box:set`, `v` (and `x`) is the class parameter `T`, a generic, not a class
  named `T`.
- **Actual**: to be measured. The design note predicts `T` is read as a plain type name there.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaTypeGraphBridge` (where an in-file function's `@param`/`@return` are
  injected), `LuaCatsDeclarations.classTypeParameterNames`.
- **Other Notes**: related to [[BUG-32]] (a method using only its class's parameter on an
  instantiation). Measure before fixing; it may share a fix.
