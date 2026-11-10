---
id: "BUG-77"
title: "A wrong value assigned to a typed local is reported on the local's declaration line instead of the assignment"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-77: A wrong value assigned to a typed local is reported on the local's declaration line instead of the assignment

Found by the MAINT-55 conformance suite: 5 cases (rule `cast.local`, observed `misplaced`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `cast-local-type-002` (`rule=cast.local source=cast-local-type.lua#2`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@type integer
local x

x = true -- E
```

Cases in this group: `cast-local-type-002`, `cast-local-type-009`, `cast-local-type-017`, `cast-local-type-027`, `cast-local-type-037`.

## 2. Expected vs Actual Behavior
- **Expected**: `x = true` on line 4 gets the report.
- **Actual**: The report is on line 2 (the `local x` declaration): `boolean is not assignable to number`.
- **Recorded for `cast-local-type-002`**: findings `2-2:boolean is not assignable to number`; conformance lines [4] must be reported.

## 3. Related
[[BUG-86]] shows the same anchor on a call argument (the report lands on the callee's declaration).
