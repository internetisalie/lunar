---
id: "BUG-545"
title: "A parenthesised `fun` return list (`fun():(A?, B?)`) inside a LuaCATS table type is a parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-545: `{load: fun():(LazyPluginSpec?, string?)}` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (followed by a `local` host) | Errors |
| :-- | :-- |
| `---@type {modname: string, load: fun():(string?, string?)}[]` | `')', <, '[]' or '\|' expected, got '?'` |
| `---@alias Q3 {load: fun():(string?, number?)}` | `')', <, '[]' or '\|' expected, got '?'` |
| `---@alias Q4 {load: fun():(string, number)}` | `')', <, '[]' or '\|' expected, got ','` |

Controls from the same run, no errors: `---@type fun():(string?, number?)` and
`---@type fun():string?, number?` (the return list outside a table type).

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. A parenthesised multi-value return list is valid LuaLS syntax
  for a `fun` type, inside a table type as elsewhere.
- **Actual**: inside a table type the list is read as a single parenthesised type, so the parser
  stops at the first `?` or `,`.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/core/plugin.lua:149`.
- **Possibly the same defect as [[BUG-544]]** (an optional return inside a table type); not
  established.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`
  (`core/plugin.lua` also has a [[BUG-542]] site, so one fix alone may not move the file count). A
  fix re-records `src/test/resources/corpus/lazynvim.baseline`.
