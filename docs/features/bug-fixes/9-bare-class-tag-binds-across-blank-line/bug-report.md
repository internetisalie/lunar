---
id: "BUG-9"
title: "A bare @class tag binds to a local declared after a blank line"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-9: A bare `---@class` block binds to the next local even across a blank line

Found while triaging [[BUG-566]]'s corpus run. Observed only; root cause not investigated. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/manage/process.lua:7-16`:

```lua
---@class ProcessOpts
---@field args string[]
---@field cwd? string
-- … more optional fields

local M = {}               -- the module table, not a ProcessOpts
M.running = setmetatable({}, { __mode = "k" })
```

Callers then pass a plain options table where `ProcessOpts` is declared, e.g. `manage/task/plugin.lua:36 task:spawn(shell, { args = …, cwd = … })`.

## 2. Expected vs Actual
- **Expected**: the blank line ends the annotation block, so `ProcessOpts` is a type-level class with only its `@field`s, and `local M` is not annotated.
- **Actual**: `ProcessOpts` takes `M`'s members (`running`, `signals`, `spawn`, `abort`, `exec`). A table with `args` and `cwd` is reported as missing them: 8 *Missing required field* hits on `main`, and 11 *missing fields 'running', 'signals', …* hits with [[BUG-566]]'s fix (`pkg/rockspec.lua:40, :169, :197`; `manage/process.lua:219, :232-234`).

## 3. Context
- Not established: whether the binding comes from the stub's comment attachment (`LuaLocalVarStubElementType` hoisting the cats comment) or from type inference merging `M`'s members.
