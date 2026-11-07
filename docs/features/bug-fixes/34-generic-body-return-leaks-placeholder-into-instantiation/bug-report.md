---
id: "BUG-34"
title: "A generic function's own return expression adds its uninstantiated type to the call's result"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-34: A generic function's own return expression adds its uninstantiated type to the call's result

Found running [[TYPE-14]]'s human checklist, scenario 3.4 (GoLand 2026.2.3, libvirt builder).

## 1. Reproduction
```lua
---@class Box<T>
---@field value T
local Box = {}
---@generic T
---@param v T
---@return Box<T>
local function make(v) return { value = v } end
local made = make(1)
local mv = made.value
```

## 2. Expected vs Actual Behavior
- **Expected**: `made` is `Box<number>` and `mv` is `number`. The inlay on `made` reads
  `: Box<number>`.
- **Actual**: the inlay hints read `made: Box<number> | { ... }` and `mv: number | T`. With the
  body changed to `local function make(v) end`, they read `Box<number>` and `number`, so the extra
  arms come from the function's `return { value = v }`. In that return, `v` is the template's `T`.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: not investigated.
- **Other Notes**: requirements case 22 and `LuaTypeInlayHintsTest` case 23 use an empty body, so
  they do not cover this. TYPE-14-15 says a substituted member never shows `T` where a binding exists.
