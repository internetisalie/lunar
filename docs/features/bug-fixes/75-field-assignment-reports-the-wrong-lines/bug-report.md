---
id: "BUG-75"
title: "Assigning a wrong value to an annotated class field also reports the earlier valid assignment, and `t.x = {}` reports a missing required field"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-75: Assigning a wrong value to an annotated class field also reports the earlier valid assignment, and `t.x = {}` reports a missing required field

Found by the MAINT-55 conformance suite: 4 cases (rule `field.assign`, observed `false-report`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `assign-type-mismatch-002` (`rule=field.assign source=assign-type-mismatch.lua#2`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class A
---@field x A

---@type A
local t

t.x = {}
```

Cases in this group: `assign-type-mismatch-002`, `assign-type-mismatch-006`, `assign-type-mismatch-007`, `assign-type-mismatch-016`.

## 2. Expected vs Actual Behavior
- **Expected**: `m.x = true` (line 7) is reported once, on line 7. `t.x = {}` against `---@field x A` is silent (LuaLS marks no line).
- **Actual**: The report is repeated on the earlier, valid `m.x = 1` line (5-5, `boolean is not assignable to number`), which no conformance line marks; a `{}` assigned to a field of class type reports `Missing required field 'x'` on the assignment (assign-type-mismatch-002).
- **Recorded for `assign-type-mismatch-002`**: findings `7-7:Missing required field 'x'`; conformance lines none must be reported.

## 3. Related
[[BUG-44]] covers the converse (a wrong field value reported only as a hypothesis); [[BUG-481]] covers `{ ... }` against an array type.
