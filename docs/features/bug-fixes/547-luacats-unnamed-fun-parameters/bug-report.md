---
id: "BUG-547"
title: "A `fun` type with unnamed parameters (`fun(LazyPlugin)`) is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-547: `---@cast builders (string|fun(LazyPlugin))[]` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): the snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

```lua
---@type (string|fun(table))[]
local h
```
→ `errors=[1:':' expected, got ')']`

Control from the same run, no errors: `---@type (string|fun(p:table))[]`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. LuaLS accepts a `fun` parameter given by type alone.
- **Actual**: the parameter's type is read as its name, and a `:` is demanded after it.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/manage/task/plugin.lua:65`
  (a `---@cast`; the probe used `---@type` with the same type).
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
