---
id: NAV-03-PLAN
title: "Implementation Plan"
type: plan
parent_id: NAV-03
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/03-go-to-class-file-symbol/requirements|requirements]]"
---

# Implementation Plan: NAV-03 Go to Class / File / Symbol

Index-backed `ChooseByName` contributors. Phases map to requirement IDs.

## Phase 1: Go to Class [Must] — NAV-03-01/04
- [ ] `LuaGotoClassContributor` (§2.1) over `LuaClassNameIndex` + `LuaAliasIndex` +
      `<gotoClassContributor>` registration.
- [ ] Tests: TC-NAV-03-01 (class), TC-NAV-03-04 (alias) via `myFixture` + the
      `ChooseByNameContributor` API or a Gotocontributor test.

## Phase 2: Go to Symbol [Must] — NAV-03-02
- [ ] `LuaGotoSymbolContributor` (§2.2) over `LuaGlobalDeclarationIndex` (+ class/alias keys) +
      `<gotoSymbolContributor>` registration.
- [ ] Test: TC-NAV-03-02 (global function/variable).

## Phase 3: Presentation [Should]
- [ ] Ensure `getIcon`/`ItemPresentation` distinguishes class / alias / function / variable;
      location string = file path.

## Verification Tasks
- Integration: assert each contributor's `processNames` includes the expected name and
  `processElementsWithName` returns the right PSI (TC-NAV-03-01/02/04).
- Manual: Navigate ▸ Class / Symbol and Search Everywhere resolve to the definitions; Go to
  File opens `.lua` files.
