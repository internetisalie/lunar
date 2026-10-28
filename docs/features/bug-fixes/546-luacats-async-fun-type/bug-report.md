---
id: "BUG-546"
title: "An `async fun(…)` type is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-546: `---@alias LazyTaskFn async fun(task:LazyTask, opts:TaskOptions)` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): the snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

```lua
---@alias TF async fun(t:table, o:table)
local x = 1
```
→ `errors=[1:<, <description>, DASHES, '[]' or '|' expected, got 'fun']`

Control from the same run, no errors: `---@alias TF fun(t:table, o:table)`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. LuaLS accepts an `async` prefix on a `fun` type (it marks the
  function as asynchronous, alongside the `---@async` tag).
- **Actual**: `async` is read as a type name and the following `fun` is an error.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/manage/task/init.lua:12`.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
