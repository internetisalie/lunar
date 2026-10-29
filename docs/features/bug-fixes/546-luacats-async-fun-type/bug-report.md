---
id: "BUG-546"
title: "An `async fun(…)` type is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
Read from code, not executed. `functionSignatureType ::= 'fun' '(' …` (`luacats.bnf:196`) has no
`async` prefix. In `<TAG_TYPE>`, `async` lexes as `NAME` (`luacats.flex:219`).

The parse then goes like this:
1. `distinctType` (`:180-188`) settles on `namedType` (`:207`), so the alias type is just `async`.
2. `aliasTag`'s trailing `description` (`:82`) cannot start at `fun`, because `fun` is a `KEYWORD`
   (`flex:217`) and `KEYWORD` is not a `description` token (`:214`).
3. The comment line ends, and `fun` is reported as unexpected. That matches the variant list
   `<, <description>, DASHES, '[]' or '|'`.

## 5. Fix Strategy
`luacats.bnf`:
```
functionSignatureType ::= 'async'? 'fun' '(' functionSignatureArguments? ')' functionSignatureReturnType?
```
- Literals are matched by text (`consumeToken(builder_, "fun")` in the generated
  `LuaCatsParser.java`), so `'async'` matches the `NAME` token with no lexer change, in every state.
- The section is unpinned. A type named `async` that is not followed by `fun(` rolls back, and
  `namedType` takes it as before.
- **No accessor change**, because `tokenAccessors="no"`. `TypeParser` ignores the prefix, so an
  async `fun` is typed like a plain `fun`.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testAsyncFunType`: `---@alias TF async fun(t:table, o:table)` before `local x = 1`. The type is
    `async fun(t:table, o:table)`.
  - `testAsyncAloneIsStillATypeName`: `---@type async` before `local x`. The type is `async`, with no
    errors. It passes before and after, and pins the rollback.
  - Control `testAsyncWithoutParameterListIsStillAnError`: `---@alias TF async fun`, with
    `expectErrors = true`.
