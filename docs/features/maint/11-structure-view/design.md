---
id: "MAINT-11-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-11"
folders:
  - "[[features/maint/11-structure-view/requirements|requirements]]"
---

# Technical Design: MAINT-11 — Test Coverage: Structure View

This is a **test map**, not a production design. No production code changes. All target
symbols below are grounded with `path:line` citations.

## 1. Target Classes (grounded)

All under `src/main/kotlin/net/internetisalie/lunar/lang/structure/`. Registered in
`src/main/resources/META-INF/plugin.xml:321` via
`<lang.psiStructureViewFactory language="Lua" implementationClass="…LuaStructureViewFactory"/>`.

| Class | Path:line | Key members under test |
|---|---|---|
| `LuaStructureViewFactory` | `LuaStructureViewFactory.kt:11` | `getStructureViewBuilder(psiFile): StructureViewBuilder` (returns a `TreeBasedStructureViewBuilder` whose `createStructureViewModel(editor)` = `LuaStructureViewModel`) |
| `LuaStructureViewModel` | `LuaStructureViewModel.kt:10` | `getRoot()` → `LuaFileStructureViewTreeElement` (`:23`); `getSorters()` → `[Sorter.ALPHA_SORTER]` (`:27`); `isAlwaysLeaf(element)` (`:35`); `getSuitableClasses()` → `SUITABLE_CLASSES` (`:11`, 5 classes); `getPsiFile()` (`:19`) |
| `LuaFileStructureViewTreeElement` | `LuaFileStructureViewTreeElement.kt:9` | `getPresentation().getPresentableText()` = file name (`:12`); `getChildren()` = `TreeElementUtils.getRootChildren(myFile)` (`:22`); `getValue()` = file |
| `LuaFunctionStructureViewTreeElement` | `LuaFunctionStructureViewTreeElement.kt:9` | text = `myFuncDecl.funcName.text` (`:12`); icon `AllIcons.Nodes.Function` (`:16`); children = `getFuncBodyChildren(parList, block)` (`:22`) |
| `LuaLocalFunctionStructureViewTreeElement` | `LuaLocalFunctionStructureViewTreeElement.kt:9` | text = `nameRef.identifier.text` (`:12`); children = `getFuncBodyChildren(parList, block)` (`:22`) |
| `LuaFunctionParameterStructureViewTreeElement` | `LuaFunctionParameterStructureViewTreeElement.kt:9` | text = `myIdentifier.text` (`:12`); icon `AllIcons.Nodes.Parameter` (`:16`); children empty (`:23`) |
| `LuaLocalVariableStructureViewTreeElement` | `LuaLocalVariableStructureViewTreeElement.kt:9` | text = identifier text (`:12`); icon `AllIcons.Nodes.Variable` (`:16`); children empty (`:23`) |
| `LuaReturnStructureViewTreeElement` | `LuaReturnStructureViewTreeElement.kt:9` | text = `"return"` (`:12`); icon `AllIcons.Debugger.EvaluationResult` (`:16`); children empty (`:22`) |
| `LuaLabelStructureViewTreeElement` | `LuaLabelStructureViewTreeElement.kt:10` | text = `labelName.identifier?.text ?: labelName.firstChild?.text` (`:14`); icon `AllIcons.Nodes.Bookmark` (`:18`); children empty (`:25`) |
| `TreeElementUtils` | `TreeElementUtils.kt:6` | `getRootChildren(root: LuaFile)` (`:7`); `getFuncBodyChildren(parList, block)` (`:13`); private `getBlockChildren`/`getParListChildren`/`getLocalVariableNameListChildren` exercised transitively |

## 2. PSI accessors used by targets (grounded)

The tests configure Lua text and read PSI via these real accessors (no reflection needed):

- `LuaFile.getBlockList(): List<LuaBlock>` — `src/main/kotlin/.../lang/psi/LuaFile.kt:37`
- `LuaBlock.getStatementList(): List<LuaStatement>` — `src/main/gen/.../psi/LuaBlock.java:13`
- `LuaLocalVarDecl.getAttNameList(): List<LuaAttName>` — `LuaLocalVarDecl.java:16`
- `LuaAttName.getNameRef(): LuaNameRef` — `LuaAttName.java:14`; `LuaNameRef.getIdentifier(): PsiElement` — `LuaNameRef.java:11`
- `LuaFuncDecl.getFuncName()/getParList()/getBlock()` — `LuaFuncDecl.java:21/24/18`
- `LuaLocalFuncDecl.getNameRef()/getParList()/getBlock()` — `LuaLocalFuncDecl.java:21/24/18`
- `LuaParList.getNameList(): LuaNameList` — `LuaParList.java:11`; `LuaNameList.getNameRefList(): List<LuaNameRef>` — `LuaNameList.java:11`
- `LuaLabel.getLabelName(): LuaLabelName` — `LuaLabel.java:11`; `LuaLabelName.getIdentifier()` — `LuaLabelName.java:11`
- Marker types in `SUITABLE_CLASSES`: `LuaFile`, `LuaLabel`, `LuaFuncDecl`, `LuaLocalFuncDecl`, `LuaFinalStatement` (`LuaStructureViewModel.kt:11`)
- `LuaAssignmentStatement extends LuaStatement` (`LuaAssignmentStatement.java:8`) — a top-level
  `x = 1` is **not** in the `TreeElementUtils` `when`, so it routes to `else -> emptyList()`.

## 3. Test approach

**Base**: `com.intellij.testFramework.fixtures.BasePlatformTestCase` (used repo-wide, e.g.
`src/test/kotlin/.../lang/LuaLabelCompletionTest.kt:9`). `@RunWith(JUnit4::class)`, `@Test`.

**Fixture setup**: `myFixture.configureByText("test.lua", "…".trimIndent())` returns the
`PsiFile`; cast to `net.internetisalie.lunar.lang.psi.LuaFile`.

**No platform structure-view driver needed.** The node classes are directly constructable
and their `getChildren()` / `getPresentation()` are pure PSI reads — instantiate the model
and node objects and assert directly. This avoids the heavier
`com.intellij.testFramework.PlatformTestUtil` tree-string helpers and keeps tests fast.

**Helper (private, in each test file as needed)**:
```kotlin
private fun luaFile(text: String): LuaFile =
    myFixture.configureByText("test.lua", text.trimIndent()) as LuaFile

private fun rootChildren(file: LuaFile): List<TreeElement> =
    LuaFileStructureViewTreeElement(file).children.toList()
```

**Assertion patterns**:
- Runtime-type of nodes: `assertTrue(child is LuaFunctionStructureViewTreeElement)` /
  `assertInstanceOf(child, LuaReturnStructureViewTreeElement::class.java)`.
- Presentable text: `assertEquals("foo", node.presentation.presentableText)`.
- Icon identity: `assertSame(AllIcons.Nodes.Function, node.presentation.getIcon(false))`.
- Leaf: `assertEmpty(node.children)` and `assertTrue(model.isAlwaysLeaf(node))`.
- Sorter presence: `assertTrue(model.sorters.any { it === Sorter.ALPHA_SORTER })`.
- Suitable classes: `assertSameElements(model.suitableClasses.toList(), LuaFile::class.java, LuaLabel::class.java, LuaFuncDecl::class.java, LuaLocalFuncDecl::class.java, LuaFinalStatement::class.java)`.

**Reaching a specific function node**: from `rootChildren(file)`, `filterIsInstance<LuaFunctionStructureViewTreeElement>().first()`; then `.children`.

**EDT/read-action note**: `configureByText` and PSI reads run on the test EDT under
`BasePlatformTestCase`; no explicit `runReadAction` wrapper is required for these
synchronous PSI accessors (consistent with existing tests in the repo). Icons require the
headless font setup already provided by the gce-builder bootstrap (see `.agents/AGENTS.md`).

## 4. Current gaps (what is untested today)

`glob src/test/**/*Structure*` → **no matches**. The entire `lang/structure/` package has
zero unit tests. Every class in §1 is currently uncovered. This feature adds the first
tests. There is no existing structure-view test to extend — the new file is net-new.

## 5. Files to create

- `src/test/kotlin/net/internetisalie/lunar/lang/structure/LuaStructureViewTest.kt`
  (single file; may be split per phase per the plan, but one file is sufficient).

## Open Questions
None.
