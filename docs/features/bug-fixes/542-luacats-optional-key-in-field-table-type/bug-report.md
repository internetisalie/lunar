---
id: "BUG-542"
title: "An optional key (`name?:`) in a `@field` table type, or an optional `fun` parameter in `@overload`, is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-542: `name?:` inside a `---@field` table type or an `---@overload` parameter list does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

```lua
---@class P1
---@field notifs {msg:string, file?:string}[]
local P1 = {}
```
→ `errors=[2:':' expected, got '?']`

```lua
---@overload fun(opts?:table):table
local function f(o) end
```
→ `errors=[1:':' expected, got '?']`

Controls from the same run, no errors: the same two snippets without the `?`; and
`---@alias Q5 {name:string, file?:string}`, the same optional key in an `@alias` table type.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. `k?: T` is a valid optional key in a LuaCATS table type and
  `p?: T` a valid optional `fun` parameter; LuaLS accepts both.
- **Actual**: one `PsiErrorElement` inside the LuaCATS comment. The `@alias` form parses, so the
  failure depends on the host tag.

## 3. Context
- **Corpus sites** (`lazynvim.baseline` `parseErrorFile=` lines, all `':' expected, got '?'`):
  `lua/lazy/core/plugin.lua:16`, `pkg/rockspec.lua:13`, `view/float.lua:8`, `view/float.lua:28`
  (the `@overload`), `view/init.lua:11`, `view/render.lua:19`.
- **Related, not established**: [[BUG-502]] (`---@param opts? {indent?: number}` silently becomes an
  LDoc tag, with no error element) uses the same optional-key construct on another tag.
- **Corpus effect**: each affected file counts once in `parseErrors` and once in
  `oracleDisagreements` (a false reject against `luac`). A fix lowers both in
  `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.
