---
id: "BUG-554"
title: "A method's optional `@param name?` is required in its declared signature, so every call that omits it is *Too few arguments*"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-554: A method's optional `---@param name?` is required in its declared signature

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
---@class C
local C = {}
---@param a string
---@param b? string
function C:m(a, b) end
---@type C
local c = C
c:m("x")
```

## 2. Expected vs Actual
- **Expected**: no arity diagnostic: `b` is declared optional, so one argument is enough.
- **Actual**: `8: Too few arguments: expected at least 2, got 1` (reported twice).

## 3. Context
- **Corpus effect**: net +52 `LuaTypeAssignability` hits in `lazynvim.baseline`: 140 *Too few arguments* added, 60 removed, and 28 *Too many arguments* removed (the last is [[BUG-502]]'s intended effect). The added hits appeared once [[BUG-502]] made `---@param opts? {indent?: number}` a real `@param`. Example: `view/render.lua:175` `self:append("Tasks: ", "LazyH2")` against `Text:append(str, hl?, opts?)` ("expected at least 3, got 2").
- **Possibly relevant, not established**: `LuaCatsDeclarations.paramTypes` returns name → type only, and `LuaTypeManagerImpl.funcTypeFromStub` builds `LuaParameter`s from it with no optional flag.
