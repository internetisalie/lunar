---
id: "REFACT-02-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "REFACT-02"
status: "planned"
priority: "medium"
folders:
  - "[[features/refactoring/02-introduce-variable-refactoring/requirements|requirements]]"
---

# Technical Design: REFACT-02 Introduce Variable

## 1. Architecture Overview

### Current State
`net.internetisalie.lunar.lang.insight.LuaLabelRefactoringSupportProvider` extends
`RefactoringSupportProvider` (only in-place rename for labels). No introduce-variable handler.
PSI: expressions are `LuaExpr`; statements are `LuaStatement` under a `LuaBlock`; locals are
`local <attNameList> = <exprList>` (`LuaLocalVarDecl`). Write actions use
`WriteCommandAction.runWriteCommandAction` and `LuaElementFactory`-style creation via
`PsiFileFactory` (a throwaway `LuaFile`).

### Target State
The provider returns a `LuaIntroduceVariableHandler` that extracts the selected `LuaExpr` into a
`local` before the enclosing statement and replaces the occurrence(s), with a name template.

## 2. Core Components

### 2.1 `LuaRefactoringSupportProvider` (replace the label-only provider)
```kotlin
class LuaRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isMemberInplaceRenameAvailable(e: PsiElement, ctx: PsiElement?) = e is LuaLabelName
    override fun getIntroduceVariableHandler(): RefactoringActionHandler = LuaIntroduceVariableHandler()
    override fun isSafeDeleteAvailable(element: PsiElement) = …   // REFACT-03
}
```
Repoints the existing `<lang.refactoringSupport implementationClass=…>`.

### 2.2 `net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler`
- **Key API** (`com.intellij.refactoring.RefactoringActionHandler`):
  ```kotlin
  class LuaIntroduceVariableHandler : RefactoringActionHandler {
      override fun invoke(project: Project, editor: Editor, file: PsiFile, ctx: DataContext?)
      override fun invoke(project: Project, elements: Array<PsiElement>, ctx: DataContext?)  // no-op for editor flow
  }
  ```

## 3. Algorithms

### 3.1 Introduce (`invoke`) — REFACT-02-01/02/03
- **Steps**:
  1. Resolve the target `LuaExpr` from the selection (or `IntroduceTargetChooser` over the
     expressions covering the caret if no selection).
  2. Find the **anchor statement**: the nearest `LuaStatement` ancestor of the expression; its
     parent `LuaBlock` is the insertion block.
  3. Occurrences: collect sibling `LuaExpr`s in the same block textually equal to the target
     (`PsiEquivalenceUtil.areElementsEquivalent`); if >1 and `isOnTheFly`, offer
     `OccurrencesChooser` (REFACT-02-02).
  4. `suggestName(expr)`: a heuristic — for a call `f(...)` → `f`'s name; for `a.b` → `b`; for a
     binary expr → `result`/`value`; ensure uniqueness in scope.
  5. In `WriteCommandAction.runWriteCommandAction`:
     - Create `local <name> = <exprText>` as a `LuaLocalVarDecl` via a throwaway `LuaFile`
       (`PsiFileFactory.createFileFromText(LuaFileType, "local $name = $exprText")`), take its
       first statement.
     - Insert it into the block immediately before the anchor statement (+ a newline).
     - Replace each chosen occurrence with a `LuaNameRef` `<name>`.
  6. Start an inline rename template on the new variable's identifier
     (`TemplateBuilderImpl`/`InplaceVariableIntroducer`) seeded with the suggestion
     (REFACT-02-03).
- **Edge handling**: if the expression is itself a statement target or not a valid `LuaExpr`,
  show a "cannot introduce" hint (`CommonRefactoringUtil.showErrorHint`).

## 4. External Data & Parsing
None — PSI manipulation only.

## 5. Data Flow
Select `1 + 2` in `print(1 + 2)` → anchor = the `print(...)` statement → insert `local sum =
1 + 2` before it → replace selection with `sum` → inline-rename template on `sum`.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| Selection spans a partial expression | `IntroduceTargetChooser` snaps to a full `LuaExpr`. |
| Expression at file top level | anchor is the top-level statement; insert before it. |
| Name collision | `suggestName` disambiguates with a numeric suffix. |
| Multi-value expr (`f()` returning many) | introduced as a single local (first value); documented limitation. |

## 7. Integration Points
```xml
<!-- META-INF/plugin.xml — repoint the existing provider -->
<lang.refactoringSupport language="Lua"
    implementationClass="net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider"/>
```
- Reuses `LuaBlock`/`LuaStatement`/`LuaExpr` PSI, `PsiFileFactory`, `WriteCommandAction`,
  `OccurrencesChooser`, `PsiEquivalenceUtil`.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| REFACT-02-01 Extract expression | M | §3.1 (steps 1–5) |
| REFACT-02-02 Replace all | S | §3.1 step 3 |
| REFACT-02-03 Name + inline rename | S | §3.1 steps 4, 6 |

## 9. Alternatives Considered
- **Reuse the existing provider vs a new one**: a single `LuaRefactoringSupportProvider`
  consolidates rename (existing) + introduce + safe-delete behind one registration.

## 10. Open Questions

_None — feature has cleared the planning bar._
