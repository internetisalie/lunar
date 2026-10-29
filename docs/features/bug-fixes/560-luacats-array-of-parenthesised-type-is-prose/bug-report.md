---
id: "BUG-560"
title: "The `[]` after a parenthesised LuaCATS type (`(A|B)[]`) is read as prose, so the array is lost"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-560: The `[]` after a parenthesised LuaCATS type (`(A|B)[]`) is read as prose

A deferred finding of the LuaCATS grammar batch ([[BUG-542-BATCH]] §8). A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BaseDocumentTest`), `configureByText`, then the comment's PSI read. Executed 2026-10-28 on `20e318c4` with a throwaway probe test (not committed).

```lua
---@type (string|number)[]
local x
```

## 2. Expected vs Actual
- **Expected**: the type `(string|number)[]`, an array of the union.
- **Actual**: no error, but the `@type`'s type is `(string|number)`; the `[]` is description text.

## 3. Context
- **Corpus sites**: lazy.nvim `manage/runner.lua:6` and `manage/task/plugin.lua:65` write this form.
