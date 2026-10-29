---
id: "BUG-543"
title: "A numeric key (`[1]`) in a `@field` name or a `@field` table type is a LuaCATS parse error"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-543: `---@field [1] string` and `{[1]:string}` under `@field` do not parse

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim`. A bug report: reproduction executed, root
cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`): each snippet opened with `myFixture.configureByText`, then
`PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)` collected. Executed 2026-10-28
on `5dd675a4` with a throwaway probe test (not committed).

| Snippet (followed by a `local` host) | Errors |
| :-- | :-- |
| `---@field [1] string` | `<type> expected, got '1'` |
| `---@field [1] string?` | `<type> expected, got '1'` |
| `---@field p {[1]:string}` | `<type> expected, got '1'` |
| `---@field p (string\|{[1]:string, [string]:any})[]` | `<type> expected, got '1'` |
| `---@alias Q8 {[1]:string, [2]:string\|fun()}[]` | `CODE, DASHES, NAME, NUMBER, STRING, SYMBOL or zzz expected, got 'fun'` |

Controls from the same run, no errors: `---@field one string`; `---@alias P3 {[1]:string, [string]:any}`;
`---@alias Q9 {[1]:string, [3]?:string}[]`; `---@alias Q10 {a:string, b:string|fun()}`.

## 2. Expected vs Actual
- **Expected**: no `PsiErrorElement`. `[1]` is a valid LuaCATS field key and table-type key;
  LuaLS accepts every snippet above.
- **Actual**: one `PsiErrorElement` inside the LuaCATS comment. A numeric key parses in an `@alias`
  table type but not under `@field`; the `@alias` failure needs both a numeric key and a later
  `|fun()` member.

## 3. Context
- **Corpus sites** (`lazynvim.baseline` `parseErrorFile=` lines): `lua/lazy/core/handler/keys.lua:13`,
  `manage/runner.lua:6`, `manage/semver.lua:4`, `types.lua:52` (`<type> expected, got '1'`), and
  `view/render.lua:630` (the `@alias` form).
- **Corpus effect**: each affected file counts once in `parseErrors` and once in
  `oracleDisagreements`. A fix lowers both in `src/test/resources/corpus/lazynvim.baseline`, and the
  fixing change re-records it.

## 4. Root Cause
Read from code, not executed. There are two gaps, one in the lexer and one in the grammar.

- **Lexer (every `@field` row).** `<TAG_FIELD>` (`luacats.flex:161-168`) has no `{NUMBER}` rule. A
  lone `1` is not a `NAME`, because `NAME` (`:73`) needs `{DIGIT}+{NAME_TRAILING}+`. So `1` falls to
  the catch-all `.` (`:167`), becomes `TEXT`, and switches to `COMMENT_DATA`. `<TAG_TYPE>` has the
  rule (`:218`), which is why `@alias` lexes `[1]`.
- **Grammar (multi-entry tables).** A bracket key is accepted only by
  `dictionaryType ::= '{' '[' type ']' ':' type '}'` (`luacats.bnf:192`), which allows exactly one
  entry. `tableLiteralEntry` (`:195`) accepts only a `simpleType` key. So
  `{[1]:string, [string]:any}` fails in every tag.
  - Under `@alias` it fails silently: `description` swallows it (batch plan §2), which is why the P3
    and Q9 controls show no error.
  - Q8 and `view/render.lua:630` do error. Their fallback `description` stops at the `fun` token,
    because `KEYWORD` is not a `description` token (`:214`).

## 5. Fix Strategy
1. `luacats.flex`, `<TAG_FIELD>`: add `{NUMBER} { return LCATS_NUMBER; }` immediately before
   `{NAME}`. This is the same position as in `<TAG_TYPE>` (`:218-219`), so the `1.` tie with `NAME`
   resolves to `NUMBER` in both states.
2. `luacats.bnf`, on top of [[BUG-542]]'s edit, which this change requires because `render.lua:630`
   has `[3]?:`:
   ```
   tableLiteralEntry ::= (simpleType | fieldKeyDescriptor) '?'? ':' type
   ```
   This reuses the existing public `fieldKeyDescriptor ::= '[' type ']'` (`:113`) instead of adding a
   rule. A new public rule would add an element type to the PSI parent chain, and a private inline
   `'[' type ']'` would turn `getType()` into `getTypeList()`.
   - **Predicted accessor change** (verify in the regen diff): `LuaCatsTableLiteralEntry` gains
     `@Nullable getFieldKeyDescriptor()`.
   - `getType()` stays a single `@NotNull`, because the key's own `type` is nested inside
     `LuaCatsFieldKeyDescriptor`.
   - `dictionaryType` is unchanged and still wins for the single-entry form, since `distinctType`
     tries it first (`:182-183`).
3. `TypeParser.parseTableLiteral` (introduced by BUG-542): skip entries where
   `PsiTreeUtil.getChildOfType(entry, LuaCatsFieldKeyDescriptor::class.java) != null`. This compiles
   against both accessor sets.
   - Without the skip, `getNamedType()` and `getBuiltinType()`, which are direct-child lookups,
     return null for a bracket key. Every bracketed entry would then collapse into one member named
     `"unknown"` (`TypeParser.kt:108`).

4. `luacats.bnf`, the key branch of `fieldDescriptor`: `<<ArgType (fieldKeyDescriptor '?'?)>>`. The name
   branch already takes `name?`; the key branch took no marker, so `---@field [2]? T`
   (`core/handler/keys.lua:14`) was a parse error. The marker sits inside `argType`, as it sits inside
   `argName` for a name, so `LuaCatsDeclarations.fieldMember` drops it and admits nil the same way.
   Tests: `LuaCatsParserTest.testOptionalBracketKeyFieldName` (control
   `testDoubleMarkedBracketKeyIsStillAnError`) and `LuaCatsDeclarationsTest.testOptionalKeyedFieldAdmitsNil`.

## 6. Test Strategy
- `LuaCatsParserTest`, with `assertTagType` from the batch plan §3:
  - `testNumericFieldKey`: `---@class K` / `---@field [1] string lhs` / `local K = {}`. The
    `LuaCatsFieldTag` type is `string`.
  - `testNumericKeysInFieldTableType`: `---@field p {[1]:string, [string]:any}`. The type is
    `{[1]:string, [string]:any}`.
  - `testNumericKeysInAliasTableType`: `---@alias LazyProps {[1]:string, [2]:string|fun(), [3]?:string}[]`.
    The type is the whole text after the name. This fails on HEAD with an error at `fun`.
  - Control `testBracketKeyWithoutColonIsStillAnError`: `---@field p {[1]string}`, with
    `expectErrors = true`.
- `TestLuaCatsLexer.testField`: `[1] string` in `TAG_FIELD` lexes as `[` `SYMBOL`, `1` `NUMBER`,
  `]` `SYMBOL`, whitespace, `string` `NAME`.
- `TestTypeParser.testBracketKeyedEntriesAreNotMembers`: `{[1]: string, a: number}` gives a
  `LuaTableLiteralType` whose `localMembers.keys` is `{"a"}`.
- `manage/runner.lua:6` writes `(string|{…})[]`. There, `[]` after a parenthesised type still lands
  in `description` (`:177`, `:179`). That is not an error, and it is not changed here (batch plan §8).
