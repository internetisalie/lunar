---
id: "BUG-8"
title: "A dot call of a colon method with an explicit self matches self to the first @param"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-8: A dot call of a colon method with an explicit `self` misaligns its arguments

Found while triaging [[BUG-566]]'s corpus run. Observed only; root cause not investigated. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/core/handler/ft.lua:10`, inside `function M:add(plugin)`:

```lua
---@param plugin LazyPlugin
function M:add(plugin)          -- the parent's LazyHandler:add has the same signature
  self.super.add(self, plugin)  -- dot call, receiver passed explicitly
```

## 2. Expected vs Actual
- **Expected**: `self` fills the method's implicit `self` and `plugin` fills `@param plugin`. No arity or argument diagnostic.
- **Actual**: the arity check reports *Too many arguments: expected at most 1, got 2*. With [[BUG-566]]'s fix applied, `self` is also type-checked against `plugin: LazyPlugin` (3 hits in lazynvim: ft.lua:5, :9, :10).

## 3. Context
- Arity misfires on `main` today; the argument-type hits appear only with [[BUG-566]]'s fix, which promotes them from hypothesis to error.
- Not established: whether `C.m(obj, x)` on a colon-declared method fails the same way outside a `super` chain.
