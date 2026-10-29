---
id: "BUG-557"
title: "A comment block holding several `@class` tags and a trailing `@type` checks the next statement against every class in it"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-557: A comment block with several `@class` tags and a trailing `@type` checks the next statement against every class

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
local M = {}
---@class P1
---@field n string

---@class P2
---@field k string

---@type P1[]?
M.cache = nil
```

## 2. Expected vs Actual
- **Expected**: `M.cache` is checked against its own `---@type P1[]?` only; `P1` and `P2` are type declarations, not annotations of the statement.
- **Actual**: nine hits on line 9, including `Missing required field 'n'`, `Missing required field 'k'`, `P1[] is not assignable to P2`, `P2 is not assignable to P1[]` and `nil value is not assignable to P1[]`.

## 3. Context
- **Corpus effect**: `pkg/init.lua:30` (`M.cache = nil`, after `@class LazyPkg`, `LazyPkgSpec`, `LazyPkgSource`, `LazyPkgCache` and `---@type LazyPkg[]?`) carries 18 `LuaTypeAssignability` hits; 17 predate the batch, which adds `Missing required field 'spec'` because `LazyPkg`'s `spec` field parses since [[BUG-548]].
- The `nil value is not assignable to P1[]` hit is [[BUG-555]]'s top-level `?`, not this defect.
