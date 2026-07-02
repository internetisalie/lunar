---
id: MAINT-16
title: "MAINT-16: Test Coverage - LuaCATS Syntax & Highlighting"
type: feature
parent_id: MAINT
status: done
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-16: Test Coverage - LuaCATS Syntax & Highlighting

## Overview
Increase unit-test coverage for the LuaCATS type-annotation subsystem: the lazy-comment
tag-query accessors, the syntax-highlighter token→attribute mapping, the semantic
annotator's under-covered highlight branches, and the Quick-Documentation HTML renderer's
under-covered sections (See Also, Deprecated, alias/generic signatures, inherited fields).

This is a **coverage-only** feature. No production behaviour changes; every test targets an
already-shipped class. The three test-code targets are all under
`net.internetisalie.lunar.luacats.lang.*`.

Parent epic: [MAINT](../requirements.md).

## Scope

### In Scope
- Unit tests for the tag-query accessors of `LuaCatsLazyCommentImpl`
  (`src/main/kotlin/.../luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt:12`) that are
  currently unexercised — `getAliasTagList`, `getSeeTagList`, `getOverloadTagList`,
  `getGenericTagList`, `getVersionTagList`, `getDeprecatedTagList`, `getTypeOptionList`,
  `getFieldTagList`, `getParamTagList` — asserting count and inner tag text.
- Unit tests for `LuaCatsSyntaxHighlighter.getTokenHighlights`
  (`src/main/kotlin/.../luacats/lang/syntax/LuaCatsSyntaxHighlighter.kt:42`) mapping
  token types (TAG, NAME, SYMBOL, CONTENT) to `LuaCatsHighlight` attribute keys, and its
  `getHighlightingLexer` returning a `LuaCatsLexer`.
- Unit tests for the under-covered branches of `LuaCatsAnnotator.annotate`
  (`src/main/kotlin/.../luacats/lang/syntax/LuaCatsAnnotator.kt:20`): `LuaCatsArgSymbol`→
  `SYMBOL`, `LuaCatsArgValue`→`VALUE`, `LuaCatsParameterName`→`NAME`,
  `LuaCatsFieldNameDescriptor`→`NAME`, and bracket punctuation `(` `[` `{`→`BRACKETS`.
- Unit tests for the under-covered sections of `LuaCatsDocumentationRenderer.renderDoc`
  (`src/main/kotlin/.../luacats/lang/doc/LuaCatsDocumentationRenderer.kt:45`): See-Also
  (URL and plain-reference forms), Deprecated section, `@return name` naming, generic
  type-parameter signature block, local-function signature, and the null-return contract
  for an unsupported element.

### Out of Scope
- Editor fonts, theme colour values, and the concrete `TextAttributes` RGB (only the
  `TextAttributesKey` identity is asserted).
- The LuaCATS **lexer** state machine — already thoroughly covered by
  `TestLuaCatsLexer.kt` (17 tests over every lexer state). Not re-tested here.
- The LuaCATS **parser** error/no-error contract — already covered by
  `LuaCatsParserTest.kt` (21 tests). Not re-tested here.
- Existing semantic-highlighting cases (tags, params, complex/literal types, deprecated)
  already covered by `LuaCatsSemanticHighlightingTest.kt`. Only the annotator branches it
  omits are added.
- Type inference / resolution of LuaCATS types (a separate subsystem with its own tests).

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-16-01 | **Lazy-comment tag queries** | Must | done | For a Lua file whose leading `---@` block declares multiple tag kinds, `LuaCatsLazyCommentImpl`'s `get*TagList()` accessors return the expected non-empty lists, and the inner tag PSI exposes the expected identifier/type text. |
| MAINT-16-02 | **Syntax-highlighter token mapping** | Must | done | `LuaCatsSyntaxHighlighter.getTokenHighlights` maps each token type in a `LuaCatsSyntax` TokenSet to the matching `LuaCatsHighlight` key, an unmapped token type yields an empty array, and `getHighlightingLexer` returns a `LuaCatsLexer`. |
| MAINT-16-03 | **Annotator uncovered branches** | Must | done | `LuaCatsAnnotator` applies `SYMBOL` to `@cast` operator symbols, `VALUE` to enum option values, `NAME` to overload parameter names and field-key names, and `BRACKETS` to `(` `[` `{` grouping punctuation. |
| MAINT-16-04 | **Documentation renderer sections** | Must | done | `LuaCatsDocumentationRenderer.renderDoc` emits a See-Also section (URL becomes an `<a href>`, a plain reference becomes `<code>`), a Deprecated section with the `⚠ Deprecated:` header, a named-return row, a generic `<T>` type-param block, a local-function signature, and returns `null` for an unsupported element. |

## Detailed Test Approach

### MAINT-16-01 — `LuaCatsLazyCommentImpl` accessors
Light `BaseDocumentTest`; `configureByText(...)` a `.lua` file, obtain the comment via the
host decl's `catsComment` (`LuaCommentOwner.catsComment`), then assert accessor results
inside `runReadAction`. The comment is a `LazyParseablePsiElement`, so tag PSI is realised
on first `PsiTreeUtil` walk — the accessors themselves force parsing. Assert list sizes and
inner text (e.g. `getSeeTagList().first().argName.text == "http.get"`).

### MAINT-16-02 — `LuaCatsSyntaxHighlighter`
Pure unit test (no fixture needed): instantiate `LuaCatsSyntaxHighlighter()` and call
`getTokenHighlights(LuaCatsElementTypes.TAG)` etc., asserting the returned array's single
element `externalName` equals the expected `LuaCatsHighlight` key. Assert an unmapped type
(e.g. `LuaCatsElementTypes.WHITESPACE`) returns an empty array, and `getHighlightingLexer()`
is a `LuaCatsLexer` instance.

### MAINT-16-03 — `LuaCatsAnnotator`
Reuse the `LuaCatsSemanticHighlightingTest` real-flow pattern: `configureByText(...)` then
`myFixture.doHighlighting()`, asserting a `HighlightInfo` exists whose
`forcedTextAttributesKey` equals the expected `LuaCatsHighlight` key and whose `text`
matches. This exercises the annotator end-to-end (the repo DoD gate for annotators).

### MAINT-16-04 — `LuaCatsDocumentationRenderer`
Reuse the `LuaCatsDocumentationRendererTest` pattern: `EdtTestUtil.runInEdtAndWait +
runReadAction`, place `<caret>` on the decl, resolve the `LuaCommentOwner`, call
`LuaCatsDocumentationRenderer.renderDoc(element)`, and assert the returned HTML `contains`
the expected fragments. For the null contract, call `renderDoc` on a `LuaFile` root (not a
`LuaCommentOwner`, class, or alias tag) and assert `null`.

## Test Cases

| # | Req | Given (input `.lua`) | When (action) | Then (expected) |
|---|-----|----------------------|---------------|-----------------|
| 1 | 16-01 | `---@alias Mode "r"\|"w"` + `local m` | `catsComment.aliasTagList` | size 1; `.first().argName.text == "Mode"` |
| 2 | 16-01 | `---@see http.get Fetches data` + `local x` | `catsComment.seeTagList` | size 1; `.first().argName.text == "http.get"` |
| 3 | 16-01 | `---@overload fun(x: string): string` on `function f(x) end` | `catsComment.overloadTagList` | size 1 (non-empty) |
| 4 | 16-01 | `---@generic T` + `---@param x T` + `---@return T` on `function id(x) end` | `catsComment.genericTagList` | size 1; first `genericTypeParams.genericTypeParamList.first().argName.text == "T"` |
| 5 | 16-01 | `---@version >5.2` + `local x` | `catsComment.versionTagList` | size 1 (non-empty) |
| 6 | 16-01 | `---@param id number` + `---@param name string` on `function f(id, name) end` | `catsComment.paramTagList` | size 2; `[0].argName.text == "id"`, `[0].argType.text == "number"` |
| 7 | 16-02 | (none) | `LuaCatsSyntaxHighlighter().getTokenHighlights(LuaCatsElementTypes.TAG)` | array size 1; `[0].externalName == LuaCatsHighlight.TAG.externalName` |
| 8 | 16-02 | (none) | `getTokenHighlights(LuaCatsElementTypes.NAME)` | `[0].externalName == LuaCatsHighlight.NAME.externalName` |
| 9 | 16-02 | (none) | `getTokenHighlights(LuaCatsElementTypes.SYMBOL)` | `[0].externalName == LuaCatsHighlight.SYMBOL.externalName` |
| 10 | 16-02 | (none) | `getTokenHighlights(LuaCatsElementTypes.KEYWORD)` | empty array (KEYWORD is not in any `fillMap` TokenSet) |
| 11 | 16-02 | (none) | `LuaCatsSyntaxHighlighter().highlightingLexer` | is a `LuaCatsLexer` |
| 12 | 16-03 | `---@cast x +string, -nil` + `local x` | `doHighlighting()` | `+` highlighted `LuaCatsHighlight.SYMBOL` |
| 13 | 16-03 | `---@enum E` + `---\| "A" # first` + `local E = {}` | `doHighlighting()` | `"A"` highlighted `LuaCatsHighlight.VALUE` |
| 14 | 16-03 | `---@overload fun(objectID: integer): boolean` on `function f(objectID) end` | `doHighlighting()` | `objectID` highlighted `LuaCatsHighlight.NAME` |
| 15 | 16-03 | `---@class C` + `---@field [string] integer` + `local C = {}` | `doHighlighting()` | `(` highlighted `LuaCatsHighlight.BRACKETS` |
| 16 | 16-04 | `---@see https://lua.org The manual` on `function f() end` | `renderDoc(f)` | HTML contains `<a href="https://lua.org">` and `See Also:` |
| 17 | 16-04 | `---@see other.func Related` on `function f() end` | `renderDoc(f)` | HTML contains `<code>other.func</code>` (plain reference, no `<a href`) |
| 18 | 16-04 | `---@deprecated Use bar instead` on `function foo() end` | `renderDoc(foo)` | HTML contains `⚠ Deprecated:` and `Use bar instead` |
| 19 | 16-04 | `---@return number count The total` on `function f() end` | `renderDoc(f)` | HTML contains `Returns:`, the type `number`, and `<code>count</code>` |
| 20 | 16-04 | `---@generic T` + `---@param x T` + `---@return T` on `function id(x) return x end` | `renderDoc(id)` | HTML definition block contains `&lt;`/`>` type-param delimiter and `T` |
| 21 | 16-04 | `---@param n number` on `local function f(n) end` | `renderDoc(f)` | HTML contains `local function` and `f` |
| 22 | 16-04 | plain `local x = 1` (no cats comment) | `renderDoc(luaFileRoot)` | returns `null` |

## Acceptance Criteria
- [ ] MAINT-16-01: TC 1–6 — every listed accessor returns the expected list/text.
- [ ] MAINT-16-02: TC 7–11 — token→attribute mapping and lexer type verified.
- [ ] MAINT-16-03: TC 12–15 — annotator SYMBOL/VALUE/NAME/BRACKETS branches highlighted.
- [ ] MAINT-16-04: TC 16–22 — renderer See/Deprecated/named-return/generic/local/null covered.
- [ ] `tooling/gce-builder/gce-builder.sh run test` is green (no regression).

## Non-Functional Requirements
- **Threading**: PSI/highlighting reads run inside `runReadAction` (and EDT via
  `EdtTestUtil.runInEdtAndWait` for renderer/highlighting flows), matching the existing
  `LuaCatsDocumentationRendererTest`/`LuaCatsSemanticHighlightingTest`.
- **Fixtures**: light `BaseDocumentTest` / `BasePlatformTestCase`; no hand-built PSI mocks.
- **Speed**: `configureByText`-based; no stub-index rebuild required (none of these tests
  need cross-file resolution — the inherited-fields renderer branch is left out of scope
  because it requires an indexed parent class).

## Dependencies
- `net.internetisalie.lunar.luacats.lang.psi.impl.LuaCatsLazyCommentImpl` (accessors).
- `net.internetisalie.lunar.luacats.lang.syntax.LuaCatsSyntaxHighlighter` /
  `LuaCatsAnnotator` / `LuaCatsHighlight`.
- `net.internetisalie.lunar.luacats.lang.doc.LuaCatsDocumentationRenderer`.
- `net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes` (gen).
- No new `plugin.xml` extension points (test-only feature).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
