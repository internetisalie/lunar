---
id: "BUG-544"
title: "An optional `fun` return type (`fun():boolean?`) inside a LuaCATS table type is a parse error"
type: "bug"
parent_id: "BUG"
status: "done"
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

## 4. Root Cause
Read from code, not executed. The type grammar has no optional suffix anywhere:
- `type ::= '(' unionType ')' | unionType` (`luacats.bnf:177`)
- `unionType ::= arrayType { '|' arrayType }*` (`:178`)
- `arrayType ::= distinctType '[]'?` (`:179`)

`T?` works at tag top level only by accident. The type ends before `?`, and the tag's trailing
`description` (`:214`) takes the `?` as prose. Inside a table type, the next token must be `,` or
`}` (`:193-195`), so the `?` is an error. This matches the reported variant list:
`',', <, '[]', '|' or '}'`.

The three controls are not evidence that `T?` is supported:
- `S2` (`fun(p:table):boolean?` at top level) parses with `?` as description.
- `Q1` (`{a:string?}`) fails silently under `@alias` (batch plan §2).
- Only the no-`?` control is a genuine parse.

**Same rule as [[BUG-545]].** Both failures sit in
`functionSignatureReturnType ::= <<ArgSymbol (':')>> <<ArgType type>>` (`:200`). That rule accepts
neither a `?` suffix (this report) nor a parenthesised return list (BUG-545). The corpus site of
BUG-545 (`fun():(LazyPluginSpec?, string?)`) needs both. One edit to `:200` fixes both reports,
delivered as one commit.

## 5. Fix Strategy
`luacats.bnf`. Two new **private** helpers, so no PSI element type is added:

```
private optionalType ::= type '?'?
private functionReturnTypes ::= '(' optionalType { ',' optionalType }* ')' | optionalType
functionSignatureReturnType ::= <<ArgSymbol (':')>> <<ArgType functionReturnTypes>>
tableLiteralEntry ::= (simpleType | fieldKeyDescriptor) '?'? ':' optionalType   // was ': type'
dictionaryType ::= '{' '[' type ']' ':' optionalType '}'                        // was ': type'
functionSignatureArgument ::= <<ArgName argumentName>> '?'? <<ArgSymbol (':')>> <<ArgType optionalType>>
```

The suffix applies **only where a type is nested in another type**: table values, dictionary values,
`fun` parameter types and `fun` returns. A tag's own top-level type keeps today's behaviour, with
`?` going to `description`.
- That boundary is deliberate. `LuaTypeGraphBridge.isSimpleTypeName` (`LuaTypeGraphBridge.kt:20`)
  treats any text without `<>|()[]` as a single class name.
- A top-level `string?` in `argType.text` would therefore resolve as a class literally named
  `string?` and silently lose every optional annotation's type.

Ordering in `functionReturnTypes`:
- The list alternative comes first and also covers `fun():(A|B)`. For that input,
  `optionalType` → `type` → `unionType` reads `A|B`, and the first `LuaCatsType` under the
  `argType` is the same union as before.
- `TypeParser.kt:126-130` reads the return as that first descendant `LuaCatsType`, so a list
  contributes its first value. That matches the nominal layer's single return
  (`LuaCatsDeclarations` "Only the first").

**No accessor changes.** The helpers are private and inline into their callers, and every
`type`/`argType` child keeps its arity. Consumer text changes:
- `argType.text` of a `fun` parameter now includes a trailing `?`. This is visible in
  `LuaParameterInfoHandler.kt:183,229` as the declared type string, which matches what was written.
- `TypeParser.parseFunctionArgument` (BUG-542) extends `isOptional` with
  `|| arg.argType.text.trimEnd().endsWith("?")`. A nil-admitting parameter may be omitted.
- When [[BUG-514]] later makes `argType` nullable, this becomes `arg.argType?.text`. The safe call
  compiles against both accessor sets, so it can be written that way here.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testOptionalFunReturnInTableType`: `---@alias S1 {title:string, filter:fun(p:table):boolean?}`.
    The type is the full table text.
  - `testOptionalFunReturnAtTopLevelBelongsToTheType`: `---@alias S2 fun(p:table):boolean?`. The type
    is `fun(p:table):boolean?`. On HEAD the type is `fun(p:table):boolean`.
  - `testOptionalValueInTableType`: `---@alias Q1 {a:string?}`. The type is `{a:string?}`.
  - `testTopLevelOptionalStaysOutsideTheType`, a behaviour-preservation control: `---@type string?`.
    The type is `string`. It passes before and after, and pins the boundary above.
  - Control `testDoubleOptionalIsStillAnError`: `---@field p {a:string??}`, with `expectErrors = true`.
- `TestTypeParser.testNilableFunParameterIsOptional`: `fun(a: string?)` gives `params[0].isOptional`.
- BUG-545's return-list cases live in that report.
