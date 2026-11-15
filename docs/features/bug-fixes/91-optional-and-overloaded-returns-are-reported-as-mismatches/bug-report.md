---
id: "BUG-91"
title: "`return nil` for `@return number?`, and `return false` where an `@overload` returns `boolean`, are reported as not assignable"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-91: `return nil` for `@return number?`, and `return false` where an `@overload` returns `boolean`, are reported as not assignable

Found by the MAINT-55 conformance suite: 3 cases (rule `return.mismatch`, observed `false-report`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `return-type-mismatch-003` (`rule=return.mismatch source=return-type-mismatch.lua#3`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@return number?
function F()
    return nil
end
```

Cases in this group: `return-type-mismatch-003`, `return-type-mismatch-012`, `return-type-mismatch-015`.

## 2. Expected vs Actual Behavior
- **Expected**: No report: `nil` is allowed by `number?`; the `boolean` return matches the overload.
- **Actual**: `nil value is not assignable to number` (return-type-mismatch-003), `boolean is not assignable to number` over the function body (return-type-mismatch-012, -015).
- **Recorded for `return-type-mismatch-003`**: findings `2-4:nil value is not assignable to number`; conformance lines none must be reported.

## 3. Related
[[BUG-85]] reports the same shape for an optional trailing return.

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
