---
id: "BUG-83"
title: "A function annotated to return a value, whose end is reachable without a `return`, is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-83: A function annotated to return a value, whose end is reachable without a `return`, is not reported

Found by the MAINT-55 conformance suite: 8 cases (rule `return.missing`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-return-001` (`rule=return.missing source=missing-return.lua#1`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@type fun():number
local function f()
end -- E
```

Cases in this group: `missing-return-001`, `missing-return-004`, `missing-return-005`, `missing-return-006`, `missing-return-008`, `missing-return-010`, `missing-return-011`, `missing-return-015`.

## 2. Expected vs Actual Behavior
- **Expected**: The function's closing `end` is reported (LuaLS `missing-return`), for a `@type fun():number` local, and for a branch that returns only under `if`/`elseif`.
- **Actual**: No finding, for every case in the group.
- **Recorded for `missing-return-001`**: no findings; conformance lines [3] must be reported.
