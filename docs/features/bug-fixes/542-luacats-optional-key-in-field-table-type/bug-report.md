---
id: "BUG-542"
title: "An optional key (`name?:`) in a `@field` table type, or an optional `fun` parameter in `@overload`, is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
Read from code, not executed. Two rules have no slot for the `?` marker:

- **Table-type key**: `tableLiteralEntry ::= simpleType ':' type` (`luacats.bnf:195`). After the key
  `file`, the parser demands `':'` and meets `?`, so `literalTableType` (`:193`) fails, then `type`
  (`:177`) and the whole `fieldTag` (`:108`), whose `<<ArgType type>>` is required.
- **`fun` parameter**: `functionSignatureArgument ::= <<ArgName argumentName>> <<ArgSymbol (':')>>
  <<ArgType type>>` (`:198`). `@overload` reaches it through `overloadFunctionSignature` (`:134-139`).

The `@alias` control passes only because `aliasTag`'s type is optional and `description` swallows
the failed type as prose (`:82`, `:214`). The control's PSI has no `argType` at all. This mechanism is
described once, in the batch plan §2.

[[BUG-502]] is the same defect. Its `@param` falls back to `ldocParamTag` (`:153`) rather than
reporting an error. The fix below is the fix for both reports.

## 5. Fix Strategy
`luacats.bnf`, no lexer change (`?` is already a `SYMBOL` in every type-bearing state, `luacats.flex:74`):

```
tableLiteralEntry ::= simpleType '?'? ':' type
functionSignatureArgument ::= <<ArgName argumentName>> '?'? <<ArgSymbol (':')>> <<ArgType type>>
```

The bare `'?'` leaf is deliberate. Wrapping it in `<<ArgSymbol>>` would turn
`LuaCatsFunctionSignatureArgument.getArgSymbol()` into a list. **No accessor changes**:
`tokenAccessors="no"` (`:17`), so a literal adds no method.

Kotlin, same commit, `lang/psi/types/TypeParser.kt`. Both edits compile against the old and new
accessor sets because they use only `ASTNode` and existing classes:
- `:121`: `arg.children` skips leaves (`ASTDelegatePsiElement.getChildrenSkippingLeaves`), so the
  existing `?` check can never be true. Replace it with
  `arg.node.getChildren(null).any { it.text == "?" }`, and move the per-argument mapping into
  `private fun parseFunctionArgument(arg: LuaCatsFunctionSignatureArgument, context: PsiElement): LuaParameter`.
- `:105-113`: move the table branch into
  `private fun parseTableLiteral(tableLiteral: LuaCatsLiteralTableType, context: PsiElement): LuaType`.
  When an entry has a direct `?` leaf (`entry.node.getChildren(null)`), store the member type as
  `LuaUnionType(setOf(valType, LuaPrimitiveType.NIL))`, the same widening BUG-401 applies to
  `@field name?` (`LuaCatsDeclarations.kt:53-55`).

Without the first edit, `fun(opts?:T)` would parse as a required parameter. `LuaFunctionType` from a
`fun(...)` is `declaredSignature = true` (`TypeParser.kt:133`, BUG-419), so every zero-argument call
would get an arity warning.

## 6. Test Strategy
Each positive test fails on HEAD. The `@alias` case fails because `argType` is null there, not
because an error exists.
- `LuaCatsParserTest` (`src/test/kotlin/.../luacats/lang/parser/`), using the `assertTagType` helper
  that the batch plan §3 defines:
  - `testOptionalKeyInFieldTableType`: `---@class P1` / `---@field notifs {msg:string, file?:string}[]`
    / `local P1 = {}`. The `LuaCatsFieldTag` type text is `{msg:string, file?:string}[]`.
  - `testOptionalKeyInAliasTableType`: `---@alias Q5 {name:string, file?:string}`. The type text is
    `{name:string, file?:string}`.
  - `testOptionalOverloadParameter`: `---@overload fun(opts?:table):table` before
    `local function f(o) end`. No errors, and `overloadFunctionSignature.functionSignatureArgumentList`
    has size 1.
  - Control `testTableKeyWithoutColonIsStillAnError`: `---@field p {file?string}`, with `expectErrors = true`.
- `TestTypeParser` (`src/test/kotlin/.../lang/types/`):
  - `testOptionalFunParameter`: `fun(opts?: table)` gives `params[0]` named `opts` with
    `isOptional == true`.
  - `testOptionalTableKeyAdmitsNil`: `{a?: string}` gives a `LuaTableLiteralType` whose member `a`
    is a union of `string` and `nil`.
- The BUG-502 regression test lives in that report.
