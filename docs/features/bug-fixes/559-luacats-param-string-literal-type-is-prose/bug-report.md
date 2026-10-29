---
id: "BUG-559"
title: "A string-literal `@param` type (`@param k \"a\"|\"b\"`) silently becomes an LDoc tag"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-559: A string-literal `@param` type (`---@param k "a"|"b"`) silently becomes an LDoc tag

A deferred finding of the LuaCATS grammar batch ([[BUG-542-BATCH]] §8). A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BaseDocumentTest`), `configureByText`, then the comment's PSI read. Executed 2026-10-28 on `20e318c4` with a throwaway probe test (not committed).

```lua
---@param k "a"|"b"
local function f(k) end
```

## 2. Expected vs Actual
- **Expected**: a `LuaCatsParamTag` for `k` with the type `"a"|"b"`, as `@alias` and (since [[BUG-548]]) `@field` give.
- **Actual**: no `PsiErrorElement`, but the tag is a `LuaCatsLdocParamTag`, so `k` is not a declared parameter.

## 3. Context
- **Same class as** [[BUG-548]] (`@field`) and [[BUG-514]] (`@return`): a lexer state with no string rule.
