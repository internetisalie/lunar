---
id: "BUG-91"
title: "`return nil` for `@return number?`, and `return false` where an `@overload` returns `boolean`, are reported as not assignable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-91: `return nil` for `@return number?`, and `return false` where an `@overload` returns `boolean`, are reported as not assignable

Found by the MAINT-55 conformance suite: 3 cases (rule `return.mismatch`, observed `false-report`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `return-type-mismatch-003` (`rule=return.mismatch source=return-type-mismatch.lua#3`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@return number?
function F()
    return nil
end
```

Cases in this group: `return-type-mismatch-003`, `return-type-mismatch-012`, `return-type-mismatch-015`.

## 2. Expected vs Actual Behavior
- **Expected**: No report: `nil` is allowed by `number?`; the `boolean` return matches the overload.
- **Actual**: `nil value is not assignable to number` (return-type-mismatch-003), `boolean is not assignable to number` over the function body (return-type-mismatch-012, -015).
- **Recorded for `return-type-mismatch-003`**: findings `2-4:nil value is not assignable to number`; conformance lines none must be reported.

## 3. Related
[[BUG-85]] reports the same shape for an optional trailing return.
