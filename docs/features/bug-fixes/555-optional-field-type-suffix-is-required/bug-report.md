---
id: "BUG-555"
title: "A `@field name T?` field is required: the trailing `?` does not make the type admit nil"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-555: A `---@field name T?` field is required: the trailing `?` does not make the type admit nil

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
---@class D
---@field u string?
---@field v string
local D = {}
---@type D
local d = { v = "x" }
```

## 2. Expected vs Actual
- **Expected**: no diagnostic for `u`: `string?` admits nil, as `---@field u? string` does (BUG-401).
- **Actual**: `6: Missing required field 'u'` (and line 4 reports both `u` and `v`; see [[BUG-558]]).

## 3. Context
- **Corpus effect**: 10 hits added in `lazynvim.baseline` (9 `LuaTypeAssignability`, 1 `LuaReturnTypeMismatch`), all `Missing required field 'url'` from `LazyPluginBase`'s `---@field url string?` (`types.lua:55`), e.g. `core/plugin.lua:95` `self.meta:add({ spec })`.
- **Possibly relevant, not established**: at a tag's top level the grammar leaves `?` out of the type and in the description, deliberately (batch plan §4), so `argType.text` is `string`.
