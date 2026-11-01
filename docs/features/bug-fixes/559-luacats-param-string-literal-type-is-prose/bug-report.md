---
id: "BUG-559"
title: "A string-literal `@param` type (`@param k \"a\"|\"b\"`) silently becomes an LDoc tag"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-559: A string-literal `@param` type (`---@param k "a"|"b"`) silently becomes an LDoc tag

A deferred finding of the LuaCATS grammar batch ([[BUG-542-BATCH]] §8).

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

## 4. Root Cause
`<TAG_PARAM>` had no string or number rule, so the type lexed as text, `paramTag` failed, and
`ldocParamTag` took the tag as prose with no error. Each tag's lexer state listed its own type tokens,
which is why this repeated BUG-514 and BUG-548.

## 5. Fix Strategy
One rule block in `luacats.flex` now serves `TAG_CAST`, `TAG_FIELD`, `TAG_PARAM`, `TAG_RETURN` and
`TAG_TYPE`. `returnDescription` accepts `CODE`, because backticks in `@return` prose now lex as code.

## 6. Test Strategy
`LuaCatsTypeFormsTest` runs every type form in every tag that takes a type: 14 failures on the old
lexer, none now. The corpus sweep counts `@param` prose fallbacks as `LdocParamFallback`; lazy.nvim has
one, a `@param` with no name.
