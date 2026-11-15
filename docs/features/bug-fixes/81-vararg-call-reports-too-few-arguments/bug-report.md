---
id: "BUG-81"
title: "A call that passes `...` (or fewer arguments to optional trailing parameters) is reported as too few arguments"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-81: A call that passes `...` (or fewer arguments to optional trailing parameters) is reported as too few arguments

Found by the MAINT-55 conformance suite: 3 cases (rule `arity.tooFew`, observed `false-report`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-parameter-005` (`rule=arity.tooFew source=missing-parameter.lua#5`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@param a integer
---@param b integer
local function f(a, b)
end

f(...)
```

Cases in this group: `missing-parameter-005`, `missing-parameter-006`, `missing-parameter-009`.

## 2. Expected vs Actual Behavior
- **Expected**: No report: `f(...)` supplies an unknown number of arguments; `x(1)` leaves a `integer|nil` parameter unset.
- **Actual**: `Too few arguments: expected at least 2, got 1` on the call line.
- **Recorded for `missing-parameter-005`**: findings `6-6:Too few arguments: expected at least 2, got 1`; conformance lines none must be reported.

## Root Cause
Two independent gaps in the arity check (`LuaTypeGraph.reportArity`, `LuaTypeGraph.kt`), which compares `use.params.size` (one entry per argument expression) with the callee's required count:

1. **A multi-value last argument counts as one.** The call demand is built from `argExprs` in `LuaTypesVisitor` (`visitFuncCall`, the `callDemand` literal) and in `reportDeclaredArity` for a cross-file callee. `...` and a trailing call (`f(g())`, cases 005 and 006) expand to any number of values in Lua, but each contributed exactly one parameter, so `provided < minParams` fired.
2. **`T|nil` was not optional.** `LuaCatsDeclarations.isOptionalParam` recognised only `p? T` and `p T?`. Case 009 writes `---@param b integer|nil`, which the visitor (`isOptional` of the matching `@param`) therefore left required, and the cross-file `ParamDecl.isOptional` likewise.

## Fix Strategy
- `LuaGraphType.Function.openEnded`, set on a call demand by `LuaTypesVisitor.suppliesManyValues(lastArg)` (last argument is a `LuaFuncCall` or an `...` terminal); `reportArity` skips only the too-few branch for an open-ended demand. Too-many and every other check are untouched; a parenthesised call `f((g()))` is a single value and is still counted.
- `isOptionalParam` also accepts a top-level `nil` arm of the declared union (read from the `LuaCatsUnionType` PSI, so `fun(): string|nil` and `nil[]` do not count). The stub version moves 9 to 10 because `ParamDecl.isOptional` is stored in stubs.

## Test Strategy
`LuaOpenEndedCallArityTest` (light fixture, each case in the open file and cross-file): `f(...)`, `f(g())` and `h(1)` against `integer|nil` are silent; controls `f()`, `f(1)`, `f((g()))` and `h()` are still reported. The conformance cases `missing-parameter-005/006/009` move `false-report` to `agree`.
