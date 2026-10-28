---
id: "BUG-543"
title: "A numeric key (`[1]`) in a `@field` name or a `@field` table type is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-543: `---@field [1] string` and `{[1]:string}` under `@field` do not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (followed by a `local` host) | Errors |
| :-- | :-- |
| `---@field [1] string` | `<type> expected, got '1'` |
| `---@field [1] string?` | `<type> expected, got '1'` |
| `---@field p {[1]:string}` | `<type> expected, got '1'` |
| `---@field p (string\|{[1]:string, [string]:any})[]` | `<type> expected, got '1'` |
| `---@alias Q8 {[1]:string, [2]:string\|fun()}[]` | `CODE, DASHES, NAME, NUMBER, STRING, SYMBOL or zzz expected, got 'fun'` |

Controls from the same run, no errors: `---@field one string`; `---@alias P3 {[1]:string, [string]:any}`;
`---@alias Q9 {[1]:string, [3]?:string}[]`; `---@alias Q10 {a:string, b:string|fun()}`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. `[1]` is a valid LuaCATS field key and table-type key;
  LuaLS accepts every snippet above.
- **Actual**: one `PsiErrorElement` inside the LuaCATS comment. A numeric key parses in an `@alias`
  table type but not under `@field`; the `@alias` failure needs both a numeric key and a later
  `|fun()` member.

## 3. Context
- **Corpus sites** (`lazynvim.baseline` `parseErrorFile=` lines): `lua/lazy/core/handler/keys.lua:13`,
  `manage/runner.lua:6`, `manage/semver.lua:4`, `types.lua:52` (`<type> expected, got '1'`), and
  `view/render.lua:630` (the `@alias` form).
- **Corpus effect**: each affected file counts once in `parseErrors` and once in
  `oracleDisagreements`. A fix lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the
  fixing change re-records it.
