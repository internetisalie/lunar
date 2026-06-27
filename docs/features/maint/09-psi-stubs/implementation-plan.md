---
id: "MAINT-09-PLAN"
title: "Implementation Plan"
type: "plan"
status: "in_progress"
priority: "medium"
parent_id: "MAINT-09"
folders:
  - "[[features/maint/09-psi-stubs/requirements|requirements]]"
---

# MAINT-09: Implementation Plan

Test-only feature. Each phase adds one JUnit4 light-fixture test class (design §2.x) under
`src/test/kotlin/net/internetisalie/lunar/…`, extends `BasePlatformTestCase`, and is
annotated `@RunWith(JUnit4::class)`. Phases are independent and may land in any order; each
leaves the build green. No production code, no `plugin.xml` change (design §7).

Run after each phase: `tooling/gce-builder/gce-builder.sh run "test --tests *<ClassName>*"`,
then `ktlintFormat ktlintCheck` before committing.

## Phases

### Phase 1: PSI factory tests [Must]
- **Goal**: Cover `LuaElementFactory` construction (MAINT-09-01).
- **Tasks**:
  - [x] Create `src/test/kotlin/net/internetisalie/lunar/lang/psi/LuaElementFactoryTest.kt`
        (`net.internetisalie.lunar.lang.psi.LuaElementFactoryTest`) — realizes design §2.1.
        Methods `testCreateIdentifierProducesNamedElement`, `testCreateLabelRefProducesLuaLabelRef`,
        `testCreateLabelProducesLuaLabel`, `testCreateGotoStatementProducesLuaGotoStatement`,
        `testCreateExpressionProducesLuaExpr`, `testCreateFileParsesWithoutErrorElements`,
        `testCreateNewLineIsWhitespace`. Use `project` from `BasePlatformTestCase`.
- **Exit criteria**: `LuaElementFactoryTest` green; covers TC-09-01-a/b/c.

### Phase 2: LuaCATS comment resolution tests [Must]
- **Goal**: Cover `LuaPsiImplUtil.getCatsComment()` for `@type`, function decls, nearest
  preceding, and the association-break null case (MAINT-09-02).
- **Tasks**:
  - [x] Create `src/test/kotlin/net/internetisalie/lunar/lang/psi/LuaCatsCommentResolutionTest.kt`
        (`net.internetisalie.lunar.lang.psi.LuaCatsCommentResolutionTest`) — realizes design §2.2.
        Methods `testTypeCommentResolvedForLocalVar`, `testCatsCommentResolvedForFuncDecl`,
        `testNearestPrecedingCommentIsChosen`, `testInterveningStatementBreaksAssociation`.
        Locate decls with `PsiTreeUtil.findChildOfType` (per `TestLuaStubIndexing.kt:51,93`).
- **Exit criteria**: `LuaCatsCommentResolutionTest` green; covers TC-09-02-a/b/c.

### Phase 3: Scope-crawl & sequencing tests [Must]
- **Goal**: Cover forward-reference rejection and nested-scope climb via
  `scopeCrawlUp`/`LuaBlockExt` (MAINT-09-03).
- **Tasks**:
  - [x] Create `src/test/kotlin/net/internetisalie/lunar/lang/resolve/LuaScopeResolveTest.kt`
        (`net.internetisalie.lunar.lang.resolve.LuaScopeResolveTest`) — realizes design §2.3.
        Add the private `resolveAtCaret(text): PsiElement?` helper (design §3.1). Methods
        `testForwardReferenceDoesNotResolveToLaterLocal`, `testNestedBlockResolvesOuterLocal`,
        `testReferenceAfterDeclarationResolves`.
- **Exit criteria**: `LuaScopeResolveTest` green; covers TC-09-03-a/b.

### Phase 4: Function scope & implicit-self tests [Must]
- **Goal**: Cover numeric-`for` loop variables, loop-variable confinement, function
  parameters, and implicit `self` (MAINT-09-04).
- **Tasks**:
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/lang/resolve/LuaFunctionScopeTest.kt`
        (`net.internetisalie.lunar.lang.resolve.LuaFunctionScopeTest`) — realizes design §2.4
        (reuses the §3.1 `resolveAtCaret` helper). Methods
        `testNumericForVariableResolvesInBody`, `testLoopVariableNotVisibleAfterLoop`,
        `testImplicitSelfResolvesInsideMethod`, `testFunctionParameterResolvesInBody`.
        Assert `self` resolves to the receiver identifier `obj` (design §6,
        `LuaScopeProcessor.kt:83`).
- **Exit criteria**: `LuaFunctionScopeTest` green; covers TC-09-04-a/b/c.

### Phase 5: Stub serialization round-trip tests [Must]
- **Goal**: Cover stub hoisting and serialize/deserialize round-trip for all three stub
  element types (MAINT-09-05).
- **Tasks**:
  - [ ] Create `src/test/kotlin/net/internetisalie/lunar/lang/psi/stubs/LuaStubSerializationTest.kt`
        (`net.internetisalie.lunar.lang.psi.stubs.LuaStubSerializationTest`) — realizes design
        §2.5. Add the private helpers `buildStubTree`, `roundTrip`, and the `inline reified`
        `Stub.collect<T>()` DFS (design §3.2). Methods `testLocalVarStubHoistsClassAndExtends`,
        `testLocalVarStubSerializationRoundTrip`, `testFuncStubReturnTypeRoundTrip`,
        `testLocalFuncStubRoundTrip`. Declare `SerializerNotFoundException` on round-trip
        methods (design §3.2); never use `!!`.
- **Exit criteria**: `LuaStubSerializationTest` green; covers TC-09-05-a/b/c.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-09-01 | M | Phase 1 |
| MAINT-09-02 | M | Phase 2 |
| MAINT-09-03 | M | Phase 3 |
| MAINT-09-04 | M | Phase 4 |
| MAINT-09-05 | M | Phase 5 |

## Verification Tasks
- [x] `LuaElementFactoryTest` — covers TC-09-01-a/b/c (AC-09-01).
- [x] `LuaCatsCommentResolutionTest` — covers TC-09-02-a/b/c (AC-09-02).
- [x] `LuaScopeResolveTest` — covers TC-09-03-a/b (AC-09-03).
- [ ] `LuaFunctionScopeTest` — covers TC-09-04-a/b/c (AC-09-04).
- [ ] `LuaStubSerializationTest` — covers TC-09-05-a/b/c (AC-09-05).
- [ ] Full suite green: `tooling/gce-builder/gce-builder.sh run test` (no regressions).
- [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on new files.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: PSI factory tests | done | Must |
| Phase 2: LuaCATS comment resolution tests | done | Must |
| Phase 3: Scope-crawl & sequencing tests | done | Must |
| Phase 4: Function scope & implicit-self tests | planned | Must |
| Phase 5: Stub serialization round-trip tests | planned | Must |
