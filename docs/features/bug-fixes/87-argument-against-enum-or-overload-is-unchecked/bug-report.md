---
id: "BUG-87"
title: "An argument that is not an `@enum` member (`f(t)`, `f(3)`, `f({ h = 1 })`), or does not fit an `@overload` constructor (`MyClass(1)`), is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-87: An argument that is not an `@enum` member (`f(t)`, `f(3)`, `f({ h = 1 })`), or does not fit an `@overload` constructor (`MyClass(1)`), is not reported

Found by the MAINT-55 conformance suite: 4 cases (rule `arg.other`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `param-type-mismatch-006` (`rule=arg source=param-type-mismatch.lua#6`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@enum A
local t = {
    x = 1,
    y = 2,
}

---@param x A
local function f(x)
end

f(t) -- E
f(t.x)
f(1)
f(3) -- E
```

Cases in this group: `param-type-mismatch-006`, `param-type-mismatch-007`, `param-type-mismatch-008`, `param-type-mismatch-020`.

## 2. Expected vs Actual Behavior
- **Expected**: The marked call is reported as a parameter type mismatch.
- **Actual**: No finding, for every case in the group.
- **Recorded for `param-type-mismatch-006`**: no findings; conformance lines [11, 14] must be reported.
