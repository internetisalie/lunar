---
id: "BUG-80"
title: "A table constructor missing a required `@field` (`@type A` `return {}`; `{x = 1}` without `[1]`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-80: A table constructor missing a required `@field` (`@type A` `return {}`; `{x = 1}` without `[1]`) is not reported

Found by the MAINT-55 conformance suite: 3 cases (rule `field.missing`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `missing-fields-019` (`rule=field.missing source=missing-fields.lua#19`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class A
---@field x integer

---@type A
return {} -- E
```

Cases in this group: `missing-fields-019`, `missing-fields-027`, `missing-fields-038`.

## 2. Expected vs Actual Behavior
- **Expected**: The constructor is reported with the missing required fields (LuaLS `missing-fields`).
- **Actual**: No finding, for every case in the group.
- **Recorded for `missing-fields-019`**: no findings; conformance lines [5] must be reported.

## 3. Related
[[BUG-481]] covers a different missing-fields disagreement (missing-fields-017).
