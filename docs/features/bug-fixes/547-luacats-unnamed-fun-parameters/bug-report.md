---
id: "BUG-547"
title: "A `fun` type with unnamed parameters (`fun(LazyPlugin)`) is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
Read from code, not executed. `functionSignatureArgument ::= <<ArgName argumentName>>
<<ArgSymbol (':')>> <<ArgType type>>` (`luacats.bnf:198`, with `argumentName ::= NAME` at `:199`)
has only the named form. `table` is consumed as the parameter name, and `':'` is demanded at `)`.

The corpus site is a `@cast`. Its type lexes in `<TAG_CAST>` (`luacats.flex:128-133`), where `fun`
is a `NAME`. Since literals match by text, the failure is the same rule's.

## 5. Fix Strategy
`luacats.bnf`, on top of [[BUG-542]] (`'?'?`) and [[BUG-544]] (`optionalType`):
```
functionSignatureArgument ::= (<<ArgName argumentName>> '?'? <<ArgSymbol (':')>> <<ArgType optionalType>>)
                            | <<ArgType optionalType>>
```
- The named form is tried first. `fun(table)` fails at `:` without a pin, rolls back, and the
  type-only alternative takes `table`.
- **Accessor change** (predicted; verify in the regen diff of `LuaCatsFunctionSignatureArgument.java`):
  - `getArgName()` and `getArgSymbol()` go from `@NotNull` to `@Nullable`.
  - `getArgType()` stays `@NotNull`, because it is required in both branches. Grammar-Kit already
    gives `LuaCatsType.getUnionType()` `@NotNull` for the same shape at `:177`.

Kotlin consumers must compile against **both** accessor sets, because `generate.sh` compiles before it
regenerates. A safe call on a `@NotNull` Java getter is only a warning, so edit these before
regenerating:
- `LuaParameterInfoHandler.kt:182` and `:228`: `it.argName.text` becomes `it.argName?.text ?: "_"`.
- `TypeParser.parseFunctionArgument` (BUG-542; today `TypeParser.kt:117`): the name is
  `arg.argName?.text ?: "_"`.
  - `"_"` is the placeholder for a positional parameter.
  - `LuaTypeInlayHintProvider.kt:47` already suppresses name hints for `_`.

No other main-source consumer reads `LuaCatsFunctionSignatureArgument.argName`/`argSymbol` (grep of
`functionSignatureArgumentList` over `src/main/kotlin`).

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testUnnamedFunParameter`: `---@type fun(table)` before `local h`. The type is `fun(table)`.
  - `testUnnamedFunParameterInCast`: the corpus form `---@cast builders (string|fun(LazyPlugin))[]`
    after `local builders`. No errors; `doTest` only, because the trailing `[]` is prose (batch plan §8).
  - Control `testParameterWithColonButNoTypeIsStillAnError`: `---@type fun(a:)`, with
    `expectErrors = true`.
- `TestTypeParser.testUnnamedFunParameterIsPositional`: `fun(table)` gives `params[0].name == "_"`
  and `params[0].type == LuaPrimitiveType.TABLE`.
- `LuaParameterInfoHandlerTest.testOverloadUnnamedParameter`: copy `testOverloadSupport` with
  `--- @overload fun(string): void`. The second candidate's `params` is `["_"]` and its `types` is
  `["string"]`. On HEAD there is only one candidate.
