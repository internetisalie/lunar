---
id: "BUG-35"
title: "Go to Class lists a generic class three times"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-35: Go to Class lists a generic class three times

Found running [[TYPE-14]]'s human checklist, scenario 3.2 (GoLand 2026.2.3, libvirt builder).

## 1. Reproduction
One project file, `s31.lua`:
```lua
---@class Box<T>
---@field value T
---@field items T[]
local Box = {}
---@type Box<string>
local b
---@generic T
---@param v T
---@return Box<T>
local function make(v) end
```
Navigate → Class (Ctrl+N), type `Box`.

## 2. Expected vs Actual Behavior
- **Expected**: one `Box` entry.
- **Actual**: three `Box` entries, all from `s31.lua`, and each one lands on line 1. A non-generic
  class in the same project (`---@class Box13` `local Box13 = {}`) is listed once.

## 3. Context / Environment
- **Lua Version**: any.
- **Relevant Files**: not investigated. The Goto class contributors and `LuaCatsTypeNavigation` are
  the likely places to look.
- **Other Notes**: not checked:
  - whether the `---@type Box<string>` and `---@return Box<T>` references contribute entries;
  - whether a lone `---@class Box<T>` lists once.
