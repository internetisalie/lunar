---
id: "BUG-545"
title: "A parenthesised `fun` return list (`fun():(A?, B?)`) inside a LuaCATS table type is a parse error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-545: `{load: fun():(LazyPluginSpec?, string?)}` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (followed by a `local` host) | Errors |
| :-- | :-- |
| `---@type {modname: string, load: fun():(string?, string?)}[]` | `')', <, '[]' or '\|' expected, got '?'` |
| `---@alias Q3 {load: fun():(string?, number?)}` | `')', <, '[]' or '\|' expected, got '?'` |
| `---@alias Q4 {load: fun():(string, number)}` | `')', <, '[]' or '\|' expected, got ','` |

Controls from the same run, no errors: `---@type fun():(string?, number?)` and
`---@type fun():string?, number?` (the return list outside a table type).

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. A parenthesised multi-value return list is valid LuaLS syntax
  for a `fun` type, inside a table type as elsewhere.
- **Actual**: inside a table type the list is read as a single parenthesised type, so the parser
  stops at the first `?` or `,`.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/core/plugin.lua:149`.
- **Possibly the same defect as [[BUG-544]]** (an optional return inside a table type); not
  established.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`
  (`core/plugin.lua` also has a [[BUG-542]] site, so one fix alone may not move the file count). A
  fix re-records `src/test/resources/corpus/lazynvim.baseline`.

## 4. Root Cause
Read from code, not executed. The return type is a single `type`:
`functionSignatureReturnType ::= <<ArgSymbol (':')>> <<ArgType type>>` (`luacats.bnf:200`).
`type ::= '(' unionType ')'` (`:177`) reads `(string` and then demands `)`. That produces the two
reported variant lists:
- `got '?'`, because `type` has no optional suffix ([[BUG-544]]).
- `got ','`, because no rule accepts a return list.

The `@type` controls are not genuine parses:
- `functionSignatureType` (`:196`) treats its return as optional, so `fun()` ends early.
- `typeTag`'s `description` (`:165`, `:214`) then takes `:(string?, number?)` as prose (batch plan §2).
- Inside a table type, the `}` the table needs exposes the failure.

**The same rule as BUG-544. Both reports share one fix and one commit.**

## 5. Fix Strategy
See [[BUG-544]] §5. Its `functionReturnTypes` helper covers both the parenthesised list and the
`?` suffix inside the list. Nothing else is needed for this report.

The unparenthesised form `fun():string?, number?` stays as it is: the first return belongs to the
`fun` and the rest goes to the tag's prose. Inside a table type that form is ambiguous with the entry
separator, and the parenthesised form is what `lazy.nvim` writes.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testParenReturnListInTableType`: `---@alias Q3 {load: fun():(string?, number?)}`. The type is the
    full table text.
  - `testParenReturnListWithoutOptionals`: `---@alias Q4 {load: fun():(string, number)}`. The type is
    the full table text.
  - `testParenReturnListAtTopLevel`:
    `---@type {modname: string, load: fun():(string?, string?)}[]` before `local x`. The type is the
    full text.
  - Control `testEmptyReturnListIsStillAnError`: `---@field p {load: fun():()}`, with
    `expectErrors = true`.
- `TestTypeParser.testParenReturnListTypesTheFirstValue`: `fun(): (string, number)` gives a
  `LuaFunctionType` whose `returnType` is `string`.
