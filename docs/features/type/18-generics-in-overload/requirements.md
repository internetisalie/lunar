---
id: "TYPE-18"
title: "18: @generic Names Inside @overload Signatures"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "TYPE"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-18: @generic Names Inside @overload Signatures

## Overview
A function declared with `@generic T` can use `T` in its `@overload` signatures. A call that fits an
overload binds `T` from its own arguments and returns the instantiated type, and `T` in the overload is
not reported as an unknown type. Parent: [[features/type/requirements|TYPE]].

## Example
```lua
---@generic T
---@param value T
---@return T
---@overload fun(value: T, fallback: T): T
local function pick(value, fallback) return value or fallback end

local a = pick(1)           -- number (primary)
local b = pick("x", "y")    -- string (overload)
```
LuaLS 3.17.0: "Generic type parameters now work in `@overload` annotations"
([#723](https://github.com/LuaLS/lua-language-server/issues/723)); before it the overload reported
"Undefined type or alias `T`" ([#1170](https://github.com/LuaLS/lua-language-server/issues/1170)).
The changelog gives no result types; those in the comments are this feature's requirement.

## Current behaviour in Lunar
- `@overload` signatures never reach the type engine (`LuaSignatureCopier.kt:118-119`), so a generic
  name in one is not instantiated by anything. This is [[TYPE-16]]'s gap.
- Whether `T` in the overload tag raises an unresolved-type diagnostic is Not yet probed.
  Probe first: run the example through highlighting in a light fixture and record diagnostics on the
  `@overload` line.

## Requirements
| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| TYPE-18-01 | **The overload sees the tag's generics** | M | Not Implemented | `T` in `@overload fun(value: T, fallback: T): T` resolves to the `@generic T` of the same comment: no unresolved-type diagnostic on it. |
| TYPE-18-02 | **An overload call instantiates per call** | M | Not Implemented | `pick("x", "y")` is `string`; a second call `pick(1, 2)` in the same file is `number` and does not disturb the first. |
| TYPE-18-03 | **The primary signature still instantiates** | M | Not Implemented | `pick(1)` is `number`. |
| TYPE-18-04 | **A cross-file callee instantiates** | S | Not Implemented | The same calls through a `require`d module give `number` and `string`. |
| TYPE-18-05 | **A name not declared by `@generic` stays unresolved** | S | Not Implemented | `@overload fun(value: U): U` with no `@generic U` still reports an unresolved type. |

## Depends on / Related
- Depends on: [[TYPE-16]] (overloads as function types), [[TYPE-14]] (done).
- Related: [[TYPE-17]] (inline `fun<T>`), issues #723 and #1170 in the LuaLS survey.

## Out of scope
- Binding `T` from a lambda argument ([[BUG-33]]).
- Constraint checks on the overload's `T` ([[BUG-88]]).
