---
id: "BUG-94"
title: "A class method annotated `@generic T: SomeClass` / `@param self T` reports missing fields and too many arguments, and not the undefined field"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-94: A class method annotated `@generic T: SomeClass` / `@param self T` reports missing fields and too many arguments, and not the undefined field

Found by the MAINT-55 conformance suite: 1 case (rule `field.undefined`, observed `false-report`) disagrees with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `undefined-field-013` (`rule=field.undefined source=undefined-field.lua#13`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class SomeClass
---@field someExistingVar integer
local SomeClass = {}

---@generic T: SomeClass
---@param self T
---@return T
function SomeClass:xyz()
    print(self.someExistingVar)
    print(self.doesNotExist) -- E
    return self
end
```

Cases in this group: `undefined-field-013`.

## 2. Expected vs Actual Behavior
- **Expected**: Only `self.doesNotExist` (line 10) is reported.
- **Actual**: `Missing required field 'someExistingVar'` (line 3), `{ ... } | SomeClass is not assignable to SomeClass` (lines 8-12) and `Too many arguments: expected at most 0, got 1` (line 8); line 10 is not reported.
- **Recorded for `undefined-field-013`**: findings `3-3:Missing required field 'someExistingVar'`, `8-12:{ ... } | SomeClass is not assignable to SomeClass`, `8-8:Too many arguments: expected at most 0, got 1`; conformance lines [10] must be reported.

## 3. Related
[[BUG-74]] is a likely mechanism for the too-many-arguments report; not confirmed.
