---
id: "REFACT-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "REFACT-02"
status: "planned"
priority: "medium"
folders:
  - "[[features/refactoring/02-introduce-variable-refactoring/requirements|requirements]]"
---

# Implementation Plan: REFACT-02 Introduce Variable

## Phase 1: Provider + handler [Must] — REFACT-02-01
- [ ] `LuaRefactoringSupportProvider` (extends the label provider) + repoint
      `<lang.refactoringSupport>`; `getIntroduceVariableHandler`.
- [ ] `LuaIntroduceVariableHandler.invoke` (§3.1 steps 1–5): anchor statement, insert `local`,
      replace occurrence, within `WriteCommandAction`.
- [ ] Tests: TC-REFACT-02-01, TC-REFACT-02-03 (`myFixture` invoke + check result).

## Phase 2: Occurrences + naming [Should] — REFACT-02-02/03
- [ ] `OccurrencesChooser` + `PsiEquivalenceUtil` for replace-all; `suggestName` heuristic.
- [ ] Inline-rename template on the new variable.
- [ ] Test: TC-REFACT-02-02.

## Verification Tasks
- Unit (`myFixture.configureByText` + invoke handler + `checkResult`): introduced local +
  replacement; replace-all; name suggestion.
- Manual: Ctrl+Alt+V on a selected expression.
