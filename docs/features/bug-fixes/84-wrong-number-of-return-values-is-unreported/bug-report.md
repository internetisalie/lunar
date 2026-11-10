---
id: "BUG-84"
title: "A `return` with too few or too many values for the declared returns (`return` for `fun():number`; `return 1, true`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-84: A `return` with too few or too many values for the declared returns (`return` for `fun():number`; `return 1, true`) is not reported

Found by the MAINT-55 conformance suite: 6 cases (rule `return.count`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-return-value-001` (`rule=return.count source=missing-return-value.lua#1`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@type fun():number
local function f()
    return -- E
end
```

Cases in this group: `missing-return-value-001`, `missing-return-value-002`, `redundant-return-value-001`, `redundant-return-value-002`, `redundant-return-value-003`, `redundant-return-value-004`.

## 2. Expected vs Actual Behavior
- **Expected**: The `return` statement is reported (LuaLS `missing-return-value` / `redundant-return-value`).
- **Actual**: No finding, for every case in the group.
- **Recorded for `missing-return-value-001`**: no findings; conformance lines [3] must be reported.
