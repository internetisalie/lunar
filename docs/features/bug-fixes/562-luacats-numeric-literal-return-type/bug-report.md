---
id: "BUG-562"
title: "A numeric-literal `@return` type (`@return 1|2`) is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-562: A numeric-literal `@return` type (`---@return 1|2`) is a LuaCATS parse error

A deferred finding of the LuaCATS grammar batch ([[BUG-542-BATCH]] §8).

## 1. Reproduction

Light fixture (`BaseDocumentTest`), `configureByText`, then the comment's PSI read. Executed 2026-10-28 on `20e318c4` with a throwaway probe test (not committed).

```lua
---@return 1|2
function f() end
```

## 2. Expected vs Actual
- **Expected**: no error; the return type is the union of the two numeric literals, as under `@alias`.
- **Actual**: `<type> expected, got '1'`.

## 3. Context
- **Same class as** [[BUG-543]] (`@field` had no `{NUMBER}` rule).

## 4. Root Cause and Fix
`<TAG_RETURN>` had no number rule. Fixed with [[BUG-559]]'s shared type rules, and covered by
`LuaCatsTypeFormsTest`.
