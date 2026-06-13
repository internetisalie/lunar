---
id: "NAV-06-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "NAV-06"
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/06-hierarchy-view/requirements|requirements]]"
---

# Implementation Plan: NAV-06 Hierarchy View

## Phase 1: Provider + supertypes [Could] — NAV-06-02
- [ ] `LuaTypeHierarchyProvider` + `LuaTypeHierarchyBrowser` (§2.1) + `<typeHierarchyProvider>`.
- [ ] Supertype tree structure (§3.1) with cycle guard.
- [ ] Test: TC-NAV-06-02 (supertypes of `Derived` include `Base`).

## Phase 2: Subtypes [Could] — NAV-06-01
- [ ] Subtype scan over `LuaClassNameIndex` keys (§3.2).
- [ ] Test: TC-NAV-06-01 (`Base` subtypes include `Derived`).

## Phase 3: Method hierarchy [Could] — NAV-06-03
- [ ] Reuse NAV-05 `findSuperMembers` up + subtype scan down (§3.3).

## Verification Tasks
- Integration: hierarchy tree structures yield the expected class nodes (TC-NAV-06-01/02).
- Manual: Navigate ▸ Type Hierarchy on a `@class` shows super/sub trees.
- `NAV-06-DR-01`: add a reverse subtype stub index if the scan is slow.
