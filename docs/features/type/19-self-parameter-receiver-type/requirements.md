---
id: "TYPE-19"
title: "19: @param self T Returns the Receiver's Type"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-19: @param self T Returns the Receiver's Type

## Overview
A fluent method annotated `---@param self T` with `---@return T` returns whatever the method was called
on, so a call on a subclass instance, or on a generic class instance, keeps its own type instead of the
declaring class's. Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@class List
local List = {}

---@generic T
---@param self T
---@return T
function List:identity() return self end

---@class Kid : List
---@type Kid
local kid

local r = kid:identity()   -- Kid
```
LuaLS 3.17.0: "Methods with `@generic T` and `@param self T` now correctly resolve return type to the
receiver's concrete type (e.g., `List<number>:identity()` returns `List<number>`)"
([#1000](https://github.com/LuaLS/lua-language-server/issues/1000), changelog entry).

## Current behaviour in Lunar
- A method declared with `:` binds its receiver to `self` through the pending-self edge
  (`LuaTypesVisitor.kt:1560-1565`); no code reads a `---@param self` tag to bind a generic.
- What `kid:identity()` returns is Not yet probed. Probe first: the example in a light fixture, then the
  inferred type of `r` for `Kid`, for `List`, and for a `List<number>` instance (the generic-class variant
  needs `---@class List<V>`).

## Boundary with BUG-94
[[BUG-94]] covers the method's **body**: with `@generic T: SomeClass` and `@param self T`, a read of
`self.someExistingVar` or `self.doesNotExist` inside the method, and the false reports it produces on the
declaration (missing fields, too many arguments). This feature covers the **call site**: the type a
caller sees from `receiver:method()`. A fix for one does not need the other's code path, and neither
changes the other's expected results.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-19-01 | **The receiver's class is returned** | M | Not Implemented | The example: `local l ---@type List`; `l:identity()` is `List`. |
| TYPE-19-02 | **A subclass receiver keeps its class** | M | Not Implemented | `kid:identity()` is `Kid`, not `List`. |
| TYPE-19-03 | **A generic class instance keeps its arguments** | M | Not Implemented | With `---@class List<V>`, a `List<number>` receiver gives `List<number>`. |
| TYPE-19-04 | **A constrained `T` works the same** | S | Not Implemented | `@generic T: List` with `@param self T` gives the same results as 01-03. |
| TYPE-19-05 | **A `.` call binds `T` from the first argument** | C | Not Implemented | `List.identity(kid)` is `Kid`. Shares the call path of [[BUG-31]]. |

## Depends on / Related
- Depends on: [[TYPE-14]] (done).
- Related: [[BUG-94]] (the in-body half), [[TYPE-20]] (`self` typed with the generic class instead of a bare `T`),
  survey issue #1000 (its `T<V, _>` form, a generic applied to arguments, is not in this feature).

## Out of scope
- Anything inside the method body (BUG-94).
- `T<V, _>`: a type parameter applied to arguments (#1000's original form); upstream behaviour is not recorded.
