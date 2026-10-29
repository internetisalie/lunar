---
id: "BUG-558"
title: "A `@class` declaration's own table (`local M = {}`) is checked for every required instance field, inherited ones included"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-558: A `@class` declaration's own table (`local M = {}`) is checked for every required instance field

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
---@class B
---@field a string
local B = {}
---@class K: B
local K = {}
```

## 2. Expected vs Actual
- **Expected**: no diagnostic: the table a `@class` annotates is the class (its methods and defaults), not an instance literal that must carry every field.
- **Actual**: `3: Missing required field 'a'` and `5: Missing required field 'a'`.

## 3. Context
- **Corpus effect**: 6 `LuaTypeAssignability` hits added and 4 removed in `lazynvim.baseline` (net +2); the added ones are at `core/handler/ft.lua:5` (`LazyFiletypeHandler:LazyEventHandler`). The baseline already carried this pattern before the batch (`symbol.LuaTypeAssignability.active=5`, `did_setup=5`, `handlers=5`, …).
