---
id: "BUG-514"
title: "Lunar's LuaCATS parser rejects a string-literal union in @return and a vararg parameter inside a fun() type"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-514: two valid LuaCATS forms produce parse errors

Found while planning [[TARGET-12]] and [[BUG-513]]. The `lua-5.1` stubs use both forms.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`). Each snippet is opened with `myFixture.configureByText`,
and then `PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` is collected. Executed
2026-10-15 on `c24bbbff` (source identical to `84fa67b0`), as TARGET-12 run `r19`:

```lua
---@return "nil"|"boolean"|"number"
function f1(v) end
```
→ `errors=[<type> expected, got '"']`

```lua
---@overload fun(repl: fun(...: any): string): string
function f4(r) end
```
→ `errors=[')' or NAME expected, got '.']`

Controls from the same run parse with no errors:
- a string literal as an `@overload` parameter type,
  `---@overload fun(opt: "stop"): nil`;
- `---@return any ...`;
- `---@type function[]`.

## 2. Expected vs Actual
- **Expected**: both snippets parse with no `PsiErrorElement`. A string-literal union is a valid
  LuaCATS return type, and `...: T` is a valid vararg parameter in a `fun()` type. LuaLS accepts
  both.
- **Actual**: each produces one `PsiErrorElement` inside the LuaCATS comment.

## 3. Context
- **Where it shows up**: `runtime/standard/lua-5.1/builtin.lua:92`, `coroutine.lua:48` and `io.lua:144`
  (string-literal unions in `@return`), and `string.lua:66` (vararg in `fun()`). TARGET-12's LuaJIT
  copies of those files inherit the errors.
- **Impact measured so far**: none of these errors stops a declaration from resolving
  (`TARGET-12-00-DR-14`). What else the parse failure costs, such as the return type of `type()` or
  `gsub`'s callback typing, is not established.
- **Not the same as**: TYPE-14's "Q4 vararg generics" (`docs/features/type/14-generics-baseline/risks-and-gaps.md`),
  which is about typing `...` positions, not this parse error.

## 4. Root Cause
Read from code, not executed. There are two independent gaps.
- **String literal in `@return`** (lexer). `<TAG_RETURN>` (`luacats.flex:195-201`) has no
  `{STRINGD}`/`{STRINGS}` rule, so `"` falls to the catch-all `.` (`:200`) as `TEXT`.
  `returnTypeDescriptor` (`luacats.bnf:159`) needs a `type`, and `literalType` (`:190`) needs a
  `STRING` token. This is the same gap as [[BUG-548]]'s, in a different state.
- **Vararg `fun` parameter** (lexer and grammar).
  - `<TAG_TYPE>` (`flex:215-224`) has no `{ELLIPSIS}` rule (`:42`, used only by `TAG_PARAM`/`TAG_RETURN`).
    `...` therefore lexes as a `TEXT` `.` that switches to `COMMENT_DATA`, which is the reported
    `')' or NAME expected, got '.'`.
  - Even when lexed, `argumentName ::= NAME` (`bnf:199`) excludes it.
  - `TypeParser.kt:118` (`isVararg = argName == "..."`) shows that the name slot was meant to hold `...`.

**Scope boundary.** The stub lines §3 cites now read differently from the repro snippets (checked at
`8008739e`):
- `builtin.lua:92`, `coroutine.lua:48` and `io.lua:141` write `'"nil"'|'"boolean"'|…`. That is an
  apostrophe-quoted string containing double quotes, and `STRINGS = {APOS}{NAME}{APOS}` (`flex:76`)
  lexes it in no state.
- `string.lua:73` and `debug.lua:54` write `fun(...any)`, with no colon.

The fix below makes the documented forms (§1) parse. It does not change how those stub lines parse.
Rewriting the stubs is tracked in the batch plan §8, not done here.

## 5. Fix Strategy
`luacats.flex`:
- `<TAG_RETURN>`: add `{STRINGD}` and `{STRINGS}` → `LCATS_STRING` before `{TYPES}`, mirroring
  `<TAG_TYPE>` (`:220-221`).
- `<TAG_TYPE>` and `<TAG_FIELD>`: add `{ELLIPSIS} { return LCATS_SYMBOL; }` before `{TYPES}`. Longest
  match prefers it over the 1-character catch-all. `<TAG_FIELD>` gets it because a `@field` callback
  type hits the same gap.

`luacats.bnf`, on top of [[BUG-542]], [[BUG-544]] and [[BUG-547]]:
```
private argumentName ::= NAME | '...'
functionSignatureArgument ::= (<<ArgName argumentName>> '?'? <<ArgSymbol (':')>> <<ArgType optionalType>>)
                            | <<ArgName ('...')>>
                            | <<ArgType optionalType>>
```
- The middle branch is the untyped vararg `fun(...)`, which the pinned neovim definitions library
  (`779c3696`) writes, for example `---@param f fun(...) Function to execute…`.

**Accessor change** (predicted; verify in the regen diff): `LuaCatsFunctionSignatureArgument.getArgType()`
goes from `@NotNull` to `@Nullable`, because the middle branch has none. Before regenerating, edit
these so they compile against both sets:
- `LuaParameterInfoHandler.kt:183` and `:229`: `it.argType.text` becomes `it.argType?.text`. The
  `types` lists are already `List<String?>`; see `LuaParameterInfoCandidate` at `:12-17`.
- `TypeParser.parseFunctionArgument`:
  - `PsiTreeUtil.findChildOfType(arg.argType, …)` already accepts null and yields `ANY`.
  - The BUG-544 `isOptional` clause uses `arg.argType?.text`.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testStringLiteralUnionReturn`: the §1 `@return` snippet. The `LuaCatsReturnTypeDescriptor`
    type is `"nil"|"boolean"|"number"`.
  - `testVarargFunParameter`: the §1 `@overload` snippet. No errors (`doTest`).
  - `testUntypedVarargFunParameter`: `---@type fun(...)` before `local f`. The type is `fun(...)`.
  - Controls, each with `expectErrors = true`:
    - `testTwoDotVarargIsStillAnError`: `---@type fun(..: any)`.
    - `testUnterminatedReturnStringIsStillAnError`: `---@return "nil`.
- `TestLuaCatsLexer`:
  - `testReturn`: `"a"|'b'` in `TAG_RETURN` lexes as `STRING`, `SYMBOL`, `STRING`.
  - New `testTypeEllipsis`: `fun(...: any)` in `TAG_TYPE` lexes `...` as one `SYMBOL`.
- `TestTypeParser`:
  - `testVarargFunParameter`: `fun(...: any)` gives `params[0].isVararg`.
  - `testUntypedVarargFunParameter`: `fun(...)` gives `isVararg` with type `ANY`.
- `LuaParameterInfoHandlerTest.testOverloadUntypedVararg`: `--- @overload fun(...): void`. The
  second candidate has `params == ["..."]` and `types == [null]`.
