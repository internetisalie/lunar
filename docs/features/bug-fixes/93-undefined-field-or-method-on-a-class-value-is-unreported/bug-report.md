---
id: "BUG-93"
title: "Reading an undeclared field or calling an undeclared method on a class-typed value (`v.field3`, `Foo:method2()`) is not reported"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-93: Reading an undeclared field or calling an undeclared method on a class-typed value (`v.field3`, `Foo:method2()`) is not reported

Found by the MAINT-55 conformance suite: 10 cases (rule `field.undefined`, observed `missed`) disagree with LuaLS's expected findings. Cases are in `src/test/resources/conformance/luacats/cases/`, and the recorded outcomes in `results.json`.

## 1. Reproduction
Conformance case `undefined-field-001` (`rule=field.undefined source=undefined-field.lua#1`); lines marked `-- E` are the ones LuaLS reports.

```lua
---@class Foo
---@field field1 integer
local mt = {}
function mt:Constructor()
    self.field2 = 1
end
function mt:method1() return 1 end
function mt.method2() return 2 end

---@class Bar: Foo
---@field field4 integer
local mt2 = {}

---@type Foo
local v
print(v.field1 + 1)
print(v.field2 + 1)
print(v.field3 + 1) -- E
print(v:method1())
print(v.method2())
print(v:method3()) -- E

---@type Bar
local v2
print(v2.field1 + 1)
print(v2.field2 + 1)
print(v2.field3 + 1) -- E
print(v2.field4 + 1)
print(v2:method1())
print(v2.method2())
print(v2:method3()) -- E

local v3 = {}
print(v3.abc)

---@class Bar2
local mt3
function mt3:method() return 1 end
print(mt3:method())
```

Cases in this group: `undefined-field-001`, `undefined-field-002`, `undefined-field-003`, `undefined-field-004`, `undefined-field-006`, `undefined-field-007`, `undefined-field-008`, `undefined-field-012`, `undefined-field-014`, `undefined-field-015`.

## 2. Expected vs Actual Behavior
- **Expected**: The marked access is reported as an undefined field (LuaLS `undefined-field`).
- **Actual**: No finding, for every case in the group.
- **Recorded for `undefined-field-001`**: no findings; conformance lines [18, 21, 27, 31] must be reported.
