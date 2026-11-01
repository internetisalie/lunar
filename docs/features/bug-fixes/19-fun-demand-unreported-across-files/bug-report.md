---
id: "BUG-19"
title: "A `fun()` parameter demand is not reported when the callee is in another file"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-19: A `fun()` parameter demand is not reported when the callee is in another file

Found while fixing [[BUG-15]].

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

## 4. Root Cause
Not specific to other files, and the bug's title is too narrow. A global declared elsewhere is a
declaration-typed callee, so `LuaTypesVisitor.demandDeclaredParams` checks its arguments.
`carriesGraphNodes` skipped any demand holding a function or table, because a structural check wires
edges into the type's own nodes, and those nodes belong to a seed shared by every call in the file
(BUG-425). A `fun()` demand was therefore never made.

## 5. Fix Strategy
`detachedDemand` copies a function type onto fresh parameter nodes for each argument. A parameter
whose type itself holds nodes stays untyped in the copy.

Left as they were: table demands; a callback's return type, because `fun()` has a `nil` return node
that a callback's return value must not break; `take(1, 2)`, an extra argument to a declared global;
and the method rows in §2.

## 6. Test Strategy
`LuaCrossFileFunctionDemandTest` covers a function, a number and a bare demand, with controls for a
scalar demand, an omitted optional and an untagged global. One test checks that a call does not
change the signature for the next.

The sweep's `LuaTypeAssignability` count for zerobrane rises from 4 to 7. All three new hits are one
false positive, filed as [[BUG-21]].
