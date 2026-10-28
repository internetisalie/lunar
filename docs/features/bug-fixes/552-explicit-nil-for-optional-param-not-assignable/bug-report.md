---
id: "BUG-552"
title: "An explicit `nil` argument for an optional `@param mode? string` is reported as not assignable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-552: `loadfile(file, nil, env)` reports *nil value is not assignable to string* on LuaJIT 2.1

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-12). A bug
report: reproduction executed, root cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`) with `LuaTypeAssignabilityInspection` enabled, the project
pinned to LuaJIT 2.1 with `CorpusTarget.pin`, `myFixture.doHighlighting()`. Executed 2026-10-28 on
`5dd675a4` with a throwaway probe test (not committed).

```lua
local env = {}
local f = loadfile("x.lua", nil, env)
print(f)
```
→ `2:'nil':nil value is not assignable to string`

Control from the same run, no hit: `loadfile("x.lua")`, the optional parameter omitted.

## 2. Expected vs Actual
- **Expected**: no hit. The stub declares `---@param mode? string`
  (`src/main/resources/runtime/luajit/luajit-2.1/builtin.lua`), so `mode` accepts `nil`, whether
  omitted or passed explicitly.
- **Actual**: an explicit `nil` is checked against `string`, as if the parameter were not optional.

## 3. Context
- **Not reproduced on Standard 5.1** in the same run, because that stub declares
  `loadfile(filename)` with one parameter. Whether every optional `---@param name? T` rejects an
  explicit `nil`, or only this stub's, is not established.
- **Corpus site**: `lua/lazy/pkg/rockspec.lua:228`, part of the
  `inspection.LuaTypeAssignability=448` floor in `src/test/resources/corpus/lazynvim.baseline`. A
  fix lowers it, and the fixing change re-records the baseline.
