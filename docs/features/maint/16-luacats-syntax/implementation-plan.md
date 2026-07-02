---
id: "MAINT-16-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-16"
folders:
  - "[[features/maint/16-luacats-syntax/requirements|requirements]]"
---

# MAINT-16: Implementation Plan

All phases are **test-only** (add new test classes; no production edits). Each phase is one
new file, independently runnable and verifiable.

## Phases

### Phase 1: Lazy-comment tag-query tests [Must]
- **Goal**: Cover the unexercised `LuaCatsLazyCommentImpl` `get*TagList()` accessors.
- **Task**:
  - [ ] Add `src/test/kotlin/net/internetisalie/lunar/luacats/lang/psi/impl/LuaCatsLazyCommentTest.kt`
        extending `BaseDocumentTest`, per design F1. Methods: `testAliasTagList`,
        `testSeeTagList`, `testOverloadTagList`, `testGenericTagList`, `testVersionTagList`,
        `testParamTagList` — TC 1–6. Reach the comment via
        `PsiTreeUtil.findChildrenOfType(file, LuaCommentOwner::class.java).first().catsComment`
        inside `EdtTestUtil.runInEdtAndWait + runReadAction`; assert list size and inner
        `argName.text` / `argType.text`.
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsLazyCommentTest*"`

### Phase 2: Syntax-highlighter mapping tests [Must]
- **Goal**: Cover `LuaCatsSyntaxHighlighter.getTokenHighlights` / `getHighlightingLexer`.
- **Task**:
  - [ ] Add `src/test/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsSyntaxHighlighterTest.kt`
        extending `BasePlatformTestCase` (JUnit-3 `testXxx` methods), per design F2. Methods:
        `testTagTokenMapping`, `testNameTokenMapping`, `testSymbolTokenMapping`,
        `testUnmappedTokenReturnsEmpty`, `testHighlightingLexerIsLuaCatsLexer` — TC 7–11.
        Assert `keys[0].externalName == LuaCatsHighlight.<KEY>.externalName`; unmapped
        `LuaCatsElementTypes.KEYWORD` → `assertEmpty`.
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsSyntaxHighlighterTest*"`

### Phase 3: Annotator branch tests [Must]
- **Goal**: Cover the `SYMBOL` / `VALUE` / `NAME` / `BRACKETS` branches of `LuaCatsAnnotator`.
- **Task**:
  - [ ] Add `src/test/kotlin/net/internetisalie/lunar/luacats/lang/syntax/LuaCatsAnnotatorTest.kt`
        extending `BaseDocumentTest`, per design F3, reusing the `assertHighlighted(text, key)`
        helper form from `LuaCatsSemanticHighlightingTest`. Methods: `testCastSymbol`,
        `testEnumOptionValue`, `testOverloadParameterName`, `testFieldKeyBracket` — TC 12–15.
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsAnnotatorTest*"`

### Phase 4: Documentation-renderer section tests [Must]
- **Goal**: Cover See/Deprecated/named-return/generic/local branches + the null contract.
- **Task**:
  - [ ] Add `src/test/kotlin/net/internetisalie/lunar/luacats/lang/doc/LuaCatsDocumentationRendererSectionsTest.kt`
        extending `BaseDocumentTest`, per design F4, reusing the `EdtTestUtil.runInEdtAndWait
        + runReadAction` + `<caret>` + `renderDoc` + `assertContains` pattern from
        `LuaCatsDocumentationRendererTest`. Methods: `testSeeSectionUrl`,
        `testSeeSectionPlainReference`, `testDeprecatedSection`, `testNamedReturnRow`,
        `testGenericTypeParamBlock`, `testLocalFunctionSignature`,
        `testUnsupportedElementReturnsNull` — TC 16–22.
- **Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsDocumentationRendererSectionsTest*"`

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-16-01 | M | Phase 1 |
| MAINT-16-02 | M | Phase 2 |
| MAINT-16-03 | M | Phase 3 |
| MAINT-16-04 | M | Phase 4 |

## Verification Tasks
- [ ] Each phase's `--tests` pattern passes (commands above).
- [ ] Full-suite regression: `tooling/gce-builder/gce-builder.sh run test` is green.
- [ ] Lint the four new files: `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"`.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Lazy-comment tag-query tests | planned | Must |
| Phase 2: Syntax-highlighter mapping tests | planned | Must |
| Phase 3: Annotator branch tests | planned | Must |
| Phase 4: Documentation-renderer section tests | planned | Must |
