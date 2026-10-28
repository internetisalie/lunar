---
id: "BUG-548"
title: "A string-literal type or string-literal union (`\"a\" | \"b\"`) in a `@field` is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-548: `---@field source "lazy" | "packspec" | "rockspec"` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated. Design §3.2 named the class "spaced string-literal union"; the probe below
shows the spacing is irrelevant and a single literal fails too.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (after `---@class P4`, before `local P4 = {}`) | Errors |
| :-- | :-- |
| `---@field source "lazy" \| "packspec"` | `2:<type> or '?' expected, got '"'` |
| `---@field source "lazy"\|"packspec"` | `2:<type> or '?' expected, got '"'` |
| `---@field source "lazy"` | `2:<type> or '?' expected, got '"'` |

Controls from the same run, no errors: `---@alias K "a" | "b"` and `---@param k "a" | "b"`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. A string literal is a valid LuaCATS type in `@field` as in
  `@alias` and `@param`.
- **Actual**: under `@field`, any type starting with a string literal is an error.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/pkg/init.lua:11`.
- **Related, not established**: [[BUG-514]] reports a string-literal union rejected in `@return`.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
