---
id: "BUG-549"
title: "A `@see |text|` reference is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-549: `---@see |luaL_loadfile()|` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): the snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

```lua
---@see |luaL_loadfile()|
local function f() end
```
→ `errors=[1:NAME expected, got '|']`

Control from the same run, no errors: `---@see luaL_loadfile`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. LuaLS treats the text after `@see` as free-form; `|name|` is
  the Vim help-tag style common in Neovim plugins.
- **Actual**: `@see` demands a `NAME` and the `|` is an error.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/core/cache.lua:228`.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
