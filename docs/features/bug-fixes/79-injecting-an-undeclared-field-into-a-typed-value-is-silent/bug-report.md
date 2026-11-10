---
id: "BUG-79"
title: "Assigning a field the class or record type does not declare (`m.yy = 1` on a `@type Class` value) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-79: Assigning a field the class or record type does not declare (`m.yy = 1` on a `@type Class` value) is not reported

Found by the MAINT-55 conformance suite: 5 cases (rule `field.inject`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `inject-field-001` (`rule=field.inject source=inject-field.lua#1`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class Class
local m = {}

m.xx = 1 -- OK

---@type Class
local m

m.xx = 1 -- OK
m.yy = 1 -- Warning -- E
```

Cases in this group: `inject-field-001`, `inject-field-003`, `inject-field-005`, `inject-field-007`, `inject-field-008`.

## 2. Expected vs Actual Behavior
- **Expected**: `m.yy = 1` is reported as injecting a field the type does not declare (LuaLS `inject-field`).
- **Actual**: No finding, for every case in the group.
- **Recorded for `inject-field-001`**: no findings; conformance lines [10] must be reported.
