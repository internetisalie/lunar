---
id: "BUG-76"
title: "A wrong value in a table constructor assigned to a typed local (`local t = { x = true }` for `{x: integer}`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-76: A wrong value in a table constructor assigned to a typed local (`local t = { x = true }` for `{x: integer}`) is not reported

Found by the MAINT-55 conformance suite: 6 cases (rule `assign.local`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `assign-type-mismatch-008` (`rule=assign.local source=assign-type-mismatch.lua#8`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class A
---@field x integer

---@type A
local t = {
    x = true -- E
}
```

Cases in this group: `assign-type-mismatch-008`, `assign-type-mismatch-028`, `assign-type-mismatch-032`, `assign-type-mismatch-038`, `assign-type-mismatch-041`, `assign-type-mismatch-048`.

## 2. Expected vs Actual Behavior
- **Expected**: Each marked field value in the constructor is reported as not assignable to its declared field type.
- **Actual**: No finding at all, for every case in the group.
- **Recorded for `assign-type-mismatch-008`**: no findings; conformance lines [6] must be reported.
