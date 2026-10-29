---
id: "BUG-549"
title: "A `@see |text|` reference is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
Read from code, not executed. `seeTag ::= '@see' <<ArgName NAME>> description?`
(`luacats.bnf:161`) requires a `NAME`. `<TAG_SEE>` (`luacats.flex:203-208`) lexes only `NAME`, `.`
and `:`, so `|` falls to the catch-all `.` (`:207`) and becomes `TEXT`. `seeTag` fails, and no other
`anyTag` alternative starts with `@see`.

## 5. Fix Strategy
`luacats.flex`, `<TAG_SEE>`: add before the catch-all
```
    {PIPE}[^|\r\n]+{PIPE}   { return LCATS_NAME; }
```
A Vim help-tag reference then becomes one `NAME`, and the existing rule takes it as the reference.
Text after it lexes as before and is the description.

A lexer rule is chosen over a grammar alternative:
- **Lexer rule (chosen).** No grammar or accessor change.
  `LuaCatsSeeTag.getArgName()` stays `@NotNull`, and its one consumer,
  `LuaCatsDocumentationRenderer.kt:563` (`tag.argName.text`), keeps working.
- **Grammar alternative (rejected).** `'@see' (<<ArgName NAME>> description? | description)` would
  make that getter nullable for no gain.
- Quick documentation renders the reference as `<code>|luaL_loadfile()|</code>` (`:583`).

## 6. Test Strategy
- `LuaCatsParserTest.testSeeVimHelpTagReference`: `---@see |luaL_loadfile()|` before
  `local function f() end`. No errors, and the `LuaCatsSeeTag`'s `argName.text` is
  `|luaL_loadfile()|`.
- `LuaCatsParserTest.testSeeHelpTagWithDescription`: `---@see |vim.fn| for details`. No errors,
  `argName.text` is `|vim.fn|`, and `description.text.trim()` is `for details`.
- Control `testSeeUnterminatedPipeIsStillAnError`: `---@see |luaL_loadfile()`, with
  `expectErrors = true`.
- `TestLuaCatsLexer.testSee`: `|a.b()| x` in `TAG_SEE` lexes as `|a.b()|` `NAME`, whitespace, `x`
  `NAME`.
