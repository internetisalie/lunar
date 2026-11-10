---
id: "BUG-85"
title: "`return 1` in a function declared `@return number, number?` is reported as `nil` not assignable to `number`"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-85: `return 1` in a function declared `@return number, number?` is reported as `nil` not assignable to `number`

Found by the MAINT-55 conformance suite: 1 case (rule `return.count`, observed `false-report`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-return-value-004` (`rule=return.count source=missing-return-value.lua#4`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@return number, number?
function F()
    return 1
end
```

Cases in this group: `missing-return-value-004`.

## 2. Expected vs Actual Behavior
- **Expected**: No report: the second return is optional.
- **Actual**: `nil value is not assignable to number`, spanning the whole function (lines 2-4).
- **Recorded for `missing-return-value-004`**: findings `2-4:nil value is not assignable to number`; conformance lines none must be reported.

## 3. Related
[[BUG-91]] reports the same shape for a single optional return (`---@return number?`).
