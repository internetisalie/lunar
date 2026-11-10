---
id: "BUG-86"
title: "A wrong primitive argument (`f(true)` for `number`) is reported on the callee's parameter declaration instead of the call"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-86: A wrong primitive argument (`f(true)` for `number`) is reported on the callee's parameter declaration instead of the call

Found by the MAINT-55 conformance suite: 2 cases (rule `arg.primitive`, observed `misplaced`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `param-type-mismatch-001` (`rule=arg source=param-type-mismatch.lua#1`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@param x number
local function f(x) end

f(true) -- E
```

Cases in this group: `param-type-mismatch-001`, `param-type-mismatch-018`.

## 2. Expected vs Actual Behavior
- **Expected**: `f(true)` on line 4 gets the report.
- **Actual**: The report is on line 2 (the `local function f(x)` declaration): `boolean is not assignable to number`.
- **Recorded for `param-type-mismatch-001`**: findings `2-2:boolean is not assignable to number`; conformance lines [4] must be reported.

## 3. Related
[[BUG-77]] shows the same anchor on an assignment to a typed local.
