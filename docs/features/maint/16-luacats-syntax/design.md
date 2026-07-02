---
id: "MAINT-16-DESIGN"
title: "MAINT-16: Design (Test Map)"
type: "design"
parent_id: "MAINT-16"
folders:
  - "[[features/maint/16-luacats-syntax/requirements|requirements]]"
---

# MAINT-16: Design — Test Map

This is a **coverage** feature: no production code changes. The "design" is a map from each
target production symbol to the test that exercises it, with grounded `file:line` evidence
and the exact test approach. All targets already exist and ship today.

## Target Inventory (grounded)

| # | Target symbol | Path:line | Kind |
|---|---------------|-----------|------|
| T1 | `LuaCatsLazyCommentImpl.getAliasTagList / getSeeTagList / getOverloadTagList / getGenericTagList / getVersionTagList / getParamTagList / getTypeOptionList` | `src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/impl/LuaCatsLazyCommentImpl.kt:27,107,83,63,127,91,115` | PSI accessors |
| T2 | `LuaCatsSyntaxHighlighter.getTokenHighlights(IElementType): Array<TextAttributesKey>` | `src/main/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsSyntaxHighlighter.kt:42` | SyntaxHighlighter |
| T3 | `LuaCatsSyntaxHighlighter.getHighlightingLexer(): Lexer` | `.../LuaCatsSyntaxHighlighter.kt:38` | SyntaxHighlighter |
| T4 | `LuaCatsAnnotator.annotate(PsiElement, AnnotationHolder)` — `ARG_SYMBOL`, `ARG_VALUE`, `PARAMETER_NAME`, `FIELD_NAME_DESCRIPTOR`, bracket branches | `.../luacats/lang/syntax/LuaCatsAnnotator.kt:20,41,40,37,38,45` | Annotator |
| T5 | `LuaCatsDocumentationRenderer.renderDoc(PsiElement): String?` — See/Deprecated/named-return/generic/local branches + null path | `.../luacats/lang/doc/LuaCatsDocumentationRenderer.kt:45` | Doc renderer |

Supporting types (all grounded, `src/main/gen/net/internetisalie/lunar/luacats/lang/psi/`):
- `LuaCatsComment` accessors used above (`LuaCatsComment.java:11,38,53,59,71,77,86`).
- `LuaCatsSeeTag.getArgName()` (`LuaCatsSeeTag.java:11`), `getDescription()` (`:14`).
- `LuaCatsAliasTag.getArgName()` (`LuaCatsAliasTag.java:11`).
- `LuaCatsParamTag.getArgName()/getArgType()` (`LuaCatsParamTag.java:11,17`).
- `LuaCatsGenericTypeParam.getArgName()` (`LuaCatsGenericTypeParam.java:11`).
- `LuaCatsTypeOption.getArgValue()` (`LuaCatsTypeOption.java:11`).
- `LuaCatsElementTypes.{TAG,NAME,SYMBOL,KEYWORD}` (`LuaCatsElementTypes.java:89,85,88,84`).
- `LuaCatsHighlight.{TAG,NAME,SYMBOL,VALUE,BRACKETS}` (`.../syntax/LuaCatsHighlight.kt:9,16,20,18,22`).
- `net.internetisalie.lunar.lang.psi.LuaCommentOwner.catsComment` — host-decl accessor used
  to reach the (unstubbed) comment; consistent with the AGENTS.md "LuaCATS tags ride a host
  decl's stub" lesson: tags are found by walking the realised comment PSI, not the stub index.

## Test Files (new)

Three new test classes, mirroring the package layout of their targets.

### F1 — `LuaCatsLazyCommentTest` (T1) — MAINT-16-01
Path: `src/test/kotlin/net/internetisalie/lunar/luacats/lang/psi/impl/LuaCatsLazyCommentTest.kt`
Extends `net.internetisalie.lunar.BaseDocumentTest` (`src/test/.../BaseDocumentTest.kt:26`).

Approach: `configureByText(...)` a `.lua` file; inside
`EdtTestUtil.runInEdtAndWait + runReadAction`, reach the comment via the host decl. Pattern
(as in `LuaDocumentationTest.kt:109`):
```kotlin
val owner = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaCommentOwner::class.java).first()
val cats = owner.catsComment!!               // LuaCatsComment (the lazy impl)
assertEquals(1, cats.seeTagList.size)
assertEquals("http.get", cats.seeTagList.first().argName.text)
```
Kotlin property syntax (`cats.seeTagList`) resolves to the Java `getSeeTagList()`.

Methods:
- `testAliasTagList` (TC 1)
- `testSeeTagList` (TC 2)
- `testOverloadTagList` (TC 3)
- `testGenericTagList` (TC 4)
- `testVersionTagList` (TC 5)
- `testParamTagList` (TC 6)

### F2 — `LuaCatsSyntaxHighlighterTest` (T2, T3) — MAINT-16-02
Path: `src/test/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsSyntaxHighlighterTest.kt`
Extends `com.intellij.testFramework.fixtures.BasePlatformTestCase` (JUnit 3 style, like
`LuaCatsElementTypeTest.kt:5` — no fixture text needed; `TextAttributesKey.createTextAttributesKey`
requires the platform application, so a platform test case is used, not a bare unit test).

Approach:
```kotlin
val hl = LuaCatsSyntaxHighlighter()
val keys = hl.getTokenHighlights(LuaCatsElementTypes.TAG)
assertEquals(1, keys.size)
assertEquals(LuaCatsHighlight.TAG.externalName, keys[0].externalName)
```
`getTokenHighlights` calls `pack(colors[tokenType])` — `pack` returns an empty array when
its argument is null, so an unmapped type (`KEYWORD`, absent from every `fillMap` TokenSet
in the highlighter `init`, `LuaCatsSyntaxHighlighter.kt:31`) yields `[]`.

Methods:
- `testTagTokenMapping` (TC 7)
- `testNameTokenMapping` (TC 8)
- `testSymbolTokenMapping` (TC 9)
- `testUnmappedTokenReturnsEmpty` (TC 10)
- `testHighlightingLexerIsLuaCatsLexer` (TC 11) — `assertTrue(hl.highlightingLexer is LuaCatsLexer)`

### F3 — `LuaCatsAnnotatorTest` (T4) — MAINT-16-03
Path: `src/test/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsAnnotatorTest.kt`
Extends `BaseDocumentTest`.

Approach: identical to the shipping `LuaCatsSemanticHighlightingTest.kt:8` — reuse its
`assertHighlighted(text, key)` helper form:
```kotlin
private fun assertHighlighted(text: String, key: TextAttributesKey) {
    val infos = myFixture.doHighlighting()
    assertTrue(infos.any { it.forcedTextAttributesKey == key && it.text == text })
}
```
`doHighlighting()` runs the real annotator pass (the annotator is registered in `plugin.xml`
for the Lua language — same path the existing highlighting test relies on). This is the
real-flow DoD gate for annotators.

Methods (each `configureByText` then `assertHighlighted`):
- `testCastSymbol` (TC 12) — `+` → `LuaCatsHighlight.SYMBOL` (annotator `LuaCatsArgSymbol` branch, `:41`)
- `testEnumOptionValue` (TC 13) — `"A"` → `LuaCatsHighlight.VALUE` (`LuaCatsArgValue` branch, `:40`)
- `testOverloadParameterName` (TC 14) — `objectID` → `LuaCatsHighlight.NAME` (`LuaCatsParameterName` branch, `:37`)
- `testFieldKeyBracket` (TC 15) — `(` → `LuaCatsHighlight.BRACKETS` (SYMBOL element-type bracket branch, `:45`)

Note: TC 15 uses a grouping `(`; if `---@field [string] integer` does not surface a `(`,
the test file uses `---@type fun(): void` whose `(` is a bracket — both reach the same
bracket branch (`LuaCatsAnnotator.kt:47`). The implementer picks whichever configured text
yields a `(` `HighlightInfo`; the assertion (bracket char → `BRACKETS`) is fixed.

### F4 — `LuaCatsDocumentationRendererSectionsTest` (T5) — MAINT-16-04
Path: `src/test/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRendererSectionsTest.kt`
Extends `BaseDocumentTest`. (Separate from the existing `LuaCatsDocumentationRendererTest`
to avoid touching a green file; same package.)

Approach: identical to `LuaCatsDocumentationRendererTest.kt:17` — `EdtTestUtil.runInEdtAndWait
+ runReadAction`, `<caret>` on the decl, resolve `LuaCommentOwner`, call
`LuaCatsDocumentationRenderer.renderDoc(element)`, assert `doc!!.contains(fragment)`.

Fragment sources (grounded in the renderer):
- See-Also URL → `buildSeeSection` emits `<a href="…">` when the ref matches
  `^(https?://…)` (`LuaCatsDocumentationRenderer.kt:474`); header "See Also:" (`:467`).
- Plain See ref → `<code>reference</code>` else-branch (`:484`).
- Deprecated → `buildDeprecatedSection` header `⚠ Deprecated:` (`:496`).
- Named return → `buildReturnSection` header "Returns:" + `<code>name</code>` (`:380,387`).
- Generic type-params → `buildFunctionSignatureTypeParams` emits `<`/`>` operators + the
  param name (`:226`).
- Local function → `buildLocalFunctionSignature` emits "local function" (`:173`).
- Null path → `renderDoc` returns null when `element` is neither `LuaCommentOwner`,
  `LuaCatsClassTag`, nor `LuaCatsAliasTag` (`:46-50`); pass the `LuaFile` root.

Methods:
- `testSeeSectionUrl` (TC 16)
- `testSeeSectionPlainReference` (TC 17)
- `testDeprecatedSection` (TC 18)
- `testNamedReturnRow` (TC 19)
- `testGenericTypeParamBlock` (TC 20)
- `testLocalFunctionSignature` (TC 21)
- `testUnsupportedElementReturnsNull` (TC 22) — `assertNull(LuaCatsDocumentationRenderer.renderDoc(myFixture.file))`

## Requirement → Test Coverage

| Requirement | Test file | Methods |
|-------------|-----------|---------|
| MAINT-16-01 | F1 | testAlias/See/Overload/Generic/Version/ParamTagList |
| MAINT-16-02 | F2 | testTag/Name/Symbol/UnmappedTokenMapping, testHighlightingLexerIsLuaCatsLexer |
| MAINT-16-03 | F3 | testCastSymbol, testEnumOptionValue, testOverloadParameterName, testFieldKeyBracket |
| MAINT-16-04 | F4 | testSeeSectionUrl/PlainReference, testDeprecatedSection, testNamedReturnRow, testGenericTypeParamBlock, testLocalFunctionSignature, testUnsupportedElementReturnsNull |

## Non-Functional Notes
- No `plugin.xml` change (test-only).
- No stub-index rebuild — none of these tests need cross-file resolution. (The renderer's
  inherited-fields branch, `LuaCatsDocumentationRenderer.kt:419`, needs an indexed parent
  class via `LuaClassNameIndex`; it is left out of scope in requirements to keep fixtures
  light — it would require `IndexedDocumentTest`.)
- Threading: reads inside `runReadAction`; EDT via `EdtTestUtil.runInEdtAndWait` for F1/F4;
  `doHighlighting()` in F3 handles its own EDT/threading.

## Open Questions
None.

## See Also
- Requirements: [requirements.md](requirements.md)
- Plan: [implementation-plan.md](implementation-plan.md)
