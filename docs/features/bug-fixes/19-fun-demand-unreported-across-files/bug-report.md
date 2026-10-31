---
id: "BUG-19"
title: "A `fun()` parameter demand is not reported when the callee is in another file"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-19: A `fun()` parameter demand is not reported when the callee is in another file

Found while fixing [[BUG-15]]. Observed only; root cause not investigated.

## 1. Reproduction

`defs.lua` holds `---@param fn fun()` and `function take(fn) end`; another file's
`---@param a string`, `local function f(a) end`, `take(f)` reports nothing.

Run in an `IndexedBasePlatformTestCase` with `LuaTypeAssignabilityInspection` enabled, `defs.lua` from
`myFixture.addFileToProject`, the caller from `configureByText`, reading
`LuaTypesSnapshot.forFile(file).getErrors()` on `2382558c`.

## 2. Expected vs Actual
- **Expected**: the same report as in one file, *Too few arguments: expected at least 1, got 0*.
- **Actual**: nothing.

| Callee | Demand | Argument | Same file | Other file |
|---|---|---|---|---|
| global `take` | `fun()` | `f(a)` | *Too few arguments: expected at least 1, got 0* | **nothing** |
| global `take` | `fun()` | `1` | *number is not assignable to fun()* | **nothing** |
| global `take` | `fun()` | `function(a) end` | not measured | **nothing** |
| global `take` | `number` | `'x'` | *string is not assignable to number* | *string is not assignable to number* |
| method `d:on` | `fun()` | `f(a)` | nothing | nothing |
| method `d:on` | `fun()` | `1` | nothing, with `local D` | nothing |

The `number` row shows that the callee resolves and its `---@param` is read across files, so the
global case is specific to the function-typed demand. It also covers more than function arguments,
since `take(1)` is silent as well.

The method rows are silent in one file too, so that form is not a cross-file effect, despite this bug's
title. [[BUG-566]] (done) checks a method's arguments, and these rows were not reconciled with it.

## 3. Context
- A cross-file test of a bare `function` demand proves nothing today. [[BUG-15]] established that any
  function meets a bare demand, but across files `---@param fn function` with `take(f)` and with
  `take(1)` are both silent, so a silent result there cannot show that the bare demand was evaluated.
- `take(1, 2)` against a one-parameter callee in another file is silent as well. It was seen once and
  not compared with the same-file case.
