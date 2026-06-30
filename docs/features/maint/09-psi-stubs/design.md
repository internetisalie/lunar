---
id: "MAINT-09-DESIGN"
title: "Technical Design"
type: "design"
priority: "medium"
parent_id: "MAINT-09"
folders:
  - "[[features/maint/09-psi-stubs/requirements|requirements]]"
---

# Technical Design: MAINT-09 — Test Coverage: PSI & Stubs

This is a **test-coverage** feature: it adds no production code and changes no
`plugin.xml`. It adds five JUnit4 light-fixture test classes that exercise existing,
already-shipped production symbols in `lang/psi/`, `lang/`, and `lang/psi/stubs/`. Every
production symbol named below is verified against the real source with `file:line`
evidence; every test method maps to a `Must` requirement.

## 1. Architecture Overview

### Current State
PSI / stub behaviour is partially covered today:

- `src/test/kotlin/net/internetisalie/lunar/lang/TestLuaStubIndexing.kt` — stub **indexing**
  (`LuaGlobalDeclarationIndex` / `LuaClassNameIndex` / `LuaAliasIndex`) and a few
  `getCatsComment()` sanity checks for `@class`/`@param`/`@return`/`@alias`.
- `src/test/kotlin/net/internetisalie/lunar/lang/LuaReferenceTest.kt` — **positive**
  resolution of a local var, multiple locals, a generic-`for` variable, and a global.

Gaps (no existing coverage): `LuaElementFactory` construction; `getCatsComment()` for `@type`
and its nearest-preceding / association-break logic; **forward-reference rejection** and
nested-scope climb in `scopeCrawlUp`/`LuaBlockExt`; numeric-`for` variables, **loop-variable
confinement**, and **implicit `self`**; and stub **serialize/deserialize** byte round-trip
(only indexing, not serialization, is tested today).

### Prior Art in This Repo
Searched `src/test/kotlin/**` (grep `Stub`, `Resolve`, `Reference`, `Cats`, `Factory`,
`Scope`). Found and **EXTENDED, not duplicated**:

- `TestLuaStubIndexing.kt` (`lang/TestLuaStubIndexing.kt:18`) — covers *indexing* and
  `getCatsComment` for `@class`. MAINT-09 adds the **serialize/deserialize round-trip**
  (`LuaStubSerializationTest`) and the **`@type` / nearest-preceding** comment cases
  (`LuaCatsCommentResolutionTest`) it does not cover. The new classes do not re-assert index
  membership.
- `LuaReferenceTest.kt` (`lang/LuaReferenceTest.kt:10`) — covers *positive* resolution.
  MAINT-09 adds the **negative / sequencing** cases (forward references, loop-variable
  confinement) and **implicit `self`**, which `LuaReferenceTest` omits. The shared
  resolve-at-caret idiom (`LuaReferenceTest.kt:19-23`) is reused, not re-tested.

No production component is replaced. No new production class is created.

### Target State
Five new test classes, one per `Must` requirement, under the existing `src/test/kotlin`
mirror of the production package tree, all extending
`com.intellij.testFramework.fixtures.BasePlatformTestCase` and annotated
`@RunWith(JUnit4::class)` (the convention in `TestLuaStubIndexing.kt:17` and
`LuaReferenceTest.kt:9`).

| Test class (FQCN) | Requirement |
|-------------------|-------------|
| `net.internetisalie.lunar.lang.psi.LuaElementFactoryTest` | MAINT-09-01 |
| `net.internetisalie.lunar.lang.psi.LuaCatsCommentResolutionTest` | MAINT-09-02 |
| `net.internetisalie.lunar.lang.resolve.LuaScopeResolveTest` | MAINT-09-03 |
| `net.internetisalie.lunar.lang.resolve.LuaFunctionScopeTest` | MAINT-09-04 |
| `net.internetisalie.lunar.lang.psi.stubs.LuaStubSerializationTest` | MAINT-09-05 |

## 2. Core Components

> Every *existing* symbol referenced in a signature below is verified real (grep → file:line).

### 2.1 `net.internetisalie.lunar.lang.psi.LuaElementFactoryTest` (MAINT-09-01)
- **Responsibility**: Verify `LuaElementFactory` (`lang/psi/LuaElementFactory.kt:11`, an
  `object`) constructs valid, parseable PSI from text without syntax errors.
- **Threading**: Default test thread (EDT with read/write access supplied by
  `BasePlatformTestCase`). PSI is created via `PsiFileFactory.createFileFromText`
  (`LuaElementFactory.kt:43`); no explicit read action needed inside a light-fixture test.
- **Collaborators**: `LuaElementFactory`; `project` (from `BasePlatformTestCase.getProject()`);
  PSI types `LuaLabelRef` (gen), `LuaLabel` (gen), `LuaGotoStatement` (gen), `LuaExpr` (gen),
  `LuaFile` (`lang/psi/LuaFile.kt`).
- **Key API** (test methods, all `fun … (): Unit`, `@Test`):
  ```kotlin
  @Test fun testCreateIdentifierProducesNamedElement()      // createIdentifier:12
  @Test fun testCreateLabelRefProducesLuaLabelRef()         // createLabelRef:17
  @Test fun testCreateLabelProducesLuaLabel()               // createLabel:27
  @Test fun testCreateGotoStatementProducesLuaGotoStatement() // createGotoStatement:22
  @Test fun testCreateExpressionProducesLuaExpr()           // createExpression:32
  @Test fun testCreateFileParsesWithoutErrorElements()      // createFile:41
  @Test fun testCreateNewLineIsWhitespace()                 // createNewLine:37
  ```

### 2.2 `net.internetisalie.lunar.lang.psi.LuaCatsCommentResolutionTest` (MAINT-09-02)
- **Responsibility**: Verify `LuaPsiImplUtil.getCatsComment(owner)`
  (`lang/psi/LuaPsiImplUtil.kt:14`) returns the nearest preceding LuaCATS comment block for
  a local variable and a function declaration, and returns `null` when an intervening
  non-comment statement breaks the association.
- **Threading**: Default test thread.
- **Collaborators**: `LuaPsiImplUtil.getCatsComment` (returns `LuaCatsComment?`);
  `LuaCatsComment` (`luacats/lang/psi/LuaCatsComment` — `getTypeTagList()`, `getClassTagList()`,
  `getReturnTagList()`); PSI `LuaLocalVarDecl` (gen), `LuaFuncDecl` (gen); `PsiTreeUtil`.
- **Key API**:
  ```kotlin
  @Test fun testTypeCommentResolvedForLocalVar()        // AC-09-02
  @Test fun testCatsCommentResolvedForFuncDecl()
  @Test fun testNearestPrecedingCommentIsChosen()
  @Test fun testInterveningStatementBreaksAssociation() // expects null (LuaPsiImplUtil.kt:36)
  ```

### 2.3 `net.internetisalie.lunar.lang.resolve.LuaScopeResolveTest` (MAINT-09-03)
- **Responsibility**: Verify the sequential (early-binding) scope walk: forward references
  do not resolve, and `scopeCrawlUp` climbs nested blocks to an enclosing-scope local.
- **Threading**: Default test thread; resolution runs under the platform read action
  supplied by `myFixture`.
- **Collaborators**: production resolution path
  `LuaResolveUtil.scopeCrawlUp` (`lang/psi/LuaResolveUtil.kt:9`) →
  `LuaBlock.processDeclarations` (`lang/psi/LuaBlockExt.kt:25`, early-binding break at
  `LuaBlockExt.kt:34`) → `LuaScopeProcessor` (`lang/LuaScopeProcessor.kt`), reached through
  `LuaNameReference` (`lang/LuaNameReference.kt`). Tests drive it through
  `PsiReference.resolve()`, exactly like `LuaReferenceTest.kt:19-23`.
- **Key API**:
  ```kotlin
  private fun resolveAtCaret(text: String): PsiElement?   // §3.1 helper
  @Test fun testForwardReferenceDoesNotResolveToLaterLocal() // AC-09-03
  @Test fun testNestedBlockResolvesOuterLocal()              // scopeCrawlUp climb
  @Test fun testReferenceAfterDeclarationResolves()          // positive control
  ```

### 2.4 `net.internetisalie.lunar.lang.resolve.LuaFunctionScopeTest` (MAINT-09-04)
- **Responsibility**: Verify numeric-`for` loop variables resolve inside the loop body and
  are **not** visible after the loop, and that implicit `self` resolves inside a method
  declaration.
- **Threading**: Default test thread (read action via `myFixture`).
- **Collaborators**:
  `LuaNumericForStatement.processDeclarations` (`lang/psi/LuaForStatementExt.kt:7`,
  body-confinement guard `lastParent == block` at `LuaForStatementExt.kt:15`);
  `LuaFuncDecl.processDeclarations` (`lang/psi/LuaFunctionExt.kt:59`); the implicit-`self`
  branch in `LuaScopeProcessor.kt:82` (`name == "self" && funcName.funcNameMethod != null`,
  resolving to `funcName.funcNameMethod.nameRef.identifier` at `LuaScopeProcessor.kt:83`).
- **Key API**:
  ```kotlin
  private fun resolveAtCaret(text: String): PsiElement?
  @Test fun testNumericForVariableResolvesInBody()       // AC-09-04 (for i=1,3 do print(i) end)
  @Test fun testLoopVariableNotVisibleAfterLoop()        // confinement (negative)
  @Test fun testImplicitSelfResolvesInsideMethod()       // AC-09-04 (function obj:m() self end)
  @Test fun testFunctionParameterResolvesInBody()        // LuaFunctionExt.kt:25-30 path
  ```

### 2.5 `net.internetisalie.lunar.lang.psi.stubs.LuaStubSerializationTest` (MAINT-09-05)
- **Responsibility**: Verify each stub element type hoists its LuaCATS state onto the stub
  (`createStub`) and that the state survives a full **serialize → bytes → deserialize**
  round-trip.
- **Threading**: Default test thread.
- **Collaborators**:
  - `LuaLocalVarStubElementType` (`lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:12`,
    `serialize:44` / `deserialize:59`) producing `LuaLocalVarStub`
    (`lang/psi/stubs/LuaLocalVarStub.kt:6`: `names`, `luacatsType`, `luacatsClassName`,
    `luacatsAliasName`, `luacatsAliasTarget`, `luacatsExtends`, `luacatsFields`).
  - `LuaFuncStubElementType` (`lang/psi/stubs/impl/LuaFuncStubElementType.kt:11`) producing
    `LuaFuncStub` (`lang/psi/stubs/LuaFuncStub.kt:6`: `name`, `luacatsReturnType`,
    `luacatsParamTypes`).
  - `LuaLocalFuncStubElementType` (`lang/psi/stubs/impl/LuaLocalFuncStubElementType.kt:10`)
    producing `LuaLocalFuncStub` (`lang/psi/stubs/LuaLocalFuncStub.kt:6`).
  - Platform: `PsiFileImpl.calcStubTree(): StubTree`, `StubTree.root: PsiFileStub<*>`,
    `SerializationManagerEx.getInstanceEx()`
    (`platform/indexing-impl/.../psi/stubs/SerializationManagerEx.java:17`)
    `.serialize(Stub, OutputStream)` / `.deserialize(InputStream): Stub`
    (`StubTreeSerializer.java:12-14`).
- **Key API**:
  ```kotlin
  private fun buildStubTree(text: String): com.intellij.psi.stubs.StubTree   // §3.2
  private fun roundTrip(root: com.intellij.psi.stubs.Stub): com.intellij.psi.stubs.Stub // §3.2
  private inline fun <reified T> com.intellij.psi.stubs.Stub.collect(): List<T> // §3.2 DFS
  @Test fun testLocalVarStubHoistsClassAndExtends()   // createStub (LuaLocalVarStubElementType.kt:19)
  @Test fun testLocalVarStubSerializationRoundTrip()  // AC-09-05
  @Test fun testFuncStubReturnTypeRoundTrip()         // AC-09-05
  @Test fun testLocalFuncStubRoundTrip()
  ```

## 3. Algorithms

### 3.1 Resolve-at-caret helper (used by §2.3, §2.4)
- **Input → Output**: `String` fixture containing a single `<caret>` marker → `PsiElement?`
  (the resolved declaration, or `null`).
- **Steps** (mirrors `LuaReferenceTest.kt:19-23`):
  1. `myFixture.configureByText("test.lua", text)`.
  2. `val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null`.
  3. `val reference = leaf.parent.reference ?: return null`.
  4. `return reference.resolve()`.
- **Rules / edge handling**: a forward-reference / out-of-scope name yields either a `null`
  reference or `resolve() == null`; both are treated as "did not resolve". Assertions for the
  negative cases therefore assert `resolveAtCaret(text) == null` (and never `!!`).
- **Why this exercises the target code**: `LuaNameReference.resolve()` invokes
  `LuaResolveUtil.scopeCrawlUp` (`LuaResolveUtil.kt:9`), which calls the `processDeclarations`
  extensions under test; the early-binding break (`LuaBlockExt.kt:34`) and the
  body-confinement guard (`LuaForStatementExt.kt:15`) are on that path.

### 3.2 Stub serialize/deserialize round-trip (used by §2.5)
- **Input → Output**: `String` Lua source → a deserialized `Stub` tree whose hoisted fields
  equal the originals.
- **Steps**:
  1. `val file = myFixture.configureByText("test.lua", text) as com.intellij.psi.impl.source.PsiFileImpl`
     (`LuaFile` is a `PsiFileImpl`).
  2. `val stubTree = file.calcStubTree()` — builds the stub tree via each element type's
     `createStub` (the "save" half).
  3. `val out = java.io.ByteArrayOutputStream()`;
     `SerializationManagerEx.getInstanceEx().serialize(stubTree.root, out)`.
  4. `val restored = SerializationManagerEx.getInstanceEx().deserialize(java.io.ByteArrayInputStream(out.toByteArray()))`.
  5. `collect<T>()`: depth-first over `Stub.childrenStubs`, returning every node that
     `is T` (e.g. `LuaLocalVarStub`, `LuaFuncStub`, `LuaLocalFuncStub`).
- **Rules / edge handling**:
  - A stub with no LuaCATS comment hoists `null` / empty-map fields; the round-trip must
    preserve those `null`s (asserts `luacatsType == null` etc.), exercising
    `StubOutputStream.writeName(null)` / `readName()?.string` (`LuaLocalVarStubElementType.kt:47,65`).
  - `deserialize` declares `throws SerializerNotFoundException`; the test method declares it
    (`@Throws` / Kotlin propagation) rather than swallowing it.
  - Compare `luacatsFields` as whole `Map<String,String>` equality; order-independent.
- **Complexity**: O(n) in stub-tree size; fixtures are < 10 lines, so trivial.

## 4. External Data & Parsing
None. This feature consumes no CLI output, file contents, or network responses. The only
inputs are inline Lua source-string fixtures, listed in §5, parsed by the plugin's own lexer
/ parser. (The serialized stub bytes in §3.2 are produced and consumed entirely by the
platform's own `SerializationManagerEx` — not a format this feature parses.)

## 5. Data Flow

### Example 1: Forward-reference rejection (MAINT-09-03)
Fixture `print(<caret>x)\nlocal x = 1` → `resolveAtCaret` → `LuaNameReference.resolve` →
`scopeCrawlUp` reaches the file `LuaBlock` → `LuaBlockExt.processDeclarations` iterates
statements, hits the break at `LuaBlockExt.kt:34` because `local x`'s `textOffset >=`
the reference's `textOffset` → `x` is never offered → `resolve()` returns `null` →
test asserts `null`.

### Example 2: Stub round-trip (MAINT-09-05)
Fixture `---@class Builder\nlocal Builder = {}` → `calcStubTree` →
`LuaLocalVarStubElementType.createStub` hoists `luacatsClassName="Builder"`
(`LuaLocalVarStubElementType.kt:24-26`) → `serialize` (`:44`) writes bytes →
`deserialize` (`:59`) rebuilds `LuaLocalVarStubImpl` → `collect<LuaLocalVarStub>().first()`
→ assert `luacatsClassName == "Builder"`.

### Example 3: Implicit self (MAINT-09-04)
Fixture `function obj:m()\n  return <caret>self\nend` → resolve → `scopeCrawlUp` reaches the
`LuaFuncDecl` → `LuaScopeProcessor` execute hits the `self` branch (`LuaScopeProcessor.kt:82`)
because `funcName.funcNameMethod != null` → resolves to the receiver identifier `obj`
(`LuaScopeProcessor.kt:83`) → test asserts non-null and that the target text is `obj`.

## 6. Edge Cases
- **`createIdentifier` return shape**: `LuaElementFactory.createIdentifier` returns
  `labelRef.identifier ?: labelRef.firstChild` (`LuaElementFactory.kt:14`), a generic
  `PsiElement` (the identifier leaf) — **not** a `LuaNameRef`. The test asserts on the real
  return (non-null, `text == name`) and separately asserts `createLabelRef` returns a
  `LuaLabelRef`. See Gap 2.1 in `risks-and-gaps.md`.
- **Implicit `self` target**: resolves to the receiver identifier (`obj`), not a synthetic
  "self parameter" PSI (`LuaScopeProcessor.kt:83`). The test asserts that real target. There
  is no `isMethod` accessor; the real predicate is `funcName.funcNameMethod != null`. See
  Gap 2.2.
- **Stub with no annotation**: round-trip must preserve `null`/empty fields (§3.2).
- **`calcStubTree` after AST load**: `configureByText` loads AST first; `calcStubTree` still
  builds stubs from it — the platform-supported path; no `forceRebuild` needed
  (`LuaStubSerializationTest` does not extend `IndexedDocumentTest`).
- **Multi-name local** (`local a, b = 1, 2`): `LuaLocalVarStub.names` must contain both;
  round-trip preserves list order (`LuaLocalVarStubElementType.kt:45-46,60-64`).

## 7. Integration Points
**No `plugin.xml` changes.** No extension points, indexes, services, or registrations are
added or modified — this is test-only. New files live in the existing test source set
(`src/test/kotlin`), which is already wired into the Gradle `test` task
(`tooling/gce-builder/gce-builder.sh run test`). Test classes follow the existing convention:
extend `BasePlatformTestCase`, annotate `@RunWith(JUnit4::class)`, drive the fixture with
`myFixture.configureByText(...)` (see `TestLuaStubIndexing.kt:17-18`,
`LuaReferenceTest.kt:9-10`).

```xml
<!-- plugin.xml: NO CHANGES for MAINT-09 -->
```

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-09-01 | M | §2.1 (`LuaElementFactoryTest`) |
| MAINT-09-02 | M | §2.2 (`LuaCatsCommentResolutionTest`) |
| MAINT-09-03 | M | §2.3 (`LuaScopeResolveTest`), §3.1 |
| MAINT-09-04 | M | §2.4 (`LuaFunctionScopeTest`), §3.1 |
| MAINT-09-05 | M | §2.5 (`LuaStubSerializationTest`), §3.2 |

## 9. Alternatives Considered
- **Byte round-trip via raw `StubOutputStream`/`StubInputStream`** (their constructors are
  public, `StubOutputStream.java:18` / `StubInputStream.java:20`): rejected — both require an
  `AbstractStringEnumerator`, which has no easily-constructed in-memory implementation, and
  no platform test uses this directly (grep found 0 usages). `SerializationManagerEx`
  (§3.2) gives the same coverage of each element type's `serialize`/`deserialize` with the
  app-service enumerator already wired in.
- **Assert hoisting only (via `calcStubTree`) without the byte round-trip**: rejected —
  MAINT-09-05 explicitly requires "serialize and deserialize", so §3.2 keeps the full
  round-trip. `calcStubTree` is still used as step 1 (the "save" half).
- **`IndexedDocumentTest` base**: rejected — these tests don't need the platform stub-index
  rebuild (`IndexedDocumentTest.kt`), so the faster `BasePlatformTestCase` is used (matches
  the prior-art test classes).

## 10. Open Questions

_None — feature has cleared the planning bar._

<!-- The two requirement-wording mismatches are resolved decisions, tracked in
     risks-and-gaps.md (Gap 2.1, Gap      2.2); the tests are specified against the real APIs. -->
