---
id: "BUG-556"
title: "A bracket-keyed `@field [k] T` is a required member named `\"[k]\"`, which no table constructor can supply"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-556: A bracket-keyed `---@field [k] T` is a required member named `"[k]"`, which no constructor can supply

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
---@class E
---@field [string] number
---@field v string
local E = {}
---@type E
local e = { v = "x" }
```

## 2. Expected vs Actual
- **Expected**: no diagnostic: `[string]` is an index signature, and a numeric key such as `[1]` is satisfied by a positional entry (`{ "a" }`).
- **Actual**: `6: Missing required field '[string]'`.

## 3. Context
- **Corpus effect**: 21 hits in `lazynvim.baseline` (14 `LuaTypeAssignability`, 7 `LuaReturnTypeMismatch`), `Missing required field '[1]'`/`'[2]'`/`'[3]'`, e.g. `core/plugin.lua:95` `self.meta:add({ spec })` against `LazyPluginBase`'s `---@field [1] string?`. Numeric keys parse in `@field` since [[BUG-543]].
- **Possibly relevant, not established**: `LuaCatsDeclarations.fieldMember` names a keyed field by its descriptor text, and `LuaTypesVisitor.visitTableConstructor` records only `name = value` entries.
