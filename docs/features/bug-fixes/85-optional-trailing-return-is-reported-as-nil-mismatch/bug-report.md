---
id: "BUG-85"
title: "`return 1` in a function declared `@return number, number?` is reported as `nil` not assignable to `number`"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-85: `return 1` in a function declared `@return number, number?` is reported as `nil` not assignable to `number`

Found by the MAINT-55 conformance suite: 1 case (rule `return.count`, observed `false-report`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-return-value-004` (`rule=return.count source=missing-return-value.lua#4`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@return number, number?
function F()
    return 1
end
```

Cases in this group: `missing-return-value-004`.

## 2. Expected vs Actual Behavior
- **Expected**: No report: the second return is optional.
- **Actual**: `nil value is not assignable to number`, spanning the whole function (lines 2-4).
- **Recorded for `missing-return-value-004`**: findings `2-4:nil value is not assignable to number`; conformance lines none must be reported.

## 3. Related
[[BUG-91]] reports the same shape for a single optional return (`---@return number?`).

## Root Cause
One cause for both. `LuaTypeGraphBridge.injectReturnAnnotations` (`lang/psi/types/LuaTypeGraphBridge.kt`) turns each `@return` descriptor into a declared-demand `UseNode` of exactly `desc.argType`. Two things that widen what a position may return never reach it:
- **`T?`**: the grammar stops a tag's top-level type before `?` (`luacats.bnf`, `optionalType` comment, BUG-544/555), so the `?` opens `returnDescription`, not `argType`. The bridge reads only `argType.text`, so `number?` becomes a demand of plain `number`. A returned or implicit `nil` is then reported as `nil value is not assignable to number` (BUG-91 `-003`; BUG-85 `-004`, where the omitted second return is the implicit `nil`). `@param` already handles this: `LuaTypeAlgebra.parameterDemand` (BUG-552).
- **`@overload`**: `overloadTagList` is never read in `lang/psi/types/`, apart from `countsArguments`, so an overload's return type adds nothing to the demand (BUG-91 `-012`, `-015`).

## Fix Strategy
- Add `LuaCatsDeclarations.isOptionalReturn(descriptor)`, reading the `?` from `returnDescription`.
- Add `LuaTypeAlgebra.returnDemand(declared, isOptional, overloadReturns)`, the return counterpart of `parameterDemand`: the declared type, plus `nil` when optional, plus each overload's first return type. A generic `T` is left alone.
- `injectReturnAnnotations` uses it for the `UseNode` only. The declared value edge is unchanged, so what callers see as the function's return type does not change. Overloads widen position 0 only, since a signature's later returns have no declared `@return` node to widen.

## Test Strategy
`LuaOptionalAndOverloadedReturnTest` (light fixture, reads the snapshot errors the way the conformance runner does): explicit `return nil` for `number?`, omitted trailing `number?`, and an overload `boolean` return are silent; `true` for `number?`, `nil` for `number`, and `"s"` outside an overload are still reported. The three silent cases fail before the fix. The MAINT-55 conformance cases `missing-return-value-004`, `return-type-mismatch-003`, `-012`, `-015` move `false-report` to `agree`.
