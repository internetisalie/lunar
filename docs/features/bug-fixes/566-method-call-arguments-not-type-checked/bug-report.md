---
id: "BUG-566"
title: "A method call's argument types are never checked against the method's declared `@param` types"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-566: A method call's arguments are not type-checked against its declared parameters

Found while probing an explicit `nil` against an optional method parameter during [[BUG-554]].
Observed only; the reproduction is from a throwaway probe and the root cause is not investigated.

## 1. Reproduction

Light fixture (`IndexedBasePlatformTestCase`), `LuaTypeAssignabilityInspection` enabled,
`configureByText`, `doHighlighting()`, on the `bug-554` branch with the BUG-554 fix applied.

```lua
---@class C
local C = {}
---@param a string
---@param b? string
function C:m(a, b) end
---@type C
local c = C
c:m("x", 1)
c:m(1, "y")

---@param a string
---@param b? string
local function g(a, b) end
g("x", 1)
```
→ no hit on either `c:m` call; `g("x", 1)` reports `number is not assignable to`.

## 2. Expected vs Actual
- **Expected**: `c:m("x", 1)` and `c:m(1, "y")` report the mismatched argument, as the free
  function `g` does.
- **Actual**: no hit. Arity is checked on method calls ([[BUG-554]]), argument types are not.

## 3. Context
- Fixing this will raise `inspection.LuaTypeAssignability` on annotated corpus members
  (`lazynvim`), possibly by many hits; each rise needs triage like the LuaCATS grammar batch's.
- Not established: whether dot-called members (`C.f(x)`) and `---@field m fun(...)` members are
  affected too.
