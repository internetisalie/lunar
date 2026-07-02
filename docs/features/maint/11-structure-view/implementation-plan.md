---
id: "MAINT-11-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-11"
folders:
  - "[[features/maint/11-structure-view/requirements|requirements]]"
---

# MAINT-11: Implementation Plan

All work lands in one new test file:
`src/test/kotlin/net/internetisalie/lunar/lang/structure/LuaStructureViewTest.kt`
(`BasePlatformTestCase`, `@RunWith(JUnit4::class)`). No production code changes.

Shared private helpers (see design §3): `luaFile(text): LuaFile`,
`rootChildren(file): List<TreeElement>`.

## Phases

### Phase 1: Factory & Model wiring [Must] — MAINT-11-01
Test methods:
- `testFactoryProducesLuaStructureViewModel` (TC-11-01) — `LuaStructureViewFactory().getStructureViewBuilder(file)` is `TreeBasedStructureViewBuilder`; `createStructureViewModel(null)` is `LuaStructureViewModel`.
- `testModelRootSortersAndSuitableClasses` (TC-11-02) — root is `LuaFileStructureViewTreeElement`; sorters contain `Sorter.ALPHA_SORTER`; suitable classes are the 5 declared.

**Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"`

### Phase 2: File-level outline mapping [Must] — MAINT-11-02
Test methods:
- `testFilePresentableTextIsFileName` (TC-11-04).
- `testTopLevelStatementsMapToNodeTypesInOrder` (TC-11-03) — assert the 5 child runtime types in source order.
- `testMultipleLocalVarsBecomeSeparateNodes` (TC-11-05).

**Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"`

### Phase 3: Nested function trees [Must] — MAINT-11-03
Test methods:
- `testGlobalFunctionParamsBeforeBlockChildren` (TC-11-06).
- `testGlobalFunctionPresentableTextAndIcon` (TC-11-07).
- `testLocalFunctionChildrenAndName` (TC-11-08).

**Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"`

### Phase 4: Leaf nodes & presentation [Must] — MAINT-11-04
Test methods:
- `testLabelNodeLeafPresentation` (TC-11-09).
- `testReturnNodeLeafPresentation` (TC-11-10).
- `testLocalVariableNodeLeafPresentation` (TC-11-11).
- `testIsAlwaysLeafClassification` (TC-11-12) — leaves true, function node false.

**Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"`

### Phase 5: Routing utilities [Must] — MAINT-11-05
Test methods:
- `testUnsupportedTopLevelStatementsRouteToEmpty` (TC-11-13) — `if`/`while`/assignment yield empty root children, no exception.
- `testGetFuncBodyChildrenParamsThenBlock` (TC-11-14).
- `testGetFuncBodyChildrenHandlesEmptyBody` (TC-11-15) — no NPE on empty/absent params or body.

**Verify**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"`

## Final verification
- `tooling/gce-builder/gce-builder.sh run "test --tests *LuaStructureViewTest*"` — all 15 test methods green.
- `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` — format the new file (match surrounding IntelliJ-formatter style; ktlintCheck not repo-green, see `.agents/AGENTS.md`).
