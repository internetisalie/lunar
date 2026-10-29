---
id: "BUG-15"
title: "A bare function type demands a function with no parameters"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-15: A bare `function` type demands a function with no parameters

Found while planning [[BUG-14]]. Observed only; root cause not investigated.

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
