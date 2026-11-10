---
id: "BUG-88"
title: "An argument outside a generic's constraint (`f(1)` for `T: string | boolean | table`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-88: An argument outside a generic's constraint (`f(1)` for `T: string | boolean | table`) is not reported

Found by the MAINT-55 conformance suite: 1 case (rule `arg.generic`, observed `missed`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `param-type-mismatch-009` (`rule=arg source=param-type-mismatch.lua#9`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@generic T: string | boolean | table
---@param x T
---@return T
local function f(x)
    return x
end

f(1) -- E
```

Cases in this group: `param-type-mismatch-009`.

## 2. Expected vs Actual Behavior
- **Expected**: `f(1)` is reported: `number` does not satisfy `T`'s constraint.
- **Actual**: No finding.
- **Recorded for `param-type-mismatch-009`**: no findings; conformance lines [8] must be reported.
