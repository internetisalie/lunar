---
id: "BUG-544"
title: "An optional `fun` return type (`fun():boolean?`) inside a LuaCATS table type is a parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-544: `{filter:fun(p:LazyPlugin):boolean?}` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

```lua
---@alias S1 {title:string, filter:fun(p:table):boolean?}
local x = 1
```
→ `errors=[1:',', <, '[]', '|' or '}' expected, got '?']`

`---@alias Q2 {load: fun():string?}` gives the same error.

Controls from the same run, no errors: `{title:string, filter:fun(p:table):boolean}` (no `?`);
`---@alias S2 fun(p:table):boolean?` (the same optional return outside a table type);
`---@alias Q1 {a:string?}` (an optional field type in a table type).

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. `T?` is a valid `fun` return type wherever a type may appear;
  LuaLS accepts it inside a table type.
- **Actual**: one `PsiErrorElement` at the `?` when the `fun` type is a table-type member.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/view/sections.lua:13`.
- **Possibly the same defect as [[BUG-545]]** (a parenthesised return list inside a table type);
  not established.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
