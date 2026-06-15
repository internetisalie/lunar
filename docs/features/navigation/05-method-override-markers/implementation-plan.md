---
id: NAV-05-PLAN
title: "Implementation Plan"
type: plan
parent_id: NAV-05
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/05-method-override-markers/requirements|requirements]]"
---

# Implementation Plan: NAV-05 Method Override Markers

A `RelatedItemLineMarkerProvider` over the class hierarchy. Phases map to requirement IDs.

## Phase 1: Override detection + marker [Should] — NAV-05-01/03
- [ ] `LuaOverrideLineMarkerProvider : RelatedItemLineMarkerProvider` (§2.1) +
      `<codeInsight.lineMarkerProvider>` registration.
- [ ] `methodNameIdentifier` (§3.1) and `findSuperMembers` (§3.2) with cycle guard.
- [ ] `NavigationGutterIconBuilder` with `OverridingMethod` icon + super targets.
- [ ] Tests: TC-NAV-05-01 (override marker present + target), TC-NAV-05-03 (navigates).

## Phase 2: Implement marker [Should] — NAV-05-02
- [ ] `isAbstract` discrimination (declaration-only super → `ImplementingMethod`).
- [ ] Test: TC-NAV-05-02.

## Phase 3: Go to Super action [Should] — NAV-05-04
- [ ] `NAV-05-DR-01`: wire the Ctrl+U "Go to Super Method" handler (reuse `findSuperMembers`).

## Verification Tasks
- Unit/integration: `myFixture.findGuttersAtCaret()` / `doHighlighting` asserting the gutter
  icon + targets at a method that overrides a super (TC-NAV-05-01/02), none when no super.
- Manual: click the gutter → navigates to the super method.
