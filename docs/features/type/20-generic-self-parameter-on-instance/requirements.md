---
id: "TYPE-20"
title: "20: A Method Typed @param self Lane<T> Called on an Instance"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-20: A Method Typed @param self Lane<T> Called on an Instance

## Overview
A method may bind its own `@generic T` by typing `self` as the generic class, `---@param self Lane<T>`.
Called on a `Lane<integer>` instance, the method is found, offered in completion, and `T` is `integer`
in its signature. Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@class lanes.Lane<T>
local Lane = {}

---@generic T
---@param self lanes.Lane<T>
---@param timeout number?
---@return T
function Lane:join(timeout) end

---@type lanes.Lane<integer>
local test

local i = test:join()   -- integer
```
Reduced from LuaLS [#2196](https://github.com/LuaLS/lua-language-server/issues/2196) (the report also
gives the class a `{ [integer]: T }` parent and `@return T ...`). LuaLS 3.13.6 fixed `join` not showing
up; 3.18.0 fixed the return through `self list<T>`.

## Current behaviour in Lunar
- Not yet probed. Probe first: the example in a light fixture; record the inferred type of `i`, whether
  `test:` completion offers `join`, and any diagnostic on `test:join()`.
- Not the same defect as the open bugs, on their reports' inputs. [[BUG-32]] has no `self` parameter and no
  method `@generic`: its `T` is the class's own, substituted from the receiver. [[BUG-37]] is `T` inside an
  in-file method's own annotations. Here the method declares its own `T` and binds it through the
  `self` tag. If fixing BUG-32 makes rows 01 and 02 pass, mark them Full from that test.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-20-01 | **`T` is bound from the receiver's arguments** | M | Not Implemented | The example: `i` is `integer`. |
| TYPE-20-02 | **A different argument gives a different result** | M | Not Implemented | With `---@type lanes.Lane<string>`, `test:join()` is `string`. |
| TYPE-20-03 | **The method is offered on the instance** | M | Not Implemented | Completion after `test:` lists `join`. |
| TYPE-20-04 | **No diagnostic on the call** | M | Not Implemented | `test:join()` and `test:join(1)` report nothing; `test:join("x")` reports a type mismatch on `timeout`. |
| TYPE-20-05 | **A raw instance binds `T` to `any`** | S | Not Implemented | With `---@type lanes.Lane` (no arguments), `test:join()` is `any` (LuaLS 3.19.2 resolves a raw generic class's unbound parameters to `any`). |
| TYPE-20-06 | **`T` links the receiver and another parameter** | C | Not Implemented | With an added `---@param v T`, `test:push("x")` on a `Lane<integer>` reports a mismatch. |

## Depends on / Related
- Depends on: [[TYPE-14]] (done).
- Related: [[BUG-32]], [[BUG-37]], [[TYPE-19]] (bare `self T`).

## Out of scope
- The `{ [integer]: T }` table-literal parent from the original report (survey #734 row).
- A variadic return `T ...` (survey Q4).
