---
id: "BUG-92"
title: "`return true` inside a `@type fun():number` local function is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-92: `return true` inside a `@type fun():number` local function is not reported

Found by the MAINT-55 conformance suite: 1 case (rule `return.mismatch`, observed `missed`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `return-type-mismatch-016` (`rule=return.mismatch source=return-type-mismatch.lua#16`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@type fun():number
local function f()
    return true -- E
end
```

Cases in this group: `return-type-mismatch-016`.

## 2. Expected vs Actual Behavior
- **Expected**: `return true` (line 3) is reported as not assignable to `number`.
- **Actual**: No finding.
- **Recorded for `return-type-mismatch-016`**: no findings; conformance lines [3] must be reported.
