---
id: "BUG-81"
title: "A call that passes `...` (or fewer arguments to optional trailing parameters) is reported as too few arguments"
type: "bug"
parent_id: "BUG"
status: "todo"
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
