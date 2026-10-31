---
id: "BUG-15"
title: "A bare function type demands a function with no parameters"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-15: A bare `function` type demands a function with no parameters

Found while planning [[BUG-14]].

## 1. Reproduction

```lua
---@param fn function
local function take(fn) end

---@param a string
local function f(a) end

take(f)
```

## 2. Expected vs Actual
- **Expected**: no diagnostic. `function` names any function and says nothing about its parameters,
  so any function value is acceptable.
- **Actual**: *Too few arguments: expected at least 1, got 0*, reported at `fn` (line 2) and at `f`
  (line 7). This is the same result as declaring `fn` as `fun()`, a function that is called with no
  arguments.

The same report appears with `---@param fn fun()`, where it is arguably correct, since `fn` is
declared to be called with no arguments. Only the bare `function` case is the bug.

## 3. Context
- Reproduced on `2bfae0aa` (before [[BUG-8]]) and on `18618d8f` (after it), so neither BUG-8 nor
  [[BUG-14]] introduced it.
- With the parameter optional (`---@param a? string`) there is no diagnostic.
- The [[BUG-14]] planner's probe (`P1_plainReq_to_function`, `P2_plainOpt_to_function` and
  `P3_plainReq_to_funEmpty`) showed both the bare type and `fun()` becoming the same zero-parameter
  graph function, near `LuaGraphType.kt:262`. That is a lead, not a confirmed cause.

## 4. Root Cause
The bare type had no representation of its own. `LuaGraphType.fromLuaType` mapped
`LuaPrimitiveType.FUNCTION` to `Function(emptyList(), emptyList())` (`LuaGraphType.kt`), and
`TYPEOF_MAP` built the same value for `type(x) == "function"` (`LuaTypesVisitor.kt`). That value is
indistinguishable from a signature that really has no parameters, so `LuaTypeGraph`'s arity bounds
(`minArity`, `maxArity`, used by `checkFunctionCompatibility` and `isFunctionCompatible`) counted the
empty list as a statement about arity.

The defect is wider than the reproduction, because it applies to both sides of a function check.
Measured on `main` with `LuaBareFunctionTypeTest`, before any fix:

| Bare type is | Example | Result on `main` |
|---|---|---|
| the demand | `---@param fn function`, `take(f)` with `f(a, b, c)` | *Too few arguments: expected at least 3, got 0* |
| a member of a union demand | `---@param fn string\|function`, `take(f)` | *fun(a) is not assignable to union fun() \| string* |
| a field demand | `---@field cb function`, `{ cb = f }` | *Too few arguments*, plus `fun() \| fun(a)` mismatches |
| the value called | `---@param fn function`, `fn(1, 2, 3)` | *Too many arguments: expected at most 0, got 3* |
| the value passed | `---@type function` into `fun(a: string, b: string)` | *Too many arguments: expected at most 0, got 2* |
| a type-guard result | `if type(x) == "function" then x(1, 2) end` | *Too many arguments: expected at most 0, got 2* |
| carried across files | `---@return function`, `make()(1, 2)` | *Too many arguments: expected at most 0, got 2* |

`declaredSignature` cannot make the distinction: the bare type and the demand of a real zero-argument
call both have it false and both have no parameters, and that call must keep reporting.

The cross-file row has a second cause. `functionToLuaType` turned the graph value into
`LuaFunctionType(emptyList(), VOID)`, so the type came back from `fromLuaType` as an ordinary function
with no parameters that returns nil. Calling a `---@return function` value therefore gave a certain
`nil`, though `function` says nothing about what a call returns.

## 5. Fix Strategy
The invariant: **a bare `function` neither demands nor promises a parameter list.**

- Add `LuaGraphType.Function.anySignature`, built only by `Function.bare()`, used at both construction
  sites.
- `minArity` is 0 against a bare demand and `maxArity` is unbounded for a bare value. Both function
  checks read these, so the reporting and dry-run paths cannot differ. `alignedParams` already pairs
  nothing, because the bare side has no parameters.
- `functionToLuaType`'s caller maps a bare value back to `LuaPrimitiveType.FUNCTION`, so the type
  survives leaving the graph. A consequence is that hints and hover show `function`, not `fun()`.
- `displayName` prints `function`, so *number is not assignable to function* names the declared type.

`fun()`, `function() end` and a call with no arguments stay zero-parameter signatures. A non-function
value still fails a bare demand.

## 6. Test Strategy
`LuaBareFunctionTypeTest` reads messages from the graph, so an inferred-signature hypothesis counts as
well as a warning. It covers functions with no, one, many, inferred and vararg parameters, anonymous
functions, colon methods and `fun(...)` values against a bare demand; every row of the table in
section 4; and the controls: `fun()` against a required parameter, `fun()` called with an argument, a
zero-argument call and an inferred zero-argument demand against a required parameter, and a
non-function against a bare demand. `testResultOfCallingBareReturnIsNotNilAcrossFiles` pins the
return half of the second cause.

Three type goldens change, in 103 lines, all under `[elements]`; no `[errors]` or `[globals]` line
moves. Each line is a stub function that takes or returns `function`, such as `pairs`, `pcall` and
`load`. The lines that show the bare type read `function` where they read `fun()`, and the others keep
their text and change only the hash that covers that nested name. With the `displayName` and
`functionToLuaType` edits removed, the old goldens pass unchanged, so the change is naming only.

## 7. Corpus Expectations
The corpus was measured, not predicted. Sweeping with `Function.bare()` not setting the flag
reproduces the old baselines, and the only member that moves is `zerobrane`: 2406 findings become
2389, with 17 removed and none added. `zerobrane.baseline` is re-recorded to match.

| Baseline line | Old | New | Findings removed |
|---|---|---|---|
| `inspection.LuaTypeAssignability` | 18 | 4 | `findreplace.lua` 22, 377 (2), 490 (2), 494, 495, 499; `outline.lua` 387, 391, 400, 694 (2), 696 |
| `inspection.LuaReturnTypeMismatch` | 1 | absent | `outline.lua` 388 |
| `inspection.LuaSuspiciousConcatenation` | 3 | 1 | `menu_project.lua` 96, 136 |
| `symbol.LuaSuspiciousConcatenation.nil` | 3 | 1 | the same two findings |

The 15 assignability and return findings all come from one shape, which the `LuaTypes.kt` change
removes: both files iterate with `local files = coroutine.wrap(...)`, and the stub declares
`---@return function`. The result of `files()` was a certain `nil`, and it then flowed into
`self.curfilename` and `isInSubDir`. With that one line removed, these 15 return and the two below do
not. In the code the iterator returns file names, so all 15 were false reports.

The two `menu_project.lua` findings read `ide.config.default.interpreter`, which `config.lua:185` sets
to `'luadeb'`, so they were false reports too. They go away with the flag and do not depend on the
`LuaTypes.kt` change, but the path from the flag to that expression was not traced.
