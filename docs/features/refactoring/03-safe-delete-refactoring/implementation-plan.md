---
id: REFACT-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: REFACT-03
status: "planned"
priority: "medium"
folders:
  - "[[features/refactoring/03-safe-delete-refactoring/requirements|requirements]]"
---

# Implementation Plan: REFACT-03 Safe Delete

Depends on NAV-02 (find-usages) and REFACT-02's consolidated provider.

## Phase 1: Availability + processor [Must] — REFACT-03-01/02
- [ ] `LuaRefactoringSupportProvider.isSafeDeleteAvailable` → declaration-site identifiers (§2.1).
- [ ] `LuaSafeDeleteProcessor : SafeDeleteProcessorDelegate` (§2.2/§3.1) + `<refactoring.safeDeleteProcessor>`.
- [ ] `findUsages` via `ReferencesSearch` + `isReferenceTo`; element-to-delete handling (§3.2).
- [ ] Tests: TC-REFACT-03-01 (unused deleted), TC-REFACT-03-03 (unavailable target).

## Phase 2: Conflict path [Should] — REFACT-03-03
- [ ] Mark remaining usages as unsafe so the platform shows the conflict dialog.
- [ ] Test: TC-REFACT-03-02 (used local prompts).

## Verification Tasks
- Unit (`myFixture.safeDelete` / processor invocation): unused removed; used → conflict; keyword
  target unavailable.
- Manual: Safe Delete (Alt+Delete) on an unused local and a used global.
