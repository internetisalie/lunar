---
id: "BUG-548"
title: "A string-literal type or string-literal union (`\"a\" | \"b\"`) in a `@field` is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-548: `---@field source "lazy" | "packspec" | "rockspec"` does not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated. Design §3.2 named the class "spaced string-literal union"; the probe below
shows the spacing is irrelevant and a single literal fails too.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (after `---@class P4`, before `local P4 = {}`) | Errors |
| :-- | :-- |
| `---@field source "lazy" \| "packspec"` | `2:<type> or '?' expected, got '"'` |
| `---@field source "lazy"\|"packspec"` | `2:<type> or '?' expected, got '"'` |
| `---@field source "lazy"` | `2:<type> or '?' expected, got '"'` |

Controls from the same run, no errors: `---@alias K "a" | "b"` and `---@param k "a" | "b"`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. A string literal is a valid LuaCATS type in `@field` as in
  `@alias` and `@param`.
- **Actual**: under `@field`, any type starting with a string literal is an error.

## 3. Context
- **Corpus site** (`lazynvim.baseline` `parseErrorFile=` line): `lua/lazy/pkg/init.lua:11`.
- **Related, not established**: [[BUG-514]] reports a string-literal union rejected in `@return`.
- **Corpus effect**: the file counts once in `parseErrors` and once in `oracleDisagreements`. A fix
  lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the fixing change re-records it.

## 4. Root Cause
Read from code, not executed. This is a lexer gap. `<TAG_FIELD>` (`luacats.flex:161-168`) has no
`{STRINGD}`/`{STRINGS}` rule, so the opening `"` falls to the catch-all `.` (`:167`). It becomes
`TEXT`, and the rest of the line becomes one `COMMENT_DATA` `TEXT` (`:125`).
`literalType ::= STRING | …` (`luacats.bnf:190`) needs a `STRING` token. `<TAG_TYPE>` has both rules
(`:220-221`), which is why `@alias` works.

The `@param` control is not a genuine parse. `<TAG_PARAM>` (`:187-193`) has the same gap, so
`paramTag` fails and the line falls back to `ldocParamTag` (`luacats.bnf:153`) as prose. That is a
separate defect and is not fixed here (batch plan §8).

[[BUG-514]]'s `@return` literal is the same gap in `<TAG_RETURN>`. It is fixed in that report's own
commit.

## 5. Fix Strategy
`luacats.flex`, `<TAG_FIELD>`: add, before `{TYPES}`,
```
    {STRINGD}               { return LCATS_STRING; }
    {STRINGS}               { return LCATS_STRING; }
```
These are the same two macros and the same order as `<TAG_TYPE>`, so a string-literal type has one
lexical definition across states.
- **No grammar or accessor change.**
- Side effect: quoted prose in a `@field` description now lexes as `STRING` rather than `TEXT`.
  `description` accepts `STRING` (`luacats.bnf:214`).
- After a string, lexing stays in `TAG_FIELD` instead of falling to `COMMENT_DATA`. So a later
  `private`/`protected`/`public` word lexes as `KEYWORD`, which `description` rejects. Unquoted prose
  already has that hazard today. The batch plan §6 corpus check is what would catch a new instance.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testStringLiteralUnionFieldType`: `---@class P4` / `---@field source "lazy" | "packspec"` /
    `local P4 = {}`. The type is `"lazy" | "packspec"`.
  - `testSingleStringLiteralFieldType`: `---@field source "lazy"`. The type is `"lazy"`.
  - Control `testUnterminatedStringFieldTypeIsStillAnError`: `---@field source "lazy`, with
    `expectErrors = true`.
- `TestLuaCatsLexer.testField`: `x "a"|'b'` in `TAG_FIELD` lexes as `x` `NAME`, whitespace, `"a"`
  `STRING`, `|` `SYMBOL`, `'b'` `STRING`.
