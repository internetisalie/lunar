---
id: "BUG-26"
title: "A local assigned `cond and <unknown>` is shown with the type demanded of it"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-26: A local assigned `cond and <unknown>` is shown with the type demanded of it

Found while planning [[MAINT-52]] (its Gap 2.2). Related to [[BUG-553]], which is done.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), one file, with `wx` undefined:

```lua
local e = cond and wx.thing
---@param n number
local function g(n) end
g(e)
```

Read `LuaTypesSnapshot.forFile(file).getValueType(<nameRef e>).displayName()`.

## 2. Expected vs Actual Behavior
- **Expected**: an unknown type (`any`, or no type), because nothing the engine can model is
  assigned to `e`.
- **Actual**: `number`, the type `g` demands of its argument. Executed on `79380bf0` and on
  `7ec1adb7` (MAINT-52 risks-and-gaps, case c12, local `e`).

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: `LuaTypesVisitor.visitBinOpExpr` (`and`/`or`) and `LuaTypes.typeOf`, which shows
  an un-assigned variable's demand.
- **Other Notes**: the inlay hint and Quick Doc read the same type.
